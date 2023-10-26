package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class FMLokalisationVsLookup {
  private final HashMap<String, String> lookup;

  public FMLokalisationVsLookup() {
    lookup =
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
  }

  public final String lookupFMLokalisationVSDisplay(String code) {
    return lookup.get(code);
  }
}
