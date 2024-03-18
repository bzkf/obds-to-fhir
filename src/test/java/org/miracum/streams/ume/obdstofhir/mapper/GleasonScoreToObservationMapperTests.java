package org.miracum.streams.ume.obdstofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung.Menge_OP.OP.GleasonScore;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung.Menge_OP.OP.Modul_Prostata;
import org.miracum.streams.ume.obdstofhir.processor.ObdsProcessorTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {FhirProperties.class},
    properties = {"app.version=0.0.0-test"})
@EnableConfigurationProperties()
class GleasonScoreToObservationMapperTests extends ObdsProcessorTest {
  private final GleasonScoreToObservationMapper sut;

  @Autowired
  GleasonScoreToObservationMapperTests(FhirProperties fhirProps) {
    sut = new GleasonScoreToObservationMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"7b, 7", "10c, 10", "3, 3"})
  void map_withGivenScoreErgebnis_shouldSetToValueInteger(
      String gleasonScoreErgebnis, Integer expectedValue) {

    var gleasonScore = new GleasonScore();
    gleasonScore.setGleasonScoreErgebnis(gleasonScoreErgebnis);
    var modulProstata = new Modul_Prostata();

    modulProstata.setGleasonScore(gleasonScore);

    var observation = sut.map(modulProstata, "pid-1", "op-id-1");

    assertThat(observation.getValueIntegerType().getValue()).isEqualTo(expectedValue);
  }

  @Test
  void map_withGivenModulProstata_shouldMatchSnapshot() {

    var gleasonScore = new GleasonScore();
    gleasonScore.setGleasonScoreErgebnis("3");
    var modulProstata = new Modul_Prostata();

    modulProstata.setGleasonScore(gleasonScore);

    var observation = sut.map(modulProstata, "pid-1", "op-id-1");

    var fhirJson = fhirParser.encodeResourceToString(observation);
    Approvals.verify(fhirJson, new Options().forFile().withExtension(".fhir.json"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "123", "ff", "c"})
  void map_withInvalidGleasonScoreErgebnis_shouldThrowException(String gleasonScoreErgebnis) {

    var gleasonScore = new GleasonScore();
    gleasonScore.setGleasonScoreErgebnis(gleasonScoreErgebnis);
    var modulProstata = new Modul_Prostata();

    modulProstata.setGleasonScore(gleasonScore);

    assertThatThrownBy(() -> sut.map(modulProstata, "pid-1", "op-id-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
