package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class OperationMapperTest extends MapperTest {
  private static OperationMapper sut;

  private static final Logger LOG = LoggerFactory.getLogger(OperationMapper.class);

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new OperationMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_2.xml"})
  void map_withGivenObds_shouldCreateValidProcedure(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    System.out.println("Loaded resource: " + resource.getPath());

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    var subject = new Reference("Patient/any");
    var condition = new Reference("Condition/any");

    var opMeldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getOP() != null)
            .findFirst()
            .get();

    // Map and get the list of procedures
    final var resultResources = sut.map(opMeldung.getOP(), subject, condition);

    assertThat(resultResources).isNotEmpty();
    LOG.info("Length of resultResources {}", resultResources.size());
    LOG.info("Number of OPS codes: {}", opMeldung);

    verifyAll(resultResources, sourceFile);
  }
}
