package org.miracum.streams.ume.obdstofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsObservationMapper.ModulProstataMappingParams;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID.Modul_Prostata;
import org.miracum.streams.ume.obdstofhir.model.Meldeanlass;
import org.miracum.streams.ume.obdstofhir.processor.ObdsProcessorTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {FhirProperties.class},
    properties = {"app-version=0.0.0-test"})
@EnableConfigurationProperties()
class PsaToObservationMapperTests extends ObdsProcessorTest {
  private final PsaToObservationMapper sut;
  private final FhirProperties fhirProps;

  @Autowired
  PsaToObservationMapperTests(FhirProperties fhirProps) {
    this.fhirProps = fhirProps;
    sut = new PsaToObservationMapper(fhirProps);
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

  @Test
  void map_withGivenPsaValue_shouldSetAsObservationValue() {
    var expectedPsa = 80.0d;
    var modulProstata = new Modul_Prostata();

    modulProstata.setPSA(Optional.of(expectedPsa));
    modulProstata.setDatumPSA(Optional.empty());

    var params =
        new ModulProstataMappingParams(
            Meldeanlass.BEHANDLUNGSENDE,
            modulProstata,
            "pid-1",
            "op-id-1",
            new DateTimeType("2000-01-01T00:00:00"),
            new DateTimeType("2000-12-01T00:00:00"));

    var observation = sut.map(params, getPatientReference("pid-1"), "test");

    assertThat(observation.getValueQuantity().getValue().doubleValue()).isEqualTo(expectedPsa);
  }

  @Test
  void map_withGivenPsaValueAndDatumPsa_shouldUseDatumPsaAsEffectiveDate() {
    var expectedPsa = 80.0d;
    var modulProstata = new Modul_Prostata();

    modulProstata.setPSA(Optional.of(expectedPsa));
    modulProstata.setDatumPSA(Optional.of("01.07.2023"));

    var params =
        new ModulProstataMappingParams(
            Meldeanlass.BEHANDLUNGSENDE,
            modulProstata,
            "pid-1",
            "op-id-1",
            new DateTimeType("2022-12-01T00:00:00"),
            new DateTimeType("2023-12-01T00:00:00"));

    var observation = sut.map(params, getPatientReference("pid-1"), "test");

    assertThat(observation.getEffectiveDateTimeType().asStringValue()).isEqualTo("2023-07-01");
  }

  @Test
  void map_withGivenPsaValue_shouldMatchSnapshot() {
    var expectedPsa = 80.0d;
    var modulProstata = new Modul_Prostata();

    modulProstata.setPSA(Optional.of(expectedPsa));
    modulProstata.setDatumPSA(Optional.of("01.07.2023"));

    var params =
        new ModulProstataMappingParams(
            Meldeanlass.BEHANDLUNGSENDE,
            modulProstata,
            "pid-1",
            "op-id-1",
            new DateTimeType("2022-12-01T00:00:00"),
            new DateTimeType("2023-12-01T00:00:00"));

    var observation = sut.map(params, getPatientReference("pid-1"), "test");

    var fhirJson = fhirParser.encodeResourceToString(observation);
    Approvals.verify(fhirJson, new Options().forFile().withExtension(".fhir.json"));
  }
}
