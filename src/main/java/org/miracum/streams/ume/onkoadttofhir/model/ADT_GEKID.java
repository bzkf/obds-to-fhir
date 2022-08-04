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
      @JsonProperty private Menge_Meldung Menge_Meldung; // TODO Liste an Meldungen möglich?

      @Data
      public static class Patienten_Stammdaten {

        @JsonProperty private String KrankenversichertenNr;
      }

      @Data
      public static class Menge_Meldung {

        @JsonProperty private Meldung Meldung;

        @Data
        public static class Meldung {
          @JsonProperty private String Meldung_ID;

          @JsonProperty private Diagnose Diagnose;

          @Data
          public static class Diagnose {

            @JsonProperty private Menge_Histologie Menge_Histologie;

            @Data
            public static class Menge_Histologie {

              @JsonProperty private Histologie Histologie;

              @Data
              public static class Histologie {

                @JsonProperty private String Histologie_ID;
              }
            }
          }
        }
      }
    }
  }
}
