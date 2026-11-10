package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.AnlassGleasonScoreTyp;
import de.basisdatensatz.obds.v3.JNU;
import de.basisdatensatz.obds.v3.ModulProstataTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.dizuker.tofhir.FhirExtensions.DataAbsentReason;
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
 * Maps {@code Modul_Prostata}. A Meldung has only one of the sections that may contain it
 * (Diagnose, Verlauf, OP, Pathologie), so the Meldung_ID plus a fixed suffix identifies each
 * Observation. The Gleason score is mapped by {@link GleasonScoreMapper}.
 */
@Service
public class ModulProstataMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(ModulProstataMapper.class);

  private final GleasonScoreMapper gleasonScoreMapper;

  protected ModulProstataMapper(
      FhirProperties fhirProperties, GleasonScoreMapper gleasonScoreMapper) {
    super(fhirProperties);
    this.gleasonScoreMapper = gleasonScoreMapper;
  }

  /**
   * @param effective the date of the section {@code Modul_Prostata} is part of, e.g. the OP date
   * @param op the OP Procedure. Only available in an OP Meldung. Used as the focus of the
   *     Clavien-Dindo grade.
   * @param specimen the Specimen of the section's Histologie, if there is one. Used as the specimen
   *     of the Gleason score.
   */
  public List<Observation> map(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType effective,
      @Nullable Reference op,
      @Nullable Reference specimen) {
    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);
    if (op != null) {
      verifyReference(op, ResourceType.Procedure);
    }
    if (specimen != null) {
      verifyReference(specimen, ResourceType.Specimen);
    }

    var identifiers = fhirProperties.getSystems().getIdentifiers();
    var datumStanzen = convertObdsDatumToDateTimeType(modulProstata.getDatumStanzen()).orElse(null);
    var results = new ArrayList<Observation>();

    if (modulProstata.getPSA() != null) {
      // the profile fixes the unit to "ng/ml" but the UCUM code to "ng/mL"
      var psa =
          new Quantity()
              .setValue(modulProstata.getPSA())
              .setUnit("ng/ml")
              .setSystem(fhirProperties.getSystems().getUcum())
              .setCode("ng/mL");
      results.add(
          withLaboratoryCodeAndValue(
              createBaseObservation(
                  Onkologie.Profiles.miiPrOnkoProstatePsa(),
                  identifiers.getProstataPsaObservationId(),
                  meldungId + "-modul-prostata-psa",
                  patient,
                  condition,
                  convertObdsDatumToDateTimeType(modulProstata.getDatumPSA()).orElse(null)),
              loinc("2857-1", "Prostate specific Ag [Mass/volume] in Serum or Plasma"),
              psa));
    }

    if (modulProstata.getAnzahlStanzen() != null) {
      results.add(
          withLaboratoryCodeAndValue(
              createBaseObservation(
                  Onkologie.Profiles.miiPrOnkoProstateAnzahlStanzen(),
                  identifiers.getProstataAnzahlStanzenObservationId(),
                  meldungId + "-modul-prostata-anzahl-stanzen",
                  patient,
                  condition,
                  datumStanzen),
              loinc("44652-6", "Total number of cores in Tissue core"),
              new IntegerType(modulProstata.getAnzahlStanzen())));
    }

    if (modulProstata.getAnzahlPosStanzen() != null) {
      results.add(
          withLaboratoryCodeAndValue(
              createBaseObservation(
                  Onkologie.Profiles.miiPrOnkoProstateAnzahlPositiveStanzen(),
                  identifiers.getProstataAnzahlPositiveStanzenObservationId(),
                  meldungId + "-modul-prostata-anzahl-positive-stanzen",
                  patient,
                  condition,
                  datumStanzen),
              loinc("44651-8", "Tissue cores.positive.carcinoma in Tissue core"),
              new IntegerType(modulProstata.getAnzahlPosStanzen())));
    }

    if (modulProstata.getCaBefallStanze() != null) {
      results.add(
          withLaboratoryCodeAndValue(
              createBaseObservation(
                  Onkologie.Profiles.miiPrOnkoProstateCaBefallStanze(),
                  identifiers.getProstataCaBefallStanzeObservationId(),
                  meldungId + "-modul-prostata-ca-befall-stanze",
                  patient,
                  condition,
                  datumStanzen),
              loinc("44654-2", "Tissue involved by tumor in Prostate tumor"),
              percentage(modulProstata.getCaBefallStanze().getProzentzahl())));
    }

    if (modulProstata.getKomplPostOPClavienDindo() != null) {
      results.add(
          mapClavienDindo(
              modulProstata.getKomplPostOPClavienDindo(),
              meldungId,
              patient,
              condition,
              effective,
              op));
    }

    if (modulProstata.getGleasonScore() != null) {
      // in an OP Meldung, the Histologie is the resected tissue, not the biopsies a score with
      // Anlass S was determined on
      var isBiopsyScoreInOp =
          op != null && modulProstata.getAnlassGleasonScore() == AnlassGleasonScoreTyp.S;
      results.addAll(
          gleasonScoreMapper.map(
              modulProstata,
              meldungId,
              patient,
              condition,
              effective,
              isBiopsyScoreInOp ? null : specimen));
    }

    return results;
  }

  private Observation withLaboratoryCodeAndValue(Observation observation, Coding code, Type value) {
    observation.addCategory(observationCategory(ObservationCategory.LABORATORY));
    observation.setCode(new CodeableConcept(code));
    return observation.setValue(value);
  }

  /**
   * valueQuantity.value is 1..1 in mii-pr-onko-prostate-ca-befall-stanze, so an unknown percentage
   * is set as a value with a data-absent-reason.
   */
  private Quantity percentage(@Nullable Integer prozentzahl) {
    if (prozentzahl != null) {
      return ucumQuantity(BigDecimal.valueOf(prozentzahl), "%");
    }

    var unknown = new DecimalType();
    unknown.addExtension(DataAbsentReason.unknown());
    return new Quantity()
        .setValueElement(unknown)
        .setUnit("%")
        .setSystem(fhirProperties.getSystems().getUcum())
        .setCode("%");
  }

  private Observation mapClavienDindo(
      @NonNull JNU komplikation,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType effective,
      @Nullable Reference op) {
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoProstateClavienDindo(),
            fhirProperties.getSystems().getIdentifiers().getProstataClavienDindoObservationId(),
            meldungId + "-modul-prostata-clavien-dindo",
            patient,
            condition,
            effective);
    observation.addCategory(observationCategory(ObservationCategory.SURVEY));
    observation.setCode(
        new CodeableConcept(
            snomed("789279006", "Clavien-Dindo classification grade (observable entity)")));
    observation.setValue(
        new CodeableConcept(
            Onkologie.CodeSystems.MiiCsOnkoProstataPostsurgicalComplications.fromValueOrThrow(
                    komplikation.value())
                .coding()));

    if (op != null) {
      observation.addFocus(op);
    } else {
      LOG.warn("OP reference is null for Clavien-Dindo score observation");
    }

    return observation;
  }
}
