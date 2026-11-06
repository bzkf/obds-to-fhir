package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import com.github.difflib.text.DiffRowGenerator;
import com.spun.util.introspection.Caller;
import de.basisdatensatz.obds.v3.MengeWeitereKlassifikationTyp;
import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.ProfileTestConfig;
import java.io.IOException;
import java.util.Arrays;
import java.util.StringJoiner;
import org.approvaltests.Approvals;
import org.approvaltests.scrubbers.Scrubbers;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(classes = {ObdsToFhirBundleMapperTestConfig.class, ProfileTestConfig.class})
@EnableConfigurationProperties
@Configuration
class ObdsToFhirBundleMapperTest extends MapperTest {
  private static ObdsToFhirBundleMapper sut;

  @BeforeAll
  static void beforeAll(@Autowired ObdsToFhirBundleMapper bundleMapper) {
    sut = bundleMapper;
  }

  @ParameterizedTest
  @CsvSource({
    "Testpatient_1.xml",
    "Testpatient_2.xml",
    "Testpatient_3.xml",
    "Testpatient_CLL.xml",
    "Testpatient_leer.xml",
    "Testpatient_Mamma.xml",
    "Testpatient_Melanom.xml",
    "Testpatient_Prostata.xml",
    "Testpatient_Prostata_Diagnosedatum_Geschaetzt.xml",
    "Testpatient_Patho.xml",
    "Testpatient_Patho2.xml",
    "Testpatient_Rektum.xml",
    "Folgepaket_Testpatient_Rektum.xml",
    "Testpatientin_Cervix.xml",
    "Testperson_CervixC53.xml",
    "Testperson_Cervixinsitu.xml",
    "Priopatient_3.xml",
    "GroupSequence01.xml",
    "Folgepaket_Testpatient_Mamma.xml",
    "obdsv3_st_strahlenart.xml",
    "obdsv3_missing_meldungen.xml",
    "Folgepaket_Testpatientin_Cervix.xml"
  })
  void map_withGivenObds_shouldCreateBundleMatchingSnapshot(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    final var results = sut.map(obds);

    var caller = Caller.get(0);
    var methodName = caller.getMethodName();
    var className = this.getClass().getSimpleName();

    var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);

    for (int i = 0; i < results.size(); i++) {
      var fhirJson = fhirParser.encodeResourceToString(results.get(i).bundle());
      Approvals.verify(
          fhirJson,
          Approvals.NAMES
              .withParameters("")
              .withScrubber(PROFILE_VERSION_SCRUBBER)
              .forFile()
              .withBaseName(String.format("%s/%s.%s.%d", className, methodName, sourceFile, i))
              .forFile()
              .withExtension(".fhir.json"));
    }
  }

  @Test
  void shouldDetectDuplicateResources() {
    var p1 = new Patient();
    p1.setId("Patient/1");
    p1.setActive(false);
    var p2 = new Patient();
    p2.setId("Patient/1");
    p2.setActive(true);
    p2.addIdentifier().setSystem("example").setValue("123");

    var parser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
    var existing = Arrays.asList(parser.encodeResourceToString(p1).split("\n"));
    var updated = Arrays.asList(parser.encodeResourceToString(p2).split("\n"));

    var generator =
        DiffRowGenerator.create()
            .showInlineDiffs(true)
            .inlineDiffByWord(true)
            .oldTag(f -> "~")
            .newTag(f -> "**")
            .build();

    var rows = generator.generateDiffRows(existing, updated);

    var sj = new StringJoiner("\n");
    sj.add("|original|new|");
    sj.add("|--------|---|");
    for (var row : rows) {
      sj.add("|" + row.getOldLine() + "|" + row.getNewLine() + "|");
    }

    assertThat(sj.toString())
        .hasToString(
            """
                |original|new|
                |--------|---|
                |{|{|
                |  "resourceType": "Patient",|  "resourceType": "Patient",|
                |  "id": "1",|  "id": "1",|
                |  ~"active"~: ~false~|  **"identifier"**: **[ {**|
                ||**    "system": "example",**|
                ||**    "value": "123"**|
                ||**  } ],**|
                ||**  "active": true**|
                |}|}|""");
  }

  private OBDS readObds(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();
    return xmlMapper().readValue(resource.openStream(), OBDS.class);
  }

  @Test
  void map_withWeitereKlassifikationInOp_shouldCreateObservation() throws IOException {
    final var obds = readObds("Testpatient_1.xml");
    var opMeldung =
        obds.getMengePatient().getPatient().getFirst().getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getOP() != null)
            .findFirst()
            .orElseThrow();
    var klassifikation = new MengeWeitereKlassifikationTyp.WeitereKlassifikation();
    klassifikation.setName("Test-Klassifikation");
    klassifikation.setStadium("IIa");
    var menge = new MengeWeitereKlassifikationTyp();
    menge.getWeitereKlassifikation().add(klassifikation);
    opMeldung.getOP().setMengeWeitereKlassifikation(menge);

    var observations =
        sut.map(obds).getFirst().bundle().getEntry().stream()
            .map(e -> e.getResource())
            .filter(Observation.class::isInstance)
            .map(Observation.class::cast)
            .filter(
                o -> o.getMeta().hasProfile(Onkologie.Profiles.miiPrOnkoWeitereKlassifikationen()))
            .toList();

    assertThat(observations).hasSize(1);
    assertThat(observations.getFirst().getCode().getText()).isEqualTo("Test-Klassifikation");
    assertThat(observations.getFirst().getValueCodeableConcept().getText()).isEqualTo("IIa");
    assertThat(observations.getFirst().getFocusFirstRep().getReference()).startsWith("Condition/");
  }

  @Test
  void map_withMeldungAfterPathologie_shouldKeepThePathologieDataOnTheCondition()
      throws IOException {
    final var obds = readObds("Testpatient_Patho.xml");
    var meldungen = obds.getMengePatient().getPatient().getFirst().getMengeMeldung().getMeldung();
    var pathologieMeldung =
        meldungen.stream().filter(m -> m.getPathologie() != null).findFirst().orElseThrow();

    // only creates the Condition of the same tumor from its Tumorzuordnung, like a Verlauf would
    var laterMeldung = new Meldung();
    laterMeldung.setMeldungID("later");
    laterMeldung.setTumorzuordnung(pathologieMeldung.getTumorzuordnung());
    meldungen.add(laterMeldung);

    var condition =
        sut.map(obds).getFirst().bundle().getEntry().stream()
            .map(e -> e.getResource())
            .filter(Condition.class::isInstance)
            .map(Condition.class::cast)
            .filter(
                c ->
                    c.getIdentifierFirstRep()
                        .getValue()
                        .endsWith(pathologieMeldung.getTumorzuordnung().getTumorID()))
            .findFirst()
            .orElseThrow();

    assertThat(condition.getCode().getText())
        .isEqualTo(pathologieMeldung.getPathologie().getPrimaertumorDiagnosetext());
    assertThat(condition.getBodySiteFirstRep().getText())
        .isEqualTo(pathologieMeldung.getPathologie().getPrimaertumorTopographieFreitext());
    assertThat(condition.hasVerificationStatus()).isTrue();
  }

  @Test
  void map_shouldKeepTheLoggingContextOfTheCaller() throws IOException {
    final var obds = readObds("Testpatient_Tumorkonferenz1.xml");

    MDC.put("fileName", "Testpatient_Tumorkonferenz1.xml");
    try {
      sut.map(obds);

      assertThat(MDC.get("fileName")).isEqualTo("Testpatient_Tumorkonferenz1.xml");
      assertThat(MDC.get("meldungId")).isNull();
      assertThat(MDC.get("tumorId")).isNull();
    } finally {
      MDC.remove("fileName");
    }
  }

  @SpringBootTest(
      classes = {ObdsToFhirBundleMapperTestConfig.class, ProfileTestConfig.class},
      properties = "fhir.mappings.create-provenance-resources.enabled=true")
  @EnableConfigurationProperties
  @Configuration
  static class ObdsToFhirBundleMapperWithProvenanceEnabledTest extends MapperTest {
    private static ObdsToFhirBundleMapper sut;

    @BeforeAll
    static void beforeAll(@Autowired ObdsToFhirBundleMapper bundleMapper) {
      sut = bundleMapper;
    }

    @ParameterizedTest
    @CsvSource({
      "Testpatient_1.xml",
      "Testpatient_2.xml",
    })
    void map_withGivenObdsAndEnabledProvenance_shouldCreateBundleMatchingSnapshot(String sourceFile)
        throws IOException {
      final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
      assertThat(resource).isNotNull();

      final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

      final var results = sut.map(obds);

      var caller = Caller.get(0);
      var methodName = caller.getMethodName();
      var className = this.getClass().getSimpleName();

      var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);

      for (int i = 0; i < results.size(); i++) {
        var result = results.get(i);
        var scrubber =
            Scrubbers.scrubAll(
                ProvenanceMapperTest.FHIR_DATE_TIME_SCRUBBER, PROFILE_VERSION_SCRUBBER);
        Approvals.verify(
            fhirParser.encodeResourceToString(result.bundle()),
            Approvals.NAMES
                .withParameters("")
                .withScrubber(scrubber)
                .forFile()
                .withBaseName(String.format("%s/%s.%s.%d", className, methodName, sourceFile, i))
                .forFile()
                .withExtension(".fhir.json"));
        for (int j = 0; j < result.provenances().size(); j++) {
          Approvals.verify(
              fhirParser.encodeResourceToString(result.provenances().get(j)),
              Approvals.NAMES
                  .withParameters("")
                  .withScrubber(scrubber)
                  .forFile()
                  .withBaseName(
                      String.format(
                          "%s/%s.%s.%d.provenance.%d", className, methodName, sourceFile, i, j))
                  .forFile()
                  .withExtension(".fhir.json"));
        }
      }
    }
  }

  @SpringBootTest(
      classes = {ObdsToFhirBundleMapperTestConfig.class, ProfileTestConfig.class},
      properties = "fhir.mappings.patient-id-regex=^(\\\\d)\\\\d*$")
  @EnableConfigurationProperties
  @Configuration
  static class ObdsToFhirBundleMapperWithPatientIdRegexSetTest extends MapperTest {
    private static ObdsToFhirBundleMapper sut;

    @BeforeAll
    static void beforeAll(@Autowired ObdsToFhirBundleMapper bundleMapper) {
      sut = bundleMapper;
    }

    @Test
    void map_withPatientIdNotMatchingRegex_shouldNotExposeItInTheException() throws IOException {
      final var resource = this.getClass().getClassLoader().getResource("obds3/Testpatient_1.xml");
      assertThat(resource).isNotNull();

      final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);
      obds.getMengePatient().getPatient().getFirst().setPatientID("not-a-number");

      assertThatThrownBy(() -> sut.map(obds))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageNotContaining("not-a-number");
    }

    @ParameterizedTest
    @CsvSource({
      "Testpatient_1.xml",
    })
    void map_withGivenObdsAndPatientIdRegex_shouldCreateBundleMatchingSnapshot(String sourceFile)
        throws IOException {
      final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
      assertThat(resource).isNotNull();

      final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

      final var results = sut.map(obds);

      var caller = Caller.get(0);
      var methodName = caller.getMethodName();
      var className = this.getClass().getSimpleName();

      var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);

      for (int i = 0; i < results.size(); i++) {
        var fhirJson = fhirParser.encodeResourceToString(results.get(i).bundle());
        Approvals.verify(
            fhirJson,
            Approvals.NAMES
                .withParameters("")
                .withScrubber(
                    Scrubbers.scrubAll(
                        ProvenanceMapperTest.FHIR_DATE_TIME_SCRUBBER, PROFILE_VERSION_SCRUBBER))
                .forFile()
                .withBaseName(String.format("%s/%s.%s.%d", className, methodName, sourceFile, i))
                .forFile()
                .withExtension(".fhir.json"));
      }
    }
  }
}
