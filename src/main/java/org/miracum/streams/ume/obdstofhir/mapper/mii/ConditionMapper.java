package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.SeitenlokalisationTyp;
import de.basisdatensatz.obds.v3.TumorzuordnungTyp;
import java.util.EnumMap;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConditionMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ConditionMapper.class);
  private static final Pattern icdVersionPattern =
      Pattern.compile("^(10 (?<versionYear>20\\d{2}) ((GM)|(WHO))|Sonstige)$");
  private final EnumMap<SeitenlokalisationTyp, Coding> seitenlokalisationToSnomedLookup;

  public ConditionMapper(FhirProperties fhirProperties) {
    super(fhirProperties);

    seitenlokalisationToSnomedLookup = new EnumMap<>(SeitenlokalisationTyp.class);
    seitenlokalisationToSnomedLookup.put(
        SeitenlokalisationTyp.L,
        fhirProperties
            .getCodings()
            .snomed()
            .setCode("7771000")
            .setDisplay("Left (qualifier value)"));
    seitenlokalisationToSnomedLookup.put(
        SeitenlokalisationTyp.R,
        fhirProperties
            .getCodings()
            .snomed()
            .setCode("24028007")
            .setDisplay("Right (qualifier value)"));
    seitenlokalisationToSnomedLookup.put(
        SeitenlokalisationTyp.B,
        fhirProperties
            .getCodings()
            .snomed()
            .setCode("51440002")
            .setDisplay("Right and left (qualifier value)"));
    seitenlokalisationToSnomedLookup.put(
        SeitenlokalisationTyp.M,
        fhirProperties
            .getCodings()
            .snomed()
            .setCode("260528009")
            .setDisplay("Median (qualifier value)"));
    seitenlokalisationToSnomedLookup.put(
        SeitenlokalisationTyp.T,
        fhirProperties
            .getCodings()
            .snomed()
            .setCode("385432009")
            .setDisplay("Not applicable (qualifier value)"));
    seitenlokalisationToSnomedLookup.put(
        SeitenlokalisationTyp.U,
        fhirProperties
            .getCodings()
            .snomed()
            .setCode("261665006")
            .setDisplay("Unknown (qualifier value)"));
  }

  public Condition map(
      @NonNull OBDS.MengePatient.Patient.MengeMeldung.Meldung meldung,
      @NonNull Reference patient,
      @NonNull XMLGregorianCalendar meldeDatum,
      @NonNull String patientId) {
    Objects.requireNonNull(meldung.getTumorzuordnung());
    Objects.requireNonNull(meldung.getMeldungID());

    verifyReference(patient, ResourceType.Patient);

    var condition = new Condition();

    if (meldung.getDiagnose() == null) {
      LOG.debug(
          "Diagnose is null for Meldung. Only the Tumorzuordnung will be used to create the Condition.");
      var tag =
          fhirProperties
              .getCodings()
              .snomed()
              .setCode("255599008")
              .setDisplay("Incomplete (qualifier value)");
      condition.getMeta().addTag(tag);
    }

    var identifier = buildConditionIdentifier(meldung.getTumorzuordnung(), patientId);
    condition.addIdentifier(identifier);
    condition.setId(computeResourceIdFromIdentifier(identifier));

    condition.setSubject(patient);
    condition.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoDiagnosePrimaertumor());

    var tumorzuordnung = meldung.getTumorzuordnung();

    var icd =
        new Coding()
            .setSystem(fhirProperties.getSystems().getIcd10gm())
            .setCode(tumorzuordnung.getPrimaertumorICD().getCode());

    var icd10Version = tumorzuordnung.getPrimaertumorICD().getVersion();
    StringType versionElement = null;
    if (StringUtils.hasText(icd10Version)) {
      var matcher = icdVersionPattern.matcher(icd10Version);
      if (matcher.matches() && StringUtils.hasText(matcher.group("versionYear"))) {
        versionElement = new StringType(matcher.group("versionYear"));
      } else {
        LOG.warn(
            "Primaertumor_ICD_Version doesn't match expected format. Expected: '{}', actual: '{}'",
            icdVersionPattern.pattern(),
            icd10Version);
      }
    } else {
      LOG.warn("Primaertumor_ICD_Version is unset or contains only whitespaces");
    }

    if (versionElement == null) {
      versionElement = new StringType();
      versionElement.addExtension(
          fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
    }

    icd.setVersionElement(versionElement);

    condition.setCode(new CodeableConcept(icd));

    if (tumorzuordnung.getMorphologieICDO() != null
        && tumorzuordnung.getMorphologieICDO().getCode() != null) {
      var morphologie = new CodeableConcept();
      morphologie
          .addCoding()
          .setSystem(fhirProperties.getSystems().getIcdo3Morphologie())
          .setCode(tumorzuordnung.getMorphologieICDO().getCode())
          .setVersion(tumorzuordnung.getMorphologieICDO().getVersion());

      condition.addExtension(
          fhirProperties.getExtensions().getMiiExOnkoHistologyMorphologyBehaviorIcdo3(),
          morphologie);
    }

    // icd-o topography and diagnosis verification status are only available for Diagnosemeldungen.
    if (meldung.getDiagnose() != null) {
      var diagnoseMeldung = meldung.getDiagnose();

      if (diagnoseMeldung.getPrimaertumorTopographieICDO() != null) {
        CodeableConcept topographie =
            new CodeableConcept(
                new Coding()
                    .setSystem(fhirProperties.getSystems().getIcdo3Morphologie())
                    .setCode(meldung.getDiagnose().getPrimaertumorTopographieICDO().getCode())
                    .setVersion(
                        meldung.getDiagnose().getPrimaertumorTopographieICDO().getVersion()));
        condition.addBodySite(topographie);
      }

      if (diagnoseMeldung.getPrimaertumorTopographieFreitext() != null) {
        var freitext = diagnoseMeldung.getPrimaertumorTopographieFreitext();
        condition.getBodySiteFirstRep().setText(freitext);
      }

      if (diagnoseMeldung.getDiagnosesicherung() != null) {
        Coding verStatus =
            new Coding(fhirProperties.getSystems().getConditionVerStatus(), "confirmed", "");
        Coding diagnosesicherung =
            new Coding(
                fhirProperties.getSystems().getMiiCsOnkoPrimaertumorDiagnosesicherung(),
                meldung.getDiagnose().getDiagnosesicherung().value(),
                "");

        CodeableConcept verificationStatus = new CodeableConcept();
        verificationStatus.addCoding(verStatus).addCoding(diagnosesicherung);
        condition.setVerificationStatus(verificationStatus);
      }
    }

    if (tumorzuordnung.getSeitenlokalisation() != null) {
      CodeableConcept seitenlokalisation =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getMiiCsOnkoSeitenlokalisation())
                  .setCode(tumorzuordnung.getSeitenlokalisation().value()));

      var snomedBodySite =
          seitenlokalisationToSnomedLookup.get(tumorzuordnung.getSeitenlokalisation());
      if (snomedBodySite != null) {
        seitenlokalisation.addCoding(snomedBodySite);
      } else {
        LOG.warn(
            "Seitenlokalisation {} not found in lookup table. No Snomed code will be added.",
            tumorzuordnung.getSeitenlokalisation());
      }

      condition.addBodySite(seitenlokalisation);
    }

    convertObdsDatumToDateTimeType(tumorzuordnung.getDiagnosedatum())
        .ifPresentOrElse(
            diagnoseDatum ->
                condition.addExtension(
                    fhirProperties.getExtensions().getConditionAssertedDate(), diagnoseDatum),
            () -> {
              LOG.warn("Diagnosedatum is unset. Setting data absent extension.");
              var absentDateTime = new DateTimeType();
              absentDateTime.addExtension(
                  fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
              condition.addExtension(
                  fhirProperties.getExtensions().getConditionAssertedDate(), absentDateTime);
            });

    // recordedDate is 1..1, so we need to set it even if the date is absent.
    convertObdsDatumToDateTimeType(meldeDatum)
        .ifPresentOrElse(
            condition::setRecordedDateElement,
            () -> {
              LOG.warn("MeldeDatum is unset. Setting data absent extension.");
              var absentDateTime = new DateTimeType();
              absentDateTime.addExtension(
                  fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
              condition.setRecordedDateElement(absentDateTime);
            });

    return condition;
  }

  private Identifier buildConditionIdentifier(TumorzuordnungTyp tumorzuordnung, String patientId) {
    return new Identifier()
        .setSystem(fhirProperties.getSystems().getConditionId())
        .setValue(patientId + "-" + tumorzuordnung.getTumorID());
  }
}
