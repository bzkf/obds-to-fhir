package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.AnlassGleasonScoreTyp;
import de.basisdatensatz.obds.v3.ModulProstataTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.dizuker.tofhir.ReferenceUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.ObservationCategory;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Maps the {@code GleasonScore} of {@code Modul_Prostata} to the Gleason profiles of the MII
 * Onkologie IG:
 *
 * <ul>
 *   <li>{@code MII_PR_Onko_Prostata_Gleason_Pattern} for each documented pattern.
 *   <li>{@code MII_PR_Onko_Prostata_Gleason_Score_Gesamt} whenever the total score is known, from
 *       both patterns or from {@code ScoreErgebnis}. The profile is only meant for a total score
 *       without patterns and grade group, but this way every report has its total in one place.
 *   <li>{@code MII_PR_Onko_Prostata_Gleason_Grade_Group} when it can be determined: from both
 *       patterns, except 2+5 and 5+2, or from a total score that determines it on its own (up to 6,
 *       7a, 7b, 10).
 * </ul>
 *
 * <p>The value codings of a pattern and of the total score carry their number as an {@code
 * itemWeight}, so the total Gleason score is available as a number. The grade group has none.
 */
@Service
public class GleasonScoreMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(GleasonScoreMapper.class);

  /** oBDS {@code ScoreErgebnis}: 2 to 10, where 7a is 3+4 and 7b is 4+3. */
  private static final Pattern SCORE_PATTERN = Pattern.compile("^(10|[2-9])([ab]?)$");

  /** mii-vs-onko-prostata-gleason-patterns, by pattern 1 to 5. */
  private static final List<String> PATTERN_CODES =
      List.of("369770006", "369771005", "369772003", "369773008", "369774002");

  /** mii-vs-onko-prostata-gleason-score-gesamt, by score 2 to 10. */
  private static final List<String> SCORE_CODES =
      List.of(
          "49878003",
          "46677009",
          "18430005",
          "74013009",
          "84556003",
          "57403001",
          "33013007",
          "58925000",
          "24514009");

  /**
   * An ISUP grade group, coded by its pattern combination as in mii-vs-onko-prostata-gleason-score.
   */
  private record GradeGroup(int primary, int secondary, int group, String code) {
    String display() {
      return String.format(
          "International Society of Urological Pathology grade group %d"
              + " (Gleason score %d + %d = %d) (qualifier value)",
          group, primary, secondary, primary + secondary);
    }
  }

  private static final List<GradeGroup> GRADE_GROUPS =
      List.of(
          new GradeGroup(3, 3, 1, "1279715000"),
          new GradeGroup(3, 4, 2, "1279714001"),
          new GradeGroup(4, 3, 3, "1279716004"),
          new GradeGroup(3, 5, 4, "1279718003"),
          new GradeGroup(4, 4, 4, "1279717008"),
          new GradeGroup(5, 3, 4, "1279719006"),
          new GradeGroup(4, 5, 5, "1279720000"),
          new GradeGroup(5, 4, 5, "1279721001"),
          new GradeGroup(5, 5, 5, "1279722008"));

  private record PatternType(
      String identifierSuffix,
      String snomedCode,
      String snomedDisplay,
      String loincCode,
      String loincDisplay) {}

  private static final PatternType PRIMARY =
      new PatternType(
          "primary-gleason-pattern",
          "384994009",
          "Primary Gleason pattern (observable entity)",
          "44641-9",
          "Gleason pattern.primary in Prostate tumor");

  private static final PatternType SECONDARY =
      new PatternType(
          "secondary-gleason-pattern",
          "384995005",
          "Secondary Gleason pattern (observable entity)",
          "44642-7",
          "Gleason pattern.secondary in Prostate tumor");

  /** What all Gleason Observations of one {@code Modul_Prostata} share. */
  private record Context(
      String meldungId,
      Reference patient,
      Reference condition,
      @Nullable DateTimeType effective,
      @Nullable CodeableConcept method,
      @Nullable Reference specimen) {}

  protected GleasonScoreMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  /**
   * @param sectionDate the date of the section {@code Modul_Prostata} is part of. Used as the
   *     effective date unless the score was determined on biopsies with a known {@code
   *     DatumStanzen}, as the profiles want the date the tissue was taken.
   * @param specimen the Specimen the score was determined on, if known
   * @return the total score and the grade group, if any, followed by the patterns. Empty if the
   *     score documents nothing usable.
   */
  public List<Observation> map(
      @NonNull ModulProstataTyp modulProstata,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @Nullable DateTimeType sectionDate,
      @Nullable Reference specimen) {
    var gleasonScore = Objects.requireNonNull(modulProstata.getGleasonScore());
    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    var anlass = modulProstata.getAnlassGleasonScore();
    var effective =
        convertObdsDatumToDateTimeType(modulProstata.getDatumStanzen())
            .filter(datumStanzen -> anlass == AnlassGleasonScoreTyp.S)
            .orElse(sectionDate);
    var context = new Context(meldungId, patient, condition, effective, method(anlass), specimen);

    var primary = parsePattern(gleasonScore.getGradPrimaer());
    var secondary = parsePattern(gleasonScore.getGradSekundaer());
    var scoreErgebnis = gleasonScore.getScoreErgebnis();

    var patterns = new ArrayList<Observation>();
    if (primary != null) {
      patterns.add(mapPattern(context, PRIMARY, primary));
    }
    if (secondary != null) {
      patterns.add(mapPattern(context, SECONDARY, secondary));
    }
    var patternReferences = patterns.stream().map(ReferenceUtils::createReferenceTo).toList();

    var results = new ArrayList<Observation>();
    if (primary != null && secondary != null) {
      var total = primary + secondary;
      var notation = primary + "+" + secondary;
      if (StringUtils.hasText(scoreErgebnis) && !Objects.equals(parseScore(scoreErgebnis), total)) {
        LOG.warn(
            "ScoreErgebnis {} doesn't match the patterns {}. Using the patterns.",
            scoreErgebnis,
            notation);
      }

      results.add(mapScoreGesamt(context, total, notation, patternReferences));
      gradeGroup(primary, secondary)
          .map(gradeGroup -> mapGradeGroup(context, gradeGroup, notation, patternReferences))
          .ifPresent(results::add);
    } else if (StringUtils.hasText(scoreErgebnis)) {
      var total = parseScore(scoreErgebnis);
      if (total != null) {
        var scoreGesamt = mapScoreGesamt(context, total, scoreErgebnis, List.of());
        results.add(scoreGesamt);
        gradeGroup(scoreErgebnis)
            .map(
                gradeGroup ->
                    mapGradeGroup(
                        context,
                        gradeGroup,
                        scoreErgebnis,
                        List.of(ReferenceUtils.createReferenceTo(scoreGesamt))))
            .ifPresent(results::add);
      }
    } else {
      LOG.warn(
          "GleasonScore has neither both patterns nor a ScoreErgebnis. "
              + "Only the documented patterns are mapped.");
    }

    results.addAll(patterns);
    return results;
  }

  /**
   * ISUP defines grade group 1 as a score of up to 6. SNOMED's only concept for it is named after
   * 3+3, so lower patterns like 2+3 use it as well.
   *
   * @return empty for 2+5 and 5+2: of the combinations adding up to 7, ISUP only defines 3+4 and
   *     4+3
   */
  private static Optional<GradeGroup> gradeGroup(int primary, int secondary) {
    var isGradeGroup1 = primary + secondary <= 6;
    return GRADE_GROUPS.stream()
        .filter(
            g ->
                isGradeGroup1
                    ? g.group() == 1
                    : g.primary() == primary && g.secondary() == secondary)
        .findFirst();
  }

  /** The grade group of a total score that determines it without the patterns. */
  private static Optional<GradeGroup> gradeGroup(String scoreErgebnis) {
    return switch (scoreErgebnis) {
      case "7a" -> gradeGroup(3, 4);
      case "7b" -> gradeGroup(4, 3);
      case "10" -> gradeGroup(5, 5);
      default ->
          Optional.ofNullable(parseScore(scoreErgebnis))
              .filter(score -> score <= 6)
              .flatMap(score -> gradeGroup(3, 3));
    };
  }

  private static @Nullable Integer parsePattern(@Nullable String grad) {
    if (!StringUtils.hasText(grad)) {
      return null;
    }
    if (!grad.matches("^[1-5]$")) {
      LOG.warn("Ignoring the Gleason pattern {}, which isn't one of 1 to 5.", grad);
      return null;
    }
    return Integer.parseInt(grad);
  }

  private static @Nullable Integer parseScore(String scoreErgebnis) {
    var matcher = SCORE_PATTERN.matcher(scoreErgebnis);
    if (!matcher.matches()) {
      LOG.warn(
          "Ignoring the Gleason ScoreErgebnis {}, which isn't 2 to 10, 7a or 7b.", scoreErgebnis);
      return null;
    }
    return Integer.parseInt(matcher.group(1));
  }

  private Observation mapPattern(Context context, PatternType type, int pattern) {
    return createGleasonObservation(
        context,
        Onkologie.Profiles.miiPrOnkoProstateGleasonPatterns(),
        fhirProperties.getSystems().getIdentifiers().getProstataGleasonPatternsObservationId(),
        type.identifierSuffix(),
        snomed(type.snomedCode(), type.snomedDisplay()),
        loinc(type.loincCode(), type.loincDisplay()),
        withItemWeight(
            snomed(PATTERN_CODES.get(pattern - 1), "Gleason Pattern " + pattern + " (finding)"),
            pattern),
        null);
  }

  private Observation mapGradeGroup(
      Context context, GradeGroup gradeGroup, String notation, List<Reference> derivedFrom) {
    return createGleasonObservation(
            context,
            Onkologie.Profiles.miiPrOnkoProstateGleasonGradeGroup(),
            fhirProperties.getSystems().getIdentifiers().getProstataGleasonScoreObservationId(),
            "gleason-grade-group",
            snomed(
                "1812491000004107",
                "Histologic grade of primary malignant neoplasm of prostate by International"
                    + " Society of Urological Pathology technique (observable entity)"),
            loinc("94734-1", "Prostate cancer grade group [Score] in Prostate tumor Qualitative"),
            snomed(gradeGroup.code(), gradeGroup.display()),
            notation)
        .setDerivedFrom(derivedFrom);
  }

  private Observation mapScoreGesamt(
      Context context, int score, String notation, List<Reference> derivedFrom) {
    return createGleasonObservation(
            context,
            Onkologie.Profiles.miiPrOnkoProstateGleasonScoreGesamt(),
            fhirProperties.getSystems().getIdentifiers().getProstataGleasonScoreObservationId(),
            "gleason-score-gesamt",
            snomed("372278000", "Gleason score (observable entity)"),
            loinc("35266-6", "Gleason score in Specimen Qualitative"),
            withItemWeight(
                snomed(
                    SCORE_CODES.get(score - 2),
                    String.format("Gleason grade score %d out of 10 (finding)", score)),
                score),
            notation)
        .setDerivedFrom(derivedFrom);
  }

  /**
   * @param notation how the source documented the value, e.g. {@code 2+3} for a grade group 1 or
   *     {@code 7a} for a total score. Kept as the value's text.
   */
  private Observation createGleasonObservation(
      Context context,
      String profile,
      String identifierSystem,
      String identifierSuffix,
      Coding snomedCode,
      Coding loincCode,
      Coding value,
      @Nullable String notation) {
    var observation =
        createBaseObservation(
            profile,
            identifierSystem,
            context.meldungId() + "-modul-prostata-" + identifierSuffix,
            context.patient(),
            context.condition(),
            context.effective());
    observation.addCategory(observationCategory(ObservationCategory.LABORATORY));
    observation.setCode(new CodeableConcept().addCoding(snomedCode).addCoding(loincCode));
    observation.setMethod(context.method());
    observation.setSpecimen(context.specimen());
    observation.setValue(new CodeableConcept(value).setText(notation));

    return observation;
  }

  private Coding withItemWeight(Coding coding, int itemWeight) {
    coding.addExtension(
        fhirProperties.getExtensions().getItemWeight(), new DecimalType(itemWeight));
    return coding;
  }

  private @Nullable CodeableConcept method(@Nullable AnlassGleasonScoreTyp anlass) {
    if (anlass == null) {
      return null;
    }

    var coding =
        switch (anlass) {
          case O -> snomed("65801008", "Excision (procedure)");
          case S -> snomed("86273004", "Biopsy (procedure)");
          case U -> snomed("261665006", "Unknown (qualifier value)");
        };
    return new CodeableConcept(coding);
  }
}
