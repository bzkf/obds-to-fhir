package io.github.bzkf.obdstofhir.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Feature;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.serde.Obdsv3Deserializer;
import io.github.bzkf.obdstofhir.serde.Obdsv3Serializer;
import lombok.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@JsonFormat(with = Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
public class MeldungExportV3 {

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
