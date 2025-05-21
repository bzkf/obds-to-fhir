package org.miracum.streams.ume.obdstofhir.mapper.mii;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.SeitenlokalisationTyp;
import de.basisdatensatz.obds.v3.TumorzuordnungTyp;
import java.util.HashMap;
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
  private final HashMap<SeitenlokalisationTyp, Coding> seitenlokalisationToSnomedLookup;

  public ConditionMapper(FhirProperties fhirProperties) {
    super(fhirProperties);

    var snomed = fhirProperties.getSystems().getSnomed();
    seitenlokalisationToSnomedLookup =
        new HashMap<>() {
          {
            put(SeitenlokalisationTyp.L, new Coding(snomed, "7771000", "Left (qualifier value)"));
            put(SeitenlokalisationTyp.R, new Coding(snomed, "24028007", "Right (qualifier value)"));
            put(
                SeitenlokalisationTyp.B,
                new Coding(snomed, "51440002", "Right and left (qualifier value)"));
            put(
                SeitenlokalisationTyp.M,
                new Coding(snomed, "260528009", "Median (qualifier value)"));
            put(
                SeitenlokalisationTyp.T,
                new Coding(snomed, "385432009", "Not applicable (qualifier value)"));
            put(
                SeitenlokalisationTyp.U,
                new Coding(snomed, "261665006", "Unknown (qualifier value)"));
          }
        };
  }

  public Condition map(
      @NonNull OBDS.MengePatient.Patient.MengeMeldung.Meldung meldung,
      @NonNull Reference patient,
      @NonNull XMLGregorianCalendar meldeDatum,
      @NonNull String patientId) {
    Objects.requireNonNull(meldung.getTumorzuordnung());
    Objects.requireNonNull(meldung.getDiagnose());
    Objects.requireNonNull(meldung.getMeldungID());

    verifyReference(patient, ResourceType.Patient);

    var condition = new Condition();

    var identifier = buildConditionIdentifier(meldung.getTumorzuordnung(), patientId);
    condition.addIdentifier(identifier);
    condition.setId(computeResourceIdFromIdentifier(identifier));

    if (meldung.getDiagnose().getDiagnosesicherung() != null) {
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

    condition.setSubject(patient);
    condition.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoDiagnosePrimaertumor());

    var tumorzuordnung = meldung.getTumorzuordnung();

    Coding icd =
        new Coding(
            fhirProperties.getSystems().getIcd10gm(),
            tumorzuordnung.getPrimaertumorICD().getCode(),
            "");

    var icd10Version = tumorzuordnung.getPrimaertumorICD().getVersion();
    if (StringUtils.hasLength(icd10Version)) {
      var matcher = icdVersionPattern.matcher(icd10Version);
      if (matcher.matches()) {
        icd.setVersion(matcher.group("versionYear"));
      } else {
        LOG.warn(
            "Primaertumor_ICD_Version doesn't match expected format. Expected: '{}', actual: '{}'",
            icdVersionPattern.pattern(),
            icd10Version);
      }
    } else {
      LOG.warn("Primaertumor_ICD_Version is unset or contains only whitespaces");
    }
    CodeableConcept code = new CodeableConcept().addCoding(icd);
    condition.setCode(code);

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

    if (meldung.getDiagnose().getPrimaertumorTopographieICDO() != null) {
      CodeableConcept topographie =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getIcdo3Morphologie())
                  .setCode(meldung.getDiagnose().getPrimaertumorTopographieICDO().getCode())
                  .setVersion(meldung.getDiagnose().getPrimaertumorTopographieICDO().getVersion()));
      condition.addBodySite(topographie);
    }

    if (tumorzuordnung.getSeitenlokalisation() != null) {
      CodeableConcept seitenlokalisation =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getMiiCsOnkoSeitenlokalisation())
                  .setCode(tumorzuordnung.getSeitenlokalisation().value()));
      condition.addBodySite(seitenlokalisation);

      var snomedBodySite =
          seitenlokalisationToSnomedLookup.get(tumorzuordnung.getSeitenlokalisation());
      if (snomedBodySite != null) {
        condition.addBodySite(new CodeableConcept(snomedBodySite));
      } else {
        LOG.warn(
            "Seitenlokalisation {} not found in lookup table. No Snomed code will be added.",
            tumorzuordnung.getSeitenlokalisation());
      }
    }

    convertObdsDatumToDateTimeType(tumorzuordnung.getDiagnosedatum())
        .ifPresent(
            diagnoseDatum ->
                condition.addExtension(
                    fhirProperties.getExtensions().getConditionAssertedDate(), diagnoseDatum));

    var recorded = new DateTimeType(meldeDatum.toGregorianCalendar().getTime());
    recorded.setPrecision(TemporalPrecisionEnum.DAY);
    condition.setRecordedDateElement(recorded);

    return condition;
  }

  public Identifier buildConditionIdentifier(TumorzuordnungTyp tumorzuordnung, String patientId) {
    return new Identifier()
        .setSystem(fhirProperties.getSystems().getConditionId())
        .setValue(patientId + "-" + tumorzuordnung.getTumorID());
  }
}
