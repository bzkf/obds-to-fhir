package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    final var xmlMapper =
        XmlMapper.builder()
            .defaultUseWrapper(false)
            .addModule(new JakartaXmlBindAnnotationModule())
            .addModule(new Jdk8Module())
            // added because the Testpatient_*.xml contain the `xsi:schemaLocation` attribute which
            // isn't code-generated
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    final var obds = xmlMapper.readValue(resource.openStream(), OBDS.class);
    final List<OBDS> obdsList = new ArrayList<>();
    obdsList.add(obds);

    final var bundles = sut.map(obdsList);

    verifyAll(bundles, sourceFile);
  }
}
