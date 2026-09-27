package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.TNMTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.mii.TNMMapper.TnmType;
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
          List.of("y", "r", "a"),
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
    Assertions.assertEquals(
        "http://loinc.org", component.getCode().getCodingFirstRep().getSystem());
    Assertions.assertEquals("42030-7", component.getCode().getCodingFirstRep().getCode());
    Assertions.assertEquals(
        Onkologie.CodeSystems.miiCsOnkoTnmUicc(),
        component.getValueCodeableConcept().getCodingFirstRep().getSystem());
    Assertions.assertEquals("m", component.getValueCodeableConcept().getCodingFirstRep().getCode());

    Assertions.assertTrue(
        observations.stream()
            .filter(o -> !o.getMeta().hasProfile(Onkologie.Profiles.miiPrOnkoTnmTKategorie()))
            .noneMatch(Observation::hasComponent));
  }
}
