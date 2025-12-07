package io.github.bzkf.obdstofhir.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "fhir.mappings.patient-reference-generation.record-id-database")
@ConditionalOnProperty(
    name = "fhir.mappings.patient-reference-generation.strategy",
    havingValue = "RECORD_ID_DATABASE_LOOKUP")
@Validated
public record RecordIdDbConfig(String jdbcUrl, String username, String password, String query) {}
