package org.miracum.streams.ume.onkoadttofhir.lookup;

import java.util.HashMap;

public class SYSTTherapieartCSLookup {

  private final HashMap<String, String> lookup;

  public SYSTTherapieartCSLookup() {
    lookup =
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
          }
        };
  }

  public final String lookupSYSTTherapieartCSLookupDisplay(String code) {
    return lookup.get(code);
  }
}
