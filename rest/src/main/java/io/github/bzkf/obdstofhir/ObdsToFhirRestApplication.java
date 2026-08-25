package io.github.bzkf.obdstofhir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(
    exclude = {org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class})
@ConfigurationPropertiesScan
public class ObdsToFhirRestApplication {

  public static void main(String[] args) {
    SpringApplication.run(ObdsToFhirRestApplication.class, args);
  }
}
