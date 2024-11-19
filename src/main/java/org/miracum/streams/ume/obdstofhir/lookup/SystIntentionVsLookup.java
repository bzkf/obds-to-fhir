package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class SystIntentionVsLookup {

  private static final HashMap<String, String> lookup = new HashMap<>();

  static {
    lookup.put("K", "kurativ");
    lookup.put("P", "palliativ");
    lookup.put("S", "sonstiges");
    lookup.put("X", "keine Angabe");
  }

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
