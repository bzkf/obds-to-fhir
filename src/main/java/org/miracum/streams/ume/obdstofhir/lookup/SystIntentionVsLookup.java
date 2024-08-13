package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class SystIntentionVsLookup {
  private static final HashMap<String, String> lookup =
      new HashMap<>() {
        {
          put("K", "kurativ");
          put("P", "palliativ");
          put("S", "sonstiges");
          put("X", "keine Angabe");
        }
      };

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
