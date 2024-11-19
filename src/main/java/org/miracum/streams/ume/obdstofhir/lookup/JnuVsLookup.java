package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class JnuVsLookup {

  private static final HashMap<String, String> lookup = new HashMap<>();

  static {
    lookup.put("J", "Ja");
    lookup.put("N", "Nein");
    lookup.put("U", "unbekannt");
  }

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
