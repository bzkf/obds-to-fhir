package io.github.bzkf.obdstofhir.patientreference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.bzkf.obdstofhir.config.RecordIdDbConfig;
import org.hl7.fhir.r4.model.Identifier;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class RecordIdDatabaseLookupPatientReferenceStrategyTest {
  private static final String QUERY = "SELECT record_id FROM patients WHERE patient_id = ?";

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final RecordIdDbConfig dbConfig = new RecordIdDbConfig("jdbc:test", "user", "pwd", QUERY);

  private static Identifier identifierOf(String patientId) {
    return new Identifier().setSystem("http://example.org/patient-id").setValue(patientId);
  }

  @Test
  void resolve_withPatientAddedToDatabaseAfterFirstLookup_shouldFindIt() {
    when(jdbcTemplate.queryForObject(QUERY, String.class, "patient-1"))
        .thenThrow(new EmptyResultDataAccessException(1))
        .thenReturn("record-1");
    var sut = new RecordIdDatabaseLookupPatientReferenceStrategy(jdbcTemplate, dbConfig);

    var first = sut.resolve(identifierOf("patient-1"));
    var second = sut.resolve(identifierOf("patient-1"));

    assertThat(first.existsOnServer()).isFalse();
    assertThat(second.existsOnServer()).isTrue();
    assertThat(second.reference().getReference()).isEqualTo("Patient/record-1");
  }

  @Test
  void resolve_withFullCache_shouldEvictTheLeastRecentlyUsedRecordId() {
    when(jdbcTemplate.queryForObject(QUERY, String.class, "patient-1")).thenReturn("record-1");
    when(jdbcTemplate.queryForObject(QUERY, String.class, "patient-2")).thenReturn("record-2");
    when(jdbcTemplate.queryForObject(QUERY, String.class, "patient-3")).thenReturn("record-3");
    var sut = new RecordIdDatabaseLookupPatientReferenceStrategy(jdbcTemplate, dbConfig, 2);

    sut.resolve(identifierOf("patient-1"));
    sut.resolve(identifierOf("patient-2"));
    // makes patient-2 the least recently used one
    sut.resolve(identifierOf("patient-1"));
    sut.resolve(identifierOf("patient-3"));

    sut.resolve(identifierOf("patient-1"));
    sut.resolve(identifierOf("patient-2"));

    verify(jdbcTemplate, times(1)).queryForObject(QUERY, String.class, "patient-1");
    verify(jdbcTemplate, times(2)).queryForObject(QUERY, String.class, "patient-2");
  }
}
