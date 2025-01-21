package org.miracum.streams.ume.obdstofhir.lookup.obds;

import de.basisdatensatz.obds.v3.SYSTTyp.Therapieart;
import java.util.HashMap;

public class OPSTherapietypLookup {
  private static final HashMap<Therapieart, OPS> lookup = new HashMap<>();

  static {
    lookup.put(
        Therapieart.CH,
        OPS.of("8-54", "Zytostatische Chemotherapie, Immuntherapie und antiretrovirale Therapie"));
    lookup.put(Therapieart.HO, OPS.of("6-00", "Applikation von Medikamenten"));
    lookup.put(
        Therapieart.IM,
        OPS.of("8-54", "Zytostatische Chemotherapie, Immuntherapie und antiretrovirale Therapie"));
    lookup.put(
        Therapieart.SZ,
        OPS.of(
            "8-86",
            "Autogene und allogene Stammzelltherapie und lokale Therapie mit Blutbestandteilen und Hepatozyten"));
    lookup.put(
        Therapieart.CI,
        OPS.of("8-54", "Zytostatische Chemotherapie, Immuntherapie und antiretrovirale Therapie"));
  }

  public static String lookupCode(Therapieart obdsCode) {
    return lookup.get(obdsCode) != null ? lookup.get(obdsCode).getCode() : null;
  }

  public static String lookupDisplay(Therapieart obdsCode) {
    return lookup.get(obdsCode) != null ? lookup.get(obdsCode).getDisplay() : null;
  }

  private static class OPS {
    private String code;
    private String display;

    private OPS(String code, String display) {
      this.code = code;
      this.display = display;
    }

    public static OPS of(String code, String display) {
      return new OPS(code, display);
    }

    public String getCode() {
      return this.code;
    }

    public String getDisplay() {
      return this.display;
    }
  }
}
