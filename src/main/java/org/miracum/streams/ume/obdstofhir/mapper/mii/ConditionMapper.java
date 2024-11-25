package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConditionMapper extends ObdsToFhirMapper
    implements Mapper<OBDS.MengePatient.Patient.MengeMeldung.Meldung, Condition> {

  private static final Logger LOG = LoggerFactory.getLogger(ConditionMapper.class);
  private static final Pattern icdVersionPattern =
      Pattern.compile("^(10 (?<versionYear>20\\d{2}) ((GM)|(WHO))|Sonstige)$");

  @Autowired
  public ConditionMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Condition map(OBDS.MengePatient.Patient.MengeMeldung.Meldung meldung, Reference patient) {
    var condition = new Condition();

    verifyReference(patient, ResourceType.PATIENT);

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
    if (tumorzuordnung == null) {
      throw new RuntimeException("Tumorzuordnung ist null");
    }

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

    var morphologie = new CodeableConcept();
    morphologie
        .addCoding()
        .setSystem(fhirProperties.getSystems().getIcdo3Morphologie())
        .setCode(tumorzuordnung.getMorphologieICDO().getCode())
        .setVersion(tumorzuordnung.getMorphologieICDO().getVersion());

    condition.addExtension(
        fhirProperties.getExtensions().getMiiExOnkoHistologyMorphologyBehaviorIcdo3(), morphologie);

    List<CodeableConcept> bodySite = new ArrayList<>();

    if (meldung.getDiagnose().getPrimaertumorTopographieICDO() != null) {
      CodeableConcept topographie =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getIcdo3Morphologie())
                  .setCode(meldung.getDiagnose().getPrimaertumorTopographieICDO().getCode())
                  .setVersion(meldung.getDiagnose().getPrimaertumorTopographieICDO().getVersion()));
      bodySite.add(topographie);
    }

    if (tumorzuordnung.getSeitenlokalisation() != null) {
      CodeableConcept seitenlokalisation =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getMiiCsOnkoSeitenlokalisation())
                  .setCode(tumorzuordnung.getSeitenlokalisation().value()));
      bodySite.add(seitenlokalisation);
    }
    condition.setBodySite(bodySite);

    condition.setRecordedDate(
        tumorzuordnung.getDiagnosedatum().getValue().toGregorianCalendar().getTime());
    return condition;
  }
}
