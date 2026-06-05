package io.github.bzkf.obdstofhir;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProfileTestConfig {

  @Bean
  Function<OBDS.MengePatient.Patient, PatientLookupResult> patientReferenceGenerator(
      FhirProperties fhirProperties) {
    return p -> {
      var system = fhirProperties.getSystems().getIdentifiers().getPatientId();
      var value = p.getPatientID();

      var reference = new Reference("Patient/" + DigestUtils.sha256Hex(system + "|" + value));
      var identifier = new Identifier().setSystem(system).setValue(value);
      reference.setIdentifier(identifier);
      return new PatientLookupResult(reference, false);
    };
  }
}
