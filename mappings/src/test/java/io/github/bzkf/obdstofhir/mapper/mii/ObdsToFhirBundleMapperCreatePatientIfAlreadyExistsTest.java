package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import java.io.IOException;
import java.util.function.Function;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(
    classes = {
      ObdsToFhirBundleMapperTestConfig.class,
      ObdsToFhirBundleMapperCreatePatientIfAlreadyExistsTest.ExistingPatientTestConfig.class,
    },
    properties = "fhir.mappings.create-patient-resources.if-already-exists=true")
@EnableConfigurationProperties
@Configuration
class ObdsToFhirBundleMapperCreatePatientIfAlreadyExistsTest extends MapperTest {
  static final String SERVER_PATIENT_ID = "server-generated-patient-id";

  @Configuration
  static class ExistingPatientTestConfig {
    @Bean
    Function<OBDS.MengePatient.Patient, PatientLookupResult> patientReferenceGenerator(
        FhirProperties fhirProperties) {
      return p -> {
        var system = fhirProperties.getSystems().getIdentifiers().getPatientId();
        // Simulate a patient that already exists on the server with a server-side id
        var reference = new Reference("Patient/" + SERVER_PATIENT_ID);
        reference.setIdentifier(new Identifier().setSystem(system).setValue(SERVER_PATIENT_ID));
        return new PatientLookupResult(reference, true);
      };
    }
  }

  private static ObdsToFhirBundleMapper sut;
  private static FhirProperties fhirProperties;

  @BeforeAll
  static void beforeAll(
      @Autowired ObdsToFhirBundleMapper bundleMapper, @Autowired FhirProperties fhirProps) {
    sut = bundleMapper;
    fhirProperties = fhirProps;
  }

  @Test
  void
      map_whenPatientExistsOnServer_shouldAddPatientToBundleAndUseAlreadyExistingPatientIdAsReference()
          throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/Testpatient_leer.xml");
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);
    final var patientId = obds.getMengePatient().getPatient().getFirst().getPatientID();

    final var bundles = sut.map(obds);
    assertThat(bundles).hasSize(1);

    var bundle = bundles.getFirst();

    // Patient resource should be present in the bundle even though existsOnServer=true
    var patientEntries =
        bundle.getEntry().stream()
            .filter(e -> e.getResource().getResourceType() == ResourceType.Patient)
            .toList();
    assertThat(patientEntries).hasSize(1);

    // The patient's id should be the server-generated one
    var patientInBundle = (Patient) patientEntries.getFirst().getResource();
    var system = fhirProperties.getSystems().getIdentifiers().getPatientId();

    assertThat(patientInBundle.getId()).isEqualTo(SERVER_PATIENT_ID);
    assertThat(patientInBundle.getIdentifierFirstRep().getValue()).isEqualTo(SERVER_PATIENT_ID);
    assertThat(patientInBundle.getIdentifierFirstRep().getSystem()).isEqualTo(system);

    // Verify that non-Patient resources that have a subject/patient reference
    // use the locally computed Patient.id, not the server-generated id
    var expectedPatientRef = "Patient/" + SERVER_PATIENT_ID;
    bundle.getEntry().stream()
        .map(e -> e.getResource())
        .filter(r -> r.getResourceType() != ResourceType.Patient)
        .forEach(
            r -> {
              var ref = extractPatientReference(r);
              if (ref != null && ref.hasReference()) {
                assertThat(ref.getReference())
                    .as("Patient reference in %s should use existing id", r.getResourceType())
                    .isEqualTo(expectedPatientRef);
                // Logical identifier should still be present
                assertThat(ref.getIdentifier()).isNotNull();
                assertThat(ref.getIdentifier().getValue()).isEqualTo(SERVER_PATIENT_ID);
              }
            });
  }

  private static Reference extractPatientReference(org.hl7.fhir.r4.model.Resource resource) {
    return switch (resource.getResourceType()) {
      case Condition -> ((org.hl7.fhir.r4.model.Condition) resource).getSubject();
      case Observation -> ((org.hl7.fhir.r4.model.Observation) resource).getSubject();
      case Procedure -> ((org.hl7.fhir.r4.model.Procedure) resource).getSubject();
      case MedicationStatement ->
          ((org.hl7.fhir.r4.model.MedicationStatement) resource).getSubject();
      case Specimen -> ((org.hl7.fhir.r4.model.Specimen) resource).getSubject();
      case DiagnosticReport -> ((org.hl7.fhir.r4.model.DiagnosticReport) resource).getSubject();
      case CarePlan -> ((org.hl7.fhir.r4.model.CarePlan) resource).getSubject();
      default -> null;
    };
  }
}
