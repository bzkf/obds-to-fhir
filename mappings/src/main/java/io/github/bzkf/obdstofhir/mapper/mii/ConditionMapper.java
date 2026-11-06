package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.DiagnoseTyp;
import de.basisdatensatz.obds.v3.MorphologieICDOTyp;
import de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung;
import de.basisdatensatz.obds.v3.PathologieTyp;
import de.basisdatensatz.obds.v3.TopographieICDOTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.Objects;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hl7.fhir.r4.model.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConditionMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ConditionMapper.class);

  public ConditionMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  /** What a Diagnose and a Pathologie both state about the primary tumor. */
  private record PrimaryTumorDetails(
      @Nullable String diagnosetext,
      @Nullable TopographieICDOTyp topographie,
      @Nullable String topographieFreitext,
      @Nullable String diagnosesicherung) {

    static @Nullable PrimaryTumorDetails of(
        @Nullable DiagnoseTyp diagnose, @Nullable PathologieTyp pathologie) {
      if (diagnose != null) {
        return new PrimaryTumorDetails(
            diagnose.getPrimaertumorDiagnosetext(),
            diagnose.getPrimaertumorTopographieICDO(),
            diagnose.getPrimaertumorTopographieFreitext(),
            diagnose.getDiagnosesicherung() != null
                ? diagnose.getDiagnosesicherung().value()
                : null);
      }

      if (pathologie != null) {
        return new PrimaryTumorDetails(
            pathologie.getPrimaertumorDiagnosetext(),
            pathologie.getPrimaertumorTopographieICDO(),
            pathologie.getPrimaertumorTopographieFreitext(),
            pathologie.getDiagnosesicherung());
      }

      return null;
    }
  }

  public Condition map(
      @NonNull Meldung meldung,
      @NonNull Reference patient,
      @NonNull XMLGregorianCalendar meldeDatum,
      @NonNull String patientId) {
    return map(meldung, patient, meldeDatum, patientId, meldung.getPathologie());
  }

  /**
   * @param pathologie the Pathologie of the tumor to take the topography, Diagnosesicherung and
   *     diagnosis text from if the Meldung has no Diagnose
   */
  public Condition map(
      @NonNull Meldung meldung,
      @NonNull Reference patient,
      @NonNull XMLGregorianCalendar meldeDatum,
      @NonNull String patientId,
      @Nullable PathologieTyp pathologie) {
    Objects.requireNonNull(meldung.getTumorzuordnung());
    Objects.requireNonNull(meldung.getMeldungID());

    verifyReference(patient, ResourceType.Patient);

    var condition = new Condition();

    if (meldung.getDiagnose() == null) {
      LOG.debug(
          "Diagnose is null for Meldung. Only the Tumorzuordnung and Pathologie, if any, will be used to create the Condition.");
      var tag =
          fhirProperties
              .getCodings()
              .snomed()
              .setCode("255599008")
              .setDisplay("Incomplete (qualifier value)");
      condition.getMeta().addTag(tag);
    }

    setIdentifierAndId(
        condition,
        fhirProperties.getSystems().getIdentifiers().getPrimaerdiagnoseConditionId(),
        patientId + "-" + meldung.getTumorzuordnung().getTumorID());

    condition.setSubject(patient);
    condition.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoDiagnosePrimaertumor());

    var tumorzuordnung = meldung.getTumorzuordnung();

    var details = PrimaryTumorDetails.of(meldung.getDiagnose(), pathologie);

    var primaertumorIcd = tumorzuordnung.getPrimaertumorICD();
    var code =
        mapIcdToConcept(
            primaertumorIcd.getCode(),
            primaertumorIcd.getVersion(),
            "Primaertumor_ICD_Version",
            LOG);

    if (details != null && StringUtils.hasText(details.diagnosetext())) {
      code.setText(details.diagnosetext());
    }

    condition.setCode(code);

    if (meldung.getDiagnose() != null && meldung.getDiagnose().getHistologie() != null) {
      var distinctMorphologyCodes =
          meldung.getDiagnose().getHistologie().getMorphologieICDO().stream()
              .map(MorphologieICDOTyp::getCode)
              .distinct()
              .toList();

      if (distinctMorphologyCodes.size() > 1) {
        LOG.warn(
            "Diagnonse Histologie element contains more than one distinct ICD-O3 code: {}. "
                + "Adding multiple extensions.",
            distinctMorphologyCodes);
      }

      for (var morphologieCode : distinctMorphologyCodes) {
        var morphologie = new CodeableConcept(icdo3Coding(morphologieCode));

        morphologie.setText(meldung.getDiagnose().getHistologie().getMorphologieFreitext());

        condition.addExtension(
            Onkologie.Extensions.miiExOnkoHistologyMorphologyBehaviorIcdo3(morphologie));
      }
    } else {
      if (tumorzuordnung.getMorphologieICDO() != null
          && tumorzuordnung.getMorphologieICDO().getCode() != null) {
        var morphologie =
            new CodeableConcept(icdo3Coding(tumorzuordnung.getMorphologieICDO().getCode()));

        condition.addExtension(
            Onkologie.Extensions.miiExOnkoHistologyMorphologyBehaviorIcdo3(morphologie));
      }
    }

    if (details != null) {
      if (details.topographie() != null) {
        condition.addBodySite(new CodeableConcept(icdo3Coding(details.topographie().getCode())));
      }

      if (details.topographieFreitext() != null) {
        condition.getBodySiteFirstRep().setText(details.topographieFreitext());
      }

      if (details.diagnosesicherung() != null) {
        Coding verStatus =
            new Coding(fhirProperties.getSystems().getConditionVerStatus(), "confirmed", "");
        var verificationStatus = new CodeableConcept().addCoding(verStatus);

        Onkologie.CodeSystems.MiiCsOnkoPrimaertumorDiagnosesicherung.fromValue(
                details.diagnosesicherung())
            .ifPresentOrElse(
                value -> verificationStatus.addCoding(value.coding()),
                () ->
                    LOG.warn(
                        "Unknown Diagnosesicherung {}, not adding it to the Condition.",
                        details.diagnosesicherung()));

        condition.setVerificationStatus(verificationStatus);
      }
    }

    if (tumorzuordnung.getSeitenlokalisation() != null) {
      var seitenlokalisation =
          new CodeableConcept(
              Onkologie.CodeSystems.MiiCsOnkoSeitenlokalisation.fromValueOrThrow(
                      tumorzuordnung.getSeitenlokalisation().value())
                  .coding());

      condition.addBodySite(seitenlokalisation);
    }

    convertObdsDatumToDateTimeType(tumorzuordnung.getDiagnosedatum())
        .ifPresentOrElse(
            diagnoseDatum ->
                condition.addExtension(
                    fhirProperties.getExtensions().getConditionAssertedDate(), diagnoseDatum),
            () -> {
              LOG.warn("Diagnosedatum is unset. Setting data absent extension.");
              condition.addExtension(
                  fhirProperties.getExtensions().getConditionAssertedDate(), absentDateTime());
            });

    // recordedDate is 1..1, so we need to set it even if the date is absent.
    convertObdsDatumToDateTimeType(meldeDatum)
        .ifPresentOrElse(
            condition::setRecordedDateElement,
            () -> {
              LOG.warn("MeldeDatum is unset. Setting data absent extension.");
              condition.setRecordedDateElement(absentDateTime());
            });

    return condition;
  }

  private Coding icdo3Coding(String code) {
    // no version: the oBDS ICD-O version ("31", "32", "33", "bb") is not a version of the
    // terminology.hl7.org ICD-O-3 CodeSystem
    return new Coding().setSystem(fhirProperties.getSystems().getIcdo3Morphologie()).setCode(code);
  }
}
