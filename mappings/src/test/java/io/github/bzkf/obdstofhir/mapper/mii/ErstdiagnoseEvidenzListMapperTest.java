package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class ErstdiagnoseEvidenzListMapperTest extends MapperTest {
  private static ErstdiagnoseEvidenzListMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new ErstdiagnoseEvidenzListMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_2.xml", "Testpatient_3.xml"})
  void map_withGivenObds_shouldCreateValidList(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var subject = new Reference("Patient/any");
    var observation = new Reference("Observation/any");
    var dr = new Reference("DiagnosticReport/any");

    var list = new ArrayList<ListResource>();

    for (var meldung : obdsPatient.getMengeMeldung().getMeldung()) {
      if (meldung.getDiagnose() != null) {
        var mapped =
            sut.map(
                obdsPatient.getPatientID(),
                meldung.getTumorzuordnung().getTumorID(),
                subject,
                List.of(observation, dr));
        list.add(mapped);
      }
    }

    verifyAll(list, sourceFile);
  }
}
