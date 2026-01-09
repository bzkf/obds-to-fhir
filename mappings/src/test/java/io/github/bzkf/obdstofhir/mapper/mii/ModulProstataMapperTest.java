package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class, GleasonScoreMapper.class})
@EnableConfigurationProperties
class ModulProstataMapperTest extends MapperTest {
  private static ModulProstataMapper sut;

  @BeforeAll
  static void beforeEach(
      @Autowired FhirProperties fhirProps, @Autowired GleasonScoreMapper gleasonScoreMapper) {
    sut = new ModulProstataMapper(fhirProps, gleasonScoreMapper);
  }

  @ParameterizedTest
  @CsvSource({
    "Priopatient_2.xml",
    "Testpatient_2.xml",
    "Testpatient_Patho2.xml",
    "Testpatient_Prostata.xml",
    "Testpatient_Prostata_Kein_Ergebnis.xml",
    "Folgepaket_Testpatient_Patho2.xml"
  })
  void map_withGivenObds_shouldCreateValidObservations(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var subject = new Reference("Patient/any");
    var diagnose = new Reference("Condition/Primärdiagnose");
    var op = new Reference("Procedure/any");

    final var list = new ArrayList<Observation>();
    for (var meldung : obdsPatient.getMengeMeldung().getMeldung()) {
      if (meldung.getPathologie() != null && meldung.getPathologie().getModulProstata() != null) {
        var observation =
            sut.map(
                meldung.getPathologie().getModulProstata(),
                meldung.getMeldungID(),
                subject,
                diagnose);
        list.addAll(observation);
      }
      if (meldung.getDiagnose() != null && meldung.getDiagnose().getModulProstata() != null) {
        var observation =
            sut.map(
                meldung.getDiagnose().getModulProstata(),
                meldung.getMeldungID(),
                subject,
                diagnose);
        list.addAll(observation);
      }
      if (meldung.getOP() != null && meldung.getOP().getModulProstata() != null) {
        var observation =
            sut.map(
                meldung.getOP().getModulProstata(),
                meldung.getMeldungID(),
                subject,
                diagnose,
                null,
                List.of(op));
        list.addAll(observation);
      }
    }

    verifyAll(list, sourceFile);
  }
}
