package org.miracum.streams.ume.obdstofhir.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@AllArgsConstructor
@Builder
@EqualsAndHashCode
@Getter
public class StructKey implements Serializable {
  private String referenzNummer;
  private String tumorId;
}
