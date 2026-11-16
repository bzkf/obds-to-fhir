package io.github.bzkf.obdstofhir.patientreference;

import io.github.bzkf.obdstofhir.config.RecordIdDbConfig;
import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

/**
 * Looks up an internal RecordID for a Patient_ID in an external database. Found RecordIDs are
 * cached, up to a fixed number. A Patient_ID that wasn't found isn't cached, since it may be added
 * to the database later.
 */
public class RecordIdDatabaseLookupPatientReferenceStrategy implements PatientReferenceStrategy {
  private static final Logger LOG =
      LoggerFactory.getLogger(RecordIdDatabaseLookupPatientReferenceStrategy.class);
  private static final int MAX_CACHED_RECORD_IDS = 100_000;

  private final JdbcTemplate jdbcTemplate;
  private final RecordIdDbConfig dbConfig;
  private final Map<String, String> recordIdByPatientIdCache;

  public RecordIdDatabaseLookupPatientReferenceStrategy(
      JdbcTemplate jdbcTemplate, RecordIdDbConfig dbConfig) {
    this(jdbcTemplate, dbConfig, MAX_CACHED_RECORD_IDS);
  }

  RecordIdDatabaseLookupPatientReferenceStrategy(
      JdbcTemplate jdbcTemplate, RecordIdDbConfig dbConfig, int maxCachedRecordIds) {
    this.jdbcTemplate = jdbcTemplate;
    this.dbConfig = dbConfig;
    // evicts the least recently used entry once it's full
    this.recordIdByPatientIdCache =
        Collections.synchronizedMap(
            new LinkedHashMap<String, String>(16, 0.75f, true) {
              @Override
              protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > maxCachedRecordIds;
              }
            });
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

    LOG.warn("No record ID found for patient");
    LOG.debug("No record ID found for Patient_ID: {}", patientIdentifier.getValue());

    return new PatientLookupResult(reference, false);
  }

  private Optional<String> findRecordIdByPatientId(String patientId) {
    var cachedRecordId = recordIdByPatientIdCache.get(patientId);
    if (cachedRecordId != null) {
      return Optional.of(cachedRecordId);
    }

    var recordId = queryRecordIdByPatientId(patientId);
    recordId.ifPresent(id -> recordIdByPatientIdCache.put(patientId, id));
    return recordId;
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
