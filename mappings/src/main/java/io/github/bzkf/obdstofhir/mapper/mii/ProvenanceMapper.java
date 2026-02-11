package io.github.bzkf.obdstofhir.mapper.mii;

import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Provenance.ProvenanceEntityRole;
import org.hl7.fhir.r4.model.Reference;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProvenanceMapper extends ObdsToFhirMapper {

  @Value("${lib-version}")
  private String libVersion;

  public ProvenanceMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Provenance map(@NonNull List<Reference> targets, @NonNull String meldungId) {
    var deviceIdentifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getIdentifiers().getObdsToFhirDeviceId())
            .setValue("obds-to-fhir-" + libVersion);

    var provenance = new Provenance();
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getIdentifiers().getProvenanceId())
            .setValue(slugifier.slugify(deviceIdentifier.getValue() + "-" + meldungId));
    // weirdly, the Provenance resource does not have an identifier element,
    // we still create it to compute the Provenance.id.
    provenance.setId(computeResourceIdFromIdentifier(identifier));

    provenance.setTarget(targets);
    provenance.setOccurred(new DateTimeType(Date.from(Instant.now())));
    provenance.setRecorded(Date.from(Instant.now()));

    provenance
        .addAgent()
        .setType(
            new CodeableConcept(
                new Coding(
                    fhirProperties.getSystems().getProvenanceParticipantType(),
                    "assembler",
                    "Assembler")))
        .addRole(
            new CodeableConcept(
                new Coding(fhirProperties.getSystems().getV3ParticipationType(), "AUT", "author")))
        .setWho(new Reference().setIdentifier(deviceIdentifier));

    provenance.setActivity(
        new CodeableConcept(
            new Coding(fhirProperties.getSystems().getV3DataOperation(), "CREATE", "create")));

    provenance
        .addEntity()
        .setRole(ProvenanceEntityRole.SOURCE)
        .setWhat(
            new Reference()
                .setIdentifier(
                    new Identifier()
                        .setSystem(fhirProperties.getSystems().getIdentifiers().getObdsMeldungId())
                        .setValue(meldungId)));
    return provenance;
  }
}
