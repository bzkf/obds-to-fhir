package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HistologieTyp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VerlaufshistologieObservationMapper extends ObdsToFhirMapper {

  private static final Logger LOG =
      LoggerFactory.getLogger(VerlaufshistologieObservationMapper.class);

  public VerlaufshistologieObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      HistologieTyp histologie, Reference patient, Reference specimen, Reference diagnose) {
    Objects.requireNonNull(histologie, "HistologieTyp must not be null");
    Objects.requireNonNull(patient, "Reference to Patient must not be null");
    Objects.requireNonNull(specimen, "Reference to Specimen must not be null");
    Objects.requireNonNull(specimen, "Reference to Condition must not be null");
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
    Validate.isTrue(
        Objects.equals(
            diagnose.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.CONDITION.toCode()),
        "The condition reference should point to a Condition resource");

    var observations = new ArrayList<Observation>();

    for (var morph : histologie.getMorphologieICDO()) {
      var observation = new Observation();

      // Meta
      observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoHistologieIcdo3());

      // Identifer
      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getObservationHistologieId())
              .setValue(histologie.getHistologieID() + "_ICDO3_" + morph.getCode());
      observation.addIdentifier(identifier);
      // Id
      observation.setId(computeResourceIdFromIdentifier(identifier));

      // Status
      observation.setStatus(Observation.ObservationStatus.FINAL);

      // Code
      var code =
          new CodeableConcept(new Coding(fhirProperties.getSystems().getLoinc(), "59847-4", ""));
      observation.setCode(code);

      // subject
      observation.setSubject(patient);

      // focus
      observation.addFocus(diagnose);

      // effective
      convertObdsDatumToDateTimeType(histologie.getTumorHistologiedatum())
          .ifPresent(observation::setEffective);

      // specimen
      observation.setSpecimen(specimen);

      var coding =
          new Coding()
              .setSystem(fhirProperties.getSystems().getIcdo3Morphologie())
              .setCode(morph.getCode())
              .setVersion(morph.getVersion());
      var value = new CodeableConcept(coding).setText(histologie.getMorphologieFreitext());
      observation.setValue(value);

      observations.add(observation);
    }

    return observations;
  }
}
