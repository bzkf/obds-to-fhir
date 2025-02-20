package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.ModulProstataTyp;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.springframework.stereotype.Service;

@Service
public class GleasonScoreMapper extends ObdsToFhirMapper {

  protected GleasonScoreMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(
      ModulProstataTyp modulProstata, String meldungId, Reference patient, Reference condition) {

    Objects.requireNonNull(modulProstata);
    Objects.requireNonNull(modulProstata.getGleasonScore());
    Objects.requireNonNull(modulProstata.getGleasonScore().getScoreErgebnis());
    Objects.requireNonNull(meldungId);
    Objects.requireNonNull(patient);
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");
    Objects.requireNonNull(condition);
    Validate.isTrue(
        Objects.equals(
            condition.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.CONDITION.toCode()),
        "The condition reference should point to a Condition resource");

    var observation = new Observation();

    // TODO: define and add a profile URL
    observation.getMeta().addProfile("");

    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getAllgemeinerLeistungszustandEcogId())
            .setValue("gleason-" + meldungId);
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    observation.setSubject(patient);

    observation.addFocus(condition);

    observation.setStatus(Observation.ObservationStatus.FINAL);

    observation.setCode(
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getSnomed())
                .setCode("385377005")
                .setDisplay("Gleason grade finding for prostatic cancer")));

    // TODO: component for primary/secondary score if set
    // TODO: always set the valueCodeableConcept to the combined score (maybe as snomed or loinc)
    // TODO: use the value ordinal extension for the combined score as well

    return observation;
  }
}
