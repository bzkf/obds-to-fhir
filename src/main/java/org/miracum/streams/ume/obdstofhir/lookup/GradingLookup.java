package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class GradingLookup {

  private static final HashMap<String, String> lookup =
      new HashMap<>() {
        {
          put("0", "malignes Melanom der Konjunktiva");
          put("1", "gut differenziert");
          put("2", "mäßig differenziert");
          put("3", "schlecht differenziert");
          put("4", "undifferenziert");
          put("X", "nicht bestimmbar");
          put("L", "low grade (G1 oder G2)");
          put("M", "intermediate (G2 oder G3)");
          put("H", "high grade (G3 oder G4)");
          put("B", "Borderline");
          put("U", "unbekannt");
          put("T", "trifft nicht zu");
        }
      };

  public final String lookupGradingDisplay(String code) {
    return lookup.get(code);
  }
}
