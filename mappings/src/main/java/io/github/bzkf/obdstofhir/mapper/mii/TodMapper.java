package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.AllgemeinICDTyp;
import de.basisdatensatz.obds.v3.TodTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.*;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TodMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(TodMapper.class);
  private static final Pattern icdVersionPattern =
      Pattern.compile("^(10 (?<versionYear>20\\d{2}) ((GM)|(WHO))|Sonstige)$");

  public TodMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  private Observation createBaseObservation(
      Reference patient,
      Optional<Reference> condition,
      Optional<DateTimeType> todesZeitpunkt,
      TodTyp tod) {

    var observation = new Observation();

    observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoTod());
    observation.setStatus(Observation.ObservationStatus.FINAL);

    // Code | 184305005 | Cause of death (observable entity)
    var snomedCode =
        fhirProperties
            .getCodings()
            .snomed()
            .setCode("184305005")
            .setDisplay("Cause of death (observable entity)");
    observation.setCode(new CodeableConcept(snomedCode));

    // Subject
    observation.setSubject(patient);

    // Effective | Sterbedatum
    todesZeitpunkt.ifPresent(observation::setEffective);

    // Focus | Bezugsdiagnose
    condition.ifPresent(observation::addFocus);

    // Interpretation | Tod Tumorbedingt
    if (tod.getTodTumorbedingt() != null) {
      var interpretation = new CodeableConcept();
      interpretation
          .addCoding()
          .setSystem(fhirProperties.getSystems().getMiiCsOnkoTodInterpretation())
          .setCode(tod.getTodTumorbedingt().value());
      observation.setInterpretation(Arrays.asList(interpretation));
    }

    return observation;
  }

  public List<Observation> map(
      @NonNull TodTyp tod,
      @NonNull Reference patient,
      Reference condition,
      @NonNull Boolean fromOnkoPatientTable) {
    // Validation
    verifyReference(patient, ResourceType.Patient);
    if (condition != null) {
      verifyReference(condition, ResourceType.Condition);
    }

    String identifierValue;

    if (tod.getAbschlussID() != null) {
      identifierValue = tod.getAbschlussID();
    } else {
      // if the AbschlussID is not set, fall back to creating the identifier based on
      // the Patient ID and Condition ID. This is similar to what obds2-to-obds3 does,
      // which uses patient_id + tumor_id
      // XXX: we might want to consider using the Patient.identifier.value and
      // Condition.identifier.value here instead for a shorter value.
      if (condition != null) {
        identifierValue = String.format("%s-%s", patient.getReference(), condition.getReference());
      } else {
        identifierValue = patient.getReference();
        if (fromOnkoPatientTable) {
          identifierValue += "fromOnkoPatientTable";
        }
      }
    }

    var observationList = new ArrayList<Observation>();

    var todesZeitpunkt = convertObdsDatumToDateTimeType(tod.getSterbedatum());

    if (tod.getMengeTodesursachen() != null) {
      for (AllgemeinICDTyp todesursache : tod.getMengeTodesursachen().getTodesursacheICD()) {

        var observation =
            createBaseObservation(patient, Optional.ofNullable(condition), todesZeitpunkt, tod);

        // Identifier: set identifier per todesursache
        Identifier identifier =
            new Identifier()
                .setSystem(fhirProperties.getSystems().getIdentifiers().getTodObservationId())
                .setValue(slugifier.slugify(identifierValue + "-" + todesursache.getCode()));
        observation.addIdentifier(identifier);
        observation.setId(computeResourceIdFromIdentifier(identifier));

        // Value | Todesursache(n) ICD10GM
        var icd10Version = todesursache.getVersion();
        StringType versionElement = null;
        if (StringUtils.hasText(icd10Version)) {
          var matcher = icdVersionPattern.matcher(icd10Version);
          if (matcher.matches() && StringUtils.hasText(matcher.group("versionYear"))) {
            versionElement = new StringType(matcher.group("versionYear"));
          } else {
            LOG.warn(
                "Todesursachen_ICD_Version doesn't match expected format. Expected: '{}', actual: '{}'",
                icdVersionPattern.pattern(),
                icd10Version);
          }
        } else {
          LOG.debug("Todesursachen_ICD_Version is unset or contains only whitespaces");
        }

        if (versionElement == null) {
          versionElement = new StringType();
          versionElement.addExtension(
              fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
        }

        var todesursacheConcept = new CodeableConcept();
        todesursacheConcept
            .addCoding()
            .setSystem(fhirProperties.getSystems().getIcd10gm())
            .setCode(todesursache.getCode())
            .setVersionElement(versionElement);

        observation.setValue(todesursacheConcept);

        observationList.add(observation);
      }

    } else {
      var observation =
          createBaseObservation(patient, Optional.ofNullable(condition), todesZeitpunkt, tod);

      // if the ICD cause of death is unset, use the plain identifier without icd code
      // suffix
      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getIdentifiers().getTodObservationId())
              .setValue(slugifier.slugify(identifierValue));
      observation.addIdentifier(identifier);
      observation.setId(computeResourceIdFromIdentifier(identifier));

      observationList.add(observation);
    }

    return observationList;
  }
}
