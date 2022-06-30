package org.miracum.streams.ume.onkoadttofhir;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "fhir")
@Data
public class FhirProperties {
  private FhirSystems systems;

  @Data
  public static class FhirSystems {
    private String TODO; // TODO
  }
}
