package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.AllgemeinerLeistungszustand;
import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class LeistungszustandMapperTest extends MapperTest {

  private static LeistungszustandMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new LeistungszustandMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_2.xml", "Testpatient_3.xml"})
  void map_withGivenObds_shouldCreateValidConditionResource(String sourceFile) throws IOException {
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

    final var leistungszustand =
        sut.map(
            conMeldung.getDiagnose().getAllgemeinerLeistungszustand(),
            conMeldung.getMeldungID(),
            conMeldung.getTumorzuordnung().getDiagnosedatum(),
            new Reference("Patient/1"),
            new Reference("Condition/Primärdiagnose"));

    verify(leistungszustand.getFirst(), sourceFile);
  }

  @ParameterizedTest
  @CsvSource({
    "ECOG_0,",
    "ECOG_4,",
    "U,",
    "KARNOFSKY_10,10%",
    "KARNOFSKY_70,70%",
    "KARNOFSKY_100,100%"
  })
  void map_shouldOnlyCreateKarnofskyObservationForKarnofskyValues(
      AllgemeinerLeistungszustand leistungszustand, String expectedCode) {
    final var observations =
        sut.map(
            leistungszustand,
            "10_1_VE-1",
            (DateTimeType) null,
            new Reference("Patient/1"),
            new Reference("Condition/Primärdiagnose"));

    if (expectedCode == null) {
      assertThat(observations).hasSize(1);
      assertThat(observations.getFirst().hasDerivedFrom()).isFalse();
      return;
    }

    assertThat(observations).hasSize(2);

    final var karnofsky = observations.get(1);
    assertThat(karnofsky.getValueCodeableConcept().getCodingFirstRep().getCode())
        .isEqualTo(expectedCode);

    final var ecog = observations.getFirst();
    assertThat(ecog.getDerivedFromFirstRep().getReference())
        .isEqualTo("Observation/" + karnofsky.getIdPart());
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_3.xml"})
  void map_withKarnofskyInput_shouldAlsoCreateKarnofskyObservation(String sourceFile)
      throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var verlaufMeldungOptional =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getVerlauf() != null)
            .filter(m -> m.getVerlauf().getAllgemeinerLeistungszustand() != null)
            .findFirst();
    assertThat(verlaufMeldungOptional).isPresent();
    var verlaufMeldung = verlaufMeldungOptional.get();

    final var observations =
        sut.map(
            verlaufMeldung.getVerlauf().getAllgemeinerLeistungszustand(),
            verlaufMeldung.getMeldungID(),
            verlaufMeldung.getVerlauf().getUntersuchungsdatumVerlauf(),
            new Reference("Patient/1"),
            new Reference("Condition/Primärdiagnose"));

    assertThat(observations).hasSize(2);
    verify(observations.get(1), sourceFile);
  }
}
