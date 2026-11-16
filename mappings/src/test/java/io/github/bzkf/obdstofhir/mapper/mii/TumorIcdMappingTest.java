package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class TumorIcdMappingTest extends MapperTest {
  @Autowired private FhirProperties fhirProperties;

  static Stream<Arguments> catalogs() {
    return Stream.of(
        Arguments.of("10 2020 GM", "GM", "2020"),
        Arguments.of("10 2020 WHO", "WHO", "2020"),
        Arguments.of("10 2021 WHO", "WHO", "2021"),
        Arguments.of("Sonstige", "OTHER", null),
        Arguments.of(null, "GM", null),
        Arguments.of("", "GM", null),
        Arguments.of("   ", "GM", null),
        Arguments.of("not-a-version", "GM", null));
  }

  @ParameterizedTest
  @MethodSource("catalogs")
  void map_shouldShareCatalogRulesAndRetainCallerText(
      String version, String catalog, String expectedYear) throws IOException {
    var resource = getClass().getClassLoader().getResource("obds3/Testpatient_Diagnose.xml");
    assertThat(resource).isNotNull();
    OBDS obds;
    try (var input = resource.openStream()) {
      obds = xmlMapper().readValue(input, OBDS.class);
    }
    var patient = obds.getMengePatient().getPatient().getFirst();
    var meldung =
        patient.getMengeMeldung().getMeldung().stream()
            .filter(
                m ->
                    m.getDiagnose() != null
                        && m.getDiagnose().getMengeFruehereTumorerkrankung() != null)
            .findFirst()
            .orElseThrow();
    var previous = meldung.getDiagnose().getMengeFruehereTumorerkrankung();
    var previousTumor = previous.getFruehereTumorerkrankung().getFirst();
    var primaryIcd = meldung.getTumorzuordnung().getPrimaertumorICD();
    primaryIcd.setCode("C43.7");
    primaryIcd.setVersion(version);
    previousTumor.getICD().setCode("C43.7");
    previousTumor.getICD().setVersion(version);
    meldung.getDiagnose().setPrimaertumorDiagnosetext("Primary diagnosis text");
    previousTumor.setFreitext("Previous tumor text");

    var primary =
        new ConditionMapper(fhirProperties)
            .map(meldung, new Reference("Patient/1"), obds.getMeldedatum(), patient.getPatientID());
    var earlier =
        new FruehereTumorerkrankungenMapper(fhirProperties)
            .map(
                previous,
                new Reference("Patient/1"),
                new Identifier().setSystem("urn:test").setValue("primary"),
                obds.getMeldedatum())
            .getFirst();
    assertThat(primary.getCode().getText()).isEqualTo("Primary diagnosis text");
    assertThat(earlier.getCode().getText()).isEqualTo("Previous tumor text");
    assertThat(primary.getCode().getCoding()).hasSize(catalog.equals("WHO") ? 2 : 1);
    assertThat(earlier.getCode().getCoding()).hasSameSizeAs(primary.getCode().getCoding());
    for (int i = 0; i < primary.getCode().getCoding().size(); i++) {
      assertThat(
              primary.getCode().getCoding().get(i).equalsDeep(earlier.getCode().getCoding().get(i)))
          .isTrue();
      assertThat(primary.getCode().getCoding().get(i))
          .isNotSameAs(earlier.getCode().getCoding().get(i));
    }
    Coding gm = primary.getCode().getCodingFirstRep();
    assertThat(gm.getSystem()).isEqualTo(fhirProperties.getSystems().getIcd10gm());
    if (catalog.equals("GM")) {
      assertThat(gm.getCode()).isEqualTo("C43.7");
      assertThat(gm.getVersion()).isEqualTo(expectedYear);
      assertThat(gm.getCodeElement().hasExtension()).isFalse();
      if (expectedYear == null) {
        assertThat(
                gm.getVersionElement()
                    .getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/data-absent-reason")
                    .getValue()
                    .primitiveValue())
            .isEqualTo("unknown");
      } else {
        assertThat(gm.getVersionElement().hasExtension()).isFalse();
      }
    } else {
      assertThat(gm.getCode()).isNull();
      assertThat(gm.getVersion()).isNull();
      assertThat(
              gm.getCodeElement()
                  .getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/data-absent-reason")
                  .getValue()
                  .primitiveValue())
          .isEqualTo("not-applicable");
      assertThat(
              gm.getVersionElement()
                  .getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/data-absent-reason")
                  .getValue()
                  .primitiveValue())
          .isEqualTo("not-applicable");
    }
    if (catalog.equals("WHO")) {
      var who = primary.getCode().getCoding().get(1);
      assertThat(who.getSystem()).isEqualTo(fhirProperties.getSystems().getIcd10who());
      assertThat(who.getCode()).isEqualTo("C43.7");
      assertThat(who.getVersion()).isEqualTo(expectedYear);
      assertThat(who.getCodeElement().hasExtension()).isFalse();
      assertThat(who.getVersionElement().hasExtension()).isFalse();
    }
  }
}
