package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;
import java.util.List;

public class SideEffectTherapyGradingLookup {
  private final HashMap<String, List<String>> lookup;

  public SideEffectTherapyGradingLookup() {
    lookup =
        new HashMap<>() {
          {
            put("K", List.of("0", "keine"));
            put("1", List.of("1", "mild"));
            put("2", List.of("2", "moderat"));
            put("3", List.of("3", "schwerwiegend"));
            put("4", List.of("4", "lebensbedrohlich"));
            put("5", List.of("5", "tödlich"));
          }
        };
  }

  public final String lookupSideEffectTherapyGradingCode(String code) {
    return lookup.get(code).get(0);
  }

  public final String lookupSideEffectTherapyGradingDisplay(String code) {
    return lookup.get(code).get(1);
  }
}
