package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;

public class FMLokalisationVsLookup {

  private static final HashMap<String, String> lookup = new HashMap<>();

  static {
    lookup.put("PUL", "Lunge");
    lookup.put("OSS", "Knochen");
    lookup.put("HEP", "Leber");
    lookup.put("BRA", "Hirn");
    lookup.put("LYM", "Lymphknoten");
    lookup.put("MAR", "Knochenmark");
    lookup.put("PLE", "Pleura");
    lookup.put("PER", "Peritoneum");
    lookup.put("ADR", "Nebennieren");
    lookup.put("SKI", "Haut");
    lookup.put("OTH", "Andere Organe");
    lookup.put("GEN", "Generalisierte Metastasierung");
  }

  public static String lookupDisplay(String code) {
    return lookup.get(code);
  }
}
