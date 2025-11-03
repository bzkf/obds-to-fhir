package io.github.bzkf.obdstofhir.lookup;

import java.util.HashMap;
import java.util.List;

public class SideEffectTherapyGradingLookup {
  private static final HashMap<String, List<String>> lookup =
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

  public static String lookupCode(String code) {
    return lookup.get(code) != null ? lookup.get(code).get(0) : null;
  }

  public static String lookupDisplay(String code) {
    return lookup.get(code) != null ? lookup.get(code).get(1) : null;
  }
}
