package io.github.bzkf.obdstofhir.patientreference;

import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import org.hl7.fhir.r4.model.Identifier;

/**
 * Resolves a FHIR {@code Patient} reference for a given oBDS patient identifier. Implementations
 * encapsulate one of the configurable {@code fhir.mappings.patient-reference-generation.strategy}
 * options.
 */
public interface PatientReferenceStrategy {
  PatientLookupResult resolve(Identifier patientIdentifier);
}
