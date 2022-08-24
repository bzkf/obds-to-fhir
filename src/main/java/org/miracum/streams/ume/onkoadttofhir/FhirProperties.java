package org.miracum.streams.ume.onkoadttofhir;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "fhir")
@Data
public class FhirProperties {
  private FhirSystems systems;
  private FhirDisplay display;
  private FhirProfiles profiles;

  @Data
  public static class FhirSystems {
    private String patientId;
    private String identifierType;
    private String conditionId;
    private String ObservationId;
    private String observationCategorySystem;
    private String loinc;
    private String idco3Morphologie;
    private String gradingDktk;
  }

  @Data
  public static class FhirProfiles {
    private String histologie;
    private String grading;
  }

  @Data
  public static class FhirDisplay {
    private String histologyLoinc;
    private String gradingLoinc;
  }
}
