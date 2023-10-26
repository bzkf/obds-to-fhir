package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class DisplayAdtSeitenlokalisationLookup {
  private final HashMap<String, String> lookup;

  public DisplayAdtSeitenlokalisationLookup() {
    lookup =
        new HashMap<>() {
          {
            put("L", "links");
            put("R", "rechts");
            put("B", "beidseitig (sollte bei bestimmten Tumoren 2 Meldungen ergeben)");
            put("M", "Mittellinie/Mittig");
            put(
                "T",
                "trifft nicht zu (Seitenangabe nicht sinnvoll, einschließlich Systemerkrankungen)");
            put("U", "unbekannt");
          }
        };
  }

  public final String lookupAdtSeitenlokalisationDisplay(String code) {
    return lookup.get(code);
  }
}
