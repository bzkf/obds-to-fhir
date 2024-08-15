package org.miracum.streams.ume.obdstofhir.model;

import java.io.Serializable;
import lombok.Builder;

@Builder
public record StructKey(String referenzNummer, String tumorId) implements Serializable {}
