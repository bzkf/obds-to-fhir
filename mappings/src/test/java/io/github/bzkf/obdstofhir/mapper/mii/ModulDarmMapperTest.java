package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.ModulDarmTyp;
import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.RASMutationTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
    "Testpatient_Patho.xml",
  })
  void map_withGivenObds_shouldCreateValidResources(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var subject = new Reference("Patient/any");
    var diagnose = new Reference("Condition/Primärdiagnose");
    var op = new Reference("Procedure/any");
    var specimen = new Reference("Specimen/any");

    final var list = new ArrayList<Resource>();
    for (var meldung : obdsPatient.getMengeMeldung().getMeldung()) {
      if (meldung.getPathologie() != null && meldung.getPathologie().getModulDarm() != null) {
        var pathologie = meldung.getPathologie();
        var effective =
            Optional.ofNullable(pathologie.getHistologie())
                .flatMap(
                    h ->
                        ObdsToFhirMapper.convertObdsDatumToDateTimeType(
                            h.getTumorHistologiedatum()))
                .orElse(null);
        var resources =
            sut.map(
                pathologie.getModulDarm(),
                meldung.getMeldungID(),
                subject,
                diagnose,
                effective,
                null,
                specimen);
        list.addAll(resources);
      }
      if (meldung.getDiagnose() != null && meldung.getDiagnose().getModulDarm() != null) {
        var effective =
            ObdsToFhirMapper.convertObdsDatumToDateTimeType(
                    meldung.getTumorzuordnung().getDiagnosedatum())
                .orElse(null);
        var resources =
            sut.map(
                meldung.getDiagnose().getModulDarm(),
                meldung.getMeldungID(),
                subject,
                diagnose,
                effective,
                null,
                specimen);
        list.addAll(resources);
      }
      if (meldung.getOP() != null && meldung.getOP().getModulDarm() != null) {
        var resources =
            sut.map(
                meldung.getOP().getModulDarm(),
                meldung.getMeldungID(),
                subject,
                diagnose,
                ObdsToFhirMapper.convertObdsDatumToDateTimeType(meldung.getOP().getDatum())
                    .orElse(null),
                op,
                specimen);
        list.addAll(resources);
      }
      if (meldung.getVerlauf() != null && meldung.getVerlauf().getModulDarm() != null) {
        var resources =
            sut.map(
                meldung.getVerlauf().getModulDarm(),
                meldung.getMeldungID(),
                subject,
                diagnose,
                ObdsToFhirMapper.convertObdsDatumToDateTimeType(
                        meldung.getVerlauf().getUntersuchungsdatumVerlauf())
                    .orElse(null),
                null,
                specimen);
        list.addAll(resources);
      }
    }

    verifyAll(list, sourceFile);
  }

  @Test
  void map_withRasMutationNichtUntersucht_shouldSetDataAbsentReasonInsteadOfInterpretation() {
    var modulDarm = new ModulDarmTyp();
    modulDarm.setRASMutation(RASMutationTyp.N);

    var resources =
        sut.map(
            modulDarm,
            "meldung-1",
            new Reference("Patient/any"),
            new Reference("Condition/any"),
            null,
            null,
            null);

    assertThat(resources).singleElement().isInstanceOf(Observation.class);
    var observation = (Observation) resources.getFirst();
    assertThat(observation.hasInterpretation()).isFalse();
    assertThat(observation.getDataAbsentReason().getCodingFirstRep().getCode())
        .isEqualTo("not-performed");
  }
}
