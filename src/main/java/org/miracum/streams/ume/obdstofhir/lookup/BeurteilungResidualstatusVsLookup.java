package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class BeurteilungResidualstatusVsLookup {

  private static final HashMap<String, String> lookup = new HashMap<>();

  static {
    lookup.put("RX", "Vorhandensein von Residualtumor kann nicht beurteilt werden");
    lookup.put("R0", "kein Residualtumor");
    lookup.put("R1", "Mikroskopischer Residualtumor");
    lookup.put("R1(is)", "In-Situ-Rest");
    lookup.put("R1(cy+)", "Cytologischer Rest");
    lookup.put("R2", "Makroskopischer Residualtumor");
  }

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
