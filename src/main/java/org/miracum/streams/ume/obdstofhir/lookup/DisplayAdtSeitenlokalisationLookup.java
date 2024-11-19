package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class DisplayAdtSeitenlokalisationLookup {

  private static final HashMap<String, String> lookup = new HashMap<>();

  static {
    lookup.put("L", "links");
    lookup.put("R", "rechts");
    lookup.put("B", "beidseitig (sollte bei bestimmten Tumoren 2 Meldungen ergeben)");
    lookup.put("M", "Mittellinie/Mittig");
    lookup.put(
        "T", "trifft nicht zu (Seitenangabe nicht sinnvoll, einschließlich Systemerkrankungen)");
    lookup.put("U", "unbekannt");
  }

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
