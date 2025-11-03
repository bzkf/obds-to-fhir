package io.github.bzkf.obdstofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.util.BundleUtil;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.*;
import io.github.bzkf.obdstofhir.model.Tupel;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import org.approvaltests.Approvals;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Procedure;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

class ObdsProcedureProcessorTest extends ObdsProcessorTest {

  private final FhirProperties fhirProps;
  private final ObdsMedicationStatementMapper onkoMedicationStatementMapper;
  private final ObdsObservationMapper onkoObservationMapper;
  private final ObdsProcedureMapper onkoProcedureMapper;
  private final ObdsPatientMapper onkoPatientMapper;
  private final ObdsConditionMapper onkoConditionMapper;

  @Autowired
  public ObdsProcedureProcessorTest(
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
            List.of(new Tupel<>("003_Pat1_Tumor1_Therapie1_Behandlungsende_OP.xml", 1)),
            9,
            9,
            0,
            8,
            "COMPLETED",
            "2021-01-04",
            "K",
            "",
            "N",
            "Nein"),
        Arguments.of(
            List.of(new Tupel<>("007_Pat2_Tumor1_Behandlungsende_ST.xml", 1)),
            3,
            0,
            3,
            0,
            "COMPLETED",
            "",
            "P",
            "N",
            "4",
            "lebensbedrohlich"));
  }

  @ParameterizedTest
  @MethodSource("generateTestData")
  void mapProcedure_withGivenAdtXml(
      List<Tupel<String, Integer>> xmlFileNames,
      int expectedProcCount,
      int expectedProcCountOp,
      int expectedProcCountSt,
      int expectedOpsCount,
      String expectedStatus,
      String expectedOpDate,
      String expectedIntention,
      String expectedStellungOP,
      String expectedcomplicationCode,
      String expectedcomplicationDisplay)
      throws IOException {

    var meldungExportList = buildMeldungExportList(xmlFileNames);

    ObdsProcessor procedureProcessor =
        new ObdsProcessor(
            fhirProps,
            onkoMedicationStatementMapper,
            onkoObservationMapper,
            onkoProcedureMapper,
            onkoPatientMapper,
            onkoConditionMapper);

    var resultBundle = procedureProcessor.getOnkoToProcedureBundleMapper().apply(meldungExportList);

    if (expectedProcCount == 0) {
      assertThat(resultBundle).isNull();
    } else {
      var procedureList = BundleUtil.toListOfResourcesOfType(ctx, resultBundle, Procedure.class);

      assertThat(procedureList).hasSize(expectedProcCount);

      var opProcedureList = new ArrayList<Procedure>();
      var stProcedureList = new ArrayList<Procedure>();

      for (Procedure proc : procedureList) {
        if (Objects.equals(
            proc.getMeta().getProfile().get(0).getValue(),
            fhirProps.getProfiles().getOpProcedure())) {
          opProcedureList.add(proc);
        } else if (Objects.equals(
            proc.getMeta().getProfile().get(0).getValue(),
            fhirProps.getProfiles().getStProcedure())) {
          stProcedureList.add(proc);
        }
      }

      assertThat(opProcedureList).hasSize(expectedProcCountOp);
      if (opProcedureList.size() == 1) {
        var opIntention =
            (CodeableConcept)
                opProcedureList
                    .get(0)
                    .getExtensionByUrl(fhirProps.getExtensions().getOpIntention())
                    .getValue();
        assertThat(opIntention.getCoding().get(0).getCode()).isEqualTo(expectedIntention);

        assertThat(opProcedureList.get(0).getStatus()).hasToString(expectedStatus);

        assertThat(opProcedureList.get(0).getPerformedDateTimeType().getValueAsString())
            .isEqualTo(expectedOpDate);

        assertThat(opProcedureList.get(0).getCode().getCoding()).hasSize(expectedOpsCount);

        assertThat(opProcedureList.get(0).getComplicationFirstRep().getCodingFirstRep().getCode())
            .isEqualTo(expectedcomplicationCode);
        assertThat(
                opProcedureList.get(0).getComplicationFirstRep().getCodingFirstRep().getDisplay())
            .isEqualTo(expectedcomplicationDisplay);
      }

      assertThat(stProcedureList).hasSize(expectedProcCountSt);

      int partOfCount = 0;
      String partOfId = "";
      List<String> partOfReferences = new ArrayList<>();
      for (var stProc : stProcedureList) {

        assertThat(stProc.getStatus()).hasToString(expectedStatus);

        var stellungOPCc =
            (CodeableConcept)
                stProc.getExtensionByUrl(fhirProps.getExtensions().getStellungOP()).getValue();
        var intentionCC =
            (CodeableConcept)
                stProc.getExtensionByUrl(fhirProps.getExtensions().getSystIntention()).getValue();

        assertThat(stellungOPCc.getCoding().get(0).getCode()).isEqualTo(expectedStellungOP);
        assertThat(intentionCC.getCoding().get(0).getCode()).isEqualTo(expectedIntention);

        assertThat(stProc.getComplicationFirstRep().getCodingFirstRep().getCode())
            .isEqualTo(expectedcomplicationCode);
        assertThat(stProc.getComplicationFirstRep().getCodingFirstRep().getDisplay())
            .isEqualTo(expectedcomplicationDisplay);

        if (stProc.getPartOf().isEmpty()) {
          partOfId = stProc.getIdElement().getIdPart();
          partOfCount++;
        } else {
          assertThat(stProc.getPartOf()).hasSize(1);
          partOfReferences.add(stProc.getPartOf().get(0).getReferenceElement().getIdPart());
        }
      }

      if (!stProcedureList.isEmpty()) {
        assertThat(partOfCount).isEqualTo(1);
        String finalPartOfId = partOfId;
        assertThat(partOfReferences).allSatisfy(ref -> assertThat(ref).isEqualTo(finalPartOfId));
      }

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
