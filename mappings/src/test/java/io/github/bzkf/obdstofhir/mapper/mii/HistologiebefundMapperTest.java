package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import java.util.ArrayList;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class HistologiebefundMapperTest extends MapperTest {
  private static HistologiebefundMapper sut;

  @BeforeAll
  static void beforeAll(@Autowired FhirProperties fhirProperties) {
    sut = new HistologiebefundMapper(fhirProperties);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_Patho.xml", "Testpatient_Patho2.xml", "Testpatient_1.xml"})
  void map_withGivenObds_shouldCreateValidDiagnosticReport(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    var subject = new Reference("Patient/any");
    var tumorkonferenz = new Reference("CarePlan/Tumorkonferenz");
    var specimen = new Reference("Specimen/Onko");

    var list = new ArrayList<DiagnosticReport>();

    for (var meldung : obdsPatient.getMengeMeldung().getMeldung()) {
      if (meldung.getPathologie() != null) {
        list.add(
            sut.map(
                meldung.getPathologie(),
                meldung.getMeldungID(),
                subject,
                tumorkonferenz,
                specimen));
      }
    }

    verifyAll(list, sourceFile);
  }
}
