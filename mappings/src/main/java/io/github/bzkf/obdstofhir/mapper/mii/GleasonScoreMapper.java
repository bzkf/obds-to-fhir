package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.ModulProstataTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.xml.datatype.XMLGregorianCalendar;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GleasonScoreMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(GleasonScoreMapper.class);

  private static final Pattern GLEASON_SCORE_PATTERN = Pattern.compile("^(\\d{1,2})[ab]?$");
  private static final Pattern GLEASON_SCORE_7_SUFFIX_PATTERN =
      Pattern.compile("^7(?<suffix>[ab])$");
  private static final Map<String, String> GLEASON_SCORE_TO_SNOMED =
      Map.ofEntries(
          Map.entry("2", "49878003"),
          Map.entry("3", "46677009"),
          Map.entry("4", "18430005"),
          Map.entry("5", "74013009"),
          Map.entry("6", "84556003"),
          Map.entry("7", "57403001"),
          Map.entry("7a", "57403001"),
          Map.entry("7b", "57403001"),
          Map.entry("8", "33013007"),
          Map.entry("9", "58925000"),
          Map.entry("10", "24514009"));

  private static final Map<String, String> GLEASON_SCORE_TO_SNOMED_GRADE_GROUP =
      Map.ofEntries(
          // International Society of Urological Pathology grade group 1 (Gleason score 3
          // + 3 = 6)
          // (qualifier value)
          Map.entry("3+3", "1279715000"),
          Map.entry(
              "3+4",
              "1279714001"), // International Society of Urological Pathology grade group 2 (Gleason
          // score 3 + 4 = 7) (qualifier value)
          Map.entry(
              "4+3",
              "1279716004"), // International Society of Urological Pathology grade group 3 (Gleason
          // score 4 + 3 = 7) (qualifier value)
          Map.entry(
              "3+5",
              "1279718003"), // International Society of Urological Pathology grade group 4 (Gleason
          // score 3 + 5 = 8) (qualifier value)
          Map.entry(
              "4+4",
              "1279717008"), // International Society of Urological Pathology grade group 4 (Gleason
          // score 4 + 4 = 8) (qualifier value)
          Map.entry(
              "5+3",
              "1279719006"), // International Society of Urological Pathology grade group 4 (Gleason
          // score 5 + 3 = 8) (qualifier value)
          Map.entry(
              "4+5",
              "1279720000"), // International Society of Urological Pathology grade group 5 (Gleason
          // score 4 + 5 = 9) (qualifier value)
          Map.entry(
              "5+4",
              "1279721001"), // International Society of Urological Pathology grade group 5 (Gleason
          // score 5 + 4 = 9) (qualifier value)
          Map.entry(
              "5+5",
              "1279722008") // International Society of Urological Pathology grade group 5 (Gleason
          // score 5 + 5 = 10) (qualifier value)
          );

  private static final Map<String, String> GLEASON_PATTERN_TO_SNOMED =
      Map.ofEntries(
          Map.entry("1", "369770006"),
          Map.entry("2", "369771005"),
          Map.entry("3", "369772003"),
          Map.entry("4", "369773008"),
          Map.entry("5", "369774002"));

  protected GleasonScoreMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition) {
    return map(modulProstata, meldungId, patient, condition, null);
  }

  public List<Observation> map(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable XMLGregorianCalendar referenceDate) {
    Objects.requireNonNull(modulProstata.getGleasonScore());

    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    var patterns = mapGleasonPatterns(modulProstata, meldungId, patient, condition, referenceDate);
    var derivedFromPatterns =
        patterns.stream()
            .map(pattern -> new Reference(pattern.getResourceType() + "/" + pattern.getId()))
            .toList();
    var gradeGroup =
        mapGleasonGradeGroup(
            modulProstata, meldungId, patient, condition, derivedFromPatterns, referenceDate);

    var result = new ArrayList<Observation>();
    result.add(gradeGroup);
    result.addAll(patterns);

    return result;
  }

  private Observation mapGleasonGradeGroup(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @NonNull List<Reference> derivedFromReferences,
      @Nullable XMLGregorianCalendar referenceDate) {
    var observation = new Observation();
    observation
        .getMeta()
        .addProfile(fhirProperties.getProfiles().getMiiPrOnkoProstateGleasonGradeGroup());

    observation.setSubject(patient);
    observation.addFocus(condition);
    observation.setStatus(ObservationStatus.FINAL);
    observation.setDerivedFrom(derivedFromReferences);

    convertObdsDatumToDateTimeType(referenceDate).ifPresent(observation::setEffective);

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties
                    .getSystems()
                    .getIdentifiers()
                    .getProstataGleasonPatternsObservationId())
            .setValue(slugifier.slugify(meldungId + "-modul-prostata-gleason-grade-group"));
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    if (modulProstata.getAnlassGleasonScore() != null) {
      var coding =
          switch (modulProstata.getAnlassGleasonScore()) {
            case O ->
                fhirProperties
                    .getCodings()
                    .snomed()
                    .setCode("65801008")
                    .setDisplay("Excision (procedure)");
            case S ->
                fhirProperties
                    .getCodings()
                    .snomed()
                    .setCode("86273004")
                    .setDisplay("Biopsy (procedure)");
            case U ->
                fhirProperties
                    .getCodings()
                    .snomed()
                    .setCode("261665006")
                    .setDisplay("Unknown (qualifier value)");
          };

      observation.setMethod(new CodeableConcept(coding));
    }

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("1812491000004107")
                .setDisplay(
                    "Histologic grade of primary malignant "
                        + "neoplasm of prostate by International Society "
                        + "of Urological Pathology technique (observable entity)")));

    var scoreErgebnis = modulProstata.getGleasonScore().getScoreErgebnis();
    if (!StringUtils.hasText(scoreErgebnis)) {
      LOG.warn(
          "Gleason score ergebnis is missing. "
              + "Attempting to reconstruct from the primary and secondary patterns.");

      var gradPrimaer = modulProstata.getGleasonScore().getGradPrimaer();
      var gradSekundaer = modulProstata.getGleasonScore().getGradSekundaer();
      Validate.notBlank(gradPrimaer);
      Validate.notBlank(gradSekundaer);

      var totalGrade = Integer.parseInt(gradPrimaer) + Integer.parseInt(gradSekundaer);

      scoreErgebnis = String.valueOf(totalGrade);

      if (gradPrimaer.equals("3") && gradSekundaer.equals("4")) {
        scoreErgebnis += "a";
      } else if (gradPrimaer.equals("4") && gradSekundaer.equals("3")) {
        scoreErgebnis += "b";
      }

      LOG.debug(
          "Reconstructed Gleason score ergebnis: {} + {} = {}",
          gradPrimaer,
          gradSekundaer,
          scoreErgebnis);
    }

    var matcher = GLEASON_SCORE_PATTERN.matcher(scoreErgebnis);
    if (!matcher.find()) {
      throw new IllegalArgumentException(
          String.format(
              "Gleason score %s doesn't match the pattern %s",
              scoreErgebnis, GLEASON_SCORE_PATTERN.pattern()));
    }

    var gleasonScoreNumeric = matcher.group(1);

    CodeableConcept valueCodeableConcept;

    // XXX: if necessary, we could also derive gradPrimaer and gradSekundaer from
    // scoreErgebnis
    if (modulProstata.getGleasonScore().getGradPrimaer() != null
        && modulProstata.getGleasonScore().getGradSekundaer() != null) {
      var gradeGroupKey =
          String.format(
              "%s+%s",
              modulProstata.getGleasonScore().getGradPrimaer(),
              modulProstata.getGleasonScore().getGradSekundaer());
      var gradeGroupSnomedCode = GLEASON_SCORE_TO_SNOMED_GRADE_GROUP.get(gradeGroupKey);
      var gradeGroupCoding = fhirProperties.getCodings().snomed().setCode(gradeGroupSnomedCode);
      valueCodeableConcept = new CodeableConcept(gradeGroupCoding).setText(gradeGroupKey);
    } else {
      LOG.warn(
          "Cannot derive Gleason grade group because primary or secondary pattern is missing.");
      var code = GLEASON_SCORE_TO_SNOMED.get(scoreErgebnis);
      var scoreCoding = fhirProperties.getCodings().snomed().setCode(code);
      valueCodeableConcept = new CodeableConcept(scoreCoding).setText(scoreErgebnis);
    }

    // add the gleason score as the ordinal value extension
    valueCodeableConcept
        .getCodingFirstRep()
        .addExtension(
            fhirProperties.getExtensions().getOrdinalValue(), new DecimalType(gleasonScoreNumeric));

    observation.setValue(valueCodeableConcept);

    return observation;
  }

  private List<Observation> mapGleasonPatterns(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable XMLGregorianCalendar referenceDate) {
    var results = new ArrayList<Observation>();
    var observation = new Observation();
    observation
        .getMeta()
        .addProfile(fhirProperties.getProfiles().getMiiPrOnkoProstateGleasonPatterns());

    observation.setSubject(patient);
    observation.addFocus(condition);
    observation.setStatus(ObservationStatus.FINAL);

    convertObdsDatumToDateTimeType(referenceDate).ifPresent(observation::setEffective);

    if (modulProstata.getAnlassGleasonScore() != null) {
      var coding =
          switch (modulProstata.getAnlassGleasonScore()) {
            case O ->
                fhirProperties
                    .getCodings()
                    .snomed()
                    .setCode("65801008")
                    .setDisplay("Excision (procedure)");
            case S ->
                fhirProperties
                    .getCodings()
                    .snomed()
                    .setCode("86273004")
                    .setDisplay("Biopsy (procedure)");
            case U ->
                fhirProperties
                    .getCodings()
                    .snomed()
                    .setCode("261665006")
                    .setDisplay("Unknown (qualifier value)");
          };

      observation.setMethod(new CodeableConcept(coding));
    }

    if (modulProstata.getGleasonScore().getGradPrimaer() != null) {
      var pattern = modulProstata.getGleasonScore().getGradPrimaer();
      var primaryPattern = observation.copy();
      var identifier =
          new Identifier()
              .setSystem(
                  fhirProperties
                      .getSystems()
                      .getIdentifiers()
                      .getProstataGleasonPatternsObservationId())
              .setValue(slugifier.slugify(meldungId + "-modul-prostata-primary-gleason-pattern"));
      primaryPattern.addIdentifier(identifier);
      primaryPattern.setId(computeResourceIdFromIdentifier(identifier));

      var coding =
          fhirProperties
              .getCodings()
              .snomed()
              .setCode("384994009")
              .setDisplay("Primary Gleason pattern (observable entity)");
      primaryPattern.setCode(new CodeableConcept(coding));

      var snomedCode = GLEASON_PATTERN_TO_SNOMED.get(pattern);
      var valueCoding =
          fhirProperties
              .getCodings()
              .snomed()
              .setCode(snomedCode)
              .setDisplay(String.format("Gleason Pattern %s (finding)", pattern));
      valueCoding.addExtension(
          fhirProperties.getExtensions().getOrdinalValue(), new DecimalType(pattern));

      primaryPattern.setValue(new CodeableConcept(valueCoding));

      results.add(primaryPattern);
    }

    if (modulProstata.getGleasonScore().getGradSekundaer() != null) {
      var pattern = modulProstata.getGleasonScore().getGradSekundaer();
      var secondaryPattern = observation.copy();
      var identifier =
          new Identifier()
              .setSystem(
                  fhirProperties
                      .getSystems()
                      .getIdentifiers()
                      .getProstataGleasonPatternsObservationId())
              .setValue(slugifier.slugify(meldungId + "-modul-prostata-secondary-gleason-pattern"));
      secondaryPattern.addIdentifier(identifier);
      secondaryPattern.setId(computeResourceIdFromIdentifier(identifier));

      var coding =
          fhirProperties
              .getCodings()
              .snomed()
              .setCode("384995005")
              .setDisplay("Secondary Gleason pattern (observable entity)");
      secondaryPattern.setCode(new CodeableConcept(coding));

      var snomedCode = GLEASON_PATTERN_TO_SNOMED.get(pattern);
      var valueCoding =
          fhirProperties
              .getCodings()
              .snomed()
              .setCode(snomedCode)
              .setDisplay(String.format("Gleason Pattern %s (finding)", pattern));
      valueCoding.addExtension(
          fhirProperties.getExtensions().getOrdinalValue(), new DecimalType(pattern));

      secondaryPattern.setValue(new CodeableConcept(valueCoding));

      results.add(secondaryPattern);
    }

    return results;
  }
}
