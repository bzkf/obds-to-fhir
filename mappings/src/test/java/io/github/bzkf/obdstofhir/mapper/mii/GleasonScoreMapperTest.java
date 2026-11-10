package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.AnlassGleasonScoreTyp;
import de.basisdatensatz.obds.v3.GleasonScoreTyp;
import de.basisdatensatz.obds.v3.ModulProstataTyp;
import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.xml.datatype.DatatypeFactory;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class GleasonScoreMapperTest extends MapperTest {
  private static final String GRADE_GROUP_PROFILE = "mii-pr-onko-prostate-gleason-grade-group";
  private static final String SCORE_GESAMT_PROFILE = "mii-pr-onko-prostate-gleason-score-gesamt";
  private static final String PATTERNS_PROFILE = "mii-pr-onko-prostate-gleason-patterns";

  private static GleasonScoreMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new GleasonScoreMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_Prostata.xml", "Testpatient_Prostata_Kein_Ergebnis.xml"})
  void map_withGivenObds_shouldCreateValidGleasonScoreObservation(String sourceFile)
      throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var subject = new Reference("Patient/any");
    var diagnose = new Reference("Condition/Primärdiagnose");
    var specimen = new Reference("Specimen/any");

    final var list = new ArrayList<Observation>();
    for (var meldung : obdsPatient.getMengeMeldung().getMeldung()) {
      for (var modulProstata :
          new ModulProstataTyp[] {
            meldung.getPathologie() != null ? meldung.getPathologie().getModulProstata() : null,
            meldung.getDiagnose() != null ? meldung.getDiagnose().getModulProstata() : null,
            meldung.getOP() != null ? meldung.getOP().getModulProstata() : null
          }) {
        if (modulProstata != null && modulProstata.getGleasonScore() != null) {
          list.addAll(
              sut.map(modulProstata, meldung.getMeldungID(), subject, diagnose, null, specimen));
        }
      }
    }

    verifyAll(list, sourceFile);
  }

  /** The fixtures all document both patterns, so these cover the other shapes of a score. */
  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {"null,null,7a", "null,null,8", "2,5,7", "4,null,8"})
  void map_withoutGradeGroupFromBothPatterns_shouldCreateValidObservations(
      @Nullable String primaer, @Nullable String sekundaer, @Nullable String ergebnis) {
    verifyAll(map(primaer, sekundaer, ergebnis), primaer + "_" + sekundaer + "_" + ergebnis);
  }

  @ParameterizedTest
  @CsvSource({"1,1,2", "2,3,5", "3,2,5", "2,4,6", "4,2,6", "1,5,6", "5,1,6", "3,3,6"})
  void map_withScoreUpTo6_shouldUseGradeGroup1AndKeepThePatterns(
      String primaer, String sekundaer, String total) {
    var observations = map(primaer, sekundaer, null);
    var gradeGroup = observations.get(1);
    assertThat(profile(gradeGroup)).isEqualTo(GRADE_GROUP_PROFILE);
    var value = gradeGroup.getValueCodeableConcept();

    assertThat(value.getCodingFirstRep().getCode()).isEqualTo("1279715000");
    assertThat(value.getText()).isEqualTo(primaer + "+" + sekundaer);
    assertThat(itemWeight(observations.getFirst().getValueCodeableConcept().getCodingFirstRep()))
        .isEqualByComparingTo(total);
  }

  @ParameterizedTest
  @CsvSource({"2,5", "5,2"})
  void map_withPatternsWithoutGradeGroup_shouldCreateTheTotalScoreFromThePatterns(
      String primaer, String sekundaer) {
    var observations = map(primaer, sekundaer, "7");

    assertThat(observations)
        .extracting(GleasonScoreMapperTest::profile)
        .doesNotContain(GRADE_GROUP_PROFILE);
    var scoreGesamt = observations.getFirst();
    assertThat(profile(scoreGesamt)).isEqualTo(SCORE_GESAMT_PROFILE);
    var value = scoreGesamt.getValueCodeableConcept();
    assertThat(value.getCodingFirstRep().getCode()).isEqualTo("57403001");
    assertThat(value.getText()).isEqualTo(primaer + "+" + sekundaer);
    assertThat(itemWeight(value.getCodingFirstRep())).isEqualByComparingTo("7");
    assertThat(scoreGesamt.getDerivedFrom())
        .extracting(Reference::getReference)
        .containsExactly(
            "Observation/" + observations.get(1).getIdPart(),
            "Observation/" + observations.get(2).getIdPart());
  }

  @Test
  void map_withPatternsWithGradeGroup_shouldCreateTheTotalScoreAndTheGradeGroupFromThePatterns() {
    var observations = map("4", "3", "7b");

    assertThat(observations)
        .extracting(GleasonScoreMapperTest::profile)
        .containsExactly(
            SCORE_GESAMT_PROFILE, GRADE_GROUP_PROFILE, PATTERNS_PROFILE, PATTERNS_PROFILE);
    var patternReferences =
        observations.subList(2, 4).stream()
            .map(pattern -> "Observation/" + pattern.getIdPart())
            .toList();

    var scoreGesamt = observations.getFirst();
    var total = scoreGesamt.getValueCodeableConcept().getCodingFirstRep();
    assertThat(total.getCode()).isEqualTo("57403001");
    assertThat(itemWeight(total)).isEqualByComparingTo("7");
    assertThat(scoreGesamt.getValueCodeableConcept().getText()).isEqualTo("4+3");
    assertThat(scoreGesamt.getDerivedFrom())
        .extracting(Reference::getReference)
        .isEqualTo(patternReferences);

    var gradeGroup = observations.get(1);
    var coding = gradeGroup.getValueCodeableConcept().getCodingFirstRep();
    assertThat(coding.getCode()).isEqualTo("1279716004");
    assertThat(coding.getDisplay())
        .isEqualTo(
            "International Society of Urological Pathology grade group 3"
                + " (Gleason score 4 + 3 = 7) (qualifier value)");
    assertThat(coding.hasExtension()).isFalse();
    assertThat(gradeGroup.getDerivedFrom())
        .extracting(Reference::getReference)
        .isEqualTo(patternReferences);
    assertThat(gradeGroup.getIdentifierFirstRep().getSystem())
        .endsWith("/prostata-gleason-score-observation-id");
  }

  @ParameterizedTest
  @CsvSource({"7a,1279714001,7", "7b,1279716004,7", "10,1279722008,10", "5,1279715000,5"})
  void map_withScoreOnlyDeterminingTheGradeGroup_shouldDeriveTheGradeGroupFromTheTotalScore(
      String score, String gradeGroupCode, String total) {
    var observations = map(null, null, score);

    assertThat(observations)
        .extracting(GleasonScoreMapperTest::profile)
        .containsExactly(SCORE_GESAMT_PROFILE, GRADE_GROUP_PROFILE);
    var gradeGroup = observations.get(1);
    var coding = gradeGroup.getValueCodeableConcept().getCodingFirstRep();
    assertThat(coding.getCode()).isEqualTo(gradeGroupCode);
    assertThat(coding.hasExtension()).isFalse();
    assertThat(itemWeight(observations.getFirst().getValueCodeableConcept().getCodingFirstRep()))
        .isEqualByComparingTo(total);
    assertThat(gradeGroup.getValueCodeableConcept().getText()).isEqualTo(score);
    assertThat(gradeGroup.getDerivedFrom())
        .extracting(Reference::getReference)
        .containsExactly("Observation/" + observations.getFirst().getIdPart());
  }

  @ParameterizedTest
  @CsvSource({"7,57403001", "8,33013007", "9,58925000"})
  void map_withScoreOnly_shouldOnlyCreateTheTotalScore(String score, String scoreCode) {
    var observations = map(null, null, score);

    assertThat(observations).hasSize(1);
    var scoreGesamt = observations.getFirst();
    assertThat(profile(scoreGesamt)).isEqualTo(SCORE_GESAMT_PROFILE);
    assertThat(scoreGesamt.getCode().getCoding())
        .extracting(Coding::getCode)
        .containsExactly("372278000", "35266-6");
    assertThat(scoreGesamt.getValueCodeableConcept().getCodingFirstRep().getCode())
        .isEqualTo(scoreCode);
    assertThat(scoreGesamt.getIdentifierFirstRep().getValue())
        .isEqualTo("meldung-1-modul-prostata-gleason-score-gesamt");
  }

  @Test
  void map_withOnePatternAndScore_shouldCreateTheTotalScoreAndThePattern() {
    var observations = map("4", null, "8");

    assertThat(observations)
        .extracting(GleasonScoreMapperTest::profile)
        .containsExactly(SCORE_GESAMT_PROFILE, PATTERNS_PROFILE);
    assertThat(observations.getFirst().getDerivedFrom()).isEmpty();
  }

  @Test
  void map_withOnePatternOnly_shouldOnlyCreateThePattern() {
    var observations = map(null, "4", null);

    assertThat(observations)
        .extracting(GleasonScoreMapperTest::profile)
        .containsExactly(PATTERNS_PROFILE);
    assertThat(observations.getFirst().getCode().getCoding())
        .extracting(Coding::getCode)
        .containsExactly("384995005", "44642-7");
  }

  @Test
  void map_withEmptyGleasonScore_shouldCreateNothing() {
    assertThat(map(null, null, null)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {"S,2021-07-08", "O,2024-01-02", "U,2024-01-02", "null,2024-01-02"})
  void map_shouldUseDatumStanzenOnlyForBiopsies(
      @Nullable AnlassGleasonScoreTyp anlass, String expectedEffective) throws Exception {
    var modulProstata = modulProstata("3", "4", "7a");
    modulProstata.setAnlassGleasonScore(anlass);
    modulProstata.setDatumStanzen(
        DatatypeFactory.newInstance().newXMLGregorianCalendar("2021-07-08"));

    var observations =
        sut.map(
            modulProstata,
            "meldung-1",
            new Reference("Patient/any"),
            new Reference("Condition/any"),
            new DateTimeType("2024-01-02"),
            null);

    assertThat(observations)
        .extracting(o -> o.getEffectiveDateTimeType().getValueAsString())
        .containsOnly(expectedEffective);
  }

  private static List<Observation> map(
      @Nullable String primaer, @Nullable String sekundaer, @Nullable String ergebnis) {
    return sut.map(
        modulProstata(primaer, sekundaer, ergebnis),
        "meldung-1",
        new Reference("Patient/any"),
        new Reference("Condition/any"),
        null,
        null);
  }

  private static ModulProstataTyp modulProstata(
      @Nullable String primaer, @Nullable String sekundaer, @Nullable String ergebnis) {
    var gleasonScore = new GleasonScoreTyp();
    gleasonScore.setGradPrimaer(primaer);
    gleasonScore.setGradSekundaer(sekundaer);
    gleasonScore.setScoreErgebnis(ergebnis);
    var modulProstata = new ModulProstataTyp();
    modulProstata.setGleasonScore(gleasonScore);
    return modulProstata;
  }

  private static String profile(Observation observation) {
    var profile = observation.getMeta().getProfile().getFirst().getValue();
    return profile.substring(profile.lastIndexOf('/') + 1).replaceAll("\\|.*$", "");
  }

  private static BigDecimal itemWeight(Coding coding) {
    return ((DecimalType)
            coding
                .getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/itemWeight")
                .getValue())
        .getValue();
  }
}
