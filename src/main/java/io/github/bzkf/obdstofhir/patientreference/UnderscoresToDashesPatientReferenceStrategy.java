package io.github.bzkf.obdstofhir.patientreference;

import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;

public class UnderscoresToDashesPatientReferenceStrategy implements PatientReferenceStrategy {
  @Override
  public PatientLookupResult resolve(Identifier patientIdentifier) {
    var id = patientIdentifier.getValue().replace('_', '-');
    return new PatientLookupResult(
        new Reference("Patient/" + id).setIdentifier(patientIdentifier), false);
  }
}
