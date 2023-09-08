package org.miracum.streams.ume.onkoadttofhir.mapper;

import java.util.*;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.lookup.*;
import org.miracum.streams.ume.onkoadttofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung;
import org.miracum.streams.ume.onkoadttofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung.Menge_ST.ST.Menge_Bestrahlung.Bestrahlung;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.miracum.streams.ume.onkoadttofhir.model.Tupel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoProcedureMapper extends OnkoToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(OnkoProcedureMapper.class);

  @Value("${app.version}")
  private String appVersion;

  @Value("${app.enableCheckDigitConversion}")
  private boolean checkDigitConversion;

  private final OPIntentionVsLookup displayOPIntentionLookup = new OPIntentionVsLookup();

  private final BeurteilungResidualstatusVsLookup displayBeurteilungResidualstatusLookup =
      new BeurteilungResidualstatusVsLookup();

  private final StellungOpVsLookup displayStellungOpLookup = new StellungOpVsLookup();

  private final SystIntentionVsLookup displaySystIntentionLookup = new SystIntentionVsLookup();

  private final SideEffectTherapyGradingLookup displaySideEffectGradingLookup =
      new SideEffectTherapyGradingLookup();

  private final SYSTTherapieartCSLookup displaySystTherapieLookup = new SYSTTherapieartCSLookup();

  private final OPKomplikationVsLookup displayOPKomplicationLookup = new OPKomplikationVsLookup();

  public OnkoProcedureMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Bundle mapOnkoResourcesToProcedure(List<MeldungExport> meldungExportList) {

    if (meldungExportList.size() > 2
        || meldungExportList.isEmpty()) { // TODO warum lehre Liste überhaupt möglich
      return null;
    }

    var bundle = new Bundle();

    // get last element of meldungExportList
    // TODO ueberpruefen ob letzte Meldung reicht
    var meldungExport = meldungExportList.get(meldungExportList.size() - 1);

    LOG.debug("Mapping Meldung {} to {}", getReportingIdFromAdt(meldungExport), "procedure");

    var meldung =
        meldungExport
            .getXml_daten()
            .getMenge_Patient()
            .getPatient()
            .getMenge_Meldung()
            .getMeldung();

    var senderId = meldungExport.getXml_daten().getAbsender().getAbsender_ID();
    var softwareId = meldungExport.getXml_daten().getAbsender().getSoftware_ID();

    var patId = getPatIdFromAdt(meldungExport);
    var pid = patId;
    if (checkDigitConversion) {
      pid = convertId(patId);
    }

    var reportingReason = getReportingReasonFromAdt(meldungExport);

    if (Objects.equals(reportingReason, "behandlungsende")) {

      // OP und Strahlentherapie sofern vorhanden
      // Strahlentherapie kann auch im beginn stehen, op aber nicht
      if (meldung != null
          && meldung.getMenge_OP() != null
          && meldung.getMenge_OP().getOP() != null) {

        var opsSet = meldung.getMenge_OP().getOP().getMenge_OPS();

        if (opsSet != null && opsSet.getOP_OPS().size() > 1) {
          bundle =
              addResourceAsEntryInBundle(
                  bundle, createOpProcedure(meldung, pid, senderId, softwareId, null));
        }

        for (var opsCode : opsSet.getOP_OPS()) {
          bundle =
              addResourceAsEntryInBundle(
                  bundle, createOpProcedure(meldung, pid, senderId, softwareId, opsCode));
        }
      }
    }

    if (meldung != null && meldung.getMenge_ST() != null && meldung.getMenge_ST().getST() != null) {
      var radioTherapy = meldung.getMenge_ST().getST();

      if (radioTherapy.getMenge_Bestrahlung() != null) {
        var partialRadiations = radioTherapy.getMenge_Bestrahlung().getBestrahlung();
        var distinctPartialRadiations = new HashSet<>(partialRadiations); // removes duplicates
        var timeSpan = getTimeSpanFromPartialRadiations(partialRadiations);

        if (distinctPartialRadiations.size() > 1) {
          bundle =
              addResourceAsEntryInBundle(
                  bundle,
                  createRadiotherapyProcedure(
                      meldung,
                      pid,
                      senderId,
                      softwareId,
                      reportingReason,
                      null,
                      timeSpan,
                      distinctPartialRadiations));
        }

        for (var radio : distinctPartialRadiations) {
          bundle =
              addResourceAsEntryInBundle(
                  bundle,
                  createRadiotherapyProcedure(
                      meldung,
                      pid,
                      senderId,
                      softwareId,
                      reportingReason,
                      radio,
                      null,
                      distinctPartialRadiations));
        }
      }
    }

    bundle.setType(Bundle.BundleType.TRANSACTION);

    if (bundle.getEntry().isEmpty()) {
      return null;
    } else {
      return bundle;
    }
  }

  public Procedure createOpProcedure(
      Meldung meldung, String pid, String senderId, String softwareId, String opsCode) {

    var op = meldung.getMenge_OP().getOP();

    // Create a OP Procedure as in
    // https://simplifier.net/oncology/operation

    var opProcedure = new Procedure();

    var partOfId = pid + "op-partOf-procedure" + op.getOP_ID();

    if (opsCode != null) {

      var opProcedureIdentifier = pid + "op-procedure" + op.getOP_ID() + opsCode;

      // Id
      opProcedure.setId(this.getHash("Procedure", opProcedureIdentifier));

      // PartOf
      if (op.getMenge_OPS().getOP_OPS().size() > 1) {
        opProcedure.setPartOf(
            List.of(
                new Reference().setReference("Procedure/" + this.getHash("Procedure", partOfId))));
      }

      // Code
      var opsCodeConcept =
          new CodeableConcept()
              .addCoding(
                  new Coding()
                      .setSystem(fhirProperties.getSystems().getOps())
                      .setCode(opsCode)
                      .setVersion(op.getOP_OPS_Version()));

      opProcedure.setCode(opsCodeConcept);

    } else {
      // Id
      opProcedure.setId(this.getHash("Procedure", partOfId));
    }

    // Meta
    opProcedure.getMeta().setSource(generateProfileMetaSource(senderId, softwareId, appVersion));
    opProcedure
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getOpProcedure())));

    // check if opIntention is defined in xml, otherwise set "X"
    var opIntention = "X";
    if (op.getOP_Intention() != null) {
      opIntention = op.getOP_Intention();
    }
    // Extensions
    opProcedure
        .addExtension()
        .setUrl(fhirProperties.getExtensions().getOpIntention())
        .setValue(
            new CodeableConcept()
                .addCoding(
                    new Coding()
                        .setCode(opIntention)
                        .setSystem(fhirProperties.getSystems().getOpIntention())
                        .setDisplay(
                            displayOPIntentionLookup.lookupOPIntentionVSDisplay(opIntention))));

    // Status
    opProcedure.setStatus(Procedure.ProcedureStatus.COMPLETED);

    // Category
    opProcedure.setCategory(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem(fhirProperties.getSystems().getSystTherapieart())
                    .setCode("OP")
                    .setDisplay(
                        displaySystTherapieLookup.lookupSYSTTherapieartCSLookupDisplay(
                            List.of("OP")))));

    // Subject
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

    // Performed
    var opDateString = op.getOP_Datum();
    if (opDateString != null) {
      opProcedure.setPerformed(extractDateTimeFromADTDate(opDateString));
    }

    // ReasonReference
    opProcedure.addReasonReference(
        new Reference()
            .setReference(
                "Condition/"
                    + this.getHash(
                        "Condition",
                        pid + "condition" + meldung.getTumorzuordnung().getTumor_ID())));

    // Outcome
    if (op.getResidualstatus() != null) {
      var lokalResidualstatus = op.getResidualstatus().getLokale_Beurteilung_Residualstatus();
      var gesamtResidualstatus = op.getResidualstatus().getGesamtbeurteilung_Residualstatus();

      var outComeCodeConcept = new CodeableConcept();

      if (lokalResidualstatus != null) {
        outComeCodeConcept.addCoding(
            new Coding()
                .setSystem(fhirProperties.getSystems().getLokalBeurtResidualCS())
                .setCode(lokalResidualstatus)
                .setDisplay(
                    displayBeurteilungResidualstatusLookup.lookupBeurteilungResidualstatusDisplay(
                        lokalResidualstatus)));
      }

      if (gesamtResidualstatus != null) {
        outComeCodeConcept.addCoding(
            new Coding()
                .setSystem(fhirProperties.getSystems().getGesamtBeurtResidualCS())
                .setCode(gesamtResidualstatus)
                .setDisplay(
                    displayBeurteilungResidualstatusLookup.lookupBeurteilungResidualstatusDisplay(
                        gesamtResidualstatus)));
      }

      if (gesamtResidualstatus != null || lokalResidualstatus != null) {
        opProcedure.setOutcome(outComeCodeConcept);
      }
    }

    // Complication
    if (op.getMenge_Komplikation() != null
        && op.getMenge_Komplikation().getOP_Komplikation() != null
        && op.getMenge_Komplikation().getOP_Komplikation().size() > 0) {
      var complicationConcept = new CodeableConcept();
      for (var complication : op.getMenge_Komplikation().getOP_Komplikation()) {
        complicationConcept.addCoding(
            new Coding()
                .setSystem(fhirProperties.getSystems().getOpComplication())
                .setCode(complication)
                .setDisplay(
                    displayOPKomplicationLookup.lookupOPKomplikationVSDisplay(complication)));
      }
      opProcedure.setComplication(List.of(complicationConcept));
    }

    return opProcedure;
  }

  // Create a ST Procedure as in
  // https://simplifier.net/oncology/strahlentherapie
  public Procedure createRadiotherapyProcedure(
      Meldung meldung,
      String pid,
      String senderId,
      String softwareId,
      String meldeanlass,
      Bestrahlung radio,
      Tupel<Date, Date> timeSpan,
      HashSet<Bestrahlung> distinctPartialRadiations) {

    var radioTherapy = meldung.getMenge_ST().getST();

    var partOfId = pid + "st-partOf-procedure" + radioTherapy.getST_ID();

    var stProcedure = new Procedure();

    if (radio != null) {
      var stBeginnDateString = radio.getST_Beginn_Datum();
      var stEndDateString = radio.getST_Ende_Datum();

      var id =
          pid
              + "st-procedure"
              + radioTherapy.getST_ID()
              + stBeginnDateString
              + radio.getST_Zielgebiet()
              + radio.getST_Seite_Zielgebiet()
              + radio.getST_Applikationsart();

      // Id
      stProcedure.setId(this.getHash("Procedure", id));

      // PartOf
      if (distinctPartialRadiations.size() > 1) {
        stProcedure.setPartOf(
            List.of(
                new Reference().setReference("Procedure/" + this.getHash("Procedure", partOfId))));
      }
      // Performed
      DateTimeType stBeginnDateType = extractDateTimeFromADTDate(stBeginnDateString);
      DateTimeType stEndDateType = extractDateTimeFromADTDate(stEndDateString);

      if (stBeginnDateType != null && stEndDateType != null) {
        stProcedure.setPerformed(
            new Period().setStartElement(stBeginnDateType).setEndElement(stEndDateType));
      } else if (stBeginnDateType != null) {
        stProcedure.setPerformed(new Period().setStartElement(stBeginnDateType));
      }
    } else {
      // Id
      stProcedure.setId(this.getHash("Procedure", partOfId));

      // Performed
      if (timeSpan != null) {
        var minDate = timeSpan.getFirst();
        var maxDate = timeSpan.getSecond();
        if (minDate != null && maxDate != null) {
          stProcedure.setPerformed(
              new Period()
                  .setStartElement(new DateTimeType(minDate))
                  .setEndElement(new DateTimeType(maxDate)));
        } else if (minDate != null) {
          stProcedure.setPerformed(new Period().setStartElement(new DateTimeType(minDate)));
        }
      }
    }

    // Meta
    stProcedure.getMeta().setSource(generateProfileMetaSource(senderId, softwareId, appVersion));
    stProcedure
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getStProcedure())));

    // Extensions
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

    // check if systIntention is defined in xml, otherwise set "X"
    var systIntention = "X";
    if (radioTherapy.getST_Intention() != null) {
      systIntention = radioTherapy.getST_Intention();
    }
    stProcedure
        .addExtension()
        .setUrl(fhirProperties.getExtensions().getSystIntention())
        .setValue(
            new CodeableConcept()
                .addCoding(
                    new Coding()
                        .setCode(systIntention)
                        .setSystem(fhirProperties.getSystems().getSystIntention())
                        .setDisplay(
                            displaySystIntentionLookup.lookupSystIntentionDisplay(systIntention))));

    // Status
    if (Objects.equals(meldeanlass, "behandlungsende")) {
      stProcedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
    } else {
      stProcedure.setStatus(Procedure.ProcedureStatus.INPROGRESS);
    }

    // Category
    stProcedure.setCategory(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem(fhirProperties.getSystems().getSystTherapieart())
                    .setCode("ST")
                    .setDisplay(
                        displaySystTherapieLookup.lookupSYSTTherapieartCSLookupDisplay(
                            List.of("ST")))));

    // Subject
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

    // ReasonReference
    stProcedure.addReasonReference(
        new Reference()
            .setReference(
                "Condition/"
                    + this.getHash(
                        "Condition",
                        pid + "condition" + meldung.getTumorzuordnung().getTumor_ID())));

    // Complication
    if (radioTherapy.getMenge_Nebenwirkung() != null) {

      for (var complication : radioTherapy.getMenge_Nebenwirkung().getST_Nebenwirkung()) {

        var sideEffectsCodeConcept = new CodeableConcept();
        var sideEffectGrading = complication.getNebenwirkung_Grad();
        var siedeEffectType = complication.getNebenwirkung_Art();

        // also excludes unknown side effects
        if (sideEffectGrading != null && !sideEffectGrading.equals("U")) {
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

    return stProcedure;
  }

  public Tupel<Date, Date> getTimeSpanFromPartialRadiations(List<Bestrahlung> partialRadiations) {
    // min Beginndatum
    List<Date> minDates = new ArrayList<>();
    // maxBeginndatum
    List<Date> maxDates = new ArrayList<>();

    for (var radio : partialRadiations) {
      if (radio.getST_Beginn_Datum() != null) {
        minDates.add(extractDateTimeFromADTDate(radio.getST_Beginn_Datum()).getValue());
      }
      if (radio.getST_Ende_Datum() != null) {
        maxDates.add(extractDateTimeFromADTDate(radio.getST_Ende_Datum()).getValue());
      }
    }

    Date minDate = null;
    Date maxDate = null;

    if (!minDates.isEmpty()) {
      minDate = Collections.min(minDates);
    }
    if (!maxDates.isEmpty()) {
      maxDate = Collections.min(maxDates);
    }

    return new Tupel<>(minDate, maxDate);
  }
}
