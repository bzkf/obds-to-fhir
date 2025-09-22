package org.miracum.streams.ume.obdstofhir.mapper;

import java.util.HashSet;
import java.util.List;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.lookup.SYSTTherapieartCSLookup;
import org.miracum.streams.ume.obdstofhir.lookup.StellungOpVsLookup;
import org.miracum.streams.ume.obdstofhir.lookup.SystIntentionVsLookup;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung;
import org.miracum.streams.ume.obdstofhir.model.Meldeanlass;
import org.miracum.streams.ume.obdstofhir.model.MeldungExport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObdsMedicationStatementMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ObdsMedicationStatementMapper.class);

  @Value("${app-version}")
  private String appVersion;

  @Value("${app.enableCheckDigitConv}")
  private boolean checkDigitConversion;

  public ObdsMedicationStatementMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Bundle mapOnkoResourcesToMedicationStatement(List<MeldungExport> meldungExportList) {

    if (meldungExportList.size() > 2 || meldungExportList.isEmpty()) {
      return null;
    }

    var bundle = new Bundle();

    // get first element of meldungExportList
    var meldungExport = meldungExportList.getFirst();

    LOG.debug(
        "Mapping Meldung {} to {}",
        getReportingIdFromAdt(meldungExport),
        ResourceType.MedicationStatement);

    var meldung =
        meldungExport
            .getXml_daten()
            .getMenge_Patient()
            .getPatient()
            .getMenge_Meldung()
            .getMeldung();

    var senderId = meldungExport.getXml_daten().getAbsender().getAbsender_ID();
    var softwareId = meldungExport.getXml_daten().getAbsender().getSoftware_ID();

    final var pid = getConvertedPatIdFromMeldung(meldungExport);

    if (meldung != null
        && meldung.getMenge_SYST() != null
        && meldung.getMenge_SYST().getSYST() != null) {

      var systemTherapy = meldung.getMenge_SYST().getSYST();

      if (systemTherapy.getMenge_Substanz() != null) {
        var substances =
            new HashSet<>(
                systemTherapy.getMenge_Substanz().getSYST_Substanz()); // removes duplicates

        if (substances.size() > 1) {

          bundle =
              addResourceAsEntryInBundle(
                  bundle,
                  createSystemtherapyMedicationStatement(
                      meldung,
                      pid,
                      senderId,
                      softwareId,
                      getReportingReasonFromAdt(meldungExport),
                      null,
                      substances));
        }

        for (var sub : substances) {
          bundle =
              addResourceAsEntryInBundle(
                  bundle,
                  createSystemtherapyMedicationStatement(
                      meldung,
                      pid,
                      senderId,
                      softwareId,
                      getReportingReasonFromAdt(meldungExport),
                      sub,
                      substances));
        }
      }

    } else {
      return null;
    }

    bundle.setType(Bundle.BundleType.TRANSACTION);

    if (bundle.getEntry().isEmpty()) {
      return null;
    } else {
      return bundle;
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
      Meldeanlass meldeanlass,
      String substance,
      HashSet<String> substances) {

    var systemTherapy = meldung.getMenge_SYST().getSYST();

    var stMedicationStatement = new MedicationStatement();

    var partOfId = pid + "st-partOf-medicationStatement" + systemTherapy.getSYST_ID();

    if (substance != null) {
      // Id
      var id = pid + "st-medicationStatement" + systemTherapy.getSYST_ID() + substance;
      // resources
      stMedicationStatement.setId(this.getHash(ResourceType.MedicationStatement, id));

      // Medication
      stMedicationStatement.setMedication(new CodeableConcept().setText(substance));

      // PartOf
      if (substances.size() > 1) {
        stMedicationStatement.setPartOf(
            List.of(
                new Reference()
                    .setReference(
                        ResourceType.MedicationStatement
                            + "/"
                            + this.getHash(ResourceType.MedicationStatement, partOfId))));
      }

    } else {
      // Id
      stMedicationStatement.setId(this.getHash(ResourceType.MedicationStatement, partOfId));
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
    if (meldeanlass == Meldeanlass.BEHANDLUNGSENDE) {
      stMedicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.COMPLETED);
    } else {
      stMedicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.ACTIVE);
    }

    // Category
    var therapyCategory = new CodeableConcept();
    if (systemTherapy.getMenge_Therapieart() != null
        && systemTherapy.getMenge_Therapieart().getSYST_Therapieart() != null) {
      var category = systemTherapy.getMenge_Therapieart().getSYST_Therapieart();
      therapyCategory.addCoding(
          new Coding()
              .setSystem(fhirProperties.getSystems().getSystTherapieart())
              .setCode(SYSTTherapieartCSLookup.lookupCode(category))
              .setDisplay(SYSTTherapieartCSLookup.lookupDisplay(category)));

      if (systemTherapy.getSYST_Therapieart_Anmerkung() != null) {
        therapyCategory.setText(systemTherapy.getSYST_Therapieart_Anmerkung());
      }
      stMedicationStatement.setCategory(therapyCategory);
    } else {
      // data absent
      therapyCategory.addExtension(
          fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
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
                            StellungOpVsLookup.lookupDisplay(
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
                            SystIntentionVsLookup.lookupDisplay(
                                systemTherapy.getSYST_Intention()))));

    stMedicationStatement
        .addExtension()
        .setUrl(fhirProperties.getExtensions().getSysTheraProto())
        .setValue(new CodeableConcept().setText(systemTherapy.getSYST_Protokoll()));

    // Subject
    stMedicationStatement.setSubject(
        new Reference()
            .setReference(ResourceType.Patient + "/" + this.getHash(ResourceType.Patient, pid))
            .setIdentifier(
                new Identifier()
                    .setSystem(fhirProperties.getSystems().getIdentifiers().getPatientId())
                    .setType(
                        new CodeableConcept(
                            new Coding(
                                fhirProperties.getSystems().getIdentifierType(), "MR", null)))
                    .setValue(pid)));

    // Effective
    var systBeginnDateString = systemTherapy.getSYST_Beginn_Datum();
    var systEndDateString = systemTherapy.getSYST_Ende_Datum();

    DateTimeType systBeginnDateType = null;
    DateTimeType systEndDateType = null;

    if (systBeginnDateString != null) {
      systBeginnDateType = convertObdsDateToDateTimeType(systBeginnDateString);
    }

    if (systEndDateString != null) {
      systEndDateType = convertObdsDateToDateTimeType(systEndDateString);
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
                ResourceType.Condition
                    + "/"
                    + this.getHash(
                        ResourceType.Condition,
                        pid + "condition" + meldung.getTumorzuordnung().getTumor_ID())));

    return stMedicationStatement;
  }
}
