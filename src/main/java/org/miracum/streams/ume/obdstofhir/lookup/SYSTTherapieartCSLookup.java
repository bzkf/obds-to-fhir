package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class SYSTTherapieartCSLookup {

  private static final HashMap<String, String> lookup = new HashMap<>();

  static {
    lookup.put("CH", "Chemotherapie");
    lookup.put("HO", "Hormontherapie");
    lookup.put("IM", "Immun- und Antikörpertherapie");
    lookup.put("KM", "Knochenmarkstransplantation");
    lookup.put("WS", "Wait and see");
    lookup.put("AS", "Active Surveillance");
    lookup.put("ZS", "Zielgerichtete Substanzen");
    lookup.put("SO", "Sonstiges");
    lookup.put("ST", "Strahlentherapie");
    lookup.put("OP", "Operation");
    lookup.put("CI", "Chemo- + Immun-/Antikörpertherapie");
    lookup.put("CZ", "Chemotherapie + zielgerichtete Substanzen");
    lookup.put("CIZ", "Chemo- + Immun-/Antikörpertherapie + zielgerichtete Substanzen");
    lookup.put("IZ", "Immun-/Antikörpertherapie + zielgerichtete Substanzen");
    lookup.put("SZ", "Stammzelltransplantation (inkl. Knochenmarktransplantation)");
    lookup.put("WW", "Watchful Waiting");
  }

  private static final HashMap<String, String> multipleKeyLookup = new HashMap<>();

  static {
    multipleKeyLookup.put("CHIM", "CI");
    multipleKeyLookup.put("CHZS", "CZ");
    multipleKeyLookup.put("CHIMZS", "CIZ");
    multipleKeyLookup.put("IMZS", "IZ");
  }

  public static String lookupDisplay(List<String> code) {

    if (code.size() == 1) {
      return lookup.get(code.get(0));
    } else {
      List<String> sortedCodes = new ArrayList<>(code);
      Collections.sort(sortedCodes);
      return lookup.get(multipleKeyLookup.get(String.join("", sortedCodes)));
    }
  }

  public static String lookupCode(List<String> code) {

    if (code.size() == 1) {
      return code.get(0);
    } else {
      List<String> sortedCodes = new ArrayList<>(code);
      Collections.sort(sortedCodes);
      return multipleKeyLookup.get(String.join("", sortedCodes));
    }
  }
}
