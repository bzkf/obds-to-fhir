package org.miracum.streams.ume.obdstofhir.lookup;

import java.util.HashMap;
import java.util.List;

public class SnomedCtSeitenlokalisationLookup {
  private static final HashMap<String, List<String>> lookup =
      new HashMap<>() {
        {
          put("L", List.of("7771000", "Left"));
          put("R", List.of("24028007", "Right"));
          put("B", List.of("51440002", "Right and left / Both sides"));
          put("M", List.of("260528009", "Median"));
          put("T", List.of("396360001", "Tumor site not applicable (finding)"));
          put("U", List.of("87100004", "Topography unknown"));
        }
      };

  public final String lookupCode(String AdtCode) {
    return lookup.get(AdtCode) != null ? lookup.get(AdtCode).get(0) : null;
  }

  public final String lookupDisplay(String AdtCode) {
    return lookup.get(AdtCode) != null ? lookup.get(AdtCode).get(1) : null;
  }
}
