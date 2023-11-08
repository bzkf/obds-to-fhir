package org.miracum.streams.ume.obdstofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleUtil;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import org.approvaltests.Approvals;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.*;
import org.miracum.streams.ume.obdstofhir.model.Tupel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {
      FhirProperties.class,
      ObdsConditionMapper.class,
      ObdsMedicationStatementMapper.class,
      ObdsObservationMapper.class,
      ObdsProcedureMapper.class,
      ObdsPatientMapper.class,
      ObdsConditionMapper.class
    })
@EnableConfigurationProperties()
public class ObdsMedicationStatementProcessorTest extends ObdsProcessorTest {

  private static final Logger log =
      LoggerFactory.getLogger(ObdsMedicationStatementProcessorTest.class);

  private final FhirProperties fhirProps;
  private final ObdsMedicationStatementMapper onkoMedicationStatementMapper;
  private final ObdsObservationMapper onkoObservationMapper;
  private final ObdsProcedureMapper onkoProcedureMapper;
  private final ObdsPatientMapper onkoPatientMapper;
  private final ObdsConditionMapper onkoConditionMapper;
  private final FhirContext ctx = FhirContext.forR4();

  @Autowired
  public ObdsMedicationStatementProcessorTest(
      FhirProperties fhirProps,
      ObdsMedicationStatementMapper onkoMedicationStatementMapper,
      ObdsObservationMapper onkoObservationMapper,
      ObdsProcedureMapper onkoProcedureMapper,
      ObdsPatientMapper onkoPatientMapper,
      ObdsConditionMapper onkoConditionMapper) {
    this.fhirProps = fhirProps;
    this.onkoMedicationStatementMapper = onkoMedicationStatementMapper;
    this.onkoObservationMapper = onkoObservationMapper;
    this.onkoProcedureMapper = onkoProcedureMapper;
    this.onkoPatientMapper = onkoPatientMapper;
    this.onkoConditionMapper = onkoConditionMapper;
  }

  private static Stream<Arguments> generateTestData() {
    return Stream.of(
        Arguments.of(
            List.of(new Tupel<>("008_Pat3_Tumor1_Behandlungsende_SYST.xml", 1)),
            5,
            "CI",
            "COMPLETED",
            new Tupel<>("2021-05-22", "2021-07-20"),
            "O",
            "K"),
        Arguments.of(
            List.of(
                new Tupel<>("001_1.Pat_2Tumoren_TumorID_1_Diagnose.xml", 1),
                new Tupel<>("002_1.Pat_2Tumoren_TumorID_2_Diagnose.xml", 1)),
            0,
            null,
            "",
            new Tupel<>("", ""),
            "",
            ""));
  }

  @ParameterizedTest
  @MethodSource("generateTestData")
  void mapMedicationStatement_withGivenAdtXml(
      List<Tupel<String, Integer>> xmlFileNames,
      int expectedMedStCount,
      String expectedCategory,
      String expectedStatus,
      Tupel<String, String> expectedPeriod,
      String expectedStellungOP,
      String expectedIntention)
      throws IOException {

    var meldungExportList = buildMeldungExportList(xmlFileNames);

    ObdsProcessor medicationStatementProcessor =
        new ObdsProcessor(
            fhirProps,
            onkoMedicationStatementMapper,
            onkoObservationMapper,
            onkoProcedureMapper,
            onkoPatientMapper,
            onkoConditionMapper);

    var resultBundle =
        medicationStatementProcessor
            .getOnkoToMedicationStatementBundleMapper()
            .apply(meldungExportList);

    if (expectedMedStCount == 0) {
      assertThat(resultBundle).isNull();
    } else {
      var medicationStatementList =
          BundleUtil.toListOfResourcesOfType(ctx, resultBundle, MedicationStatement.class);

      assertThat(medicationStatementList).hasSize(expectedMedStCount);

      int partOfCount = 0;
      String partOfId = "";
      List<String> partOfReferences = new ArrayList<>();
      for (var medSt : medicationStatementList) {

        assertThat(medSt.getCategory().getCoding().get(0).getCode()).isEqualTo(expectedCategory);

        assertThat(medSt.getStatus().toString()).isEqualTo(expectedStatus);

        assertThat(medSt.getEffectivePeriod().getStartElement().getValueAsString())
            .isEqualTo(expectedPeriod.getFirst());
        assertThat(medSt.getEffectivePeriod().getEndElement().getValueAsString())
            .isEqualTo(expectedPeriod.getSecond());

        var stellungOPCc =
            (CodeableConcept)
                medSt.getExtensionByUrl(fhirProps.getExtensions().getStellungOP()).getValue();
        var intentionCC =
            (CodeableConcept)
                medSt.getExtensionByUrl(fhirProps.getExtensions().getSystIntention()).getValue();

        assertThat(stellungOPCc.getCoding().get(0).getCode()).isEqualTo(expectedStellungOP);
        assertThat(intentionCC.getCoding().get(0).getCode()).isEqualTo(expectedIntention);

        if (medSt.getPartOf().isEmpty()) {
          partOfId = medSt.getId();
          partOfCount++;
        } else {
          assertThat(medSt.getPartOf()).hasSize(1);
          partOfReferences.add(medSt.getPartOf().get(0).getReference());
        }
      }

      assertThat(partOfCount).isEqualTo(1);
      String finalPartOfId = partOfId;
      assertThat(partOfReferences).allSatisfy(ref -> ref.equals(finalPartOfId));

      var fhirJson = fhirParser.encodeResourceToString(resultBundle);
      Approvals.verify(
          fhirJson,
          Approvals.NAMES
              .withParameters(xmlFileNames.stream().map(t -> t.getFirst()).toArray(String[]::new))
              .forFile()
              .withExtension(".fhir.json"));
    }
  }
}
