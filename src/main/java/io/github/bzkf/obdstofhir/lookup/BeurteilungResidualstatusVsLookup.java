package io.github.bzkf.obdstofhir.lookup;

import java.util.HashMap;

public class BeurteilungResidualstatusVsLookup {

  private static final HashMap<String, String> lookup =
      new HashMap<>() {
        {
          put("RX", "Vorhandensein von Residualtumor kann nicht beurteilt werden");
          put("R0", "kein Residualtumor");
          put("R1", "Mikroskopischer Residualtumor");
          put("R1(is)", "In-Situ-Rest");
          put("R1(cy+)", "Cytologischer Rest");
          put("R2", "Makroskopischer Residualtumor");
        }
      };

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
