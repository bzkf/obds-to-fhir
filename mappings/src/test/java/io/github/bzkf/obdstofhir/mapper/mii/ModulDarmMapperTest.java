package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import java.util.ArrayList;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class ModulDarmMapperTest extends MapperTest {
  private static ModulDarmMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new ModulDarmMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({
    "Testpatient_Rektum.xml",
    "Folgepaket_Testpatient_Rektum.xml",
    "Testpatient_1.xml",
  })
  void map_withGivenObds_shouldCreateValidResources(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var subject = new Reference("Patient/any");
    var diagnose = new Reference("Condition/Primärdiagnose");
    var op = new Reference("Procedure/any");

    final var list = new ArrayList<Resource>();
    for (var meldung : obdsPatient.getMengeMeldung().getMeldung()) {
      if (meldung.getPathologie() != null && meldung.getPathologie().getModulDarm() != null) {
        var resources =
            sut.map(
                meldung.getPathologie().getModulDarm(), meldung.getMeldungID(), subject, diagnose);
        list.addAll(resources);
      }
      if (meldung.getDiagnose() != null && meldung.getDiagnose().getModulDarm() != null) {
        var diagnosedatum =
            meldung.getTumorzuordnung() != null
                    && meldung.getTumorzuordnung().getDiagnosedatum() != null
                ? meldung.getTumorzuordnung().getDiagnosedatum().getValue()
                : null;
        var resources =
            sut.map(
                meldung.getDiagnose().getModulDarm(),
                meldung.getMeldungID(),
                subject,
                diagnose,
                diagnosedatum);
        list.addAll(resources);
      }
      if (meldung.getOP() != null && meldung.getOP().getModulDarm() != null) {
        var resources =
            sut.map(
                meldung.getOP().getModulDarm(),
                meldung.getMeldungID(),
                subject,
                diagnose,
                meldung.getOP().getDatum(),
                op);
        list.addAll(resources);
      }
      if (meldung.getVerlauf() != null && meldung.getVerlauf().getModulDarm() != null) {
        var resources =
            sut.map(
                meldung.getVerlauf().getModulDarm(),
                meldung.getMeldungID(),
                subject,
                diagnose,
                meldung.getVerlauf().getUntersuchungsdatumVerlauf());
        list.addAll(resources);
      }
    }

    verifyAll(list, sourceFile);
  }
}
