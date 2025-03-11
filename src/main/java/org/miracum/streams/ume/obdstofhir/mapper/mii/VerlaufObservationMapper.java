package org.miracum.streams.ume.obdstofhir.mapper.mii;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import de.basisdatensatz.obds.v3.VerlaufTyp;
import java.util.Objects;
import lombok.Getter;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerlaufObservationMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(VerlaufObservationMapper.class);

  public VerlaufObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(VerlaufTyp verlauf, Reference patient, Reference condition) {

    // Validate input
    Objects.requireNonNull(verlauf, "VerlaufTyp must not be null");
    Objects.requireNonNull(condition, "Reference to Condition must not be null");

    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");

    Validate.isTrue(
        Objects.equals(
            condition.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.CONDITION.toCode()),
        "The condition reference should point to a Condition resource");

    // Create Observation
    var observation = new Observation();

    // Meta
    observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoVerlauf());

    // Identifier
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getObservationVerlaufId())
            .setValue(verlauf.getVerlaufID());
    observation.addIdentifier();
    observation.setId(computeResourceIdFromIdentifier(identifier));

    // Status
    observation.setStatus(Observation.ObservationStatus.FINAL);

    // Code
    observation.setCode(
        new CodeableConcept(
            new Coding(
                fhirProperties.getSystems().getSnomed(),
                "396432002",
                "Status of regression of tumor (observable entity)")));

    // Subject
    observation.setSubject(patient);

    // Focus
    observation.addFocus(condition);

    // Effective Date
    var date =
        new DateTimeType(verlauf.getUntersuchungsdatumVerlauf().toGregorianCalendar().getTime());
    date.setPrecision(TemporalPrecisionEnum.DAY);
    observation.setEffective(date);

    // Components
    // Tumorstatus Primärtumor
    var tumorstatusPrimaertumor =
        TumorstatusPrimaertumor.fromCode(verlauf.getVerlaufLokalerTumorstatus());
    observation.addComponent(
        new Observation.ObservationComponentComponent()
            .setCode(
                new CodeableConcept(
                    new Coding(
                        fhirProperties.getSystems().getSnomed(),
                        "277062004",
                        "Status des Residualtumors")))
            .setValue(
                new CodeableConcept(
                    new Coding(
                        fhirProperties.getSystems().getMiiCsOnkoVerlaufPrimaertumor(),
                        tumorstatusPrimaertumor.getCode(),
                        tumorstatusPrimaertumor.getDisplay()))));

    // Tumorstatus Lymphknoten
    var tumorstatusLymphknoten =
        TumorstatusLymphknoten.fromCode(verlauf.getVerlaufTumorstatusLymphknoten());
    observation.addComponent(
        new Observation.ObservationComponentComponent()
            .setCode(
                new CodeableConcept(
                    new Coding(
                        fhirProperties.getSystems().getSnomed(),
                        "399656008",
                        "Status of tumor metastasis to regional lymph nodes (observable entity)")))
            .setValue(
                new CodeableConcept(
                    new Coding(
                        fhirProperties.getSystems().getMiiCsOnkoVerlaufLymphknoten(),
                        tumorstatusLymphknoten.getCode(),
                        tumorstatusLymphknoten.getDisplay()))));

    // Tumorstatus Fernmetastasen
    var tumorstatusFernmetastasen =
        TumorstatusFernmetastasen.fromCode(verlauf.getVerlaufTumorstatusFernmetastasen());
    observation.addComponent(
        new Observation.ObservationComponentComponent()
            .setCode(
                new CodeableConcept(
                    new Coding(
                        fhirProperties.getSystems().getSnomed(),
                        "399608002",
                        "Status of distant metastasis (observable entity)")))
            .setValue(
                new CodeableConcept(
                    new Coding(
                        fhirProperties.getSystems().getMiiCsOnkoVerlaufFernmetastasen(),
                        tumorstatusFernmetastasen.getCode(),
                        tumorstatusFernmetastasen.getDisplay()))));

    // Value
    VerlaufGesamtbeurteilung value =
        VerlaufGesamtbeurteilung.fromCode(verlauf.getGesamtbeurteilungTumorstatus());
    observation.setValue(
        new CodeableConcept(
            new Coding(
                fhirProperties.getSystems().getMiiCsOnkoVerlaufGesamtbeurteilung(),
                value.getCode(),
                value.getDisplay())));

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
