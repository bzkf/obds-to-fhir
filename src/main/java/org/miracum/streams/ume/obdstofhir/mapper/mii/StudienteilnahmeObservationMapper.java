package org.miracum.streams.ume.obdstofhir.mapper.mii;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import de.basisdatensatz.obds.v3.ModulAllgemeinTyp;
import java.util.ArrayList;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StudienteilnahmeObservationMapper extends ObdsToFhirMapper {

  private static final Logger LOG =
      LoggerFactory.getLogger(StudienteilnahmeObservationMapper.class);

  public StudienteilnahmeObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(
      ModulAllgemeinTyp modulAllgemein, Reference patient, Reference diagnose, String MeldungsID) {
    // Validation
    Objects.requireNonNull(modulAllgemein, "Meldungen must not be null");
    Objects.requireNonNull(patient, "Reference to Patient must not be null");
    Objects.requireNonNull(diagnose, "Reference to Condition must not be null");

    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The patient reference should point to a Patient resource");
    Validate.isTrue(
        Objects.equals(
            diagnose.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.CONDITION.toCode()),
        "The diagnose reference should point to a Condition resource");

    // Instantiate the Observation base resources
    var observation = new Observation();

    // Meta
    observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoStudienteilnahme());

    // Identifier
    var identifier =
      new Identifier()
        .setSystem(fhirProperties.getSystems().getMiiCsOnkoStudienteilnahme())
        .setValue(MeldungsID + "_Studienteilnahme");
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    // Status
    observation.setStatus(Observation.ObservationStatus.FINAL);

    // Code
    observation.setCode(
        new CodeableConcept(
            new Coding(
                fhirProperties.getSystems().getSnomed(),
                "709491003",
                "Enrollment in clinical trial (procedure)")));

    // Subject
    observation.setSubject(patient);
    observation.addFocus(diagnose);

    // Effective Date
    var date =
        new DateTimeType(
            modulAllgemein
                .getStudienteilnahme()
                .getDatum()
                .toGregorianCalendar()
                .getTime());
    date.setPrecision(TemporalPrecisionEnum.DAY);
    observation.setEffective(date);

    // Value
    observation.setValue(
        new CodeableConcept(
            new Coding(fhirProperties.getSystems().getMiiCsOnkoStudienteilnahme(), "J", "Ja")));

    return observation;
  }
}
