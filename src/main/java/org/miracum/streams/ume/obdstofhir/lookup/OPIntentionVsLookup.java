package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class OPIntentionVsLookup {

  private static final HashMap<String, String> lookup =
      new HashMap<>() {
        {
          put("K", "kurativ");
          put("P", "palliativ");
          put("D", "diagnostisch");
          put("R", "Revision/Komplikation");
          put("S", "sonstiges");
          put("X", "Fehlende Angabe");
        }
      };

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
