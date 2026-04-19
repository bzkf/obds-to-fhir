package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.TodTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.Objects;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hl7.fhir.r4.model.*;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class VitalStatusMapper extends ObdsToFhirMapper {

  public VitalStatusMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(
      @NonNull XMLGregorianCalendar effectiveTime,
      @NonNull Reference patient,
      @Nullable TodTyp tod,
      boolean isFromAdditionalOnkoPatientInfo) {

    Objects.requireNonNull(
        patient.getIdentifier().getValue(), "Patient identifier in reference must not be null");

    var observation = new Observation();
    observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiVitalStatus());
    observation.setSubject(patient);

    // Identifer
    var vitalStatusObsIdentifierSytstem =
        fhirProperties.getSystems().getIdentifiers().getVitalStatusId();
    if (isFromAdditionalOnkoPatientInfo) {
      vitalStatusObsIdentifierSytstem =
          fhirProperties.getSystems().getIdentifiers().getTodObservationOnkostarPatientTableId();
    }

    // Identifier
    var identifier =
        new Identifier()
            .setSystem(vitalStatusObsIdentifierSytstem)
            .setValue(slugifier.slugify(patient.getIdentifier().getValue() + "-" + "vs"));
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    // status amended, bei Tod final
    if (tod != null && tod.getSterbedatum() != null) {
      observation.setStatus(Observation.ObservationStatus.FINAL);
    } else {
      observation.setStatus(Observation.ObservationStatus.AMENDED);
    }

    // set category to survey
    var category =
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getObservationCategory())
                .setCode("survey"));
    observation.addCategory(category);

    var code =
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("67162-8")
                .setDisplay("Patient Disposition"));

    observation.setCode(code);

    // set effectiveDateTime
    // if Todesdatum aus Meldung, dann "T", sonst L
    var valueCodeableConcept = new CodeableConcept();
    if (tod != null) {
      var todesdatum = convertObdsDatumToDateTimeType(tod.getSterbedatum());
      todesdatum.ifPresent(observation::setEffective);

      valueCodeableConcept.addCoding(
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsVitalStatus())
              .setCode("T")
              .setDisplay("Patient verstorben"));
    } else {
      var date = convertObdsDatumToDateTimeType(effectiveTime);
      date.ifPresent(observation::setEffective);

      valueCodeableConcept.addCoding(
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsVitalStatus())
              .setCode("L")
              .setDisplay("Patient lebt"));
    }

    observation.setValue(valueCodeableConcept);

    return observation;
  }
}
