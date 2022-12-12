package org.miracum.streams.ume.onkoadttofhir.lookup;

import java.util.HashMap;

public class TnmCpuPraefixTvsLookup {

  private final HashMap<String, String> lookup;

  public TnmCpuPraefixTvsLookup() {
    lookup =
        new HashMap<>() {
          {
            put(
                "c",
                "Kategorie wurde durch klinische Angaben festgestellt, bzw. erfüllt die Kriterien für p nicht");
            put(
                "p",
                "Feststellung der Kategorie erfolgte durch eine pathohistologische Untersuchung, mit der auch der höchste Grad der jeweiligen Kategorie hätte festgestellt werden können");
            put(
                "u",
                "Feststellung mit Ultraschall (Unterkategorie von c mit besonderer diagnostischer Relevanz, z.B. beim Rektumkarzinom)");
          }
        };
  }

  public final String lookupTnmCpuPraefixDisplay(String code) {
    return lookup.get(code);
  }
}
