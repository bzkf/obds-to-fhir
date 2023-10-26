package org.miracum.streams.ume.obdstofhir.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Tupel<T, U> {

  private T first;
  private U second;
}
