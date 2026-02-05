package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import com.github.difflib.text.DiffRowGenerator;
import com.spun.util.introspection.Caller;
import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.ProfileTestConfig;
import io.github.bzkf.obdstofhir.SubstanzToAtcMapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.StringJoiner;
import org.approvaltests.Approvals;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

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
      ModulProstataMapper.class,
      WeitereKlassifikationMapper.class,
      ErstdiagnoseEvidenzListMapper.class,
      NebenwirkungMapper.class,
      SubstanzToAtcMapper.class,
      ProfileTestConfig.class,
      FruehereTumorerkrankungenMapper.class,
    })
@EnableConfigurationProperties
@Configuration
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
    "Folgepaket_Testpatient_Mamma.xml",
    "obdsv3_st_strahlenart.xml",
    "obdsv3_missing_meldungen.xml",
    "Folgepaket_Testpatientin_Cervix.xml"
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

  @Test
  void shouldDetectDuplicateResources() {
    var p1 = new Patient();
    p1.setId("Patient/1");
    p1.setActive(false);
    var p2 = new Patient();
    p2.setId("Patient/1");
    p2.setActive(true);
    p2.addIdentifier().setSystem("example").setValue("123");

    var parser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
    var existing = Arrays.asList(parser.encodeResourceToString(p1).split("\n"));
    var updated = Arrays.asList(parser.encodeResourceToString(p2).split("\n"));

    var generator =
        DiffRowGenerator.create()
            .showInlineDiffs(true)
            .inlineDiffByWord(true)
            .oldTag(f -> "~")
            .newTag(f -> "**")
            .build();

    var rows = generator.generateDiffRows(existing, updated);

    var sj = new StringJoiner("\n");
    sj.add("|original|new|");
    sj.add("|--------|---|");
    for (var row : rows) {
      sj.add("|" + row.getOldLine() + "|" + row.getNewLine() + "|");
    }

    assertThat(sj.toString())
        .hasToString(
"""
|original|new|
|--------|---|
|{|{|
|  "resourceType": "Patient",|  "resourceType": "Patient",|
|  "id": "1",|  "id": "1",|
|  ~"active"~: ~false~|  **"identifier"**: **[ {**|
||**    "system": "example",**|
||**    "value": "123"**|
||**  } ],**|
||**  "active": true**|
|}|}|""");
  }
}
