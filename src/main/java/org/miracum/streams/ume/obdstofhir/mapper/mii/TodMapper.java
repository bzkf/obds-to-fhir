package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.AllgemeinICDTyp;
import de.basisdatensatz.obds.v3.OBDS;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TodMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(TodMapper.class);
  private static final Pattern icdVersionPattern =
      Pattern.compile("^(10 (?<versionYear>20\\d{2}) ((GM)|(WHO))|Sonstige)$");

  @Autowired
  public TodMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(
      OBDS.MengePatient.Patient.MengeMeldung.Meldung meldung, Reference patient, Reference condition) {
    // Validation
    Objects.requireNonNull(meldung.getTod());
    Objects.requireNonNull(patient);
    Objects.requireNonNull(condition);

    Validate.notBlank(meldung.getTod().getAbschlussID(), "Required ABSCHLUSS_ID is unset");
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");
    Validate.isTrue(
      Objects.equals(
        condition.getReferenceElement().getResourceType(), Enumerations.ResourceType.CONDITION.toCode()),
      "The condition reference should point to a Condition resource");


    var observation = new Observation();
    observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoTod());
    observation.setStatus(Observation.ObservationStatus.FINAL);

    Identifier identifier = new Identifier()
      .setSystem(fhirProperties.getSystems().getObservationId())
      .setValue(meldung.getTod().getAbschlussID());
    observation.addIdentifier(identifier);

    // Code | 184305005 | Cause of death (observable entity)
    var snomedCode = new CodeableConcept();
    snomedCode.addCoding().setSystem(fhirProperties.getSystems().getSnomed()).setCode("184305005");
    observation.setCode(snomedCode);

    // Subject
    observation.setSubject(patient);

    // Effective | Sterbedatum
    var todesZeitpunkt = convertObdsDatumToDateTimeType(meldung.getTod().getSterbedatum());
    if (todesZeitpunkt.isPresent()) {
      observation.setEffective(todesZeitpunkt.get());
    }

    // Focus | Bezugsdiagnose
    observation.addFocus(condition);

    // Value | Todesursache(n) ICD10GM
    if (meldung.getTod().getMengeTodesursachen() != null) {
      var todesursacheConcept = new CodeableConcept();
      for (AllgemeinICDTyp todesursache :
          meldung.getTod().getMengeTodesursachen().getTodesursacheICD()) {

        var tuIcdVersion = "";
        var icd10Version = todesursache.getVersion();
        if (StringUtils.hasLength(icd10Version)) {
          var matcher = icdVersionPattern.matcher(icd10Version);
          if (matcher.matches()) {
            tuIcdVersion = matcher.group("versionYear");
          } else {
            LOG.warn(
              "Todesursachen_ICD_Version doesn't match expected format. Expected: '{}', actual: '{}'",
              icdVersionPattern.pattern(),
              icd10Version);
          }
        } else {
          LOG.warn("Primaertumor_ICD_Version is unset or contains only whitespaces");
        }
        todesursacheConcept
            .addCoding()
            .setSystem(fhirProperties.getSystems().getIcd10gm())
            .setCode(todesursache.getCode())
            .setVersion(tuIcdVersion);
      }
      observation.setValue(todesursacheConcept);
    }

    // Interpretation | Tod Tumorbedingt
    if (meldung.getTod().getTodTumorbedingt() != null) {
      var interpretation = new CodeableConcept();
      interpretation
          .addCoding()
          .setSystem(fhirProperties.getSystems().getMiiCsOnkoTodInterpretation())
          .setCode(meldung.getTod().getTodTumorbedingt().value());
      observation.setInterpretation(Arrays.asList(interpretation));
    }

    return observation;
  }
}
