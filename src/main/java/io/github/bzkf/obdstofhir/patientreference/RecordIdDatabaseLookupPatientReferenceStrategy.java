package io.github.bzkf.obdstofhir.patientreference;

import io.github.bzkf.obdstofhir.config.RecordIdDbConfig;
import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

/** Looks up an internal RecordID for a Patient_ID in an external database, caching results. */
public class RecordIdDatabaseLookupPatientReferenceStrategy implements PatientReferenceStrategy {
  private static final Logger LOG =
      LoggerFactory.getLogger(RecordIdDatabaseLookupPatientReferenceStrategy.class);

  private final JdbcTemplate jdbcTemplate;
  private final RecordIdDbConfig dbConfig;
  private final Map<String, Optional<String>> recordIdByPatientIdCache = new ConcurrentHashMap<>();

  public RecordIdDatabaseLookupPatientReferenceStrategy(
      JdbcTemplate jdbcTemplate, RecordIdDbConfig dbConfig) {
    this.jdbcTemplate = jdbcTemplate;
    this.dbConfig = dbConfig;
  }

  @Override
  public PatientLookupResult resolve(Identifier patientIdentifier) {
    var reference = new Reference().setIdentifier(patientIdentifier);

    var idResult = findRecordIdByPatientId(patientIdentifier.getValue());

    if (idResult.isPresent()) {
      reference.setReference("Patient/" + idResult.get());
      reference.getIdentifier().setValue(idResult.get());
      return new PatientLookupResult(reference, true);
    }

    LOG.warn("No record ID found for patient: {}", patientIdentifier.getValue());

    return new PatientLookupResult(reference, false);
  }

  private Optional<String> findRecordIdByPatientId(String patientId) {
    return recordIdByPatientIdCache.computeIfAbsent(patientId, this::queryRecordIdByPatientId);
  }

  private Optional<String> queryRecordIdByPatientId(String patientId) {
    try {
      var id = jdbcTemplate.queryForObject(dbConfig.query(), String.class, patientId);
      return StringUtils.hasText(id) ? Optional.of(id) : Optional.empty();
    } catch (EmptyResultDataAccessException _) {
      return Optional.empty();
    }
  }
}
