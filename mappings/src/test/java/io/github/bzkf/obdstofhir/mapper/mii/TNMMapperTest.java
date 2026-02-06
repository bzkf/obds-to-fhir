package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.mii.TNMMapper.TnmType;
import java.io.IOException;
import java.util.ArrayList;
import org.hl7.fhir.r4.model.CodeableConcept;
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
}
