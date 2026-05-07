package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class VitalStatusMapperTest extends MapperTest {
  private static VitalStatusMapper vsm;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    vsm = new VitalStatusMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_Mamma.xml", "Testpatient_2.xml"})
  void map_withGivenObds_shouldCreateValidObservation(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    var subject = new Reference("Patient/any");
    subject.setIdentifier(new Identifier().setValue("123456"));
    var meldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getTod() != null)
            .findFirst()
            .orElse(null);
    var tMeldung = (meldung != null) ? meldung.getTod() : null;
    var meldeDatum = obds.getMeldedatum();

    var observation = vsm.map(meldeDatum, subject, tMeldung, false);

    verify(observation, sourceFile);
  }
}
