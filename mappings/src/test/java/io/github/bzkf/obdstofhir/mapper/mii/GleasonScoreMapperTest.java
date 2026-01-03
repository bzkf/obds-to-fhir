package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import java.util.ArrayList;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class GleasonScoreMapperTest extends MapperTest {
  private static GleasonScoreMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new GleasonScoreMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_Prostata.xml", "Testpatient_Prostata_Kein_Ergebnis.xml"})
  void map_withGivenObds_shouldCreateValidGleasonScoreObservation(String sourceFile)
      throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var subject = new Reference("Patient/any");
    var diagnose = new Reference("Condition/Primärdiagnose");

    final var list = new ArrayList<Observation>();
    for (var meldung : obdsPatient.getMengeMeldung().getMeldung()) {
      if (meldung.getPathologie() != null) {
        var observation =
            sut.map(
                meldung.getPathologie().getModulProstata(),
                meldung.getMeldungID(),
                subject,
                diagnose);
        list.add(observation);
      }
      if (meldung.getDiagnose() != null) {
        var observation =
            sut.map(
                meldung.getDiagnose().getModulProstata(),
                meldung.getMeldungID(),
                subject,
                diagnose);
        list.add(observation);
      }
      if (meldung.getOP() != null) {
        var observation =
            sut.map(meldung.getOP().getModulProstata(), meldung.getMeldungID(), subject, diagnose);
        list.add(observation);
      }
    }

    verifyAll(list, sourceFile);
  }
}
