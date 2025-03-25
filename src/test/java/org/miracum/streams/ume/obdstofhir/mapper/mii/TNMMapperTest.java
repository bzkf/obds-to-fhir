package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
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

    final var tnmObservations =
        sut.map(conMeldung, new Reference("Patient/1"), new Reference("Condition/Primärdiagnose"));

    verifyAll(tnmObservations, sourceFile);
  }
}
