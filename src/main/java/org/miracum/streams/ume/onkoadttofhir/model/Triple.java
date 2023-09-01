package org.miracum.streams.ume.onkoadttofhir.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Triple<T, U, V> {

  private T first;
  private U second;
  private V third;
}
