package org.miracum.streams.ume.onkoadttofhir.processor;

import java.util.Arrays;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoMedicationStatementProcessor extends OnkoProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(OnkoMedicationStatementProcessor.class);

  @Value("${app.version}")
  private String appVersion;

  @Value("#{new Boolean('${app.enableCheckDigitConversion}')}")
  private boolean checkDigitConversion;

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

    if (meldungExportList.size() > 2 || meldungExportList.isEmpty()) {
      return null;
    }

    var bundle = new Bundle();

    // get last element of meldungExportList
    // TODO ueberpruefen ob letzte Meldung reicht
    var meldungExport = meldungExportList.get(meldungExportList.size() - 1);

    LOG.debug(
        "Mapping Meldung {} to {}", getReportingIdFromAdt(meldungExport), "medicationStatement");

    var meldung =
        meldungExport
            .getXml_daten()
            .getMenge_Patient()
            .getPatient()
            .getMenge_Meldung()
            .getMeldung();

    var senderId = meldungExport.getXml_daten().getAbsender().getAbsender_ID();
    var softwareId = meldungExport.getXml_daten().getAbsender().getSoftware_ID();

    var patId = meldungExport.getReferenz_nummer();
    var pid = patId;
    if (checkDigitConversion) {
      pid = convertId(patId);
    }

    if (meldung != null
        && meldung.getMenge_SYST() != null
        && meldung.getMenge_SYST().getSYST() != null) {

      var systemTherapy = meldung.getMenge_SYST().getSYST();

      if (systemTherapy.getMenge_Substanz() != null
          && systemTherapy.getMenge_Substanz().getSYST_Substanz().size() > 1) {

        bundle =
            addResourceAsEntryInBundle(
                bundle,
                createSystemtherapyMedicationStatement(
                    meldung,
                    pid,
                    senderId,
                    softwareId,
                    getReportingReasonFromAdt(meldungExport),
                    null));

        for (var substance : systemTherapy.getMenge_Substanz().getSYST_Substanz()) {
          bundle =
              addResourceAsEntryInBundle(
                  bundle,
                  createSystemtherapyMedicationStatement(
                      meldung,
                      pid,
                      senderId,
                      softwareId,
                      getReportingReasonFromAdt(meldungExport),
                      substance));
        }
      }

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

  // Either creates a Part-of-MedicationStatement or a default MedicationStatement (if no
  // Substances are documented) in ADT
  // as in https://simplifier.net/oncology/systemtherapie
  public MedicationStatement createSystemtherapyMedicationStatement(
      Meldung meldung,
      String pid,
      String senderId,
      String softwareId,
      String meldeanlass,
      String substance) {

    var systemTherapy = meldung.getMenge_SYST().getSYST();

    var stMedicationStatement = new MedicationStatement();

    var partOfId = pid + "st-partOf-medicationStatement" + systemTherapy.getSYST_ID();

    if (substance != null) {
      // Id
      var id = pid + "st-medicationStatement" + systemTherapy.getSYST_ID() + substance;
      stMedicationStatement.setId(this.getHash("MedicationStatement", id));

      // PartOf
      if (systemTherapy.getMenge_Substanz() != null
          && systemTherapy.getMenge_Substanz().getSYST_Substanz().size() > 1) {
        stMedicationStatement.setPartOf(
            List.of(
                new Reference()
                    .setReference(
                        "MedicationStatement/" + this.getHash("MedicationStatement", partOfId))));
        // Medication
        stMedicationStatement.setMedication(new CodeableConcept().setText(substance));
      }

    } else {
      // Id
      stMedicationStatement.setId(this.getHash("MedicationStatement", partOfId));
      var absentProcedureDate = new CodeableConcept();
      absentProcedureDate.addExtension(
          fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("not-applicable"));
      stMedicationStatement.setMedication(absentProcedureDate);
    }

    /// Meta
    stMedicationStatement
        .getMeta()
        .setSource(generateProfileMetaSource(senderId, softwareId, appVersion));
    stMedicationStatement
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getSystMedStatement())));

    // Status
    if (Objects.equals(meldeanlass, "behandlungsende")) {
      stMedicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.COMPLETED);
    } else {
      stMedicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.ACTIVE);
    }

    // Category
    var category = systemTherapy.getMenge_Therapieart().getSYST_Therapieart();
    var therapyCategory = new CodeableConcept();
    therapyCategory.addCoding(
        new Coding()
            .setSystem(fhirProperties.getSystems().getSystTherapieart())
            .setCode(displaySystTherapieLookup.lookupSYSTTherapieartCSLookupCode(category))
            .setDisplay(displaySystTherapieLookup.lookupSYSTTherapieartCSLookupDisplay(category)));

    if (systemTherapy.getSYST_Therapieart_Anmerkung() != null) {
      therapyCategory.setText(systemTherapy.getSYST_Therapieart_Anmerkung());
    }
    stMedicationStatement.setCategory(therapyCategory);

    // Extension
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

    // Subject
    stMedicationStatement.setSubject(
        new Reference()
            .setReference("Patient/" + this.getHash("Patient", pid))
            .setIdentifier(
                new Identifier()
                    .setSystem(fhirProperties.getSystems().getPatientId())
                    .setValue(pid)));

    // Effective
    var systBeginnDateString = systemTherapy.getSYST_Beginn_Datum();
    var systEndDateString = systemTherapy.getSYST_Ende_Datum();

    DateTimeType systBeginnDateType = null;
    DateTimeType systEndDateType = null;

    if (systBeginnDateString != null) {
      systBeginnDateType = extractDateTimeFromADTDate(systBeginnDateString);
    }

    if (systEndDateString != null) {
      systEndDateType = extractDateTimeFromADTDate(systEndDateString);
    }

    if (systBeginnDateType != null && systEndDateType != null) {
      stMedicationStatement.setEffective(
          new Period().setStartElement(systBeginnDateType).setEndElement(systEndDateType));
    } else if (systBeginnDateType != null) {
      stMedicationStatement.setEffective(new Period().setStartElement(systBeginnDateType));
    }

    // ReasonReference
    stMedicationStatement.addReasonReference(
        new Reference()
            .setReference(
                "Condition/"
                    + this.getHash(
                        "Condition",
                        pid + "condition" + meldung.getTumorzuordnung().getTumor_ID())));

    return stMedicationStatement;
  }
}
