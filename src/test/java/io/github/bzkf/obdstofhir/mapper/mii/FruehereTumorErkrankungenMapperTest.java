package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import java.util.ArrayList;
import org.hl7.fhir.r4.model.Condition;
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
class FruehereTumorerkrankungenMapperTest extends MapperTest {
  private static FruehereTumorerkrankungenMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new FruehereTumorerkrankungenMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({
    "Testpatient_1.xml",
    "Testpatient_Diagnose.xml",
    "Hauptpaket_Testperson_Cervixinsitu.xml",
    "Testpatient_Mamma.xml"
  })
  void map_withGivenObds_shouldCreateValidConditionResources(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var subject = new Reference("Patient/any");
    var diagnose = new Reference("Condition/Primärdiagnose");

    final var list = new ArrayList<Condition>();
    for (var meldung : obdsPatient.getMengeMeldung().getMeldung()) {
      if (meldung.getDiagnose() != null
          && meldung.getDiagnose().getMengeFruehereTumorerkrankung() != null) {
        var conditions =
            sut.map(
                meldung.getDiagnose().getMengeFruehereTumorerkrankung(),
                subject,
                diagnose,
                new Identifier().setSystem("any").setValue("pd-id-1"),
                obds.getMeldedatum());
        list.addAll(conditions);
      }
    }

    verifyAll(list, sourceFile);
  }
}
