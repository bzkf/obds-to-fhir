package org.miracum.streams.ume.obdstofhir.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.util.Map;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class MeldungExport extends OnkoResource {

  @EqualsAndHashCode.Include String id;

  @EqualsAndHashCode.Include String referenz_nummer;

  @EqualsAndHashCode.Include String lkr_meldung;

  @EqualsAndHashCode.Include Integer versionsnummer;

  ADT_GEKID xml_daten;

  @JsonProperty("payload")
  public void getPayload(Map<String, Object> payload) {
    this.id = getString(payload, "ID");
    this.referenz_nummer = getString(payload, "REFERENZ_NUMMER");
    this.lkr_meldung = getString(payload, "LKR_MELDUNG");
    this.versionsnummer = getInt(payload, "VERSIONSNUMMER");

    XmlMapper xmlMapper = new XmlMapper();
    xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    xmlMapper.registerModule(new Jdk8Module());
    try {
      this.xml_daten = xmlMapper.readValue(getString(payload, "XML_DATEN"), ADT_GEKID.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
