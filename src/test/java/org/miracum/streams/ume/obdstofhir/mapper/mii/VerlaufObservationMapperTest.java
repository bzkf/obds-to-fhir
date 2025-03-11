package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

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
class VerlaufObservationMapperTest extends MapperTest {
  private static VerlaufObservationMapper sut;

  @BeforeAll
  static void beforeAll(@Autowired FhirProperties fhirProps) {
    sut = new VerlaufObservationMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_2.xml", "Testpatient_3.xml"})
  void map_withGivenObds_shouldCreateValidObservation(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    var subject = new Reference("Patient/any");
    var condition = new Reference("Condition/Primärdiagnose");
    var verlaufOptional =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getVerlauf() != null)
            .findFirst(); // TODO: does this limit tests to only one element of Verlauf?

    if (verlaufOptional.isPresent()) {
      var verlauf = verlaufOptional.get().getVerlauf();
      var observation = sut.map(verlauf, subject, condition);
      verify(observation, sourceFile);
    }
  }
}
