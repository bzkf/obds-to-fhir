package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import org.approvaltests.Approvals;
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
class GradingObservationMapperTest extends MapperTest {

  private static GradingObservationMapper sut;

  @BeforeAll
  static void beforeAll(@Autowired FhirProperties fhirProps) {
    sut = new GradingObservationMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_2.xml", "Testpatient_3.xml"})
  void map_withGivenObds_shouldCreateValidGradingObservation(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    var subject = new Reference("Patient/any");
    var diagnose = new Reference("Condition/Primärdiagnose");
    final var list = sut.map(obdsPatient.getMengeMeldung(), subject, diagnose);

    var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
    for (int i = 0; i < list.size(); i++) {
      var fhirJson = fhirParser.encodeResourceToString(list.get(i));
      Approvals.verify(
          fhirJson,
          Approvals.NAMES
              .withParameters(sourceFile, "index_" + i)
              .forFile()
              .withExtension(".fhir.json"));
    }
  }
}
