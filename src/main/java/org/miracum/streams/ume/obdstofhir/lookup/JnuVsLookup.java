package org.miracum.streams.ume.obdstofhir.lookup;

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

  public final String lookupJnuDisplay(String code) {
    return lookup.get(code);
  }
}
