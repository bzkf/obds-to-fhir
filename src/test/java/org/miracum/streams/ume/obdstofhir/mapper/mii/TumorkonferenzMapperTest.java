package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class TumorkonferenzMapperTest extends MapperTest {
  private static TumorkonferenzMapper tmk;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    tmk = new TumorkonferenzMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_2.xml", "Testpatient_3.xml"})
  void map_withGivenObds_shouldCreateValidCarePlan(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    var subject = new Reference("Patient/any");
    var primaerdiagnose = new Reference("Condition/any");
    var tumorMeldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .map(m -> m.getTumorkonferenz())
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

    if (tumorMeldung != null) {
      var carePlan = tmk.map(tumorMeldung, subject, primaerdiagnose);
      verify(carePlan, sourceFile);
    }
  }
}
