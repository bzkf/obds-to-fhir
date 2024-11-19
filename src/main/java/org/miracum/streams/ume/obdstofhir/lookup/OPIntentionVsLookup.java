package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class OPIntentionVsLookup {

  private static final HashMap<String, String> lookup = new HashMap<>();

  static {
    lookup.put("K", "kurativ");
    lookup.put("P", "palliativ");
    lookup.put("D", "diagnostisch");
    lookup.put("R", "Revision/Komplikation");
    lookup.put("S", "sonstiges");
    lookup.put("X", "Fehlende Angabe");
  }

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
