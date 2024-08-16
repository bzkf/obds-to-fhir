package org.miracum.streams.ume.obdstofhir.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import lombok.Builder;

@Builder
public record StructKey(
    @JsonProperty("REFERENZ_NUMMER") String referenzNummer,
    @JsonProperty("TUMOR_ID") String tumorId)
    implements Serializable {}
