package io.github.bzkf.obdstofhir.model;

import de.basisdatensatz.obds.v2.ADTGEKID;
import de.basisdatensatz.obds.v3.OBDS;
import lombok.Data;

@Data
public class ObdsOrAdt {
  private final OBDS obds;
  private final ADTGEKID adt;

  public static ObdsOrAdt from(OBDS obds) {
    if (obds == null) throw new IllegalArgumentException("OBDS must not be null");
    return new ObdsOrAdt(obds, null);
  }

  public static ObdsOrAdt from(ADTGEKID adt) {
    if (adt == null) throw new IllegalArgumentException("ADTGEKID must not be null");
    return new ObdsOrAdt(null, adt);
  }

  public boolean hasOBDS() {
    return obds != null;
  }

  public boolean hasADT() {
    return adt != null;
  }
}
