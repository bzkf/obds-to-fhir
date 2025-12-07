package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import java.util.ArrayList;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class SpecimenMapperTest extends MapperTest {
  private static SpecimenMapper sut;

  @BeforeAll
  static void beforeAll(@Autowired FhirProperties fhirProps) {
    sut = new SpecimenMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_2.xml", "Testpatient_3.xml"})
  void map_withGivenObds_shouldCreateValidSpecimen(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);
    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    var subject = new Reference("Patient/any");

    var list = new ArrayList<Specimen>();

    for (var meldung : obdsPatient.getMengeMeldung().getMeldung()) {
      if (meldung.getDiagnose() != null && meldung.getDiagnose().getHistologie() != null) {
        list.add(sut.map(meldung.getDiagnose().getHistologie(), subject, meldung.getMeldungID()));
      }

      if (meldung.getVerlauf() != null && meldung.getVerlauf().getHistologie() != null) {
        list.add(sut.map(meldung.getVerlauf().getHistologie(), subject, meldung.getMeldungID()));
      }

      if (meldung.getOP() != null && meldung.getOP().getHistologie() != null) {
        list.add(sut.map(meldung.getOP().getHistologie(), subject, meldung.getMeldungID()));
      }

      if (meldung.getPathologie() != null && meldung.getPathologie().getHistologie() != null) {
        list.add(sut.map(meldung.getPathologie().getHistologie(), subject, meldung.getMeldungID()));
      }
    }

    verifyAll(list, sourceFile);
  }
}
