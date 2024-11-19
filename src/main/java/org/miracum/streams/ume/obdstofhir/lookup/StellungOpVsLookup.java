package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class StellungOpVsLookup {

  private static final HashMap<String, String> lookup = new HashMap<>();

  static {
    lookup.put("O", "ohne Bezug zu einer operativen Therapie");
    lookup.put("A", "adjuvant");
    lookup.put("N", "neoadjuvant");
    lookup.put("I", "intraoperativ");
    lookup.put("S", "sonstiges");
  }

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
