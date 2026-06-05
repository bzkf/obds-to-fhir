package io.github.bzkf.obdstofhir.model;

import org.hl7.fhir.r4.model.Reference;

public record PatientLookupResult(Reference reference, boolean existsOnServer) {}
