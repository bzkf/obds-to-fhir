package io.github.bzkf.obdstofhir.patientreference;

import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import io.github.dizuker.tofhir.IdUtils;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;

public class Sha256HashedPatientReferenceStrategy implements PatientReferenceStrategy {
  @Override
  public PatientLookupResult resolve(Identifier patientIdentifier) {
    var id = IdUtils.fromIdentifier(patientIdentifier);
    return new PatientLookupResult(
        new Reference("Patient/" + id).setIdentifier(patientIdentifier),
        false // basically just a default value here
        );
  }
}
