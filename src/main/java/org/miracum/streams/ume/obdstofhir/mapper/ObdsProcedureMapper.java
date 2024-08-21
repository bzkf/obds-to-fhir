package org.miracum.streams.ume.obdstofhir.mapper;

import java.util.*;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.lookup.*;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung.Menge_ST.ST.Menge_Bestrahlung.Bestrahlung;
import org.miracum.streams.ume.obdstofhir.model.Meldeanlass;
import org.miracum.streams.ume.obdstofhir.model.MeldungExport;
import org.miracum.streams.ume.obdstofhir.model.Tupel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObdsProcedureMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ObdsProcedureMapper.class);

  @Value("${app.version}")
  private String appVersion;

  @Value("${app.enableCheckDigitConv}")
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

  public ObdsProcedureMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Bundle mapOnkoResourcesToProcedure(List<MeldungExport> meldungExportList) {

    if (meldungExportList.size() > 2
        || meldungExportList.isEmpty()) { // TODO warum lehre Liste überhaupt möglich
      return null;
    }

    var bundle = new Bundle();

    // get first element of meldungExportList
    var meldungExport = meldungExportList.get(0);

    LOG.debug(
        "Mapping Meldung {} to {}", getReportingIdFromAdt(meldungExport), ResourceType.Procedure);

    var meldung =
        meldungExport
            .getXml_daten()
            .getMenge_Patient()
            .getPatient()
            .getMenge_Meldung()
            .getMeldung();

    var senderId = meldungExport.getXml_daten().getAbsender().getAbsender_ID();
    var softwareId = meldungExport.getXml_daten().getAbsender().getSoftware_ID();

    var patId = getPatIdFromMeldung(meldungExport);
    var pid = patId;
    if (checkDigitConversion) {
      pid = convertId(patId);
    }

    var reportingReason = getReportingReasonFromAdt(meldungExport);

    if (reportingReason == Meldeanlass.BEHANDLUNGSENDE) {
      // OP und Strahlentherapie sofern vorhanden
      // Strahlentherapie kann auch im beginn stehen, op aber nicht
      if (meldung != null
          && meldung.getMenge_OP() != null
          && meldung.getMenge_OP().getOP() != null
          && meldung.getMenge_OP().getOP().getMenge_OPS() != null) {

        var opsSet = meldung.getMenge_OP().getOP().getMenge_OPS().getOP_OPS();
        var distinctOpsSet = new HashSet<>(opsSet); // removes duplicates

        if (distinctOpsSet.size() > 1) {
          bundle =
              addResourceAsEntryInBundle(
                  bundle,
                  createOpProcedure(meldung, pid, senderId, softwareId, null, distinctOpsSet));
        }

        for (var opsCode : distinctOpsSet) {
          bundle =
              addResourceAsEntryInBundle(
                  bundle,
                  createOpProcedure(meldung, pid, senderId, softwareId, opsCode, distinctOpsSet));
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
      Meldung meldung,
      String pid,
      String senderId,
      String softwareId,
      String opsCode,
      HashSet<String> distinctOpsSet) {

    var op = meldung.getMenge_OP().getOP();

    // Create a OP Procedure as in
    // https://simplifier.net/oncology/operation

    var opProcedure = new Procedure();

    var partOfId = pid + "op-partOf-procedure" + op.getOP_ID();

    if (opsCode != null) {

      var opProcedureIdentifier = pid + "op-procedure" + op.getOP_ID() + opsCode;

      // Id
      opProcedure.setId(this.getHash(ResourceType.Procedure, opProcedureIdentifier));

      // PartOf
      if (distinctOpsSet.size() > 1) {
        opProcedure.setPartOf(
            List.of(
                new Reference()
                    .setReference(
                        ResourceType.Procedure
                            + "/"
                            + this.getHash(ResourceType.Procedure, partOfId))));
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
      opProcedure.setId(this.getHash(ResourceType.Procedure, partOfId));
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
                        .setDisplay(displayOPIntentionLookup.lookupDisplay(opIntention))));

    // Status
    opProcedure.setStatus(Procedure.ProcedureStatus.COMPLETED);

    // Category
    opProcedure.setCategory(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem(fhirProperties.getSystems().getSystTherapieart())
                    .setCode("OP")
                    .setDisplay(displaySystTherapieLookup.lookupDisplay(List.of("OP")))));

    // Subject
    opProcedure.setSubject(
        new Reference()
            .setReference(ResourceType.Patient + "/" + this.getHash(ResourceType.Patient, pid))
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
    if (opDateString.isPresent()) {
      opProcedure.setPerformed(convertObdsDateToDateTimeType(opDateString.get()));
    }

    // ReasonReference
    opProcedure.addReasonReference(
        new Reference()
            .setReference(
                ResourceType.Condition
                    + "/"
                    + this.getHash(
                        ResourceType.Condition,
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
                    displayBeurteilungResidualstatusLookup.lookupDisplay(lokalResidualstatus)));
      }

      if (gesamtResidualstatus != null) {
        outComeCodeConcept.addCoding(
            new Coding()
                .setSystem(fhirProperties.getSystems().getGesamtBeurtResidualCS())
                .setCode(gesamtResidualstatus)
                .setDisplay(
                    displayBeurteilungResidualstatusLookup.lookupDisplay(gesamtResidualstatus)));
      }

      if (gesamtResidualstatus != null || lokalResidualstatus != null) {
        opProcedure.setOutcome(outComeCodeConcept);
      }
    }

    // Complication
    if (op.getMenge_Komplikation() != null
        && op.getMenge_Komplikation().getOP_Komplikation() != null
        && !op.getMenge_Komplikation().getOP_Komplikation().isEmpty()) {
      var complicationConcept = new CodeableConcept();
      for (var complication : op.getMenge_Komplikation().getOP_Komplikation()) {
        complicationConcept.addCoding(
            new Coding()
                .setSystem(fhirProperties.getSystems().getOpComplication())
                .setCode(complication)
                .setDisplay(displayOPKomplicationLookup.lookupDisplay(complication)));
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
      Meldeanlass meldeanlass,
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
      stProcedure.setId(this.getHash(ResourceType.Procedure, id));

      // PartOf
      if (distinctPartialRadiations.size() > 1) {
        stProcedure.setPartOf(
            List.of(
                new Reference()
                    .setReference(
                        ResourceType.Procedure
                            + "/"
                            + this.getHash(ResourceType.Procedure, partOfId))));
      }
      // Performed
      DateTimeType stBeginnDateType = convertObdsDateToDateTimeType(stBeginnDateString);
      DateTimeType stEndDateType = convertObdsDateToDateTimeType(stEndDateString);

      if (stBeginnDateType != null && stEndDateType != null) {
        stProcedure.setPerformed(
            new Period().setStartElement(stBeginnDateType).setEndElement(stEndDateType));
      } else if (stBeginnDateType != null) {
        stProcedure.setPerformed(new Period().setStartElement(stBeginnDateType));
      }
    } else {
      // Id
      stProcedure.setId(this.getHash(ResourceType.Procedure, partOfId));

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
                            displayStellungOpLookup.lookupDisplay(
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
                        .setDisplay(displaySystIntentionLookup.lookupDisplay(systIntention))));

    // Status
    if (meldeanlass == Meldeanlass.BEHANDLUNGSENDE) {
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
                    .setDisplay(displaySystTherapieLookup.lookupDisplay(List.of("ST")))));

    // Subject
    stProcedure.setSubject(
        new Reference()
            .setReference(ResourceType.Patient + "/" + this.getHash(ResourceType.Patient, pid))
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
                ResourceType.Condition
                    + "/"
                    + this.getHash(
                        ResourceType.Condition,
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
                  .setCode(displaySideEffectGradingLookup.lookupCode(sideEffectGrading))
                  .setDisplay(displaySideEffectGradingLookup.lookupDisplay(sideEffectGrading))
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

  /**
   * This will return the whole timespan of completed partial radiations as part of a therapy. If no
   * usable start or end dates have been found, this method will return a tuple containing `null`
   * values.
   *
   * @param partialRadiations The partial radiations
   * @return A tuple with start date and end date of all given partial radiations
   */
  public Tupel<Date, Date> getTimeSpanFromPartialRadiations(List<Bestrahlung> partialRadiations) {
    if (null == partialRadiations || partialRadiations.isEmpty()) {
      return new Tupel<>(null, null);
    }

    final var minDates =
        partialRadiations.stream()
            .map(radio -> convertObdsDateToDateTimeType(radio.getST_Beginn_Datum()))
            .filter(Objects::nonNull)
            .map(PrimitiveType::getValue)
            .toList();

    final var maxDates =
        partialRadiations.stream()
            .map(radio -> convertObdsDateToDateTimeType(radio.getST_Ende_Datum()))
            .filter(Objects::nonNull)
            .map(PrimitiveType::getValue)
            .toList();

    return Tupel.<Date, Date>builder()
        .first(minDates.isEmpty() ? null : Collections.min(minDates))
        .second(maxDates.isEmpty() ? null : Collections.max(maxDates))
        .build();
  }
}
