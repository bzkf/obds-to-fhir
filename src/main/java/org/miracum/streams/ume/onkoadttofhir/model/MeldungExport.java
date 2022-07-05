package org.miracum.streams.ume.onkoadttofhir.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class MeldungExport extends OnkoResource {

  @EqualsAndHashCode.Include Integer id;

  @EqualsAndHashCode.Include String referenz_nummer;

  // ADT_GEKID xml_daten;
  String xml_daten;

  @JsonProperty("payload")
  public void getPayload(Map<String, Object> payload) {
    this.id = getInt(payload, "ID");
    this.referenz_nummer = getString(payload, "REFERENZ_NUMMER");
    this.xml_daten = getString(payload, "XML_DATEN");
  }
}
