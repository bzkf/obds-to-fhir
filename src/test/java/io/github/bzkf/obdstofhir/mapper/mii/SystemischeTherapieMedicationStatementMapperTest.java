package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.SubstanzToAtcMapper;
import java.io.IOException;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class, SubstanzToAtcMapper.class})
@EnableConfigurationProperties
class SystemischeTherapieMedicationStatementMapperTest extends MapperTest {
  private static SystemischeTherapieMedicationStatementMapper sut;

  @BeforeAll
  static void beforeAll(
      @Autowired FhirProperties fhirProps, @Autowired SubstanzToAtcMapper substanzToAtcMapper) {
    sut = new SystemischeTherapieMedicationStatementMapper(fhirProps, substanzToAtcMapper);
  }

  @ParameterizedTest
  @CsvSource({
    "Testpatient_1.xml",
    "Testpatient_2.xml",
    "Testpatient_3.xml",
    "Testpatient_Duplicate_OPS_ICDO_Substanzen.xml",
    "Test_SysT_0.xml",
    "Test_SysT_1.xml",
    "Test_SysT_2.xml",
  })
  void map_withGivenObds_shouldCreateValidMedicationStatement(String sourceFile)
      throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    var patient = new Reference("Patient/any");
    var procedure = new Reference("Procedure/any");
    var systMeldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getSYST() != null)
            .findFirst()
            .get();
    var list = sut.map(systMeldung.getSYST(), patient, procedure);

    verifyAll(list, sourceFile);
  }
}
