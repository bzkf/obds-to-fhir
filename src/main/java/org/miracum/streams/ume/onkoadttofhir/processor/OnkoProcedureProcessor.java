package org.miracum.streams.ume.onkoadttofhir.processor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.lookup.*;
import org.miracum.streams.ume.onkoadttofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExportList;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportListSerde;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportSerde;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoProcedureProcessor extends OnkoProcessor {

  @Value("${app.version}")
  private String appVersion;

  protected OnkoProcedureProcessor(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  private final OPIntentionVsLookup displayOPIntentionLookup = new OPIntentionVsLookup();

  private final BeurteilungResidualstatusVsLookup displayBeurteilungResidualstatusLookup =
      new BeurteilungResidualstatusVsLookup();

  private final StellungOpVsLookup displayStellungOpLookup = new StellungOpVsLookup();

  private final SystIntentionVsLookup displaySystIntentionLookup = new SystIntentionVsLookup();

  private final SideEffectTherapyGradingLookup displaySideEffectGradingLookup =
      new SideEffectTherapyGradingLookup();

  private final SYSTTherapieartCSLookup displaySystTherapieLookup = new SYSTTherapieartCSLookup();

  @Bean
  public Function<KTable<String, MeldungExport>, KStream<String, Bundle>>
      getMeldungExportProcedureProcessor() {
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
            .mapValues(this.getOnkoToProcedureBundleMapper())
            .filter((key, value) -> value != null)
            .toStream();
  }

  public ValueMapper<MeldungExportList, Bundle> getOnkoToProcedureBundleMapper() {
    return meldungExporte -> {
      List<MeldungExport> meldungExportList =
          prioritiseLatestMeldungExports(
              meldungExporte, Arrays.asList("behandlungsende", "behandlungsbeginn"));

      return mapOnkoResourcesToProcedure(meldungExportList);
    };
  }

  public Bundle mapOnkoResourcesToProcedure(List<MeldungExport> meldungExportList) {

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

    if (Objects.equals(getReportingReasonFromAdt(meldungExport), "behandlungsende")) {

      // OP und Strahlentherapie sofern vorhanden
      // Strahlentherapie kann auch im beginn stehen, op aber nicht
      var patId = meldungExport.getReferenz_nummer();
      var pid = convertId(patId);

      if (meldung != null && meldung.getMenge_OP().getOP() != null) {
        bundle = createOpProcedure(bundle, meldung, pid);
      }

      if (meldung != null
          && meldung.getMenge_ST() != null
          && meldung.getMenge_ST().getST() != null) {
        bundle = createRadiotherapyProcedure(bundle, meldung, pid, "behandlungsende");
      }

    } else if (Objects.equals(getReportingReasonFromAdt(meldungExport), "behandlungsbeginn")) {

      // Strahlentherapie erstellen sofern vorhanden
      var patId = meldungExport.getReferenz_nummer();
      var pid = convertId(patId);

      if (meldung != null
          && meldung.getMenge_ST() != null
          && meldung.getMenge_ST().getST() != null) {
        bundle = createRadiotherapyProcedure(bundle, meldung, pid, "behandlungsbeginn");
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

  public Bundle createOpProcedure(Bundle bundle, Meldung meldung, String pid) {

    var op = meldung.getMenge_OP().getOP();

    // Create a OP Procedure as in
    // https://simplifier.net/oncology/operation

    var opProcedure = new Procedure();

    var opProcedureIdentifier = pid + "OP-Procedure" + op.getOP_ID();

    opProcedure.setId(this.getHash("Condition", opProcedureIdentifier));

    opProcedure
        .getMeta()
        .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkoadt-to-fhir:" + appVersion);
    opProcedure
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getOpProcedure())));

    opProcedure
        .addExtension()
        .setUrl(fhirProperties.getExtensions().getOpIntention())
        .setValue(
            new CodeableConcept()
                .addCoding(
                    new Coding()
                        .setCode(op.getOP_Intention())
                        .setSystem(fhirProperties.getSystems().getOpIntention())
                        .setDisplay(
                            displayOPIntentionLookup.lookupOPIntentionVSDisplay(
                                op.getOP_Intention()))));

    opProcedure.setStatus(Procedure.ProcedureStatus.COMPLETED);

    opProcedure.setCategory(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem(fhirProperties.getSystems().getSystTherapieart())
                    .setCode("OP")
                    .setDisplay(
                        displaySystTherapieLookup.lookupSYSTTherapieartCSLookupDisplay("OP"))));

    var opsCodeConcept = new CodeableConcept();
    for (var opsCode : op.getMenge_OPS().getOP_OPS()) {
      opsCodeConcept.addCoding(
          new Coding()
              .setSystem(fhirProperties.getSystems().getOps())
              .setCode(opsCode)
              .setVersion(op.getOP_OPS_Version()));
    }
    opProcedure.setCode(opsCodeConcept);

    opProcedure.setSubject(
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

    var opDateString = op.getOP_Datum();

    if (opDateString != null) {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
      LocalDate opDate = LocalDate.parse(opDateString, formatter);
      LocalDateTime opDateTime = opDate.atStartOfDay();
      opProcedure.setPerformed(
          new DateTimeType(Date.from(opDateTime.atZone(ZoneId.of("Europe/Berlin")).toInstant())));
    }

    opProcedure.addReasonReference(
        new Reference()
            .setReference(
                "Condition/"
                    + this.getHash(
                        "Condition",
                        pid + "condition" + meldung.getTumorzuordnung().getTumor_ID())));
    // .setIdentifier(); TODO überhaupt nötig?, ggf. problematisch da patId dann im Klartext (Noemi
    // rückmelden)

    var lokalResidualstatus = op.getResidualstatus().getLokale_Beurteilung_Residualstatus();
    var gesamtResidualstatus = op.getResidualstatus().getLokale_Beurteilung_Residualstatus();

    opProcedure.setOutcome(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem(fhirProperties.getSystems().getLokalBeurtResidualCS())
                    .setCode(lokalResidualstatus)
                    .setDisplay(
                        displayBeurteilungResidualstatusLookup
                            .lookupBeurteilungResidualstatusDisplay(lokalResidualstatus)))
            .addCoding(
                new Coding()
                    .setSystem(fhirProperties.getSystems().getGesamtBeurtResidualCS())
                    .setCode(gesamtResidualstatus)
                    .setDisplay(
                        displayBeurteilungResidualstatusLookup
                            .lookupBeurteilungResidualstatusDisplay(gesamtResidualstatus))));

    bundle = addResourceAsEntryInBundle(bundle, opProcedure);

    return bundle;
  }

  public Bundle createRadiotherapyProcedure(
      Bundle bundle, Meldung meldung, String pid, String meldeanlass) {

    var radioTherapy = meldung.getMenge_ST().getST();

    for (var radio : radioTherapy.getMenge_Bestrahlung().getBestrahlung()) {

      // Create a ST Procedure as in
      // https://simplifier.net/oncology/strahlentherapie
      var stProcedure = new Procedure();

      var stBeginnDateString = radio.getST_Beginn_Datum();
      var stEndDateString = radio.getST_Ende_Datum();

      var stProcedureIdentifier =
          pid
              + "ST-Procedure"
              + radioTherapy.getST_ID()
              + stBeginnDateString
              + radio.getST_Applikationsart(); // multiple radiation with same start date possible

      stProcedure.setId(this.getHash("Condition", stProcedureIdentifier));

      stProcedure
          .getMeta()
          .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkoadt-to-fhir:" + appVersion);
      stProcedure
          .getMeta()
          .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getStProcedure())));

      stProcedure
          .addExtension()
          .setUrl(fhirProperties.getExtensions().getStellungOP())
          .setValue(
              new CodeableConcept()
                  .addCoding(
                      new Coding()
                          .setCode(radioTherapy.getST_Stellung_OP())
                          .setSystem(fhirProperties.getSystems().getSystStellungOP())
                          .setDisplay(
                              displayStellungOpLookup.lookupStellungOpDisplay(
                                  radioTherapy.getST_Stellung_OP()))));

      stProcedure
          .addExtension()
          .setUrl(fhirProperties.getExtensions().getSystIntention())
          .setValue(
              new CodeableConcept()
                  .addCoding(
                      new Coding()
                          .setCode(radioTherapy.getST_Intention())
                          .setSystem(fhirProperties.getSystems().getSystIntention())
                          .setDisplay(
                              displaySystIntentionLookup.lookupSystIntentionDisplay(
                                  radioTherapy.getST_Intention()))));

      if (Objects.equals(meldeanlass, "behandlungsende")) {
        stProcedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
      } else {
        stProcedure.setStatus(Procedure.ProcedureStatus.INPROGRESS);
      }

      stProcedure.setCategory(
          new CodeableConcept()
              .addCoding(
                  new Coding()
                      .setSystem(fhirProperties.getSystems().getSystTherapieart())
                      .setCode("ST")
                      .setDisplay(
                          displaySystTherapieLookup.lookupSYSTTherapieartCSLookupDisplay("ST"))));

      stProcedure.setSubject(
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

      DateTimeType stBeginnDateType = null;
      DateTimeType stEndDateType = null;

      if (stBeginnDateString != null) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        LocalDate stBeginnDate = LocalDate.parse(stBeginnDateString, formatter);
        LocalDateTime stBeginnDateTime = stBeginnDate.atStartOfDay();
        stBeginnDateType =
            new DateTimeType(
                Date.from(stBeginnDateTime.atZone(ZoneId.of("Europe/Berlin")).toInstant()));
      }

      if (stEndDateString != null) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        LocalDate stEndDate = LocalDate.parse(stEndDateString, formatter);
        LocalDateTime stEndDateTime = stEndDate.atStartOfDay();
        stEndDateType =
            new DateTimeType(
                Date.from(stEndDateTime.atZone(ZoneId.of("Europe/Berlin")).toInstant()));
      }

      if (stBeginnDateType != null && stEndDateType != null) {
        stProcedure.setPerformed(
            new Period().setStartElement(stBeginnDateType).setEndElement(stEndDateType));
      } else if (stBeginnDateType != null) {
        stProcedure.setPerformed(new Period().setStartElement(stBeginnDateType));
      }

      stProcedure.addReasonReference(
          new Reference()
              .setReference(
                  "Condition/"
                      + this.getHash(
                          "Condition",
                          pid + "condition" + meldung.getTumorzuordnung().getTumor_ID())));
      // .setIdentifier(); TODO überhaupt nötig?, ggf. problematisch da patId dann im Klartext
      // (Noemi
      // rückmelden)

      if (radioTherapy.getMenge_Nebenwirkung() != null) {

        for (var complication : radioTherapy.getMenge_Nebenwirkung().getST_Nebenwirkung()) {

          var sideEffectsCodeConcept = new CodeableConcept();
          var sideEffectGrading = complication.getNebenwirkung_Grad();
          var siedeEffectType = complication.getNebenwirkung_Art();

          if (sideEffectGrading != null
              && !sideEffectGrading.equals(
                  "U")) { // TODO U wirklich rausfiltern oder bspw. data absent reason
            sideEffectsCodeConcept.addCoding(
                new Coding()
                    .setCode(
                        displaySideEffectGradingLookup.lookupSideEffectTherapyGradingCode(
                            sideEffectGrading))
                    .setDisplay(
                        displaySideEffectGradingLookup.lookupSideEffectTherapyGradingDisplay(
                            sideEffectGrading))
                    .setSystem(fhirProperties.getSystems().getCtcaeGrading()));
          }

          if (siedeEffectType != null) {
            sideEffectsCodeConcept.addCoding(
                new Coding()
                    .setCode(siedeEffectType)
                    .setSystem(fhirProperties.getSystems().getSideEffectTypeOid()));
          }

          if (sideEffectsCodeConcept.hasCoding()) {
            stProcedure.addComplication(sideEffectsCodeConcept);
          }
        }
      }

      bundle = addResourceAsEntryInBundle(bundle, stProcedure);
    }

    return bundle;
  }
}
