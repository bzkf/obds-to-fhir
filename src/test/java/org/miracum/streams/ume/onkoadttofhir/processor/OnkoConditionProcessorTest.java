package org.miracum.streams.ume.onkoadttofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hl7.fhir.r4.model.Condition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.mapper.*;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExportList;
import org.miracum.streams.ume.onkoadttofhir.model.Tupel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ResourceUtils;

@SpringBootTest(
    classes = {
      FhirProperties.class,
      OnkoConditionMapper.class,
      OnkoMedicationStatementMapper.class,
      OnkoObservationMapper.class,
      OnkoProcedureMapper.class,
      OnkoPatientMapper.class,
      OnkoConditionMapper.class
    })
@EnableConfigurationProperties()
public class OnkoConditionProcessorTest extends OnkoProcessorTest {

  private static final Logger log = LoggerFactory.getLogger(OnkoConditionProcessorTest.class);

  private final FhirProperties fhirProps;
  private final OnkoMedicationStatementMapper onkoMedicationStatementMapper;
  private final OnkoObservationMapper onkoObservationMapper;
  private final OnkoProcedureMapper onkoProcedureMapper;
  private final OnkoPatientMapper onkoPatientMapper;
  private final OnkoConditionMapper onkoConditionMapper;
  private final FhirContext ctx = FhirContext.forR4();

  @Autowired
  public OnkoConditionProcessorTest(
      FhirProperties fhirProps,
      OnkoMedicationStatementMapper onkoMedicationStatementMapper,
      OnkoObservationMapper onkoObservationMapper,
      OnkoProcedureMapper onkoProcedureMapper,
      OnkoPatientMapper onkoPatientMapper,
      OnkoConditionMapper onkoConditionMapper) {
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
            Arrays.asList(new Tupel<>("001_1.Pat_2Tumoren_TumorID_1_Diagnose.xml", 1)),
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
            Arrays.asList(new Tupel<>("002_1.Pat_2Tumoren_TumorID_2_Diagnose.xml", 1)),
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
            Arrays.asList(
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

    MeldungExportList meldungExportList = new MeldungExportList();

    int payloadId = 1;

    for (var xmlTupel : xmlFileNames) {
      File xmlFile = ResourceUtils.getFile("classpath:" + xmlTupel.getFirst());
      String xmlContent = new String(Files.readAllBytes(xmlFile.toPath()));

      var meldungsId = StringUtils.substringBetween(xmlContent, "Meldung_ID=\"", "\" Melder_ID");
      var melderId = StringUtils.substringBetween(xmlContent, "Melder_ID=\"", "\">");
      var patId = StringUtils.substringBetween(xmlContent, "Patient_ID=\"", "\">");

      Map<String, Object> payloadOnkoRessource = new HashMap<>();
      payloadOnkoRessource.put("ID", payloadId);
      payloadOnkoRessource.put("REFERENZ_NUMMER", patId);
      payloadOnkoRessource.put("LKR_MELDUNG", Integer.parseInt(meldungsId.replace(melderId, "")));
      payloadOnkoRessource.put("VERSIONSNUMMER", xmlTupel.getSecond());
      payloadOnkoRessource.put("XML_DATEN", xmlContent);

      MeldungExport meldungExport = new MeldungExport();
      meldungExport.getPayload(payloadOnkoRessource);
      meldungExportList.addElement(meldungExport);

      payloadId++;
    }

    OnkoProcessor onkoProcessor =
        new OnkoProcessor(
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

      assertThat(isValid(resultBundle)).isTrue();
    }
  }
}
