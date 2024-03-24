package org.miracum.streams.ume.obdstofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.util.BundleUtil;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import org.approvaltests.Approvals;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.*;
import org.miracum.streams.ume.obdstofhir.model.Tupel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

class ObdsObservationProcessorTest extends ObdsProcessorTest {

  private static final Logger log = LoggerFactory.getLogger(ObdsObservationProcessorTest.class);

  private final FhirProperties fhirProps;
  private final ObdsMedicationStatementMapper onkoMedicationStatementMapper;
  private final ObdsObservationMapper onkoObservationMapper;
  private final ObdsProcedureMapper onkoProcedureMapper;
  private final ObdsPatientMapper onkoPatientMapper;
  private final ObdsConditionMapper onkoConditionMapper;

  @Autowired
  public ObdsObservationProcessorTest(
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
            9,
            "9391/3",
            "2021-03-18",
            "T",
            5,
            List.of("0", "1", "0"),
            List.of("", "2a", ""),
            null,
            null),
        Arguments.of(
            List.of(
                new Tupel<>("001_1.Pat_2Tumoren_TumorID_1_Diagnose.xml", 1),
                new Tupel<>("003_Pat1_Tumor1_Therapie1_Behandlungsende_OP.xml", 1),
                new Tupel<>("010_Pat2_Tumor1_Tod.xml", 1)),
            12,
            "9391/8",
            "2021-03-20",
            "U",
            5,
            List.of("0", "1", "0"),
            List.of("is", "0", "0"),
            "R68.8",
            "2021-04-10"));
  }

  @ParameterizedTest
  @MethodSource("generateTestData")
  void mapObservation_withGivenAdtXml(
      List<Tupel<String, Integer>> xmlFileNames,
      int expectedObsvCount,
      String expectedMorphCode,
      String expectedEffectDate,
      String expectedGradingCode,
      int expectedFernMetaCount,
      List<String> expectedTnmCCodes,
      List<String> expectedTnmPCodes,
      String expectedDeathIcdCode,
      String expectedDeathDate)
      throws IOException {

    var meldungExportList = buildMeldungExportList(xmlFileNames);

    ObdsProcessor observationProcessor =
        new ObdsProcessor(
            fhirProps,
            onkoMedicationStatementMapper,
            onkoObservationMapper,
            onkoProcedureMapper,
            onkoPatientMapper,
            onkoConditionMapper);
    var resultBundle =
        observationProcessor.getOnkoToObservationBundleMapper().apply(meldungExportList);

    if (expectedObsvCount == 0) {
      assertThat(resultBundle).isNull();
    } else {
      var observationList =
          BundleUtil.toListOfResourcesOfType(ctx, resultBundle, Observation.class);

      assertThat(observationList).hasSize(expectedObsvCount);

      var fenMetaCount = 0;

      for (var obsv : observationList) {

        // Gleason Score Observations are using valueInteger,
        // so add this conversion to make sure we only check against
        // valueCodeableConcept
        if (obsv.getValue() instanceof CodeableConcept valueCodeableCon) {
          var profLoincCode = obsv.getCode().getCodingFirstRep().getCode();

          if (Objects.equals(profLoincCode, "59847-4")) { // Histologie

            var valueCode = valueCodeableCon.getCodingFirstRep().getCode();
            assertThat(valueCode).isEqualTo(expectedMorphCode);

            assertThat(obsv.getEffectiveDateTimeType().getValueAsString())
                .isEqualTo(expectedEffectDate);

          } else if (Objects.equals(profLoincCode, "59542-1")) { // Grading

            var valueCode = valueCodeableCon.getCodingFirstRep().getCode();
            assertThat(valueCode).isEqualTo(expectedGradingCode);

            assertThat(obsv.getEffectiveDateTimeType().getValueAsString())
                .isEqualTo(expectedEffectDate);

          } else if (Objects.equals(profLoincCode, "21907-1")) { // Fernmeta

            var valueCode = valueCodeableCon.getCodingFirstRep().getCode();
            assertThat(valueCode).isEqualTo("J");

            fenMetaCount += 1;

          } else if (Objects.equals(profLoincCode, "21908-9")) { // TNMc

            for (var comp : obsv.getComponent()) {
              var componentCode = comp.getCode().getCodingFirstRep().getCode();
              var componentValueCodableCon = (CodeableConcept) comp.getValue();
              var componentValue = componentValueCodableCon.getCodingFirstRep().getCode();
              if (Objects.equals(componentCode, "21905-5")) { // cTNM-T
                assertThat(componentValue).isEqualTo(expectedTnmCCodes.get(0));
              } else if (Objects.equals(componentCode, "201906-3")) { // cTNM-N
                assertThat(componentValue).isEqualTo(expectedTnmCCodes.get(1));
              } else if (Objects.equals(componentCode, "21907-1")) { // cTNM-M
                assertThat(componentValue).isEqualTo(expectedTnmCCodes.get(2));
              }
            }

          } else if (Objects.equals(profLoincCode, "21902-2")) { // TNMp

            for (var comp : obsv.getComponent()) {
              var componentCode = comp.getCode().getCodingFirstRep().getCode();
              var componentValueCodableCon = (CodeableConcept) comp.getValue();
              var componentValue = componentValueCodableCon.getCodingFirstRep().getCode();
              if (Objects.equals(componentCode, "21899-0")) { // pTNM-T
                assertThat(componentValue).isEqualTo(expectedTnmPCodes.get(0));
              } else if (Objects.equals(componentCode, "21900-6")) { // pTNM-N
                assertThat(componentValue).isEqualTo(expectedTnmPCodes.get(1));
              } else if (Objects.equals(componentCode, "21901-4")) { // pTNM-M
                assertThat(componentValue).isEqualTo(expectedTnmPCodes.get(2));
              }
            }

          } else if (Objects.equals(profLoincCode, "68343-3")) { // Death
            assertThat(((CodeableConcept) obsv.getValue()).getCodingFirstRep().getCode())
                .isEqualTo(expectedDeathIcdCode);
            assertThat(obsv.getEffectiveDateTimeType().getValueAsString())
                .isEqualTo(expectedDeathDate);
          } else {
            assertThat(true).isFalse();
          }
        }
      }
      assertThat(fenMetaCount).isEqualTo(expectedFernMetaCount);

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
