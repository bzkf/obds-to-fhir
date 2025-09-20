package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import com.spun.util.introspection.Caller;
import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {
      FhirProperties.class,
      ObdsToFhirBundleMapper.class,
      PatientMapper.class,
      ConditionMapper.class,
      SystemischeTherapieProcedureMapper.class,
      SystemischeTherapieMedicationStatementMapper.class,
      StrahlentherapieMapper.class,
      TodMapper.class,
      LeistungszustandMapper.class,
      OperationMapper.class,
      ResidualstatusMapper.class,
      HistologiebefundMapper.class,
      FernmetastasenMapper.class,
      GradingObservationMapper.class,
      LymphknotenuntersuchungMapper.class,
      SpecimenMapper.class,
      VerlaufshistologieObservationMapper.class,
      StudienteilnahmeObservationMapper.class,
      VerlaufObservationMapper.class,
      GenetischeVarianteMapper.class,
      TumorkonferenzMapper.class,
      TNMMapper.class,
      GleasonScoreMapper.class,
      WeitereKlassifikationMapper.class,
      ErstdiagnoseEvidenzListMapper.class,
    },
    properties = {"fhir.mappings.modul.prostata.enabled=true"})
@EnableConfigurationProperties
class ObdsToFhirBundleMapperTest extends MapperTest {
  private static ObdsToFhirBundleMapper sut;

  @BeforeAll
  static void beforeAll(@Autowired ObdsToFhirBundleMapper bundleMapper) {
    sut = bundleMapper;
  }

  @ParameterizedTest
  @CsvSource({
    "Testpatient_1.xml",
    "Testpatient_2.xml",
    "Testpatient_3.xml",
    "Testpatient_CLL.xml",
    "Testpatient_leer.xml",
    "Testpatient_Mamma.xml",
    "Testpatient_Prostata.xml",
    "Testpatient_Patho.xml",
    "Testpatient_Patho2.xml",
    "Testpatient_Rektum.xml",
    "Testpatientin_Cervix.xml",
    "Testperson_CervixC53.xml",
    "Testperson_Cervixinsitu.xml",
    "Priopatient_3.xml",
    "GroupSequence01.xml",
    "Folgepaket_Testpatient_Mamma.xml"
  })
  void map_withGivenObds_shouldCreateBundleMatchingSnapshot(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    final var bundles = sut.map(obds);

    var caller = Caller.get(0);
    var methodName = caller.getMethodName();
    var className = this.getClass().getSimpleName();

    var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);

    for (int i = 0; i < bundles.size(); i++) {
      var fhirJson = fhirParser.encodeResourceToString(bundles.get(i));
      Approvals.verify(
          fhirJson,
          Approvals.NAMES
              .withParameters("")
              .forFile()
              .withBaseName(String.format("%s/%s.%s.%d", className, methodName, sourceFile, i))
              .forFile()
              .withExtension(".fhir.json"));
    }
  }
}
