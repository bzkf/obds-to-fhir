package org.miracum.streams.ume.obdstofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsObservationMapper.ModulProstataMappingParams;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID.Modul_Prostata;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID.Modul_Prostata.GleasonScore;
import org.miracum.streams.ume.obdstofhir.model.Meldeanlass;
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
  private final FhirProperties fhirProps;

  @Autowired
  GleasonScoreToObservationMapperTests(FhirProperties fhirProps) {
    this.fhirProps = fhirProps;
    sut = new GleasonScoreToObservationMapper(fhirProps);
  }

  private Reference getPatientReference(String patientId) {
    return new Reference()
        .setReference(ResourceType.Patient + "/" + patientId)
        .setIdentifier(
            new Identifier()
                .setSystem(fhirProps.getSystems().getPatientId())
                .setType(
                    new CodeableConcept(
                        new Coding(fhirProps.getSystems().getIdentifierType(), "MR", null)))
                .setValue(patientId));
  }

  @ParameterizedTest
  @CsvSource({"7b, 7", "10c, 10", "3, 3"})
  void map_withGivenScoreErgebnis_shouldSetToValueInteger(
      String gleasonScoreErgebnis, Integer expectedValue) {

    var gleasonScore = new GleasonScore();
    gleasonScore.setGleasonScoreErgebnis(gleasonScoreErgebnis);
    var modulProstata = new Modul_Prostata();

    modulProstata.setGleasonScore(Optional.of(gleasonScore));

    var params =
        new ModulProstataMappingParams(
            Meldeanlass.BEHANDLUNGSENDE,
            modulProstata,
            "pid-1",
            "op-id-1",
            new DateTimeType("2000-01-01T00:00:00"),
            new DateTimeType("2001-01-01T00:00:00"));

    var observation = sut.map(params, getPatientReference("pid-1"), "test");

    assertThat(observation.getValueIntegerType().getValue()).isEqualTo(expectedValue);
  }

  @Test
  void map_withGivenModulProstata_shouldMatchSnapshot() {

    var gleasonScore = new GleasonScore();
    gleasonScore.setGleasonScoreErgebnis("3");
    var modulProstata = new Modul_Prostata();

    modulProstata.setGleasonScore(Optional.of(gleasonScore));

    var params =
        new ModulProstataMappingParams(
            Meldeanlass.BEHANDLUNGSENDE,
            modulProstata,
            "pid-1",
            "op-id-1",
            new DateTimeType("2000-01-01"),
            new DateTimeType("2001-01-01T00:00:00"));

    var observation = sut.map(params, getPatientReference("pid-1"), "test");

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

    modulProstata.setGleasonScore(Optional.of(gleasonScore));

    var params =
        new ModulProstataMappingParams(
            Meldeanlass.BEHANDLUNGSENDE,
            modulProstata,
            "pid-1",
            "op-id-1",
            new DateTimeType("2000-01-01T00:00:00"),
            new DateTimeType("2001-01-01T00:00:00"));

    var patReference = getPatientReference("pid-1");
    assertThatThrownBy(() -> sut.map(params, patReference, "test"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
