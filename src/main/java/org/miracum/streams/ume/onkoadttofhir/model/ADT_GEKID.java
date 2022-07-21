package org.miracum.streams.ume.onkoadttofhir.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import java.io.Serializable;
import java.util.List;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ADT_GEKID implements Serializable {

  @JsonProperty private String Schema_Version;

  @JsonProperty private Absender Absender;

  @JacksonXmlElementWrapper(useWrapping = false)
  private List<Menge_Patient> Menge_Patient;

  @Data
  public static class Absender {
    @JsonProperty private String Absender_Bezeichnung;

    @JsonProperty private String Absender_Ansprechpartner;

    @JsonProperty private String Absender_Anschrift;
  }

  @Data
  public static class Menge_Patient {

    @JsonProperty private Patient Patient;

    @Data
    public static class Patient {

      @JsonProperty private Patienten_Stammdaten Patienten_Stammdaten;

      @Data
      public static class Patienten_Stammdaten {

        @JsonProperty private String KrankenversichertenNr;
      }
    }
  }
}
