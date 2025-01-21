package org.miracum.streams.ume.obdstofhir.lookup.obds;

import de.basisdatensatz.obds.v3.SYSTTyp.Therapieart;
import java.util.HashMap;

public class SnomedCtTherapietypLookup {
  private static final HashMap<Therapieart, SnomedCt> lookup = new HashMap<>();

  static {
    lookup.put(Therapieart.CH, SnomedCt.of("367336001", "Chemotherapy (procedure)"));
    lookup.put(Therapieart.HO, SnomedCt.of("169413002", "Hormone therapy (procedure)"));
    lookup.put(Therapieart.IM, SnomedCt.of("897713009", "Immunotherapy (procedure)"));
    lookup.put(
        Therapieart.ZS,
        SnomedCt.of(
            "1255831008",
            "Chemotherapy for malignant neoplastic disease using targeted agent (procedure)"));
    lookup.put(
        Therapieart.SZ, SnomedCt.of("1269349006", "Transplantation of stem cell (procedure)"));
    lookup.put(
        Therapieart.CI,
        SnomedCt.of("897713009", "Antineoplastic chemoimmunotherapy (regime/therapy)"));
    lookup.put(
        Therapieart.CZ,
        SnomedCt.of(
            "1255831008",
            "Chemotherapy for malignant neoplastic disease using targeted agent (procedure)"));
    lookup.put(
        Therapieart.CIZ,
        SnomedCt.of("897713009", "Antineoplastic chemoimmunotherapy (regime/therapy)"));
    lookup.put(Therapieart.IZ, SnomedCt.of("76334006", "Immunotherapy (procedure)"));
    lookup.put(
        Therapieart.WW,
        SnomedCt.of("373818007", "No anti-cancer treatment - watchful waiting (finding)"));
    lookup.put(Therapieart.AS, SnomedCt.of("424313000", "Active surveillance (regime/therapy)"));
    lookup.put(Therapieart.WS, SnomedCt.of("310341009", "Follow-up (wait and see) (finding)"));
    lookup.put(Therapieart.SO, SnomedCt.of("74964007", "Other (qualifier value)"));
  }

  public static String lookupCode(Therapieart obdsCode) {
    return lookup.get(obdsCode) != null ? lookup.get(obdsCode).getCode() : null;
  }

  public static String lookupDisplay(Therapieart obdsCode) {
    return lookup.get(obdsCode) != null ? lookup.get(obdsCode).getDisplay() : null;
  }

  static class SnomedCt {
    private String code;
    private String display;

    private SnomedCt(String code, String display) {
      this.code = code;
      this.display = display;
    }

    public static SnomedCt of(String code, String display) {
      return new SnomedCt(code, display);
    }

    public String getCode() {
      return this.code;
    }

    public String getDisplay() {
      return this.display;
    }
  }
}
