package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class SYSTTherapieartCSLookup {

  private static final HashMap<String, String> lookup =
      new HashMap<>() {
        {
          put("CH", "Chemotherapie");
          put("HO", "Hormontherapie");
          put("IM", "Immun- und Antikörpertherapie");
          put("KM", "Knochenmarkstransplantation");
          put("WS", "Wait and see");
          put("AS", "Active Surveillance");
          put("ZS", "Zielgerichtete Substanzen");
          put("SO", "Sonstiges");
          put("ST", "Strahlentherapie");
          put("OP", "Operation");
          put("CI", "Chemo- + Immun-/Antikörpertherapie");
          put("CZ", "Chemotherapie + zielgerichtete Substanzen");
          put("CIZ", "Chemo- + Immun-/Antikörpertherapie + zielgerichtete Substanzen");
          put("IZ", "Immun-/Antikörpertherapie + zielgerichtete Substanzen");
          put("SZ", "Stammzelltransplantation (inkl. Knochenmarktransplantation)");
          put("WW", "Watchful Waiting");
        }
      };

  private static final HashMap<String, String> multipleKeyLookup =
      new HashMap<>() {
        {
          put("CHIM", "CI");
          put("CHZS", "CZ");
          put("CHIMZS", "CIZ");
          put("IMZS", "IZ");
        }
      };

  public final String lookupSYSTTherapieartCSLookupDisplay(List<String> code) {

    if (code.size() == 1) {
      return lookup.get(code.get(0));
    } else {
      List<String> sortedCodes = new ArrayList<>(code);
      Collections.sort(sortedCodes);
      return lookup.get(multipleKeyLookup.get(String.join("", sortedCodes)));
    }
  }

  public final String lookupSYSTTherapieartCSLookupCode(List<String> code) {

    if (code.size() == 1) {
      return code.get(0);
    } else {
      List<String> sortedCodes = new ArrayList<>(code);
      Collections.sort(sortedCodes);
      return multipleKeyLookup.get(String.join("", sortedCodes));
    }
  }
}
