package org.miracum.streams.ume.onkoadttofhir.processor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.lookup.SYSTTherapieartCSLookup;
import org.miracum.streams.ume.onkoadttofhir.lookup.StellungOpVsLookup;
import org.miracum.streams.ume.onkoadttofhir.lookup.SystIntentionVsLookup;
import org.miracum.streams.ume.onkoadttofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExportList;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportListSerde;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportSerde;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoMedicationStatementProcessor extends OnkoProcessor {

  @Value("${app.version}")
  private String appVersion;

  private final StellungOpVsLookup displayStellungOpLookup = new StellungOpVsLookup();

  private final SystIntentionVsLookup displaySystIntentionLookup = new SystIntentionVsLookup();

  private final SYSTTherapieartCSLookup displaySystTherapieLookup = new SYSTTherapieartCSLookup();

  protected OnkoMedicationStatementProcessor(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  @Bean
  public Function<KTable<String, MeldungExport>, KStream<String, Bundle>>
      getMeldungExportMedicationStProcessor() {
    return stringOnkoMeldungExpTable ->
        // return (stringOnkoMeldungExpTable) ->
        stringOnkoMeldungExpTable
            .filter(
                (key, value) ->
                    value
                            .getXml_daten()
                            .getMenge_Patient()
                            .getPatient()
                            .getMenge_Meldung()
                            .getMeldung()
                            .getMenge_Tumorkonferenz()
                        == null) // ignore tumor conferences
            .filter(
                ((key, value) ->
                    Objects.equals(getReportingReasonFromAdt(value), "behandlungsende")
                        || Objects.equals(getReportingReasonFromAdt(value), "behandlungsbeginn")))
            .groupBy(
                (key, value) ->
                    KeyValue.pair(
                        "Struct{REFERENZ_NUMMER="
                            + value.getReferenz_nummer()
                            + ",TUMOR_ID="
                            + getTumorIdFromAdt(value)
                            + "}",
                        value),
                Grouped.with(Serdes.String(), new MeldungExportSerde()))
            .aggregate(
                MeldungExportList::new,
                (key, value, aggregate) -> aggregate.addElement(value),
                (key, value, aggregate) -> aggregate.removeElement(value),
                Materialized.with(Serdes.String(), new MeldungExportListSerde()))
            .mapValues(this.getOnkoToMedicationStBundleMapper())
            .filter((key, value) -> value != null)
            .toStream();
  }

  public ValueMapper<MeldungExportList, Bundle> getOnkoToMedicationStBundleMapper() {
    return meldungExporte -> {
      List<MeldungExport> meldungExportList =
          prioritiseLatestMeldungExports(
              meldungExporte, Arrays.asList("behandlungsende", "behandlungsbeginn"));

      return mapOnkoResourcesToMedicationStatement(meldungExportList);
    };
  }

  public Bundle mapOnkoResourcesToMedicationStatement(List<MeldungExport> meldungExportList) {

    if (meldungExportList.size() > 2) {
      return null;
    }

    var bundle = new Bundle();

    // get last element of meldungExportList
    // TODO ueberpruefen ob letzte Meldung reicht
    var meldungExport = meldungExportList.get(meldungExportList.size() - 1);

    var meldung =
        meldungExport
            .getXml_daten()
            .getMenge_Patient()
            .getPatient()
            .getMenge_Meldung()
            .getMeldung();

    var patId = meldungExport.getReferenz_nummer();
    var pid = convertId(patId);

    if (meldung != null
        && meldung.getMenge_SYST() != null
        && meldung.getMenge_SYST().getSYST() != null) {
      bundle =
          createSystemtherapyMedicationSt(
              bundle, meldung, pid, getReportingReasonFromAdt(meldungExport));
    } else {
      return null;
    }

    bundle.setType(Bundle.BundleType.TRANSACTION);

    // check if Bundle is empty (has entries)
    if (bundle.getEntry().size() > 0) {
      return bundle;
    } else {
      return null;
    }
  }

  public Bundle createSystemtherapyMedicationSt(
      Bundle bundle, Meldung meldung, String pid, String meldeanlass) {

    var systemTherapy = meldung.getMenge_SYST().getSYST();

    // Create a Medication Statement as in
    // https://simplifier.net/oncology/systemtherapie
    var stMedicationStatement = new MedicationStatement();

    var stMedicationStatementIdentifier =
        pid + "ST-MedicationStatement" + systemTherapy.getSYST_ID();

    stMedicationStatement.setId(
        this.getHash("MedicationStatement", stMedicationStatementIdentifier));

    stMedicationStatement
        .getMeta()
        .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkoadt-to-fhir:" + appVersion);
    stMedicationStatement
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getSystMedStatement())));

    stMedicationStatement
        .addExtension()
        .setUrl(fhirProperties.getExtensions().getStellungOP())
        .setValue(
            new CodeableConcept()
                .addCoding(
                    new Coding()
                        .setCode(systemTherapy.getSYST_Stellung_OP())
                        .setSystem(fhirProperties.getSystems().getSystStellungOP())
                        .setDisplay(
                            displayStellungOpLookup.lookupStellungOpDisplay(
                                systemTherapy.getSYST_Stellung_OP()))));

    stMedicationStatement
        .addExtension()
        .setUrl(fhirProperties.getExtensions().getSystIntention())
        .setValue(
            new CodeableConcept()
                .addCoding(
                    new Coding()
                        .setCode(systemTherapy.getSYST_Intention())
                        .setSystem(fhirProperties.getSystems().getSystIntention())
                        .setDisplay(
                            displaySystIntentionLookup.lookupSystIntentionDisplay(
                                systemTherapy.getSYST_Intention()))));

    stMedicationStatement
        .addExtension()
        .setUrl(fhirProperties.getExtensions().getSysTheraProto())
        .setValue(new CodeableConcept().setText(systemTherapy.getSYST_Protokoll()));

    if (Objects.equals(meldeanlass, "behandlungsende")) {
      stMedicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.COMPLETED);
    } else {
      stMedicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.ACTIVE);
    }

    var therapyCategory = new CodeableConcept();
    for (var categegory : systemTherapy.getMenge_Therapieart().getSYST_Therapieart()) {

      therapyCategory.addCoding(
          new Coding()
              .setSystem(fhirProperties.getSystems().getSystTherapieart())
              .setCode(categegory)
              .setDisplay(
                  displaySystTherapieLookup.lookupSYSTTherapieartCSLookupDisplay(categegory)));
    }

    stMedicationStatement.setCategory(therapyCategory);

    if (systemTherapy.getMenge_Substanz() != null
        && !systemTherapy.getMenge_Substanz().getSYST_Substanz().isEmpty()) {
      // TODO mehrere Substanzen aber nur eine Medicationreferenz bzw. CodeableConcept erlaubt
      //  stMedicationStatement.setMedication(new CodeableConcept().setText(systemTherapy.getSys));
    }

    stMedicationStatement.setSubject(
        new Reference()
            .setReference("Patient/" + this.getHash("Patient", pid))
            .setIdentifier(
                new Identifier()
                    .setSystem(fhirProperties.getSystems().getPatientId())
                    .setType(
                        new CodeableConcept(
                            new Coding(
                                fhirProperties.getSystems().getIdentifierType(), "MR", null)))
                    .setValue(pid)));

    var systBeginnDateString = systemTherapy.getSYST_Beginn_Datum();
    var systEndDateString = systemTherapy.getSYST_Ende_Datum();

    DateTimeType systBeginnDateType = null;
    DateTimeType systEndDateType = null;

    if (systBeginnDateString != null) {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
      LocalDate systBeginnDate = LocalDate.parse(systBeginnDateString, formatter);
      LocalDateTime systBeginnDateTime = systBeginnDate.atStartOfDay();
      systBeginnDateType =
          new DateTimeType(
              Date.from(systBeginnDateTime.atZone(ZoneId.of("Europe/Berlin")).toInstant()));
    }

    if (systEndDateString != null) {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
      LocalDate systEndDate = LocalDate.parse(systEndDateString, formatter);
      LocalDateTime systEndDateTime = systEndDate.atStartOfDay();
      systEndDateType =
          new DateTimeType(
              Date.from(systEndDateTime.atZone(ZoneId.of("Europe/Berlin")).toInstant()));
    }

    if (systBeginnDateType != null && systEndDateType != null) {
      stMedicationStatement.setEffective(
          new Period().setStartElement(systBeginnDateType).setEndElement(systEndDateType));
    } else if (systBeginnDateType != null) {
      stMedicationStatement.setEffective(new Period().setStartElement(systBeginnDateType));
    }

    stMedicationStatement.addReasonReference(
        new Reference()
            .setReference(
                "Condition/"
                    + this.getHash(
                        "Condition",
                        pid + "condition" + meldung.getTumorzuordnung().getTumor_ID())));
    // .setIdentifier(); TODO überhaupt nötig?, ggf. problematisch da patId dann im Klartext
    // (Noemi
    // rückmelden)

    bundle = addResourceAsEntryInBundle(bundle, stMedicationStatement);

    return bundle;
  }
}
