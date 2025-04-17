package org.miracum.streams.ume.obdstofhir.model;

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
public class MeldungExportV3 extends OnkoResource {

  @EqualsAndHashCode.Include
  @JsonProperty("ID")
  String id;

  @EqualsAndHashCode.Include
  @JsonProperty("REFERENZ_NUMMER")
  String referenz_nummer;

  @EqualsAndHashCode.Include
  @JsonProperty("LKR_MELDUNG")
  String lkr_meldung;

  @EqualsAndHashCode.Include
  @JsonProperty("VERSIONSNUMMER")
  Integer versionsnummer;

  @JsonProperty("XML_DATEN")
  @JsonDeserialize(using = Obdsv3Deserializer.class)
  @JsonSerialize(using = Obdsv3Serializer.class)
  ObdsOrAdt obds_or_adt;

  public OBDS getObds() {
    return obds_or_adt.hasOBDS() ? obds_or_adt.obds() : null;
  }
}
