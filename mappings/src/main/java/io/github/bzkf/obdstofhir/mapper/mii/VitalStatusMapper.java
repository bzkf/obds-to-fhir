package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.TodTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.Objects;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

public class VitalStatusMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(TodMapper.class);

  public VitalStatusMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(
      @NonNull XMLGregorianCalendar meldeDatum,
      @NonNull Reference patient,
      TodTyp tod,
      boolean fromAdditionalOnkoPatientInfo) {

    Observation observation = new Observation();
    observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiVitalStatus());
    observation.setSubject(patient);

    // Identifer
    var vitalStatusObsIdentifierSytstem =
        fhirProperties.getSystems().getIdentifiers().getVitalStatusId();
    if (fromAdditionalOnkoPatientInfo) {
      vitalStatusObsIdentifierSytstem =
          fhirProperties.getSystems().getIdentifiers().getTodObservationOnkostarPatientTableId();
    }

    String identifierValue;
    String patientIdentifier =
        Objects.requireNonNull(
            patient.getIdentifier().getValue(), "Patient identifier must not be null");
    if (fromAdditionalOnkoPatientInfo) {
      identifierValue = String.format(patientIdentifier);
    } else {
      identifierValue = String.format(patient.getIdentifier().getValue());
    }

    // Identifier
    Identifier identifier =
        new Identifier()
            .setSystem(vitalStatusObsIdentifierSytstem)
            .setValue(slugifier.slugify(identifierValue + "-" + "VS"));
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    // status amended, bei Tod final
    if (tod != null) {
      if (tod.getSterbedatum() != null) {
        observation.setStatus(Observation.ObservationStatus.FINAL);
      }
    } else {
      observation.setStatus(Observation.ObservationStatus.AMENDED);
    }

    // set category to survey
    CodeableConcept category =
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getObservationCategory())
                .setCode("survey"));
    observation.addCategory(category);

    // set code to 67162-8
    CodeableConcept code =
        new CodeableConcept(
            new Coding().setSystem(fhirProperties.getSystems().getLoinc()).setCode("67162-8"));

    // set effectiveDateTime
    // if Todesdatum aus Meldung, dann "T", sonst L
    CodeableConcept codeableConceptvalue = new CodeableConcept();
    if (tod != null) {
      if (tod.getSterbedatum() != null) {
        var todesdatum = convertObdsDatumToDateTimeType(tod.getSterbedatum());
        todesdatum.ifPresent(observation::setEffective);
        codeableConceptvalue.addCoding(
            new Coding().setSystem(fhirProperties.getSystems().getMiiVSVitalStatus()).setCode("T"));
        observation.setValue(codeableConceptvalue);
      }
    } else {
      var date = convertObdsDatumToDateTimeType(meldeDatum);
      date.ifPresent(observation::setEffective);
      codeableConceptvalue.addCoding(
          new Coding().setSystem(fhirProperties.getSystems().getMiiVSVitalStatus()).setCode("L"));
      observation.setValue(codeableConceptvalue);
    }

    return observation;
  }
}
