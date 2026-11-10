package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.TNMTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie.CodeSystems.MiiCsOnkoTnmUicc;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.mii.TNMMapper.TnmType;
import io.github.dizuker.tofhir.FhirSystems;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class TNMMapperTest extends MapperTest {

  private static TNMMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new TNMMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_2.xml", "Testpatient_3.xml"})
  void map_withGivenObds_shouldCreateValidObservationResource(String sourceFile)
      throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    assert resource != null;
    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var conMeldungOptional =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getDiagnose() != null)
            .findFirst();
    assert conMeldungOptional.isPresent();
    var conMeldung = conMeldungOptional.get();

    final var tnmObservations = new ArrayList<Observation>();

    if (conMeldung.getDiagnose() != null) {
      if (conMeldung.getDiagnose().getCTNM() != null) {
        tnmObservations.addAll(
            sut.map(
                conMeldung.getDiagnose().getCTNM(),
                TnmType.CLINICAL,
                conMeldung.getMeldungID(),
                new Reference("Patient/1"),
                new Reference("Condition/Primärdiagnose"),
                null));
      }
      if (conMeldung.getDiagnose().getPTNM() != null) {
        tnmObservations.addAll(
            sut.map(
                conMeldung.getDiagnose().getPTNM(),
                TnmType.PATHOLOGIC,
                conMeldung.getMeldungID(),
                new Reference("Patient/1"),
                new Reference("Condition/Primärdiagnose"),
                null));
      }
    }
    if (conMeldung.getVerlauf() != null && conMeldung.getVerlauf().getTNM() != null) {
      tnmObservations.addAll(
          sut.map(
              conMeldung.getVerlauf().getTNM(),
              TnmType.GENERIC,
              conMeldung.getMeldungID(),
              new Reference("Patient/1"),
              new Reference("Condition/Primärdiagnose"),
              null));
    }
    if (conMeldung.getOP() != null && conMeldung.getOP().getTNM() != null) {
      tnmObservations.addAll(
          sut.map(
              conMeldung.getOP().getTNM(),
              TnmType.GENERIC,
              conMeldung.getMeldungID(),
              new Reference("Patient/1"),
              new Reference("Condition/Primärdiagnose"),
              null));
    }
    if (conMeldung.getPathologie() != null) {
      if (conMeldung.getPathologie().getCTNM() != null) {
        tnmObservations.addAll(
            sut.map(
                conMeldung.getPathologie().getCTNM(),
                TnmType.CLINICAL,
                conMeldung.getMeldungID(),
                new Reference("Patient/1"),
                new Reference("Condition/Primärdiagnose"),
                null));
      }
      if (conMeldung.getPathologie().getPTNM() != null) {
        tnmObservations.addAll(
            sut.map(
                conMeldung.getPathologie().getPTNM(),
                TnmType.PATHOLOGIC,
                conMeldung.getMeldungID(),
                new Reference("Patient/1"),
                new Reference("Condition/Primärdiagnose"),
                null));
      }
    }

    verifyAll(tnmObservations, sourceFile);
  }

  @Test
  void check_n_m_suffix() {

    var testString = " M1 sn  (i-)  ";

    var valueWithItcSnSuffixExtension = sut.createValueWithItcSnSuffixExtension(testString);

    Assertions.assertEquals("M1", valueWithItcSnSuffixExtension.getCodingFirstRep().getCode());
    var extensions = valueWithItcSnSuffixExtension.getExtension();
    Assertions.assertTrue(
        extensions.stream()
            .anyMatch(
                e -> ((CodeableConcept) e.getValue()).getCodingFirstRep().getCode().equals("i-")));
    Assertions.assertTrue(
        extensions.stream()
            .anyMatch(
                e -> ((CodeableConcept) e.getValue()).getCodingFirstRep().getCode().equals("sn")));
  }

  @Test
  void map_withYRASymbols_shouldAddPraefixModifierExtensionsToEachTnmCategory() {
    var tnm = new TNMTyp();
    tnm.setID("tnm-1");
    tnm.setVersion("8");
    tnm.setT("2");
    tnm.setN("0");
    tnm.setM("0");
    tnm.setYSymbol("y");
    tnm.setRSymbol("r");
    tnm.setASymbol("a");

    var observations =
        sut.map(
            tnm,
            TnmType.PATHOLOGIC,
            "meldung-1",
            new Reference("Patient/1"),
            new Reference("Condition/1"),
            null);

    // T, N, M and the grouping observation. The y, r and a symbols are no longer
    // separate observations.
    Assertions.assertEquals(4, observations.size());

    var categoryProfiles =
        List.of(
            Onkologie.Profiles.miiPrOnkoTnmTKategorie(),
            Onkologie.Profiles.miiPrOnkoTnmNKategorie(),
            Onkologie.Profiles.miiPrOnkoTnmMKategorie());

    for (var profile : categoryProfiles) {
      var category =
          observations.stream()
              .filter(o -> o.getMeta().hasProfile(profile))
              .findFirst()
              .orElseThrow();

      Assertions.assertEquals(
          List.of(
              Onkologie.Extensions.Urls.miiExOnkoTnmYPraefix(),
              Onkologie.Extensions.Urls.miiExOnkoTnmRPraefix(),
              Onkologie.Extensions.Urls.miiExOnkoTnmAPraefix()),
          category.getModifierExtension().stream().map(Extension::getUrl).toList());
      Assertions.assertEquals(
          List.of(
              MiiCsOnkoTnmUicc.Y.coding().getCode(),
              MiiCsOnkoTnmUicc.R.coding().getCode(),
              MiiCsOnkoTnmUicc.A.coding().getCode()),
          category.getModifierExtension().stream()
              .map(e -> ((CodeableConcept) e.getValue()).getCodingFirstRep().getCode())
              .toList());
    }
  }

  @Test
  void map_withoutSymbols_shouldNotAddModifierExtensions() {
    var tnm = new TNMTyp();
    tnm.setID("tnm-1");
    tnm.setVersion("8");
    tnm.setT("2");

    var observations =
        sut.map(
            tnm,
            TnmType.CLINICAL,
            "meldung-1",
            new Reference("Patient/1"),
            new Reference("Condition/1"),
            null);

    Assertions.assertTrue(observations.stream().noneMatch(Observation::hasModifierExtension));
  }

  @Test
  void map_withMSymbol_shouldAddMultipleTumorenComponentToTKategorie() {
    var tnm = new TNMTyp();
    tnm.setID("tnm-1");
    tnm.setVersion("8");
    tnm.setT("2");
    tnm.setN("0");
    tnm.setMSymbol("m");

    var observations =
        sut.map(
            tnm,
            TnmType.PATHOLOGIC,
            "meldung-1",
            new Reference("Patient/1"),
            new Reference("Condition/1"),
            null);

    // T, N and the grouping observation. The m symbol is no longer a separate observation.
    Assertions.assertEquals(3, observations.size());

    var tKategorie =
        observations.stream()
            .filter(o -> o.getMeta().hasProfile(Onkologie.Profiles.miiPrOnkoTnmTKategorie()))
            .findFirst()
            .orElseThrow();

    Assertions.assertEquals(1, tKategorie.getComponent().size());
    var component = tKategorie.getComponentFirstRep();
    Assertions.assertEquals(FhirSystems.LOINC, component.getCode().getCodingFirstRep().getSystem());
    Assertions.assertEquals("42030-7", component.getCode().getCodingFirstRep().getCode());
    Assertions.assertEquals(
        Onkologie.CodeSystems.miiCsOnkoTnmUicc(),
        component.getValueCodeableConcept().getCodingFirstRep().getSystem());
    Assertions.assertEquals(
        MiiCsOnkoTnmUicc.M.coding().getCode(),
        component.getValueCodeableConcept().getCodingFirstRep().getCode());

    Assertions.assertTrue(
        observations.stream()
            .filter(o -> !o.getMeta().hasProfile(Onkologie.Profiles.miiPrOnkoTnmTKategorie()))
            .noneMatch(Observation::hasComponent));
  }

  @ParameterizedTest
  @CsvSource({"N0(mol+),mol+", "N0(mol-),mol-", "N0 (mol+),mol+"})
  void createValue_withMolSuffix_shouldMoveItToTheItcExtension(String input, String suffix) {
    var value = sut.createValueWithItcSnSuffixExtension(input);

    assertThat(value.getCodingFirstRep().getCode()).isEqualTo("N0");
    assertThat(value.getExtension()).hasSize(1);
    assertThat(((CodeableConcept) value.getExtensionFirstRep().getValue()).getCodingFirstRep())
        .extracting(c -> c.getCode())
        .isEqualTo(suffix);
  }

  @ParameterizedTest
  @CsvSource({"m,m,", "10,10,", "'m,is',m,'m,is'", "'2,is',2,'2,is'"})
  void map_withMSymbolInValueSet_shouldUseTheCodeAndKeepTheRestAsText(
      String mSymbol, String expectedCode, String expectedText) {
    var tnm = new TNMTyp();
    tnm.setT("1");
    tnm.setMSymbol(mSymbol);

    var value = multipleTumorenComponent(tnm).getValueCodeableConcept();

    assertThat(value.getCodingFirstRep().getCode()).isEqualTo(expectedCode);
    assertThat(value.getText()).isEqualTo(expectedText);
  }

  @ParameterizedTest
  @CsvSource({"is", "11", "'11,is'"})
  void map_withMSymbolOutsideOfValueSet_shouldKeepItAsNoteWithoutAValue(String mSymbol) {
    var tnm = new TNMTyp();
    tnm.setT("1");
    tnm.setMSymbol(mSymbol);

    var tKategorie = byProfile(mapTnm(tnm), Onkologie.Profiles.miiPrOnkoTnmTKategorie());
    var component = tKategorie.getComponentFirstRep();

    assertThat(component.hasValue()).isFalse();
    assertThat(component.getDataAbsentReason().getCodingFirstRep().getCode()).isEqualTo("as-text");
    assertThat(tKategorie.getNoteFirstRep().getText()).isEqualTo("m-Symbol: " + mSymbol);
  }

  @Test
  void map_withoutVersion_shouldNotSetMethod() {
    var tnm = new TNMTyp();
    tnm.setT("1");
    tnm.setN("0");

    assertThat(mapTnm(tnm)).isNotEmpty().noneMatch(Observation::hasMethod);
  }

  @Test
  void map_withVersion_shouldSetMethod() {
    var tnm = new TNMTyp();
    tnm.setT("1");
    tnm.setVersion("8");

    assertThat(mapTnm(tnm))
        .isNotEmpty()
        .allMatch(o -> o.getMethod().getCodingFirstRep().getCode().equals("8"));
  }

  @Test
  void map_withPraefixU_shouldUseTheClinicalCodeForTNAndM() {
    var tnm = new TNMTyp();
    tnm.setT("1");
    tnm.setN("0");
    tnm.setM("0");
    tnm.setCPUPraefixT("u");
    tnm.setCPUPraefixN("u");
    tnm.setCPUPraefixM("u");

    var observations = mapTnm(tnm);

    assertKategorie(observations, Onkologie.Profiles.miiPrOnkoTnmTKategorie(), "399504009");
    assertKategorie(observations, Onkologie.Profiles.miiPrOnkoTnmNKategorie(), "399534004");
    assertKategorie(observations, Onkologie.Profiles.miiPrOnkoTnmMKategorie(), "399387003");
  }

  private static void assertKategorie(
      List<Observation> observations, String profile, String expectedCode) {
    var code = byProfile(observations, profile).getCode();
    assertThat(code.getCodingFirstRep().getCode()).isEqualTo(expectedCode);
    // the prefix itself isn't lost
    assertThat(
            ((CodeableConcept) code.getExtensionFirstRep().getValue())
                .getCodingFirstRep()
                .getCode())
        .isEqualTo("u");
  }

  private static Observation.ObservationComponentComponent multipleTumorenComponent(TNMTyp tnm) {
    return byProfile(mapTnm(tnm), Onkologie.Profiles.miiPrOnkoTnmTKategorie())
        .getComponentFirstRep();
  }

  private static Observation byProfile(List<Observation> observations, String profile) {
    return observations.stream()
        .filter(o -> o.getMeta().hasProfile(profile))
        .findFirst()
        .orElseThrow();
  }

  private static List<Observation> mapTnm(TNMTyp tnm) {
    return sut.map(
        tnm,
        TnmType.GENERIC,
        "meldung-1",
        new Reference("Patient/1"),
        new Reference("Condition/1"),
        null);
  }
}
