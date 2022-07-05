package org.miracum.streams.ume.onkoadttofhir.model;

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@XmlRootElement(name = "ADT_GEKID", namespace = "http://www.gekid.de/namespace")
@XmlAccessorType(XmlAccessType.FIELD)
public class ADT_GEKID {

  @XmlAttribute(name = "Schema_Version")
  String schema_version;

  @XmlElement(name = "Absender", namespace = "http://www.gekid.de/namespace")
  Absender absender;

  @XmlElement(name = "Menge_Patient", namespace = "http://www.gekid.de/namespace")
  List<Menge_Patient> menge_patientList;

  @Data
  @XmlAccessorType(XmlAccessType.FIELD)
  public static class Absender {
    @XmlElement(name = "Absender_Bezeichnung", namespace = "http://www.gekid.de/namespace")
    String absender_bezeichnung;

    @XmlElement(name = "Absender_Ansprechpartner", namespace = "http://www.gekid.de/namespace")
    String absender_ansprechpartner;

    @XmlElement(name = "Absender_Anschrift", namespace = "http://www.gekid.de/namespace")
    String absender_anschrift;
  }

  @Data
  @XmlAccessorType(XmlAccessType.FIELD)
  public static class Menge_Patient {

    @XmlElement(name = "Patient", namespace = "http://www.gekid.de/namespace")
    Patient patient;

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Patient {

      @XmlElement(name = "Patienten_Stammdaten", namespace = "http://www.gekid.de/namespace")
      Patienten_Stammdaten patienten_stammdaten;

      @Data
      @XmlAccessorType(XmlAccessType.FIELD)
      public static class Patienten_Stammdaten {

        @XmlElement(name = "KrankenversichertenNr", namespace = "http://www.gekid.de/namespace")
        String krankenversichertenNr;
      }
    }
  }
}
