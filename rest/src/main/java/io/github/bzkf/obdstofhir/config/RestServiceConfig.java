package io.github.bzkf.obdstofhir.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "obds.rest-service")
public record RestServiceConfig(
    boolean wrapSinglePatientBundle, OutputToKafkaConfig outputToKafka) {
  public record OutputToKafkaConfig(boolean enabled, String topic) {}
}
