package io.github.bzkf.obdstofhir;

import io.github.bzkf.obds2toobds3.ObdsMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Obdsv2v3MapperProperties.class)
public class Obdsv2v3MapperConfig {

  private final Obdsv2v3MapperProperties obdsv2v3MapperProperties;

  protected Obdsv2v3MapperConfig(Obdsv2v3MapperProperties obdsv2v3MapperProperties) {
    this.obdsv2v3MapperProperties = obdsv2v3MapperProperties;
  }

  @Bean
  public ObdsMapper obdsMapper() {
    return ObdsMapper.builder()
        .ignoreUnmappable(obdsv2v3MapperProperties.isIgnoreUnmappable())
        .fixMissingId(obdsv2v3MapperProperties.isFixMissingId())
        .disableSchemaValidation(obdsv2v3MapperProperties.isDisableSchemaValidation())
        .build();
  }
}
