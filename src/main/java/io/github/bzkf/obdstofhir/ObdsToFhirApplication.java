package io.github.bzkf.obdstofhir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(
    exclude = {org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class})
@ConfigurationPropertiesScan
public class ObdsToFhirApplication {

  public static void main(String[] args) {
    SpringApplication.run(ObdsToFhirApplication.class, args);
  }
}
