package org.miracum.streams.ume.obdstofhir.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

public class MeldungExportListV3 extends ArrayList<MeldungExport> implements Serializable {

  public MeldungExportListV3() {
    super();
  }

  public MeldungExportListV3(Collection<MeldungExport> elements) {
    super(elements);
  }

  public MeldungExportListV3 addElement(MeldungExport element) {
    this.add(element);
    return this;
  }

  public MeldungExportListV3 removeElement(MeldungExport element) {
    this.remove(element);
    return this;
  }
}
