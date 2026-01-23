package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.VerlaufTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.Objects;
import lombok.Getter;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VerlaufObservationMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(VerlaufObservationMapper.class);

  public VerlaufObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(VerlaufTyp verlauf, Reference patient, Reference condition) {

    // Validate input
    Objects.requireNonNull(verlauf, "VerlaufTyp must not be null");
    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    // Create Observation
    var observation = new Observation();

    // Meta
    observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoVerlauf());

    // Identifier
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getIdentifiers().getVerlaufObservationId())
            .setValue(slugifier.slugify(verlauf.getVerlaufID()));
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    // Status
    observation.setStatus(Observation.ObservationStatus.FINAL);

    // Code
    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("396432002")
                .setDisplay("Status of regression of tumor (observable entity)")));

    // Subject
    observation.setSubject(patient);

    // Focus
    observation.addFocus(condition);

    // Effective Date
    convertObdsDatumToDateTimeType(verlauf.getUntersuchungsdatumVerlauf())
        .ifPresent(observation::setEffective);

    // Components
    // Tumorstatus Primärtumor
    var verlaufLokalerTumorstatus = verlauf.getVerlaufLokalerTumorstatus();
    if (verlaufLokalerTumorstatus != null) {
      var tumorstatusPrimaertumor = TumorstatusPrimaertumor.fromCode(verlaufLokalerTumorstatus);
      observation.addComponent(
          new Observation.ObservationComponentComponent()
              .setCode(
                  new CodeableConcept(
                      fhirProperties
                          .getCodings()
                          .snomed()
                          .setCode("277062004")
                          .setDisplay("Status des Residualtumors")))
              .setValue(
                  new CodeableConcept(
                      new Coding(
                          fhirProperties.getSystems().getMiiCsOnkoVerlaufPrimaertumor(),
                          tumorstatusPrimaertumor.getCode(),
                          tumorstatusPrimaertumor.getDisplay()))));
    }

    // Tumorstatus Lymphknoten
    var verlaufTumorstatusLymphknoten = verlauf.getVerlaufTumorstatusLymphknoten();
    if (verlaufTumorstatusLymphknoten != null) {
      var tumorstatusLymphknoten = TumorstatusLymphknoten.fromCode(verlaufTumorstatusLymphknoten);
      observation.addComponent(
          new Observation.ObservationComponentComponent()
              .setCode(
                  new CodeableConcept(
                      fhirProperties
                          .getCodings()
                          .snomed()
                          .setCode("399656008")
                          .setDisplay(
                              "Status of tumor metastasis to regional lymph nodes (observable entity)")))
              .setValue(
                  new CodeableConcept(
                      new Coding(
                          fhirProperties.getSystems().getMiiCsOnkoVerlaufLymphknoten(),
                          tumorstatusLymphknoten.getCode(),
                          tumorstatusLymphknoten.getDisplay()))));
    }

    // Tumorstatus Fernmetastasen
    var verlaufTumorstatusFernmetastasen = verlauf.getVerlaufTumorstatusFernmetastasen();
    if (verlaufTumorstatusFernmetastasen != null) {
      var tumorstatusFernmetastasen =
          TumorstatusFernmetastasen.fromCode(verlaufTumorstatusFernmetastasen);
      observation.addComponent(
          new Observation.ObservationComponentComponent()
              .setCode(
                  new CodeableConcept(
                      fhirProperties
                          .getCodings()
                          .snomed()
                          .setCode("399608002")
                          .setDisplay("Status of distant metastasis (observable entity)")))
              .setValue(
                  new CodeableConcept(
                      new Coding(
                          fhirProperties.getSystems().getMiiCsOnkoVerlaufFernmetastasen(),
                          tumorstatusFernmetastasen.getCode(),
                          tumorstatusFernmetastasen.getDisplay()))));
    }

    // Value
    var gesamtbeurteilung = verlauf.getGesamtbeurteilungTumorstatus();
    if (gesamtbeurteilung != null) {
      var value = VerlaufGesamtbeurteilung.fromCode(gesamtbeurteilung);
      observation.setValue(
          new CodeableConcept(
              new Coding(
                  fhirProperties.getSystems().getMiiCsOnkoVerlaufGesamtbeurteilung(),
                  value.getCode(),
                  value.getDisplay())));
    }

    return observation;
  }

  @Getter
  protected enum VerlaufGesamtbeurteilung {
    V("V", "Vollremission"),
    T("T", "Teilremission"),
    K("K", "keine Änderung"),
    P("P", "Progression"),
    D("D", "divergentes Geschehen"),
    B("B", "klinische Besserung des Zustandes, Teilremissionkriterien jedoch nicht erfüllt"),
    R("R", "Vollremission mit residualen Auffälligkeiten"),
    Y("Y", "Rezidiv"),
    U("U", "Beurteilung unmöglich"),
    X("X", "fehlende Angabe");

    private final String code;
    private final String display;

    VerlaufGesamtbeurteilung(String code, String display) {
      this.code = code;
      this.display = display;
    }

    public static VerlaufGesamtbeurteilung fromCode(String code) {
      for (VerlaufGesamtbeurteilung value : values()) {
        if (value.getCode().equalsIgnoreCase(code)) {
          return value;
        }
      }
      throw new IllegalArgumentException("Ungültiger Code für Verlauf: " + code);
    }
  }

  @Getter
  protected enum TumorstatusPrimaertumor {
    K("K", "kein Tumor nachweisbar"),
    T("T", "Tumorreste (Residualtumor)"),
    P("P", "Tumorreste (Residualtumor) Progress"),
    N("N", "Tumorreste (Residualtumor) No Change"),
    R("R", "Lokalrezidiv"),
    F("F", "fraglicher Befund"),
    U("U", "unbekannt"),
    X("X", "fehlende Angabe");

    private final String code;
    private final String display;

    TumorstatusPrimaertumor(String code, String display) {
      this.code = code;
      this.display = display;
    }

    public static TumorstatusPrimaertumor fromCode(String code) {
      for (TumorstatusPrimaertumor value : values()) {
        if (value.getCode().equalsIgnoreCase(code)) {
          return value;
        }
      }
      throw new IllegalArgumentException("Ungültiger Code für Verlauf: " + code);
    }
  }

  @Getter
  protected enum TumorstatusLymphknoten {
    K("K", "kein Lymphknotenbefall nachweisbar"),
    T("T", "bekannter Lymphknotenbefall Residuen"),
    P("P", "bekannter Lymphknotenbefall Progress"),
    N("N", "bekannter Lymphknotenbefall No Change"),
    R("R", "neu aufgetretenes Lymphknotenrezidiv"),
    F("F", "fraglicher Befund"),
    U("U", "unbekannt"),
    X("X", "fehlende Angabe");

    private final String code;
    private final String display;

    TumorstatusLymphknoten(String code, String display) {
      this.code = code;
      this.display = display;
    }

    public static TumorstatusLymphknoten fromCode(String code) {
      for (TumorstatusLymphknoten value : values()) {
        if (value.getCode().equalsIgnoreCase(code)) {
          return value;
        }
      }
      throw new IllegalArgumentException("Ungültiger Code für Verlauf: " + code);
    }
  }

  @Getter
  protected enum TumorstatusFernmetastasen {
    K("K", "keine Fernmetastasen nachweisbar"),
    T("T", "Fernmetastasen Residuen"),
    P("P", "Fernmetastasen Progress"),
    N("N", "Fernmetastasen No Change"),
    R("R", "neu aufgetretene Fernmetastase(n) bzw. Metastasenrezidiv"),
    F("F", "fraglicher Befund"),
    U("U", "unbekannt"),
    X("X", "fehlende Angabe");

    private final String code;
    private final String display;

    TumorstatusFernmetastasen(String code, String display) {
      this.code = code;
      this.display = display;
    }

    public static TumorstatusFernmetastasen fromCode(String code) {
      for (TumorstatusFernmetastasen value : values()) {
        if (value.getCode().equalsIgnoreCase(code)) {
          return value;
        }
      }
      throw new IllegalArgumentException("Ungültiger Code für Verlauf: " + code);
    }
  }
}
