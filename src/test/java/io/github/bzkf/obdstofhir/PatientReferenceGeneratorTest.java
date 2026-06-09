package io.github.bzkf.obdstofhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.config.RecordIdDbConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class PatientReferenceGeneratorTest {

  @Test
  void shouldUseCachedRecordIdForRepeatedPatientIdLookups() {
    var jdbcTemplate = mock(JdbcTemplate.class);
    var query = "SELECT record_id FROM patients WHERE patient_id = ?";
    when(jdbcTemplate.queryForObject(query, String.class, "patient-1")).thenReturn("record-1");

    var generator =
        new PatientReferenceGenerator(
            createFhirProperties(),
            Optional.empty(),
            Optional.of(jdbcTemplate),
            Optional.of(new RecordIdDbConfig("jdbc:test", "user", "pwd", query)),
            false,
            "");
    ReflectionTestUtils.setField(
        generator, "strategy", PatientReferenceGenerator.Strategy.RECORD_ID_DATABASE_LOOKUP);

    var function = generator.getPatientReferenceGenerationFunction();
    var patient = new OBDS.MengePatient.Patient();
    patient.setPatientID("patient-1");

    var first = function.apply(patient);
    var second = function.apply(patient);

    assertThat(first.reference().getReference()).isEqualTo("Patient/record-1");
    assertThat(second.reference().getReference()).isEqualTo("Patient/record-1");
    verify(jdbcTemplate, times(1)).queryForObject(query, String.class, "patient-1");
  }

  @Test
  void shouldSetIdentifierValueToRecordIdFromDatabase() {
    var jdbcTemplate = mock(JdbcTemplate.class);
    var query = "SELECT record_id FROM patients WHERE patient_id = ?";

    when(jdbcTemplate.queryForObject(query, String.class, "patient-1")).thenReturn("record-1");

    var generator =
        new PatientReferenceGenerator(
            createFhirProperties(),
            Optional.empty(),
            Optional.of(jdbcTemplate),
            Optional.of(new RecordIdDbConfig("jdbc:test", "user", "pwd", query)),
            false,
            "");

    ReflectionTestUtils.setField(
        generator, "strategy", PatientReferenceGenerator.Strategy.RECORD_ID_DATABASE_LOOKUP);

    var function = generator.getPatientReferenceGenerationFunction();

    var patient = new OBDS.MengePatient.Patient();
    patient.setPatientID("patient-1");

    var result = function.apply(patient);

    assertThat(result.reference().getReference()).isEqualTo("Patient/record-1");
    assertThat(result.reference().getIdentifier().getValue()).isEqualTo("record-1");
    assertThat(result.reference().getIdentifier().getSystem())
        .isEqualTo(createFhirProperties().getSystems().getIdentifiers().getPatientId());
  }

  private FhirProperties createFhirProperties() {
    var identifiers = new FhirProperties.FhirIdentifierSystems();
    identifiers.setPatientId("http://example.org/patient-id");

    var systems = new FhirProperties.FhirSystems();
    systems.setIdentifiers(identifiers);
    systems.setIdentifierType("http://example.org/identifier-type");

    var properties = new FhirProperties();
    properties.setSystems(systems);
    return properties;
  }
}
