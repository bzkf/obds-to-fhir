package io.github.bzkf.obdstofhir.config;

import jakarta.validation.constraints.NotNull;
import java.net.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fhir.mappings.patient-reference-generation.fhir-server")
public record FhirServerConfig(@NotNull URL baseUrl, Auth auth) {
  public record Auth(BasicAuth basic) {}

  public record BasicAuth(boolean enabled, String username, String password) {}
}
