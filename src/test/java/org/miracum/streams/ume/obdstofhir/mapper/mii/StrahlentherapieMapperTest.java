package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import java.util.ArrayList;
import org.hl7.fhir.r4.model.Procedure;
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
class StrahlentherapieMapperTest extends MapperTest {
  private static StrahlentherapieMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new StrahlentherapieMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({
    "Testpatient_1.xml",
    "Testpatient_2.xml",
    "Testpatient_3.xml",
    "Testpatient_Mamma.xml",
    "Test_ST_Invalid_Applikationsart.xml"
  })
  void map_withGivenObds_shouldCreateValidProcedure(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    var subject = new Reference("Patient/any");
    var condition = new Reference("Condition/any");
    var list = new ArrayList<Procedure>();

    for (var meldung : obdsPatient.getMengeMeldung().getMeldung()) {
      if (meldung.getST() != null) {
        list.add(sut.map(meldung.getST(), subject, condition));
      }
    }

    verifyAll(list, sourceFile);
  }
}
