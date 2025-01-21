package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class PatientMapperTest extends MapperTest {
  private static PatientMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new PatientMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({
    "test1.xml",
    "Testpatient_1.xml",
    "Testpatient_2.xml",
    "Testpatient_3.xml",
    "Testpatient_leer.xml"
  })
  void map_withGivenObds_shouldCreateValidPatientResource(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    final var patient = sut.map(obdsPatient, obdsPatient.getMengeMeldung().getMeldung());

    var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
    var fhirJson = fhirParser.encodeResourceToString(patient);
    Approvals.verify(
        fhirJson, Approvals.NAMES.withParameters(sourceFile).forFile().withExtension(".fhir.json"));
  }
}
