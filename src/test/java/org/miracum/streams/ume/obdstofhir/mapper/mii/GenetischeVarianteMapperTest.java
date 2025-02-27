package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import java.util.ArrayList;
import java.util.TimeZone;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class GenetischeVarianteMapperTest extends MapperTest {
  private static GenetischeVarianteMapper sut;

  private static final Logger LOG = LoggerFactory.getLogger(GenetischeVarianteMapperTest.class);

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
    sut = new GenetischeVarianteMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_Patho.xml"})
  void map_withGivenMengeGenetik_shouldCreateValidObservation(String sourceFile)
      throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    LOG.info("Loaded resource: {}", resource.getPath());

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    assertThat(obdsPatient).isNotNull();

    var subject = new Reference("Patient/any");
    var condition = new Reference("Condition/any");

    var resultResources = new ArrayList<Observation>();

    var meldungList =
        obdsPatient
            .getMengeMeldung()
            .getMeldung();
    assertThat(meldungList).isNotNull();

    for (var meldung : meldungList) {
      if (meldung.getDiagnose() != null
          && meldung.getDiagnose().getMengeGenetik() != null
          && meldung.getDiagnose().getMengeGenetik().getGenetischeVariante() != null) {
        var observations = sut.map(meldung.getDiagnose(), subject, condition);
        if (observations != null) {
          resultResources.addAll(observations);
        }
      }

      if (meldung.getVerlauf() != null
          && meldung.getVerlauf().getMengeGenetik() != null
          && meldung.getVerlauf().getMengeGenetik().getGenetischeVariante() != null) {
        var observations = sut.map(meldung.getVerlauf(), subject, condition);
        if (observations != null) {
          resultResources.addAll(observations);
        }
      }

      if (meldung.getPathologie() != null
          && meldung.getPathologie().getMengeGenetik() != null
          && meldung.getPathologie().getMengeGenetik().getGenetischeVariante() != null) {
        var observations = sut.map(meldung.getPathologie(), subject, condition);
        if (observations != null) {
          resultResources.addAll(observations);
        }
      }

      if (meldung.getOP() != null
          && meldung.getOP().getMengeGenetik() != null
          && meldung.getOP().getMengeGenetik().getGenetischeVariante() != null) {
        var observations = sut.map(meldung.getOP(), subject, condition);
        if (observations != null) {
          resultResources.addAll(observations);
        }
      }

      LOG.info("Generated {} observations", resultResources.size());
    }

    assertThat(resultResources).isNotEmpty();
    verifyAll(resultResources, sourceFile);
  }
}
