package io.github.bzkf.obdstofhir.mapper.mii;

import static io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper.convertObdsDatumToDateTimeType;
import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
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
class ModulMammaMapperTest extends MapperTest {
  private static ModulMammaMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new ModulMammaMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({
    "Testpatient_Mamma.xml",
    "Folgepaket_Testpatient_Mamma.xml",
    "Testpatient_1.xml",
    "Testpatient_Patho.xml",
  })
  void map_withGivenObds_shouldCreateValidObservations(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var subject = new Reference("Patient/any");
    var diagnose = new Reference("Condition/Primärdiagnose");

    final var list = new ArrayList<Observation>();
    for (var meldung : obdsPatient.getMengeMeldung().getMeldung()) {
      if (meldung.getPathologie() != null && meldung.getPathologie().getModulMamma() != null) {
        var effective =
            Optional.ofNullable(meldung.getPathologie().getHistologie())
                .flatMap(h -> convertObdsDatumToDateTimeType(h.getTumorHistologiedatum()))
                .orElse(null);
        list.addAll(
            sut.map(
                meldung.getPathologie().getModulMamma(),
                meldung.getMeldungID(),
                subject,
                diagnose,
                effective));
      }
      if (meldung.getDiagnose() != null && meldung.getDiagnose().getModulMamma() != null) {
        var effective =
            convertObdsDatumToDateTimeType(meldung.getTumorzuordnung().getDiagnosedatum())
                .orElse(null);
        list.addAll(
            sut.map(
                meldung.getDiagnose().getModulMamma(),
                meldung.getMeldungID(),
                subject,
                diagnose,
                effective));
      }
      if (meldung.getOP() != null && meldung.getOP().getModulMamma() != null) {
        var effective = convertObdsDatumToDateTimeType(meldung.getOP().getDatum()).orElse(null);
        list.addAll(
            sut.map(
                meldung.getOP().getModulMamma(),
                meldung.getMeldungID(),
                subject,
                diagnose,
                effective));
      }
      if (meldung.getVerlauf() != null && meldung.getVerlauf().getModulMamma() != null) {
        var effective =
            convertObdsDatumToDateTimeType(meldung.getVerlauf().getUntersuchungsdatumVerlauf())
                .orElse(null);
        list.addAll(
            sut.map(
                meldung.getVerlauf().getModulMamma(),
                meldung.getMeldungID(),
                subject,
                diagnose,
                effective));
      }
    }

    verifyAll(list, sourceFile);
  }
}
