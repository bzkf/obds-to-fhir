package io.github.bzkf.obdstofhir;

import de.basisdatensatz.obds.v3.OBDS;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProfileTestConfig {

  @Bean
  public Function<OBDS.MengePatient.Patient, Reference> patientReferenceGenerator(
      FhirProperties fhirProperties) {
    return p -> {
      var system = fhirProperties.getSystems().getIdentifiers().getPatientId();
      var value = p.getPatientID();
      return new Reference("Patient/" + DigestUtils.sha256Hex(system + "|" + value));
    };
  }
}
