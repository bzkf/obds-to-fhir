package org.miracum.streams.ume.obdstofhir;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "obdsv2v3.mapper")
@Data
public class Obdsv2v3MapperProperties {
  private boolean ignoreUnmappable;
  private boolean fixMissingId;
  private boolean disableSchemaValidation;
}
