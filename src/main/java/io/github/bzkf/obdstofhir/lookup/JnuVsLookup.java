package io.github.bzkf.obdstofhir.lookup;

import java.util.HashMap;

public class JnuVsLookup {
  private static final HashMap<String, String> lookup =
      new HashMap<>() {
        {
          put("J", "Ja");
          put("N", "Nein");
          put("U", "unbekannt");
        }
      };

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
