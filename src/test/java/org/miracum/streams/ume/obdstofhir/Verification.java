package org.miracum.streams.ume.obdstofhir;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;
import org.approvaltests.Approvals;
import org.hl7.fhir.instance.model.api.IBaseResource;

/** Entry point for verification methods. */
public abstract class Verification {
  protected int resourceSize = 0;

  /**
   * Creates a new instance of <code>{@link SingleVerification}</code>.
   *
   * @param <T> the resource type to be verified
   * @param resource the resource to be verified
   * @return the created verification object
   */
  public static <T extends IBaseResource> SingleVerification<T> verifyThat(T resource) {
    return new SingleVerification<T>(resource);
  }

  /**
   * Creates a new instance of <code>{@link ListVerification}</code>.
   *
   * @param <T> the resources type to be verified
   * @param resources the resources to be verified
   * @return the created verification object
   */
  public static <T extends IBaseResource> ListVerification<T> verifyThat(List<T> resources) {
    return new ListVerification<T>(resources);
  }

  /**
   * Returns the verification to write more readable Verifications.
   *
   * @return the current verification object.
   */
  public Verification and() {
    return this;
  }

  /**
   * Verifies the current verification object to apply to the expected number of resources.
   *
   * @return the current verification object.
   */
  public Verification hasSize(int n) {
    assertThat(resourceSize)
        .as("Failed to assert that %d resources matches expected %d resources", n, resourceSize)
        .isEqualTo(n);
    return this;
  }

  /**
   * Verifies the current verification object does not apply to any resources.
   *
   * @return the current verification object.
   */
  public Verification isEmpty() {
    assertThat(resourceSize)
        .as("Failed to assert that given resources are empty:  Found %d resources", resourceSize)
        .isZero();
    return this;
  }

  /**
   * Verifies the current verification object does apply to some resources.
   *
   * @return the current verification object.
   */
  public Verification isNotEmpty() {
    assertThat(resourceSize).as("Failed to assert that given resources are not empty").isNotZero();
    return this;
  }

  /**
   * Verifies the current verification object is <code>{@link ListVerification}</code> and n-th
   * element matches approved source file
   *
   * @param sourceFile The approved source file
   * @return the current verification object.
   */
  public Verification nthMatches(int n, String sourceFile) {
    throw new NotImplementedException("Not implemented for single element verification");
  }

  /**
   * Verifies the current verification object matches approved source file
   *
   * @param sourceFile The approved source file
   * @return the current verification object.
   */
  public abstract Verification matches(String sourceFile);

  public static class SingleVerification<T extends IBaseResource> extends Verification {

    private T resource;

    public SingleVerification(T resource) {
      this.resourceSize = 1;
      this.resource = resource;
    }

    public SingleVerification<T> matches(String sourceFile) {
      var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
      var fhirJson = fhirParser.encodeResourceToString(resource);
      Approvals.verify(
          fhirJson,
          Approvals.NAMES.withParameters(sourceFile).forFile().withExtension(".fhir.json"));
      return this;
    }
  }

  public static class ListVerification<T extends IBaseResource> extends Verification {

    private List<T> resources;

    public ListVerification(List<T> resources) {
      this.resourceSize = resources.size();
      this.resources = resources;
    }

    @Override
    public ListVerification<T> nthMatches(int n, String sourceFile) {
      assertThat(resources).hasSizeGreaterThan(n);
      verifyThat(resources.get(n)).matches(sourceFile);
      return this;
    }

    public ListVerification<T> matches(String sourceFile) {
      for (int i = 0; i < resources.size(); i++) {
        assertThat(resources.get(i)).isNotNull();
        var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
        var fhirJson = fhirParser.encodeResourceToString(resources.get(i));
        Approvals.verify(
            fhirJson,
            Approvals.NAMES
                .withParameters(sourceFile, String.format("index_%d", i))
                .forFile()
                .withExtension(".fhir.json"));
      }
      return this;
    }
  }
}
