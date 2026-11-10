package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.JNU;
import de.basisdatensatz.obds.v3.ModulMalignesMelanomTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.ObservationCategory;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Maps the oBDS {@code Modul_Malignes_Melanom} to the corresponding MII Onkologie FHIR profiles:
 * {@code Sicherheitsabstand_Primaertumor} (MM1), {@code Tumordicke} (Breslow), {@code LDH} and
 * {@code Ulzeration} (MM4) become Observations. The excision itself is Procedure-shaped ({@code
 * MII_PR_Onko_Melanom_Exzision} specialises the OP profile) and is not mapped here.
 *
 * <p>Like {@link ModulMammaMapper}, the reference date is passed as an already converted {@link
 * DateTimeType}: the Pathologie section only offers {@code Tumor_Histologiedatum}, which may be
 * month- or year-precise, and {@code MII_PR_Onko_Melanom_LDH} requires {@code effectiveDateTime}.
 * Callers convert with {@link #convertObdsDatumToDateTimeType}.
 */
@Service
public class ModulMalignesMelanomMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(ModulMalignesMelanomMapper.class);

  protected ModulMalignesMelanomMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      @NonNull ModulMalignesMelanomTyp modulMelanom,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType effective) {
    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    var results = new ArrayList<Observation>();

    var sicherheitsabstand = modulMelanom.getSicherheitsabstandPrimaertumor();
    if (sicherheitsabstand != null) {
      results.add(
          mapSicherheitsabstand(sicherheitsabstand, meldungId, patient, condition, effective));
    }

    var tumordicke = modulMelanom.getTumordicke();
    if (tumordicke != null) {
      results.add(mapTumordicke(tumordicke, meldungId, patient, condition, effective));
    }

    var ldh = modulMelanom.getLDH();
    if (ldh != null) {
      results.add(mapLdh(ldh, meldungId, patient, condition, effective));
    }

    var ulzeration = modulMelanom.getUlzeration();
    if (ulzeration != null) {
      results.add(mapUlzeration(ulzeration, meldungId, patient, condition, effective));
    }

    return results;
  }

  /**
   * Sicherheitsabstand_Primaertumor_Typ is the minimal margin in mm as a natural number, with
   * {@code -1} for "nicht zu beurteilen". The profile asks for {@code Observation.dataAbsentReason}
   * instead of a value in that case.
   */
  private Observation mapSicherheitsabstand(
      @NonNull Integer sicherheitsabstand,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType effective) {
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoMelanomSicherheitsabstand(),
            fhirProperties
                .getSystems()
                .getIdentifiers()
                .getMelanomSicherheitsabstandObservationId(),
            meldungId + "-modul-melanom-sicherheitsabstand",
            patient,
            condition,
            effective);

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("396511007")
                .setDisplay(
                    "Distance of in situ melanoma from closest lateral surgical margin in excised"
                        + " specimen of skin (observable entity)")));

    if (sicherheitsabstand == -1) {
      observation.setDataAbsentReason(dataAbsentReason(Enumerations.DataAbsentReason.UNKNOWN));
      return observation;
    }

    observation.setValue(ucumQuantity(BigDecimal.valueOf(sicherheitsabstand), "mm"));

    return observation;
  }

  private Observation mapTumordicke(
      @NonNull BigDecimal tumordicke,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType effective) {
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoMelanomBreslowTiefe(),
            fhirProperties.getSystems().getIdentifiers().getMelanomBreslowTiefeObservationId(),
            meldungId + "-modul-melanom-breslow-tiefe",
            patient,
            condition,
            effective);

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("106243009")
                .setDisplay("Breslow depth staging for melanoma of skin (observable entity)")));

    observation.setValue(ucumQuantity(tumordicke, "mm"));

    return observation;
  }

  /**
   * {@code MII_PR_Onko_Melanom_LDH} requires {@code effectiveDateTime}, so a data absent extension
   * is set when the Meldung carries no usable date.
   */
  private Observation mapLdh(
      @NonNull Integer ldh,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType effective) {
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoMelanomLdh(),
            fhirProperties.getSystems().getIdentifiers().getMelanomLdhObservationId(),
            meldungId + "-modul-melanom-ldh",
            patient,
            condition,
            effective);

    if (effective == null) {
      LOG.warn(
          "LDH reference date is unset. Setting data absent extension for Observation.effective.");
      observation.setEffective(absentDateTime());
    }

    observation.addCategory(observationCategory(ObservationCategory.LABORATORY));

    // oBDS says nothing about the assay, so this is the method-agnostic code of the value set
    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("2532-0")
                .setDisplay(
                    "Lactate dehydrogenase [Enzymatic activity/volume] in Serum or Plasma")));

    observation.setValue(ucumQuantity(BigDecimal.valueOf(ldh), "U/L"));

    return observation;
  }

  private Observation mapUlzeration(
      @NonNull JNU ulzeration,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType effective) {
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoMelanomUlzeration(),
            fhirProperties.getSystems().getIdentifiers().getMelanomUlzerationObservationId(),
            meldungId + "-modul-melanom-ulzeration",
            patient,
            condition,
            effective);

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("6270001000004106")
                .setDisplay(
                    "Presence of ulcer in primary malignant melanoma of skin (observable entity)")));

    var coding =
        Onkologie.CodeSystems.MiiCsOnkoMelanomUlzeration.fromValueOrThrow(ulzeration.value())
            .coding();
    observation.setValue(new CodeableConcept(coding));

    return observation;
  }
}
