package org.miracum.streams.ume.onkoadttofhir.lookup;

import java.util.HashMap;

public class StellungOpVsLookup {
  private final HashMap<String, String> lookup;

  public StellungOpVsLookup() {
    lookup =
        new HashMap<>() {
          {
            put("O", "ohne Bezug zu einer operativen Therapie");
            put("A", "adjuvant");
            put("N", "neoadjuvant");
            put("I", "intraoperativ");
            put("S", "sonstiges");
          }
        };
  }

  public final String lookupStellungOpDisplay(String code) {
    return lookup.get(code);
  }
}
