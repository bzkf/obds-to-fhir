package org.miracum.streams.ume.obdstofhir.model;

import de.basisdatensatz.obds.v2.ADTGEKID;
import de.basisdatensatz.obds.v3.OBDS;

public record ObdsOrAdt(OBDS obds, ADTGEKID adt) {
  public boolean hasOBDS() {
    return obds != null;
  }

  public boolean hasADT() {
    return adt != null;
  }
}
