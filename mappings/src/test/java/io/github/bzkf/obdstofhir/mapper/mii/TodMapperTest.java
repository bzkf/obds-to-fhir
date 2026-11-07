package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.AllgemeinICDTyp;
import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.TodTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class TodMapperTest extends MapperTest {
  private static TodMapper tm;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    tm = new TodMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_Mamma.xml"})
  void map_withGivenObds_shouldCreateValidObservation(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    var subject = new Reference("Patient/any");
    var condition = new Reference("Condition/any");
    subject.setIdentifier(new Identifier().setValue("123456"));
    var meldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getTod() != null)
            .findFirst()
            .get();
    var tMeldung = meldung.getTod();
    var tumorId = "112233";

    var observations = tm.map(tMeldung, subject, condition, tumorId, false);

    verifyAll(observations, sourceFile);
  }

  @Test
  void map_withIcdGm_shouldOnlyCodeItAsGm() {
    var value = mapTodesursache("C50.9", "10 2021 GM");

    assertThat(value.getCoding()).hasSize(1);
    var gm = value.getCodingFirstRep();
    assertThat(gm.getSystem()).isEqualTo("http://fhir.de/CodeSystem/bfarm/icd-10-gm");
    assertThat(gm.getCode()).isEqualTo("C50.9");
    assertThat(gm.getVersion()).isEqualTo("2021");
    assertThat(value.hasText()).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"10 2019 WHO", "Sonstige"})
  void map_withOtherIcdCatalog_shouldNotCodeItAsGmAndKeepTheCodeAsText(String version) {
    var value = mapTodesursache("C50.9", version);

    // the profile doesn't allow a coding of another system
    assertThat(value.getCoding()).hasSize(1);
    var gm = value.getCodingFirstRep();
    assertThat(gm.getSystem()).isEqualTo("http://fhir.de/CodeSystem/bfarm/icd-10-gm");
    assertThat(gm.getCode()).isNull();
    assertThat(value.getText()).isEqualTo("C50.9");
  }

  private static CodeableConcept mapTodesursache(String code, String version) {
    var icd = new AllgemeinICDTyp();
    icd.setCode(code);
    icd.setVersion(version);
    var menge = new TodTyp.MengeTodesursachen();
    menge.getTodesursacheICD().add(icd);
    var tod = new TodTyp();
    tod.setMengeTodesursachen(menge);

    var patient = new Reference("Patient/any");
    patient.setIdentifier(new Identifier().setValue("123456"));

    return tm.map(tod, patient, new Reference("Condition/any"), "112233", false)
        .getFirst()
        .getValueCodeableConcept();
  }
}
