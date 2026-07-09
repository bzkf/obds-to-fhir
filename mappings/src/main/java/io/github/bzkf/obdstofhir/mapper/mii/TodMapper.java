package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.AllgemeinICDTyp;
import de.basisdatensatz.obds.v3.TodTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.dizuker.tofhir.IdUtils;
import java.util.*;
import org.hl7.fhir.r4.model.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TodMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(TodMapper.class);

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
      @Nullable Reference condition,
      String tumorId,
      boolean fromAdditionalOnkoPatientInfo) {
    // Validation
    verifyReference(patient, ResourceType.Patient);
    if (condition != null) {
      // fine to be null if called from the extra patient table processor
      verifyReference(condition, ResourceType.Condition);
    }

    String identifierValue;
    String patientIdentifier =
        Objects.requireNonNull(
            patient.getIdentifier().getValue(), "Patient identifier must not be null");
    if (fromAdditionalOnkoPatientInfo) {
      identifierValue = String.format("%s-%s", patientIdentifier, "fromAdditionalOnkoPatientInfo");
    } else {
      identifierValue = String.format("%s-%s", patient.getIdentifier().getValue(), tumorId);
    }

    var observationList = new ArrayList<Observation>();

    var todesZeitpunkt = convertObdsDatumToDateTimeType(tod.getSterbedatum());

    var todObsIdentifierSytstem =
        fhirProperties.getSystems().getIdentifiers().getTodObservationId();
    if (fromAdditionalOnkoPatientInfo) {
      todObsIdentifierSytstem =
          fhirProperties.getSystems().getIdentifiers().getTodObservationOnkostarPatientTableId();
    }

    if (tod.getMengeTodesursachen() != null) {
      for (AllgemeinICDTyp todesursache : tod.getMengeTodesursachen().getTodesursacheICD()) {

        var observation =
            createBaseObservation(patient, Optional.ofNullable(condition), todesZeitpunkt, tod);
        // Identifier: set identifier per todesursache
        Identifier identifier =
            new Identifier()
                .setSystem(todObsIdentifierSytstem)
                .setValue(slugifier.slugify(identifierValue + "-" + todesursache.getCode()));
        observation.addIdentifier(identifier);
        observation.setId(IdUtils.fromIdentifier(identifier));

        // Value | Todesursache(n) ICD10GM
        var versionElement =
            extractIcdVersionYear(todesursache.getVersion(), "Todesursachen_ICD_Version", LOG);

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
              .setSystem(todObsIdentifierSytstem)
              .setValue(slugifier.slugify(identifierValue));
      observation.addIdentifier(identifier);
      observation.setId(IdUtils.fromIdentifier(identifier));

      observationList.add(observation);
    }

    return observationList;
  }
}
