package io.github.bzkf.obdstofhir.lookup;

import java.util.HashMap;

public class FMLokalisationVsLookup {
  private static final HashMap<String, String> lookup =
      new HashMap<>() {
        {
          put("PUL", "Lunge");
          put("OSS", "Knochen");
          put("HEP", "Leber");
          put("BRA", "Hirn");
          put("LYM", "Lymphknoten");
          put("MAR", "Knochenmark");
          put("PLE", "Pleura");
          put("PER", "Peritoneum");
          put("ADR", "Nebennieren");
          put("SKI", "Haut");
          put("OTH", "Andere Organe");
          put("GEN", "Generalisierte Metastasierung");
        }
      };

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
