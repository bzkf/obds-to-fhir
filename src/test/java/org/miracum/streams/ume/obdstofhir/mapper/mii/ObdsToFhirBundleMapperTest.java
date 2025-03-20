package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
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
    })
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
    "Testperson_Cervixinsitu.xml"
  })
  void map_withGivenObds_shouldCreateBundleMatchingSnapshot(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    final var bundles = sut.map(obds);

    verifyAll(bundles, sourceFile);
  }
}
