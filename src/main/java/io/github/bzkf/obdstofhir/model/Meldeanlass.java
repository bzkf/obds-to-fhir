package io.github.bzkf.obdstofhir.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Meldeanlass {
  BEHANDLUNGSBEGINN("behandlungsbeginn"),
  BEHANDLUNGSENDE("behandlungsende"),
  STATUSAENDERUNG("statusaenderung"),
  STATUSMELDUNG("statusmeldung"),
  DIAGNOSE("diagnose"),
  TOD("tod"),
  HISTOLOGIE_ZYTOLOGIE("histologie_zytologie"),
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
