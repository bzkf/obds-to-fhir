package io.github.bzkf.obdstofhir.patientreference;

import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import org.apache.commons.codec.digest.DigestUtils;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;

public class Md5HashedPatientReferenceStrategy implements PatientReferenceStrategy {
  @Override
  public PatientLookupResult resolve(Identifier patientIdentifier) {
    var id = DigestUtils.md5Hex(patientIdentifier.getValue());
    return new PatientLookupResult(
        new Reference("Patient/" + id).setIdentifier(patientIdentifier), false);
  }
}
