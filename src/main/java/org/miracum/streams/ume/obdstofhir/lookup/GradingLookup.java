package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class GradingLookup {

  private static final HashMap<String, String> lookup = new HashMap<>();

  static {
    lookup.put("0", "malignes Melanom der Konjunktiva");
    lookup.put("1", "gut differenziert");
    lookup.put("2", "mäßig differenziert");
    lookup.put("3", "schlecht differenziert");
    lookup.put("4", "undifferenziert");
    lookup.put("X", "nicht bestimmbar");
    lookup.put("L", "low grade (G1 oder G2)");
    lookup.put("M", "intermediate (G2 oder G3)");
    lookup.put("H", "high grade (G3 oder G4)");
    lookup.put("B", "Borderline");
    lookup.put("U", "unbekannt");
    lookup.put("T", "trifft nicht zu");
  }

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
