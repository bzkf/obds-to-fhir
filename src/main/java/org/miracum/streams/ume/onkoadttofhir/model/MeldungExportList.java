package org.miracum.streams.ume.onkoadttofhir.model;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class MeldungExportList implements Serializable {

  LinkedList<MeldungExport> elements;

  public MeldungExportList() {
    elements = new LinkedList<>();
  }

  public MeldungExportList addElement(MeldungExport element) {
    elements.add(element);
    return this;
  }

  public List<MeldungExport> getElements() {
    return elements;
  }

  public MeldungExportList removeElement(MeldungExport element) {
    elements.remove(element);
    return this;
  }
}
