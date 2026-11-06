package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.PatientenStammdatenMelderTyp.Geschlecht;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class PatientMapperTest extends MapperTest {
  private static PatientMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new PatientMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({
    "test1.xml",
    "Testpatient_1.xml",
    "Testpatient_2.xml",
    "Testpatient_3.xml",
    "Testpatient_leer.xml"
  })
  void map_withGivenObds_shouldCreateValidPatientResource(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    final var patient = sut.map(obdsPatient, obdsPatient.getMengeMeldung().getMeldung());

    verify(patient, sourceFile);
  }

  @ParameterizedTest
  @CsvSource({"D,divers", "X,unbestimmt"})
  void map_withGeschlechtDOrX_shouldSetOtherWithGenderAmtlichExtension(
      String geschlecht, String display) throws IOException {
    var patient = mapWithGeschlecht(Geschlecht.fromValue(geschlecht));

    assertThat(patient.getGender()).isEqualTo(AdministrativeGender.OTHER);

    var extension =
        patient
            .getGenderElement()
            .getExtensionByUrl("http://fhir.de/StructureDefinition/gender-amtlich-de");
    assertThat(extension).isNotNull();

    var coding = (Coding) extension.getValue();
    assertThat(coding.getSystem()).isEqualTo("http://fhir.de/CodeSystem/gender-amtlich-de");
    assertThat(coding.getCode()).isEqualTo(geschlecht);
    assertThat(coding.getDisplay()).isEqualTo(display);
  }

  @ParameterizedTest
  @CsvSource({"M,MALE", "W,FEMALE", "U,UNKNOWN"})
  void map_withOtherGeschlecht_shouldNotAddGenderAmtlichExtension(
      String geschlecht, AdministrativeGender expected) throws IOException {
    var patient = mapWithGeschlecht(Geschlecht.fromValue(geschlecht));

    assertThat(patient.getGender()).isEqualTo(expected);
    assertThat(patient.getGenderElement().hasExtension()).isFalse();
  }

  private org.hl7.fhir.r4.model.Patient mapWithGeschlecht(Geschlecht geschlecht)
      throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/test1.xml");
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);
    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    obdsPatient.getPatientenStammdaten().setGeschlecht(geschlecht);

    return sut.map(obdsPatient, obdsPatient.getMengeMeldung().getMeldung());
  }

  @Test
  void map_withUseObdsPatientIdAsResourceIdEnabled_shouldSetPatientIdToObdsPatientId()
      throws IOException {
    ReflectionTestUtils.setField(sut, "useObdsPatientIdAsResourceId", true);
    try {
      final var resource = this.getClass().getClassLoader().getResource("obds3/test1.xml");
      assertThat(resource).isNotNull();

      final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);
      var obdsPatient = obds.getMengePatient().getPatient().getFirst();

      final var patient = sut.map(obdsPatient, obdsPatient.getMengeMeldung().getMeldung());

      assertThat(patient.getIdElement().getIdPart()).isEqualTo(obdsPatient.getPatientID());
    } finally {
      ReflectionTestUtils.setField(sut, "useObdsPatientIdAsResourceId", false);
    }
  }

  @Test
  void map_withPatientIdPrefix_shouldApplyItToIdentifierAndObdsPatientIdBasedResourceId()
      throws IOException {
    ReflectionTestUtils.setField(sut, "useObdsPatientIdAsResourceId", true);
    ReflectionTestUtils.setField(sut, "patientIdPrefix", "prefix-");
    try {
      final var resource = this.getClass().getClassLoader().getResource("obds3/test1.xml");
      assertThat(resource).isNotNull();

      final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);
      var obdsPatient = obds.getMengePatient().getPatient().getFirst();

      final var patient = sut.map(obdsPatient, obdsPatient.getMengeMeldung().getMeldung());

      var expectedId = "prefix-" + obdsPatient.getPatientID();
      assertThat(patient.getIdentifierFirstRep().getValue()).isEqualTo(expectedId);
      assertThat(patient.getIdElement().getIdPart()).isEqualTo(expectedId);
    } finally {
      ReflectionTestUtils.setField(sut, "useObdsPatientIdAsResourceId", false);
      ReflectionTestUtils.setField(sut, "patientIdPrefix", "");
    }
  }
}
