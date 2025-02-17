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
class NebenwirkungMapperTest extends MapperTest {
  private static NebenwirkungMapper sut;

  @BeforeAll
  static void beforeAll(@Autowired FhirProperties fhirProps) {
    sut = new NebenwirkungMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_2.xml", "Testpatient_3.xml"})
  void map_withGivenObds_shouldCreateValidAdverseEvent(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);
    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var subject = new Reference("Patient/any");
    var suspectedEntity = new Reference("Procedure/any");
    var nebenwirkung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getSYST() != null)
            .findFirst()
            .get()
            .getSYST()
            .getNebenwirkungen();

    final var list = sut.map(nebenwirkung, subject, suspectedEntity);

    verifyAll(list, sourceFile);
  }
}
