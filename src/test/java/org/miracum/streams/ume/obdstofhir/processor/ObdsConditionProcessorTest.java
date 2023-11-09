package org.miracum.streams.ume.obdstofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.util.BundleUtil;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.approvaltests.Approvals;
import org.hl7.fhir.r4.model.Condition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.*;
import org.miracum.streams.ume.obdstofhir.model.Tupel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

class ObdsConditionProcessorTest extends ObdsProcessorTest {

  private static final Logger log = LoggerFactory.getLogger(ObdsConditionProcessorTest.class);

  private final FhirProperties fhirProps;
  private final ObdsMedicationStatementMapper onkoMedicationStatementMapper;
  private final ObdsObservationMapper onkoObservationMapper;
  private final ObdsProcedureMapper onkoProcedureMapper;
  private final ObdsPatientMapper onkoPatientMapper;
  private final ObdsConditionMapper onkoConditionMapper;

  @Autowired
  public ObdsConditionProcessorTest(
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
            List.of(new Tupel<>("001_1.Pat_2Tumoren_TumorID_1_Diagnose.xml", 1)),
            1,
            "C72.0",
            "2021",
            "T",
            "396360001",
            2,
            1,
            5,
            "2021-03-18"),
        Arguments.of(
            List.of(new Tupel<>("002_1.Pat_2Tumoren_TumorID_2_Diagnose.xml", 1)),
            1,
            "C41.01",
            "2021",
            "T",
            "396360001",
            0,
            2,
            0,
            "2021-02-08"),
        Arguments.of(
            List.of(
                new Tupel<>("001_1.Pat_2Tumoren_TumorID_1_Diagnose.xml", 1),
                new Tupel<>("003_Pat1_Tumor1_Therapie1_Behandlungsende_OP.xml", 1),
                new Tupel<>(
                    "009_Pat1_Tumor1_Statusaenderung_Fernmeta_3x-überschreibt2xFernMetaausDIAGNOSE.xml",
                    1)),
            1,
            "C72.0",
            "2021",
            "T",
            "396360001",
            2,
            1,
            6,
            "2021-03-18"));
  }

  @ParameterizedTest
  @MethodSource("generateTestData")
  void mapCondition_withGivenAdtXml(
      List<Tupel<String, Integer>> xmlFileNames,
      int expectedConCount,
      String expectedIcdCode,
      String expectedIcdVersion,
      String expectedBodySiteAdtCode,
      String expectedBodySiteSnomedCode,
      int expectedStageCount,
      int expectedEvidenceCount,
      int expectedExtCount,
      String expectedOnsetDate)
      throws IOException {

    var meldungExportList = buildMeldungExportList(xmlFileNames);

    ObdsProcessor onkoProcessor =
        new ObdsProcessor(
            fhirProps,
            onkoMedicationStatementMapper,
            onkoObservationMapper,
            onkoProcedureMapper,
            onkoPatientMapper,
            onkoConditionMapper);

    var observResultBundle =
        onkoProcessor.getOnkoToObservationBundleMapper().apply(meldungExportList);

    var resultBundle =
        onkoProcessor
            .getOnkoToConditionBundleMapper()
            .apply(Pair.of(meldungExportList, observResultBundle));

    if (expectedConCount == 0) {
      assertThat(resultBundle).isNull();
    } else {
      var conditionList = BundleUtil.toListOfResourcesOfType(ctx, resultBundle, Condition.class);

      assertThat(conditionList).hasSize(expectedConCount);

      assertThat(conditionList.get(0).getCode().getCoding().get(0).getCode())
          .isEqualTo(expectedIcdCode);
      assertThat(conditionList.get(0).getCode().getCoding().get(0).getVersion())
          .isEqualTo(expectedIcdVersion);

      assertThat(conditionList.get(0).getBodySite().get(0).getCoding().get(0).getCode())
          .isEqualTo(expectedBodySiteAdtCode);
      assertThat(conditionList.get(0).getBodySite().get(0).getCoding().get(1).getCode())
          .isEqualTo(expectedBodySiteSnomedCode);

      assertThat(conditionList.get(0).getStage()).hasSize(expectedStageCount);

      assertThat(conditionList.get(0).getEvidence()).hasSize(expectedEvidenceCount);

      assertThat(conditionList.get(0).getExtension()).hasSize(expectedExtCount);

      assertThat(conditionList.get(0).getOnsetDateTimeType().getValueAsString())
          .isEqualTo(expectedOnsetDate);

      var fhirJson = fhirParser.encodeResourceToString(resultBundle);
      Approvals.verify(
          fhirJson,
          Approvals.NAMES
              .withParameters(
                  xmlFileNames.stream()
                      .map(t -> t.getFirst().substring(0, 5))
                      .toArray(String[]::new))
              .forFile()
              .withExtension(".fhir.json"));
    }
  }
}
