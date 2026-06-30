package io.github.bzkf.obdstofhir.patientreference;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.retry.support.RetryTemplate;

/**
 * Pseudonymizes a patient {@link Identifier} via the FHIR Pseudonymizer's {@code $de-identify}
 * operation before it is used to look up a Patient on a FHIR server.
 */
public class IdentifierPseudonymizer {
  private final IGenericClient fhirPseudonymizerClient;
  private final RetryTemplate retryTemplate;

  public IdentifierPseudonymizer(
      IGenericClient fhirPseudonymizerClient, RetryTemplate retryTemplate) {
    this.fhirPseudonymizerClient = fhirPseudonymizerClient;
    this.retryTemplate = retryTemplate;
  }

  public Identifier pseudonymize(Identifier patientIdentifier) {
    var patient = new Patient();
    patient.addIdentifier(patientIdentifier);
    var param = new Parameters();
    param.addParameter().setName("resource").setResource(patient);
    var result =
        retryTemplate.execute(
            context ->
                fhirPseudonymizerClient
                    .operation()
                    .onServer()
                    .named("de-identify")
                    .withParameters(param)
                    .returnResourceType(Patient.class)
                    .execute());
    return result.getIdentifierFirstRep();
  }
}
