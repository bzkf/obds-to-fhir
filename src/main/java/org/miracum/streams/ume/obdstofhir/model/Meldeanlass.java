package org.miracum.streams.ume.obdstofhir.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Meldeanlass {
  BEHANDLUNGSBEGINN("behandlungsbeginn"),
  BEHANDLUNGSENDE("behandlungsende"),
  STATUSAENDERUNG("statusaenderung"),
  STATUSMELDUNG("statusmeldung"),
  DIAGNOSE("diagnose"),
  TOD("tod"),
  ;

  private final String text;

  Meldeanlass(final String text) {
    this.text = text;
  }

  @Override
  @JsonValue
  public String toString() {
    return text;
  }
}
