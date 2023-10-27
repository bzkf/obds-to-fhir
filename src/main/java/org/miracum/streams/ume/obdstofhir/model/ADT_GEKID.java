package org.miracum.streams.ume.obdstofhir.model;

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

  @JsonProperty private Menge_Patient Menge_Patient;

  @Data
  public static class Absender {

    @JsonProperty private String Absender_ID;

    @JsonProperty private String Software_ID;

    @JsonProperty private String Installations_ID;

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

      @JsonProperty private Menge_Meldung Menge_Meldung;

      @Data
      public static class Patienten_Stammdaten {

        @JsonProperty private String Patient_ID;
        @JsonProperty private String KrankenversichertenNr;
        @JsonProperty private String KrankenkassenNr;
        @JsonProperty private String Patienten_Nachname;
        @JsonProperty private String Patienten_Vornamen;
        @JsonProperty private String Patienten_Titel;
        @JsonProperty private String Patienten_Namenszusatz;
        @JsonProperty private String Patienten_Geburtsname;
        @JsonProperty private String Patienten_Geschlecht;
        @JsonProperty private String Patienten_Geburtsdatum;

        @JsonProperty private Menge_Adresse Menge_Adresse;

        @Data
        public static class Menge_Adresse {

          @JacksonXmlElementWrapper(useWrapping = false)
          private List<Adresse> Adresse;

          @Data
          public static class Adresse {
            @JsonProperty private String Patienten_Strasse;
            @JsonProperty private String Patienten_Land;
            @JsonProperty private String Patienten_PLZ;
            @JsonProperty private String Patienten_Ort;
          }
        }
      }

      @Data
      public static class Menge_Meldung {

        @JsonProperty private Meldung Meldung;

        @Data
        public static class Meldung {
          @JsonProperty private String Meldung_ID;

          @JsonProperty private String Meldedatum;
          @JsonProperty private String Meldeanlass;

          @JsonProperty private Tumorzuordnung Tumorzuordnung;

          @JsonProperty private Diagnose Diagnose;
          @JsonProperty private Menge_OP Menge_OP;

          @JsonProperty private Menge_Verlauf Menge_Verlauf;

          @JsonProperty private Menge_Tumorkonferenz Menge_Tumorkonferenz;

          @JsonProperty private Menge_ST Menge_ST;

          @JsonProperty private Menge_SYST Menge_SYST;

          @Data
          @EqualsAndHashCode(callSuper = true)
          public static class Tumorzuordnung extends PrimaryConditionAbs {
            @JsonProperty private String Tumor_ID;
            @JsonProperty private String Primaertumor_ICD_Version;
            @JsonProperty private String Diagnosedatum;
            @JsonProperty private String Seitenlokalisation;
          }

          @Data
          @EqualsAndHashCode(callSuper = true)
          public static class Diagnose extends PrimaryConditionAbs {

            @JsonProperty private String Tumor_ID;
            @JsonProperty private String Primaertumor_ICD_Code;
            @JsonProperty private String Primaertumor_ICD_Version;
            // @JsonProperty private String ICD_O_Topographie_Code;  nicht sicher ob notwendig, aber
            // wenn's schon da ist, why not
            // @JsonProperty private String ICD_O_Topographie_Version;
            @JsonProperty private String Diagnosedatum;
            @JsonProperty private String Seitenlokalisation;

            @JsonProperty private Menge_Histologie Menge_Histologie;

            @JsonProperty private cTNM cTNM;
            @JsonProperty private pTNM pTNM;

            @JsonProperty private Menge_Weitere_Klassifikation Menge_Weitere_Klassifikation;

            @JsonProperty private Menge_FM Menge_FM;

            @Data
            public static class Menge_Histologie {
              @JacksonXmlElementWrapper(useWrapping = false)
              private List<Histologie> Histologie;

              @Data
              @EqualsAndHashCode(callSuper = true)
              public static class Histologie extends ADT_GEKID.HistologieAbs {

                @JsonProperty private String Histologie_ID;
                @JsonProperty private String Tumor_Histologiedatum;
                @JsonProperty private String Morphologie_Code; // Morphologie_ICD_O_3_Code
                @JsonProperty private String Morphologie_ICD_O_Version; //
                @JsonProperty private String Grading;
                @JsonProperty private String Morphologie_Freitext;
              }
            }

            @Data
            public static class Menge_FM {

              @JacksonXmlElementWrapper(useWrapping = false)
              private List<Fernmetastase> Fernmetastase;

              @Data
              @EqualsAndHashCode(callSuper = true)
              public static class Fernmetastase extends FernMetastaseAbs {

                @JsonProperty private String FM_Diagnosedatum;
                @JsonProperty private String FM_Lokalisation;
              }
            }

            @Data
            @EqualsAndHashCode(callSuper = true)
            public static class cTNM extends ADT_GEKID.CTnmAbs {
              @JsonProperty private String TNM_ID;
              @JsonProperty private String TNM_Datum;
              @JsonProperty private String TNM_Version;
              @JsonProperty private String TNM_c_p_u_Praefix_T;
              // brauchen wir nur bei OP
              @JsonProperty private String TNM_T;
              @JsonProperty private String TNM_c_p_u_Praefix_N;
              @JsonProperty private String TNM_N;
              @JsonProperty private String TNM_c_p_u_Praefix_M;
              @JsonProperty private String TNM_M;
              @JsonProperty private String TNM_y_Symbol;
              @JsonProperty private String TNM_r_Symbol;
              @JsonProperty private String TNM_m_Symbol;
            }

            @Data
            @EqualsAndHashCode(callSuper = true)
            public static class pTNM extends ADT_GEKID.PTnmAbs {
              @JsonProperty private String TNM_ID;
              @JsonProperty private String TNM_Datum;
              @JsonProperty private String TNM_Version;
              @JsonProperty private String TNM_c_p_u_Praefix_T;
              @JsonProperty private String TNM_T;
              @JsonProperty private String TNM_c_p_u_Praefix_N;
              @JsonProperty private String TNM_N;
              @JsonProperty private String TNM_c_p_u_Praefix_M;
              @JsonProperty private String TNM_M;
              @JsonProperty private String TNM_y_Symbol;
              @JsonProperty private String TNM_r_Symbol;
              @JsonProperty private String TNM_m_Symbol;
            }

            @Data
            public static class Menge_Weitere_Klassifikation {

              @JsonProperty private Weitere_Klassifikation Weitere_Klassifikation;

              @Data
              public static class Weitere_Klassifikation {
                @JsonProperty private String Name;
                @JsonProperty private String Datum;
                @JsonProperty private String Stadium;
              }
            }
          } // Diagnose Ende

          @Data
          public static class Menge_OP {
            @JsonProperty private OP OP;

            @Data
            public static class OP {

              @JsonProperty private String OP_ID;
              @JsonProperty private String OP_Intention;
              @JsonProperty private String OP_Datum;
              @JsonProperty private String OP_OPS_Version;

              @JsonProperty private Histologie Histologie;

              @JsonProperty private TNM TNM;

              @JsonProperty private Menge_OPS Menge_OPS;
              @JsonProperty private Menge_Komplikation Menge_Komplikation;

              @JsonProperty private Residualstatus Residualstatus;

              @Data
              @EqualsAndHashCode(callSuper = true)
              public static class Histologie extends ADT_GEKID.HistologieAbs {
                @JsonProperty private String Histologie_ID;
                @JsonProperty private String Tumor_Histologiedatum;
                @JsonProperty private String Morphologie_Code; // Morphologie_ICD_O_3_Code
                @JsonProperty private String Morphologie_ICD_O_Version; //
                @JsonProperty private String Grading;
                @JsonProperty private String Morphologie_Freitext;
              }

              @Data
              @EqualsAndHashCode(callSuper = true)
              public static class TNM extends ADT_GEKID.PTnmAbs {
                @JsonProperty private String TNM_ID;
                @JsonProperty private String TNM_Datum;
                @JsonProperty private String TNM_Version;

                @JsonProperty private String TNM_L;
                @JsonProperty private String TNM_V;
                @JsonProperty private String TNM_Pn;

                @JsonProperty private String TNM_c_p_u_Praefix_T;
                @JsonProperty private String TNM_T;
                @JsonProperty private String TNM_c_p_u_Praefix_N;
                @JsonProperty private String TNM_N;
                @JsonProperty private String TNM_c_p_u_Praefix_M;
                @JsonProperty private String TNM_M;
                @JsonProperty private String TNM_y_Symbol;
                @JsonProperty private String TNM_r_Symbol;
                @JsonProperty private String TNM_m_Symbol;
              }

              @Data
              public static class Menge_OPS {
                @JacksonXmlElementWrapper(useWrapping = false)
                private List<String> OP_OPS;
              }

              @Data
              public static class Menge_Komplikation {
                @JacksonXmlElementWrapper(useWrapping = false)
                private List<String> OP_Komplikation;
              }

              @Data
              public static class Residualstatus {
                @JsonProperty private String Lokale_Beurteilung_Residualstatus;
                @JsonProperty private String Gesamtbeurteilung_Residualstatus;
              }
            }
          }

          @Data
          public static class Menge_Verlauf {

            @JsonProperty private Verlauf Verlauf;

            @Data
            public static class Verlauf {

              @JsonProperty private String Verlauf_ID;

              @JsonProperty private Histologie Histologie;

              @JsonProperty private TNM TNM;

              @JsonProperty private Menge_FM Menge_FM;

              @JsonProperty private Tod Tod;

              @Data
              @EqualsAndHashCode(callSuper = true)
              public static class Histologie extends ADT_GEKID.HistologieAbs {
                @JsonProperty private String Histologie_ID;
                @JsonProperty private String Tumor_Histologiedatum;
                @JsonProperty private String Morphologie_Code; // Morphologie_ICD_O_3_Code
                @JsonProperty private String Morphologie_ICD_O_Version; //
                @JsonProperty private String Grading;
                @JsonProperty private String Morphologie_Freitext;
              }

              @Data
              @EqualsAndHashCode(callSuper = true)
              public static class TNM extends ADT_GEKID.PTnmAbs {
                @JsonProperty private String TNM_ID;
                @JsonProperty private String TNM_Datum;
                @JsonProperty private String TNM_Version;

                @JsonProperty private String TNM_L;
                @JsonProperty private String TNM_V;
                @JsonProperty private String TNM_Pn;

                @JsonProperty private String TNM_c_p_u_Praefix_T;
                @JsonProperty private String TNM_T;
                @JsonProperty private String TNM_c_p_u_Praefix_N;
                @JsonProperty private String TNM_N;
                @JsonProperty private String TNM_c_p_u_Praefix_M;
                @JsonProperty private String TNM_M;
                @JsonProperty private String TNM_y_Symbol;
                @JsonProperty private String TNM_r_Symbol;
                @JsonProperty private String TNM_m_Symbol;
              }

              @Data
              public static class Menge_FM {

                @JacksonXmlElementWrapper(useWrapping = false)
                private List<Fernmetastase> Fernmetastase;

                @Data
                @EqualsAndHashCode(callSuper = true)
                public static class Fernmetastase extends FernMetastaseAbs {

                  @JsonProperty private String FM_Diagnosedatum;
                  @JsonProperty private String FM_Lokalisation;
                }
              }

              @Data
              public static class Tod {

                @JsonProperty private String Sterbedatum;
                @JsonProperty private String Tod_tumorbedingt;

                @JsonProperty private Menge_Todesursache Menge_Todesursache;

                @Data
                public static class Menge_Todesursache {
                  @JsonProperty private String Todesursache_ICD;
                  @JsonProperty private String Todesursache_ICD_Version;
                }
              }
            }
          }

          @Data
          public static class Menge_Tumorkonferenz {

            @JsonProperty private Tumorkonferenz Tumorkonferenz;

            @Data
            public static class Tumorkonferenz {
              @JsonProperty private String Tumorkonferenz_ID;
            }
          }

          @Data
          public static class Menge_ST {

            @JsonProperty private ST ST;

            @Data
            public static class ST {

              @JsonProperty private String ST_ID;
              @JsonProperty private String ST_Intention;
              @JsonProperty private String ST_Stellung_OP;

              @JsonProperty private Menge_Bestrahlung Menge_Bestrahlung;

              @JsonProperty private Menge_Nebenwirkung Menge_Nebenwirkung;

              @Data
              public static class Menge_Bestrahlung {

                @JacksonXmlElementWrapper(useWrapping = false)
                private List<Bestrahlung> Bestrahlung;

                @Data
                public static class Bestrahlung {
                  @JsonProperty @EqualsAndHashCode.Include private String ST_Beginn_Datum;
                  @JsonProperty private String ST_Ende_Datum;
                  @JsonProperty @EqualsAndHashCode.Include private String ST_Zielgebiet;
                  @JsonProperty @EqualsAndHashCode.Include private String ST_Seite_Zielgebiet;
                  @JsonProperty @EqualsAndHashCode.Include private String ST_Applikationsart;
                }
              }

              @Data
              public static class Menge_Nebenwirkung {

                @JacksonXmlElementWrapper(useWrapping = false)
                private List<ST_Nebenwirkung> ST_Nebenwirkung;

                @Data
                public static class ST_Nebenwirkung {
                  @JsonProperty private String Nebenwirkung_Grad;
                  @JsonProperty private String Nebenwirkung_Art;
                  @JsonProperty private String Nebenwirkung_Version;
                }
              }
            }
          }

          @Data
          public static class Menge_SYST {

            @JsonProperty private SYST SYST;

            @Data
            public static class SYST {

              @JsonProperty private String SYST_ID;
              @JsonProperty private String SYST_Intention;
              @JsonProperty private String SYST_Stellung_OP;

              @JsonProperty private String SYST_Beginn_Datum;
              @JsonProperty private String SYST_Ende_Datum;
              @JsonProperty private String SYST_Ende_Grund;

              @JsonProperty private String SYST_Therapieart_Anmerkung;
              @JsonProperty private String SYST_Protokoll;

              @JsonProperty private Menge_Therapieart Menge_Therapieart;

              @JsonProperty private Menge_Nebenwirkung Menge_Nebenwirkung;
              @JsonProperty private Menge_Substanz Menge_Substanz;

              @Data
              public static class Menge_Therapieart {

                @JacksonXmlElementWrapper(useWrapping = false)
                private List<String> SYST_Therapieart;
              }

              @Data
              public static class Menge_Substanz {

                @JacksonXmlElementWrapper(useWrapping = false)
                private List<String> SYST_Substanz;
              }

              @Data
              public static class Menge_Nebenwirkung {

                @JacksonXmlElementWrapper(useWrapping = false)
                private List<SYST_Nebenwirkung> SYST_Nebenwirkung;

                @Data
                public static class SYST_Nebenwirkung {
                  @JsonProperty private String Nebenwirkung_Grad;
                  @JsonProperty private String Nebenwirkung_Art;
                  @JsonProperty private String Nebenwirkung_Version;
                }
              }
            }
          }
        }
      }
    }
  }

  @Data
  public abstract static class HistologieAbs {
    public String Histologie_ID;
    public String Tumor_Histologiedatum;
    public String Morphologie_Code; // Morphologie_ICD_O_3_Code
    public String Morphologie_ICD_O_Version; //
    public String Grading;
    public String Morphologie_Freitext;
  }

  @Data
  public abstract static class CTnmAbs {
    public String TNM_ID;
    public String TNM_Datum;
    public String TNM_Version;
    public String TNM_c_p_u_Praefix_T;
    public String TNM_T;
    public String TNM_c_p_u_Praefix_N;
    public String TNM_N;
    public String TNM_c_p_u_Praefix_M;
    public String TNM_M;
    public String TNM_y_Symbol;
    public String TNM_r_Symbol;
    public String TNM_m_Symbol;
  }

  @Data
  public abstract static class PTnmAbs {
    public String TNM_ID;
    public String TNM_Datum;
    public String TNM_Version;
    public String TNM_c_p_u_Praefix_T;
    public String TNM_T;
    public String TNM_c_p_u_Praefix_N;
    public String TNM_N;
    public String TNM_c_p_u_Praefix_M;
    public String TNM_M;
    public String TNM_y_Symbol;
    public String TNM_r_Symbol;
    public String TNM_m_Symbol;
  }

  @Data
  public abstract static class FernMetastaseAbs {
    public String FM_Diagnosedatum;
    public String FM_Lokalisation;
  }

  @Data
  public abstract static class PrimaryConditionAbs {
    public String Tumor_ID;
    public String Primaertumor_ICD_Code;
    public String Primaertumor_ICD_Version;
    public String Diagnosedatum;
    public String Seitenlokalisation;
  }
}
