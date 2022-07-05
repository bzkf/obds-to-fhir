package org.miracum.streams.ume.onkoadttofhir.processor;

import org.miracum.streams.ume.onkoadttofhir.FhirProperties;

public abstract class OnkoProcessor {
  protected final FhirProperties fhirProperties;

  protected OnkoProcessor(final FhirProperties fhirProperties) {
    this.fhirProperties = fhirProperties;
  }
}
