package io.github.bzkf.obdstofhir.patientreference;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.BundleUtil;
import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import java.util.Optional;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.jspecify.annotations.Nullable;
import org.springframework.retry.support.RetryTemplate;

/**
 * Looks up the logical Patient id on a FHIR server by identifier, optionally pseudonymizing the
 * identifier first via an {@link IdentifierPseudonymizer}.
 */
public class FhirServerLookupPatientReferenceStrategy implements PatientReferenceStrategy {
  private final IGenericClient fhirClient;
  private final RetryTemplate retryTemplate;
  private final boolean identifierOnlyReferencesAllowed;
  private final @Nullable IdentifierPseudonymizer identifierPseudonymizer;

  public FhirServerLookupPatientReferenceStrategy(
      IGenericClient fhirClient,
      RetryTemplate retryTemplate,
      boolean identifierOnlyReferencesAllowed,
      @Nullable IdentifierPseudonymizer identifierPseudonymizer) {
    this.fhirClient = fhirClient;
    this.retryTemplate = retryTemplate;
    this.identifierOnlyReferencesAllowed = identifierOnlyReferencesAllowed;
    this.identifierPseudonymizer = identifierPseudonymizer;
  }

  @Override
  public PatientLookupResult resolve(Identifier patientIdentifier) {
    // if pseudonymization is enabled, first pseudonymize the identifier and use this as the
    // identifier to look up
    var identifierToLookup =
        identifierPseudonymizer != null
            ? identifierPseudonymizer.pseudonymize(patientIdentifier)
            : patientIdentifier;

    var id = findPatientIdByIdentifier(identifierToLookup);

    // always set the (pseudonymized) identifier as part of the reference
    var reference = new Reference().setIdentifier(identifierToLookup);

    if (id.isPresent()) {
      reference.setReference("Patient/" + id.get().getIdPart());
      return new PatientLookupResult(reference, true);
    }

    if (!identifierOnlyReferencesAllowed) {
      throw new IllegalStateException(
          "Patient not found on FHIR server and identifier-only references are disabled.");
    }

    return new PatientLookupResult(reference, false);
  }

  private Optional<IIdType> findPatientIdByIdentifier(Identifier patientIdentifier) {
    var result =
        retryTemplate.execute(
            context ->
                fhirClient
                    .search()
                    .forResource(Patient.class)
                    .where(
                        Patient.IDENTIFIER
                            .exactly()
                            .systemAndIdentifier(
                                patientIdentifier.getSystem(), patientIdentifier.getValue()))
                    .execute());

    var patients =
        BundleUtil.toListOfResourcesOfType(fhirClient.getFhirContext(), result, Patient.class);

    if (patients.isEmpty()) {
      return Optional.empty();
    }

    if (patients.size() > 1) {
      throw new IllegalArgumentException("More than one patient resource matches the identifier.");
    }

    return Optional.of(patients.getFirst().getIdElement());
  }
}
