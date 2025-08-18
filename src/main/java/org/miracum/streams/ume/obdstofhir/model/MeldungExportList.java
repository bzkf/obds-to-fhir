package org.miracum.streams.ume.obdstofhir.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MeldungExportList implements Serializable {

  ArrayList<MeldungExport> elements;

  public MeldungExportList() {
    elements = new ArrayList<>();
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
