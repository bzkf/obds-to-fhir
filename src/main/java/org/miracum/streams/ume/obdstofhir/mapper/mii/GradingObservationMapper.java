package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HistologieTyp;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GradingObservationMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(GradingObservationMapper.class);

  public GradingObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(
      HistologieTyp histologie, Reference patient, Reference diagnose, Reference specimen) {
    Objects.requireNonNull(histologie, "HistologieTyp must not be null");
    Objects.requireNonNull(histologie.getGrading(), "Grading must not be null");
    Objects.requireNonNull(diagnose, "Reference to Condition must not be null");
    Objects.requireNonNull(specimen, "Reference to Specimen must not be null");
    Objects.requireNonNull(patient, "Reference to Patient must not be null");
    Validate.isTrue(
        Objects.equals(
            diagnose.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.CONDITION.toCode()),
        "The diagnose reference should point to a Condition resource");
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");
    Validate.isTrue(
        Objects.equals(
            specimen.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.SPECIMEN.toCode()),
        "The specimen reference should point to a Specimen resource");

    // TODO: sollte grading nicht auch auf das specimen verweisen?
    var observation = new Observation();
    // Meta
    observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoGrading());

    // Identifer
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getObservationHistologieId())
            .setValue(histologie.getHistologieID() + "_Grading");
    observation.addIdentifier(identifier);

    // Id
    observation.setId(computeResourceIdFromIdentifier(identifier));

    // Status
    observation.setStatus(Observation.ObservationStatus.FINAL);

    // category
    var laboratory =
        new CodeableConcept(
            new Coding(fhirProperties.getSystems().getObservationCategory(), "laboratory", ""));
    observation.addCategory(laboratory);

    // code
    var code =
        new CodeableConcept(new Coding(fhirProperties.getSystems().getLoinc(), "33732-9", ""));
    var coding = new Coding(fhirProperties.getSystems().getSnomed(), "371469007", "");
    code.addCoding(coding);
    observation.setCode(code);

    // subject
    observation.setSubject(patient);

    // focus
    observation.addFocus(diagnose);

    // specimen
    observation.setSpecimen(specimen);

    // effective
    var date = convertObdsDatumToDateTimeType(histologie.getTumorHistologiedatum());
    observation.setEffective(date);

    // value
    var value =
        new CodeableConcept(
            new Coding(
                fhirProperties.getSystems().getMiiCsOnkoGrading(), histologie.getGrading(), ""));
    observation.setValue(value);

    return observation;
  }
}
