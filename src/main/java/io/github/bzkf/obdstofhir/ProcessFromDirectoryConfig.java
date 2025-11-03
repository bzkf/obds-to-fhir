package io.github.bzkf.obdstofhir;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "obds.process-from-directory")
public record ProcessFromDirectoryConfig(
    boolean enabled,
    String path,
    OutputToDirectoryConfig outputToDirectory,
    OutputToKafkaConfig outputToKafka) {
  public record OutputToDirectoryConfig(boolean enabled, String path) {}

  public record OutputToKafkaConfig(boolean enabled, String topic) {}
}
