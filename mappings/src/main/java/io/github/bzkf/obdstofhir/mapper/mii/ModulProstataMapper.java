package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.ModulProstataTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.dizuker.tofhir.FhirExtensions.DataAbsentReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.ObservationCategory;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ModulProstataMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(ModulProstataMapper.class);

  private final GleasonScoreMapper gleasonScoreMapper;

  protected ModulProstataMapper(
      FhirProperties fhirProperties, GleasonScoreMapper gleasonScoreMapper) {
    super(fhirProperties);

    this.gleasonScoreMapper = gleasonScoreMapper;
  }

  public List<Observation> map(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition) {
    return map(modulProstata, meldungId, patient, condition, null, null);
  }

  public List<Observation> map(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType referenzDatum) {
    return map(modulProstata, meldungId, patient, condition, referenzDatum, null);
  }

  public List<Observation> map(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType referenzDatum,
      @Nullable Reference op) {
    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    var results = new ArrayList<Observation>();

    if (modulProstata.getPSA() != null) {
      var observation = mapPsa(modulProstata, meldungId, patient, condition);
      results.add(observation);
    }

    if (modulProstata.getAnzahlStanzen() != null) {
      var observation = mapAnzahlStanzen(modulProstata, meldungId, patient, condition);
      results.add(observation);
    }

    if (modulProstata.getAnzahlPosStanzen() != null) {
      var observation = mapAnzahlPositiveStanzen(modulProstata, meldungId, patient, condition);
      results.add(observation);
    }

    if (modulProstata.getCaBefallStanze() != null) {
      var observation = mapCaBefallStanzen(modulProstata, meldungId, patient, condition);
      results.add(observation);
    }

    if (modulProstata.getKomplPostOPClavienDindo() != null) {
      var observation =
          mapClavienDindoScore(modulProstata, meldungId, patient, condition, referenzDatum, op);
      results.add(observation);
    }

    if (modulProstata.getGleasonScore() != null) {
      var observations =
          gleasonScoreMapper.map(modulProstata, meldungId, patient, condition, referenzDatum);
      results.addAll(observations);
    }

    return results;
  }

  private Observation mapPsa(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition) {
    Objects.requireNonNull(modulProstata.getPSA());
    // for creating the identifier, we can use the meldungId plus a fixed suffix
    // as a single Meldung can only have one of the elements that may contain the
    // Modul_Prostata_Typ: Pathologie, Diagnose, OP.
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoProstatePsa(),
            fhirProperties.getSystems().getIdentifiers().getProstataPsaObservationId(),
            meldungId + "-modul-prostata-psa",
            patient,
            condition,
            convertObdsDatumToDateTimeType(modulProstata.getDatumPSA()).orElse(null));
    observation.addCategory(observationCategory(ObservationCategory.LABORATORY));

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("2857-1")
                .setDisplay("Prostate specific Ag [Mass/volume] in Serum or Plasma")));

    var valueQuantity =
        new Quantity()
            .setValue(modulProstata.getPSA())
            .setUnit("ng/ml")
            .setSystem(fhirProperties.getSystems().getUcum())
            .setCode("ng/mL");

    observation.setValue(valueQuantity);

    return observation;
  }

  private Observation mapAnzahlStanzen(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition) {
    Objects.requireNonNull(modulProstata.getAnzahlStanzen());
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoProstateAnzahlStanzen(),
            fhirProperties.getSystems().getIdentifiers().getProstataAnzahlStanzenObservationId(),
            meldungId + "-modul-prostata-anzahl-stanzen",
            patient,
            condition,
            convertObdsDatumToDateTimeType(modulProstata.getDatumStanzen()).orElse(null));
    observation.addCategory(observationCategory(ObservationCategory.LABORATORY));

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("44652-6")
                .setDisplay("Total number of cores in Tissue core")));

    var value = new IntegerType(modulProstata.getAnzahlStanzen());
    observation.setValue(value);

    return observation;
  }

  private Observation mapAnzahlPositiveStanzen(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition) {
    Objects.requireNonNull(modulProstata.getAnzahlPosStanzen());
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoProstateAnzahlPositiveStanzen(),
            fhirProperties
                .getSystems()
                .getIdentifiers()
                .getProstataAnzahlPositiveStanzenObservationId(),
            meldungId + "-modul-prostata-anzahl-positive-stanzen",
            patient,
            condition,
            convertObdsDatumToDateTimeType(modulProstata.getDatumStanzen()).orElse(null));
    observation.addCategory(observationCategory(ObservationCategory.LABORATORY));

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("44651-8")
                .setDisplay("Tissue cores.positive.carcinoma in Tissue core")));

    var value = new IntegerType(modulProstata.getAnzahlPosStanzen());
    observation.setValue(value);

    return observation;
  }

  private Observation mapCaBefallStanzen(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition) {
    Objects.requireNonNull(modulProstata.getCaBefallStanze());
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoProstateCaBefallStanze(),
            fhirProperties.getSystems().getIdentifiers().getProstataCaBefallStanzeObservationId(),
            meldungId + "-modul-prostata-ca-befall-stanze",
            patient,
            condition,
            convertObdsDatumToDateTimeType(modulProstata.getDatumStanzen()).orElse(null));
    observation.addCategory(observationCategory(ObservationCategory.LABORATORY));

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("44654-2")
                .setDisplay("Tissue involved by tumor in Prostate tumor")));

    var valueQuantity =
        new Quantity().setUnit("%").setSystem(fhirProperties.getSystems().getUcum()).setCode("%");

    // valueQuantity.value is 1..1 in the mii-pr-onko-prostate-ca-befall-stanze profile, so it
    // must be set even when the percentage is unknown.
    if (modulProstata.getCaBefallStanze().getProzentzahl() != null) {
      valueQuantity.setValue(modulProstata.getCaBefallStanze().getProzentzahl());
    } else {
      var absentValue = new DecimalType();
      absentValue.addExtension(DataAbsentReason.unknown());
      valueQuantity.setValueElement(absentValue);
    }

    observation.setValue(valueQuantity);

    return observation;
  }

  private Observation mapClavienDindoScore(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType referenceDate,
      @Nullable Reference op) {
    Objects.requireNonNull(modulProstata.getKomplPostOPClavienDindo());
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoProstateClavienDindo(),
            fhirProperties.getSystems().getIdentifiers().getProstataClavienDindoObservationId(),
            meldungId + "-modul-prostata-clavien-dindo",
            patient,
            condition,
            referenceDate);
    observation.addCategory(observationCategory(ObservationCategory.SURVEY));

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("789279006")
                .setDisplay("Clavien-Dindo classification grade (observable entity)")));

    var coding =
        Onkologie.CodeSystems.MiiCsOnkoProstataPostsurgicalComplications.fromValueOrThrow(
                modulProstata.getKomplPostOPClavienDindo().toString())
            .coding();

    var value = new CodeableConcept(coding);
    observation.setValue(value);

    if (op != null) {
      observation.addFocus(op);
    } else {
      LOG.warn("OP reference is null for Clavien-Dindo score observation");
    }

    return observation;
  }
}
