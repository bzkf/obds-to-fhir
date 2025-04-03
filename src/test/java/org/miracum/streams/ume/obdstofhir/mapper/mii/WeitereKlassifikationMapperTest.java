package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import java.util.ArrayList;
import org.hl7.fhir.r4.model.Observation;
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
class WeitereKlassifikationMapperTest extends MapperTest {
  private static WeitereKlassifikationMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new WeitereKlassifikationMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_2.xml", "Testpatient_3.xml"})
  void map_withGivenObds_shouldCreateValidObservation(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var subject = new Reference("Patient/any");
    var diagnose = new Reference("Condition/Primärdiagnose");

    var list = new ArrayList<Observation>();

    for (var meldung : obdsPatient.getMengeMeldung().getMeldung()) {
      if (meldung.getDiagnose() != null
          && meldung.getDiagnose().getMengeWeitereKlassifikation() != null) {
        list.addAll(
            sut.map(
                meldung.getDiagnose().getMengeWeitereKlassifikation(),
                meldung.getMeldungID(),
                subject,
                diagnose));
      }

      if (meldung.getVerlauf() != null
          && meldung.getVerlauf().getMengeWeitereKlassifikation() != null) {
        list.addAll(
            sut.map(
                meldung.getVerlauf().getMengeWeitereKlassifikation(),
                meldung.getMeldungID(),
                subject,
                diagnose));
      }

      if (meldung.getPathologie() != null
          && meldung.getPathologie().getMengeWeitereKlassifikation() != null) {
        list.addAll(
            sut.map(
                meldung.getPathologie().getMengeWeitereKlassifikation(),
                meldung.getMeldungID(),
                subject,
                diagnose));
      }
    }

    verifyAll(list, sourceFile);
  }
}
