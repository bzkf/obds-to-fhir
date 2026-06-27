package io.github.bzkf.obdstofhir.patientreference;

import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;

public class PlainPatientIdReferenceStrategy implements PatientReferenceStrategy {
  @Override
  public PatientLookupResult resolve(Identifier patientIdentifier) {
    return new PatientLookupResult(
        new Reference("Patient/" + patientIdentifier.getValue()).setIdentifier(patientIdentifier),
        false);
  }
}
