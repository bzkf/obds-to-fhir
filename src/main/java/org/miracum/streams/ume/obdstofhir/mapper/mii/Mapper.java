package org.miracum.streams.ume.obdstofhir.mapper.mii;

import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.hl7.fhir.r4.model.Reference;

public interface Mapper<S, D> {

  /**
   * Default implementation of reference validation. This does not check the existance of the
   * referenced resource!
   *
   * @param reference The reference to be validated
   * @param resourceType The required resource type of the reference
   * @return Will return `true` if reference is usable
   * @throws NullPointerException if reference is null
   * @throws IllegalArgumentException if reference is not of required reesource type.
   */
  default boolean verifyReference(Reference reference, ResourceType resourceType)
      throws NullPointerException, IllegalArgumentException {
    Objects.requireNonNull(
        reference, String.format("Reference to %s must not be null", resourceType.toString()));
    Validate.isTrue(
        Objects.equals(reference.getReferenceElement().getResourceType(), resourceType.toCode()),
        String.format("The reference should point to a %s resource", resourceType.toString()));

    return true;
  }
}
