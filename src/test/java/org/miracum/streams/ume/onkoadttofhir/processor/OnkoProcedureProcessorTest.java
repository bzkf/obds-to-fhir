package org.miracum.streams.ume.onkoadttofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Procedure;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExportList;
import org.miracum.streams.ume.onkoadttofhir.model.Tupel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ResourceUtils;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties(value = {FhirProperties.class})
public class OnkoProcedureProcessorTest extends OnkoProcessorTest {

  private static final Logger log = LoggerFactory.getLogger(OnkoProcedureProcessorTest.class);

  private final FhirProperties fhirProps;
  private final FhirContext ctx = FhirContext.forR4();

  @Autowired
  public OnkoProcedureProcessorTest(FhirProperties fhirProperties) {
    this.fhirProps = fhirProperties;
  }

  private static Stream<Arguments> generateTestData() {
    return Stream.of(
        Arguments.of(
            Arrays.asList(new Tupel<>("003_Pat1_Tumor1_Therapie1_Behandlungsende_OP.xml", 1)),
            1,
            1,
            0,
            8,
            "COMPLETED",
            "2021-01-04",
            "K",
            "",
            "N",
            "Nein"),
        Arguments.of(
            Arrays.asList(new Tupel<>("007_Pat2_Tumor1_Behandlungsende_ST.xml", 1)),
            3,
            0,
            3,
            0,
            "COMPLETED",
            "",
            "D",
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

    OnkoProcedureProcessor procedureProcessor = new OnkoProcedureProcessor(fhirProps);

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

        assertThat(opProcedureList.get(0).getStatus().toString()).isEqualTo(expectedStatus);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime expectedLocalDateTime =
            LocalDateTime.parse(expectedOpDate + " 00:00:00", fmt);

        assertThat(opProcedureList.get(0).getPerformedDateTimeType().getValue().getTime())
            .isEqualTo(
                expectedLocalDateTime
                    .atZone(ZoneId.of("Europe/Berlin"))
                    .toInstant()
                    .toEpochMilli());

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

        assertThat(stProc.getStatus().toString()).isEqualTo(expectedStatus);

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
          partOfId = stProc.getId();
          partOfCount++;
        } else {
          assertThat(stProc.getPartOf()).hasSize(1);
          partOfReferences.add(stProc.getPartOf().get(0).getReference());
        }
      }

      if (!stProcedureList.isEmpty()) {
        assertThat(partOfCount).isEqualTo(1);
        String finalPartOfId = partOfId;
        assertThat(partOfReferences).allSatisfy(ref -> ref.equals(finalPartOfId));
      }

      // TODO add missing structure definitions
      // assertThat(isValid(resultBundle)).isTrue();
    }
  }
}
