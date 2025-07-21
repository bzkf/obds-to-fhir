package org.miracum.streams.ume.obdstofhir.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import de.basisdatensatz.obds.v3.OBDS;
import lombok.*;
import org.miracum.streams.ume.obdstofhir.serde.Obdsv3Deserializer;
import org.miracum.streams.ume.obdstofhir.serde.Obdsv3Serializer;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
public class MeldungExportV3 extends OnkoResource {

  @EqualsAndHashCode.Include
  @JsonProperty("ID")
  String id;

  @EqualsAndHashCode.Include
  @JsonProperty("REFERENZ_NUMMER")
  String referenzNummer;

  @EqualsAndHashCode.Include
  @JsonProperty("LKR_MELDUNG")
  String lkrMeldung;

  @EqualsAndHashCode.Include
  @JsonProperty("VERSIONSNUMMER")
  Integer versionsnummer;

  @JsonProperty("XML_DATEN")
  @JsonDeserialize(using = Obdsv3Deserializer.class)
  @JsonSerialize(using = Obdsv3Serializer.class)
  ObdsOrAdt obdsOrAdt;

  public OBDS getObds() {
    return obdsOrAdt.hasOBDS() ? obdsOrAdt.getObds() : null;
  }
}
