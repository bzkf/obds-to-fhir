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
class TodMapperTest extends MapperTest {
  private static TodMapper tm;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    tm = new TodMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml"})
  void map_withGivenObds_shouldCreateValidObservation(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    var subject = new Reference("Patient/any");
    var condition = new Reference("Condition/any");
    var tMeldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getTod() != null)
            .findFirst()
            .get();

    var observations = tm.map(tMeldung, subject, condition);

    verifyEach(observations, sourceFile);
  }
}
