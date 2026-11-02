package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HormonrezeptorTyp;
import de.basisdatensatz.obds.v3.ModulMammaTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.dizuker.tofhir.FhirExtensions.DataAbsentReason;
import io.github.dizuker.tofhir.IdUtils;
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
 * Maps the oBDS {@code Modul_Mamma} to the corresponding MII Onkologie FHIR profiles: {@code
 * Praetherapeutischer_Menopausenstatus} (M1), the three receptor statuses (M2-M4) and both tumor
 * sizes (M7, M8) become Observations. {@code PraeopDrahtmarkierung} (M5) and {@code
 * IntraopPraeparatkontrolle} (M6) are Procedure-shaped and attached to the OP; they are not mapped
 * yet.
 *
 * <p>Both tumor sizes share the generic {@code MII_PR_Onko_Tumorgroesse} profile. The IG's profile
 * page derives invasive vs. DCIS from the Condition linked in {@code focus}, which presumes M8 is
 * only filled when there is no invasive component; oBDS allows both fields in one Meldung with a
 * single {@code Tumorzuordnung} (the upstream {@code Testpatient_1.xml} carries both), so both
 * Observations reference the same primary Condition and are told apart by their identifier suffix
 * and {@code code.text} instead.
 *
 * <p>Unlike {@link ModulProstataMapper}, the reference date is passed as an already converted
 * {@link DateTimeType}: the Pathologie section only offers {@code Tumor_Histologiedatum}, which may
 * be month- or year-precise, and {@code MII_PR_Onko_Tumorgroesse} requires {@code
 * effectiveDateTime}. Callers convert with {@link #convertObdsDatumToDateTimeType}.
 */
@Service
public class ModulMammaMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(ModulMammaMapper.class);

  private static final String DATA_ABSENT_REASON_SYSTEM =
      "http://terminology.hl7.org/CodeSystem/data-absent-reason";

  protected ModulMammaMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      @NonNull ModulMammaTyp modulMamma,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType effective) {
    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    var results = new ArrayList<Observation>();

    var menopausenstatus = modulMamma.getPraetherapeutischerMenopausenstatus();
    if (menopausenstatus != null) {
      results.add(mapMenopausenstatus(menopausenstatus, meldungId, patient, condition, effective));
    }

    var estrogen = modulMamma.getHormonrezeptorStatusOestrogen();
    if (estrogen != null) {
      results.add(mapEstrogenStatus(estrogen, meldungId, patient, condition, effective));
    }

    var progesteron = modulMamma.getHormonrezeptorStatusProgesteron();
    if (progesteron != null) {
      results.add(mapProgesteronStatus(progesteron, meldungId, patient, condition, effective));
    }

    var her2neu = modulMamma.getHer2NeuStatus();
    if (her2neu != null) {
      results.add(mapHer2neuStatus(her2neu, meldungId, patient, condition, effective));
    }

    if (modulMamma.getPraeopDrahtmarkierung() != null) {
      LOG.debug(
          "PraeopDrahtmarkierung is not mapped yet, it targets a Procedure attached to the OP.");
    }

    if (modulMamma.getIntraopPraeparatkontrolle() != null) {
      LOG.debug(
          "IntraopPraeparatkontrolle is not mapped yet, it targets Procedure.usedCode of the OP.");
    }

    var tumorgroesseInvasiv = modulMamma.getTumorgroesseInvasiv();
    if (tumorgroesseInvasiv != null) {
      results.add(
          mapTumorgroesse(
              tumorgroesseInvasiv,
              meldungId + "-modul-mamma-tumorgroesse-invasiv",
              "Tumorgröße invasives Karzinom",
              patient,
              condition,
              effective));
    }

    var tumorgroesseDcis = modulMamma.getTumorgroesseDCIS();
    if (tumorgroesseDcis != null) {
      results.add(
          mapTumorgroesse(
              tumorgroesseDcis,
              meldungId + "-modul-mamma-tumorgroesse-dcis",
              "Tumorgröße DCIS",
              patient,
              condition,
              effective));
    }

    return results;
  }

  private Observation createBaseObservation(
      @NonNull String profile,
      @NonNull String identifierSystem,
      @NonNull String identifierValue,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType effective) {
    var observation = new Observation();
    observation.getMeta().addProfile(profile);
    observation.setSubject(patient);
    observation.setStatus(Observation.ObservationStatus.FINAL);
    observation.addFocus(condition);

    var identifier =
        new Identifier().setSystem(identifierSystem).setValue(slugifier.slugify(identifierValue));
    observation.addIdentifier(identifier);
    observation.setId(IdUtils.fromIdentifier(identifier));

    if (effective != null) {
      observation.setEffective(effective);
    }

    return observation;
  }

  private Observation mapMenopausenstatus(
      @NonNull String menopausenstatus,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType effective) {
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoMammaMenopauseStatus(),
            fhirProperties.getSystems().getIdentifiers().getMammaMenopausenstatusObservationId(),
            meldungId + "-modul-mamma-menopausenstatus",
            patient,
            condition,
            effective);

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("161712005")
                .setDisplay("Menopause, function (observable entity)")));

    var value =
        switch (menopausenstatus) {
          case "1" ->
              fhirProperties
                  .getCodings()
                  .snomed()
                  .setCode("22636003")
                  .setDisplay("Premenopausal state (finding)");
          case "3" ->
              fhirProperties
                  .getCodings()
                  .snomed()
                  .setCode("76498008")
                  .setDisplay("Postmenopausal state (finding)");
          case "U" ->
              fhirProperties
                  .getCodings()
                  .snomed()
                  .setCode("261665006")
                  .setDisplay("Unknown (qualifier value)");
          default ->
              throw new IllegalArgumentException(
                  "Unknown Praetherapeutischer_Menopausenstatus: " + menopausenstatus);
        };
    observation.setValue(new CodeableConcept(value));

    return observation;
  }

  private Observation mapEstrogenStatus(
      @NonNull HormonrezeptorTyp status,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType effective) {
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoMammaRezeptorstatusEstrogen(),
            fhirProperties
                .getSystems()
                .getIdentifiers()
                .getMammaRezeptorstatusEstrogenObservationId(),
            meldungId + "-modul-mamma-rezeptorstatus-estrogen",
            patient,
            condition,
            effective);
    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("40556-3")
                .setDisplay("Estrogen receptor Ag [Presence] in Tissue by Immune stain")));

    observation.addCategory(observationCategory(ObservationCategory.LABORATORY));
    setRezeptorstatus(observation, status);

    return observation;
  }

  private Observation mapProgesteronStatus(
      @NonNull HormonrezeptorTyp status,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType effective) {
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoMammaRezeptorstatusProgesteron(),
            fhirProperties
                .getSystems()
                .getIdentifiers()
                .getMammaRezeptorstatusProgesteronObservationId(),
            meldungId + "-modul-mamma-rezeptorstatus-progesteron",
            patient,
            condition,
            effective);
    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("85339-0")
                .setDisplay(
                    "Progesterone receptor Ag [Presence] in Breast cancer specimen by Immune"
                        + " stain")));

    observation.addCategory(observationCategory(ObservationCategory.LABORATORY));
    setRezeptorstatus(observation, status);

    return observation;
  }

  /**
   * The DefinitionOBDS slice of both receptor status profiles is bound to the LOINC answer list.
   */
  private void setRezeptorstatus(
      @NonNull Observation observation, @NonNull HormonrezeptorTyp status) {
    var coding =
        switch (status) {
          case P -> fhirProperties.getCodings().loinc().setCode("LA6576-8").setDisplay("Positive");
          case N -> fhirProperties.getCodings().loinc().setCode("LA6577-6").setDisplay("Negative");
          case U -> fhirProperties.getCodings().loinc().setCode("LA4489-6").setDisplay("Unknown");
        };
    observation.setValue(new CodeableConcept(coding));
  }

  private Observation mapHer2neuStatus(
      @NonNull HormonrezeptorTyp status,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType effective) {
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoMammaHer2neuStatus(),
            fhirProperties.getSystems().getIdentifiers().getMammaHer2neuStatusObservationId(),
            meldungId + "-modul-mamma-her2neu-status",
            patient,
            condition,
            effective);
    observation.addCategory(observationCategory(ObservationCategory.LABORATORY));

    // the display is pinned by the profile's patternCoding (2026.0.3); LOINC itself and the
    // 2027 IG say "HER2 Ag [Interpretation] in Tissue", change it together with the package bump
    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("48676-1")
                .setDisplay("HER2 [Interpretation] in Tissue")));

    // the DefinitionOBDS slice uses the oBDS-specific code system with the same P/N/U codes
    var coding =
        Onkologie.CodeSystems.MiiCsOnkoMammaHer2NeuStatusObds.fromValueOrThrow(status.value())
            .coding();
    observation.setValue(new CodeableConcept(coding));

    return observation;
  }

  /**
   * Zahl_3stellig_Typ allows a 1-3 digit number or the literal {@code "U"} (unbekannt); "U" is
   * expressed via {@code Observation.dataAbsentReason} since the profile requires {@code
   * valueQuantity.value} whenever a value is present.
   */
  private Observation mapTumorgroesse(
      @NonNull String rawValue,
      @NonNull String identifierValue,
      @NonNull String codeText,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType effective) {
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoTumorgroesse(),
            fhirProperties.getSystems().getIdentifiers().getTumorgroesseObservationId(),
            identifierValue,
            patient,
            condition,
            effective);

    if (effective == null) {
      LOG.warn(
          "Tumorgroesse reference date is unset. Setting data absent extension for"
              + " Observation.effective.");
      var absentDateTime = new DateTimeType();
      absentDateTime.addExtension(DataAbsentReason.unknown());
      observation.setEffective(absentDateTime);
    }

    observation.setCode(
        new CodeableConcept()
            .addCoding(
                fhirProperties.getCodings().loinc().setCode("21889-1").setDisplay("Size Tumor"))
            .addCoding(
                fhirProperties
                    .getCodings()
                    .snomed()
                    .setCode("371479009")
                    .setDisplay("Tumor size, largest dimension (observable entity)"))
            .setText(codeText));

    if ("U".equals(rawValue)) {
      observation.setDataAbsentReason(
          new CodeableConcept(new Coding(DATA_ABSENT_REASON_SYSTEM, "unknown", "Unknown")));
      return observation;
    }

    observation.setValue(
        new Quantity()
            .setValue(new BigDecimal(rawValue))
            .setUnit("mm")
            .setSystem(fhirProperties.getSystems().getUcum())
            .setCode("mm"));

    return observation;
  }
}
