package org.miracum.streams.ume.obdstofhir;

import dev.pcvolkmer.onko.obds2to3.ObdsMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ObdsToFhirApplication {

  @Bean
  public ObdsMapper obdsMapper() {
    return ObdsMapper.builder().ignoreUnmappable(true).build();
  }

  public static void main(String[] args) {
    SpringApplication.run(ObdsToFhirApplication.class, args);
  }
}
