package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class OPIntentionVsLookup {

  private final HashMap<String, String> lookup;

  public OPIntentionVsLookup() {
    lookup =
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
  }

  public final String lookupOPIntentionVSDisplay(String code) {
    return lookup.get(code);
  }
}
