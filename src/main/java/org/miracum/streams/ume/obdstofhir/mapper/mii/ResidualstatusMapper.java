package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OPTyp;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResidualstatusMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ResidualstatusMapper.class);

  public ResidualstatusMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(OPTyp op, Reference patient, Reference focus) {
    Objects.requireNonNull(op, "OP must not be null");

    var rs = op.getResidualstatus();
    Objects.requireNonNull(rs, "Residualstatus must not be null");
    Objects.requireNonNull(patient, "Reference to Patient must not be null");
    Objects.requireNonNull(
        rs.getGesamtbeurteilungResidualstatus(),
        "GesamtbeurteilungResidualstatus must not be null");

    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(), ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");

    var observation = new Observation();

    // Identifiers
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getResidualstatusObservationId())
            .setValue(op.getOPID());
    observation.setId(computeResourceIdFromIdentifier(identifier));
    observation.setIdentifier(List.of(identifier));

    // Subject
    observation.setSubject(patient);

    // Focus
    observation.addFocus(focus);

    // Datum
    convertObdsDatumToDateTimeType(op.getDatum()).ifPresent(observation::setEffective);

    // Gesamtbeurteilung des Residualstatus
    var value = new CodeableConcept();
    value
        .addCoding()
        .setSystem(fhirProperties.getSystems().getMiiCsOnkoResidualstatus())
        .setCode(rs.getGesamtbeurteilungResidualstatus().value());
    observation.setValue(value);

    // See: https://loinc.org/84892-9/
    var code = new CodeableConcept();
    code.addCoding().setSystem(fhirProperties.getSystems().getLoinc()).setCode("84892-9");
    observation.setCode(code);

    // Status - always final
    observation.setStatus(Observation.ObservationStatus.FINAL);

    return observation;
  }
}
