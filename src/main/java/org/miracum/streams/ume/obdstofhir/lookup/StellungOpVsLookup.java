package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class StellungOpVsLookup {
  private static final HashMap<String, String> lookup =
      new HashMap<>() {
        {
          put("O", "ohne Bezug zu einer operativen Therapie");
          put("A", "adjuvant");
          put("N", "neoadjuvant");
          put("I", "intraoperativ");
          put("S", "sonstiges");
        }
      };

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
