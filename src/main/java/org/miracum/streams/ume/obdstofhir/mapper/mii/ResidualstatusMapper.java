package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OPTyp;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResidualstatusMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ResidualstatusMapper.class);

  public ResidualstatusMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(OPTyp op, Reference patient, Reference focus) {
    Objects.requireNonNull(op, "OP must not be null");

    var rs = op.getResidualstatus();
    Objects.requireNonNull(rs, "Residualstatus must not be null");
    Objects.requireNonNull(
        rs.getGesamtbeurteilungResidualstatus(),
        "GesamtbeurteilungResidualstatus must not be null");

    verifyReference(patient, ResourceType.Patient);

    var observation = new Observation();

    // Identifiers
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getResidualstatusObservationId())
            .setValue(slugifier.slugify(op.getOPID()));
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
    var code =
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("84892-9")
                .setDisplay("Residual tumor classification [Type] in Cancer specimen"));
    observation.setCode(code);

    // Status - always final
    observation.setStatus(Observation.ObservationStatus.FINAL);

    return observation;
  }
}
