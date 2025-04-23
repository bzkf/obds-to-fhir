package org.miracum.streams.ume.obdstofhir.model;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;

public class MeldungExportListV3 extends HashSet<MeldungExportV3> implements Serializable {

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
