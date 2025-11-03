package io.github.bzkf.obdstofhir.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public class OnkoResource implements Serializable {
  protected String getString(Map<String, Object> payload, String fieldName) {
    Object fieldValue = payload.get(fieldName);
    if (fieldValue == null) {
      return null;
    }
    return String.valueOf(fieldValue);
  }

  protected Integer getInt(Map<String, Object> payload, String fieldName) {
    return payload.get(fieldName) == null ? null : (Integer) payload.get(fieldName);
  }

  protected LocalDateTime getLocalDateTime(Map<String, Object> payload, String fieldName) {
    return payload.get(fieldName) == null
        ? null
        : LocalDateTime.ofInstant(
            Instant.ofEpochMilli(((Number) payload.get(fieldName)).longValue()), ZoneOffset.UTC);
  }
}
