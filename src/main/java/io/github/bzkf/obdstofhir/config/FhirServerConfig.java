package io.github.bzkf.obdstofhir.config;

import jakarta.validation.constraints.NotNull;
import java.net.URL;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "fhir.mappings.patient-reference-generation.fhir-server")
@ConditionalOnProperty(
    name = "fhir.mappings.patient-reference-generation.strategy",
    havingValue = "FHIR_SERVER_LOOKUP")
@Validated
public record FhirServerConfig(@NotNull URL baseUrl, Auth auth) {
  public record Auth(BasicAuth basic) {}

  public record BasicAuth(boolean enabled, String username, String password) {}
}
