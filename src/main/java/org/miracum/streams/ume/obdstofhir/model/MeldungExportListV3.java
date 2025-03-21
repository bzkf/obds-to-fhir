package org.miracum.streams.ume.obdstofhir.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

public class MeldungExportListV3 extends ArrayList<MeldungExportV3> implements Serializable {

  public MeldungExportListV3() {
    super();
  }

  public MeldungExportListV3(Collection<MeldungExportV3> elements) {
    super(elements);
  }

  public MeldungExportListV3 addElement(MeldungExportV3 element) {
    this.add(element);
    return this;
  }

  public MeldungExportListV3 removeElement(MeldungExportV3 element) {
    this.remove(element);
    return this;
  }
}
