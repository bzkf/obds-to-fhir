package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.ModulProstataTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hl7.fhir.r4.model.*;
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
      @Nullable XMLGregorianCalendar referenzDatum) {
    return map(modulProstata, meldungId, patient, condition, referenzDatum, null);
  }

  public List<Observation> map(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable XMLGregorianCalendar referenzDatum,
      @Nullable List<Reference> ops) {
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
          mapClavienDindoScore(modulProstata, meldungId, patient, condition, referenzDatum, ops);
      results.add(observation);
    }

    if (modulProstata.getGleasonScore() != null) {
      var observations =
          gleasonScoreMapper.map(modulProstata, meldungId, patient, condition, referenzDatum);
      results.addAll(observations);
    }

    return results;
  }

  private static Observation createBaseObservation(
      @NonNull Reference patient, @NonNull Reference condition) {
    var observation = new Observation();
    observation.setSubject(patient);
    observation.setStatus(Observation.ObservationStatus.FINAL);
    observation.addFocus(condition);

    return observation;
  }

  private Observation mapPsa(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition) {
    Objects.requireNonNull(modulProstata.getPSA());
    var observation = createBaseObservation(patient, condition);
    observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoProstatePsa());

    // for creating the identifier, we can use the meldungId plus a fixed suffix
    // as a single Meldung can only have one of the elements that may contain the
    // Modul_Prostata_Typ: Pathologie, Diagnose, OP.
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getIdentifiers().getProstataPsaObservationId())
            .setValue(slugifier.slugify(meldungId + "-modul-prostata-psa"));
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    convertObdsDatumToDateTimeType(modulProstata.getDatumPSA())
        .ifPresent(observation::setEffective);

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
            .setSystem("http://unitsofmeasure.org")
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
    var observation = createBaseObservation(patient, condition);
    observation
        .getMeta()
        .addProfile(fhirProperties.getProfiles().getMiiPrOnkoProstateAnzahlStanzen());

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties
                    .getSystems()
                    .getIdentifiers()
                    .getProstataAnzahlStanzenObservationId())
            .setValue(slugifier.slugify(meldungId + "-modul-prostata-anzahl-stanzen"));
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    convertObdsDatumToDateTimeType(modulProstata.getDatumStanzen())
        .ifPresent(observation::setEffective);

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
    var observation = createBaseObservation(patient, condition);
    observation
        .getMeta()
        .addProfile(fhirProperties.getProfiles().getMiiPrOnkoProstateAnzahlPositiveStanzen());

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties
                    .getSystems()
                    .getIdentifiers()
                    .getProstataAnzahlPositiveStanzenObservationId())
            .setValue(slugifier.slugify(meldungId + "-modul-prostata-anzahl-positive-stanzen"));
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    convertObdsDatumToDateTimeType(modulProstata.getDatumStanzen())
        .ifPresent(observation::setEffective);

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
    var observation = createBaseObservation(patient, condition);
    observation
        .getMeta()
        .addProfile(fhirProperties.getProfiles().getMiiPrOnkoProstateCaBefallStanze());

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties
                    .getSystems()
                    .getIdentifiers()
                    .getProstataCaBefallStanzeObservationId())
            .setValue(slugifier.slugify(meldungId + "-modul-prostata-ca-befall-stanze"));
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    convertObdsDatumToDateTimeType(modulProstata.getDatumStanzen())
        .ifPresent(observation::setEffective);

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("44654-2")
                .setDisplay("Tissue involved by tumor in Prostate tumor")));

    if (modulProstata.getCaBefallStanze().getU() != null) {
      var valueQuantity = new Quantity();
      valueQuantity.addExtension(
          new Extension()
              .setUrl(fhirProperties.getExtensions().getDataAbsentReason())
              .setValue(new CodeType("unknown")));
      observation.setValue(valueQuantity);
    }

    if (modulProstata.getCaBefallStanze().getProzentzahl() != null) {
      var valueQuantity =
          new Quantity()
              .setValue(modulProstata.getCaBefallStanze().getProzentzahl())
              .setUnit("%")
              .setSystem("http://unitsofmeasure.org")
              .setCode("%");

      observation.setValue(valueQuantity);
    }

    return observation;
  }

  private Observation mapClavienDindoScore(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable XMLGregorianCalendar referenceDate,
      @Nullable List<Reference> ops) {
    Objects.requireNonNull(modulProstata.getKomplPostOPClavienDindo());
    var observation = createBaseObservation(patient, condition);
    observation
        .getMeta()
        .addProfile(fhirProperties.getProfiles().getMiiPrOnkoProstateClavienDindo());

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties.getSystems().getIdentifiers().getProstataClavienDindoObservationId())
            .setValue(slugifier.slugify(meldungId + "-modul-prostata-clavien-dindo"));
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    convertObdsDatumToDateTimeType(referenceDate).ifPresent(observation::setEffective);

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("789279006")
                .setDisplay("Clavien-Dindo classification grade (observable entity)")));

    var value = new CodeableConcept();
    value
        .addCoding()
        .setSystem(fhirProperties.getSystems().getMiiCsOnkoProstataPostsurgicalComplications())
        .setCode(modulProstata.getKomplPostOPClavienDindo().toString());

    observation.setValue(value);

    if (ops != null && !ops.isEmpty()) {
      for (var op : ops) {
        observation.addFocus(op);
      }
    } else {
      LOG.warn(
          "Procedure causing the Clavien-Dindo classification grade is unset. "
              + "This causes non-compliance with the MII Onkologie profile");
    }

    return observation;
  }
}
