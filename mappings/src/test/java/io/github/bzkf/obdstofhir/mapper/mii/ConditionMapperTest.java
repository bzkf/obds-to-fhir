package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import java.util.function.Consumer;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class ConditionMapperTest extends MapperTest {

  private static ConditionMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new ConditionMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({
    "Testpatient_1.xml",
    "Testpatient_2.xml",
    "Testpatient_3.xml",
    "Testpatient_leer.xml",
    "Testpatient_Diagnose.xml",
    "Testpatient_Diagnose_ohne_Version.xml",
    "Testpatient_Diagnose_ICD_WHO.xml",
    "Testpatient_Diagnose_ICD_Sonstige.xml"
  })
  void map_withGivenObds_shouldCreateValidConditionResource(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var conMeldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getDiagnose() != null)
            .findFirst()
            .get();

    final var condition =
        sut.map(
            conMeldung,
            new Reference("Patient/1"),
            obds.getMeldedatum(),
            obdsPatient.getPatientID());

    verify(condition, sourceFile);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_Diagnose.xml,Mamma-Ca", "Testpatient_leer.xml,"})
  void map_withGivenObds_shouldSetCodeTextFromDiagnosetext(
      String sourceFile, String expectedCodeText) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var conMeldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getDiagnose() != null)
            .findFirst()
            .get();

    final var condition =
        sut.map(
            conMeldung,
            new Reference("Patient/1"),
            obds.getMeldedatum(),
            obdsPatient.getPatientID());

    assertThat(condition.getCode().getText()).isEqualTo(expectedCodeText);
  }

  @ParameterizedTest
  @CsvSource({
    "Testpatient_Diagnose.xml,C50.9,,Mamma-Ca",
    "Testpatient_Diagnose_ICD_WHO.xml,,C50.9,Mamma-Ca",
    "Testpatient_Diagnose_ICD_Sonstige.xml,,,C50.9"
  })
  void map_withGivenIcdVersion_shouldOnlyAssertTheStatedCatalog(
      String sourceFile, String expectedGmCode, String expectedWhoCode, String expectedCodeText)
      throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var conMeldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getDiagnose() != null)
            .findFirst()
            .get();

    final var condition =
        sut.map(
            conMeldung,
            new Reference("Patient/1"),
            obds.getMeldedatum(),
            obdsPatient.getPatientID());

    var gmCoding =
        condition.getCode().getCoding().stream()
            .filter(c -> "http://fhir.de/CodeSystem/bfarm/icd-10-gm".equals(c.getSystem()))
            .findFirst();
    assertThat(gmCoding).isPresent();
    assertThat(gmCoding.get().getCode()).isEqualTo(expectedGmCode);

    var whoCoding =
        condition.getCode().getCoding().stream()
            .filter(c -> "http://hl7.org/fhir/sid/icd-10".equals(c.getSystem()))
            .findFirst();
    assertThat(whoCoding.map(Coding::getCode).orElse(null)).isEqualTo(expectedWhoCode);

    assertThat(condition.getCode().getText()).isEqualTo(expectedCodeText);
  }

  @Test
  void map_withPathologieOnly_shouldUseItsTopographyDiagnosesicherungAndDiagnosetext()
      throws IOException {
    var condition = mapPathologieMeldung(meldung -> {});

    assertThat(condition.getCode().getText()).isEqualTo("Mamma-Ca");
    assertThat(condition.getBodySiteFirstRep().getText()).isEqualTo("Brust");
    assertThat(condition.getBodySiteFirstRep().getCodingFirstRep().getSystem())
        .isEqualTo("http://terminology.hl7.org/CodeSystem/icd-o-3");
    assertThat(condition.getVerificationStatus().getCoding())
        .extracting(Coding::getCode)
        .containsExactly("confirmed", "7");
    // the Diagnose is still missing
    assertThat(condition.getMeta().getTag()).extracting(Coding::getCode).contains("255599008");
  }

  @Test
  void map_withUnknownPathologieDiagnosesicherung_shouldNotThrow() throws IOException {
    var condition = mapPathologieMeldung(m -> m.getPathologie().setDiagnosesicherung("invalid"));

    assertThat(condition.getVerificationStatus().getCoding())
        .extracting(Coding::getCode)
        .containsExactly("confirmed");
  }

  private Condition mapPathologieMeldung(Consumer<Meldung> customize) throws IOException {
    final var resource =
        this.getClass().getClassLoader().getResource("obds3/Testpatient_Patho.xml");
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);
    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var meldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getPathologie() != null)
            .findFirst()
            .orElseThrow();
    customize.accept(meldung);

    return sut.map(
        meldung, new Reference("Patient/any"), obds.getMeldedatum(), obdsPatient.getPatientID());
  }
}
