package io.github.bzkf.obdstofhir.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "obds.write-grouped-obds-to-kafka")
public record WriteGroupedObdsToKafkaConfig(boolean enabled, String topic) {}
