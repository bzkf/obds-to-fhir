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
import org.hl7.fhir.r4.model.Observation;
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
public class OnkoObservationProcessorTest extends OnkoProcessorTest {

  private static final Logger log = LoggerFactory.getLogger(OnkoObservationProcessorTest.class);

  private final FhirProperties fhirProps;
  private final FhirContext ctx = FhirContext.forR4();

  @Autowired
  public OnkoObservationProcessorTest(FhirProperties fhirProperties) {
    this.fhirProps = fhirProperties;
  }

  private static Stream<Arguments> generateTestData() {
    return Stream.of(
        Arguments.of(
            Arrays.asList(new Tupel<>("001_1.Pat_2Tumoren_TumorID_1_Diagnose.xml", 1)),
            9,
            "9391/3",
            "2021-03-18",
            "T",
            5,
            Arrays.asList("0", "1", "0"),
            Arrays.asList("", "2a", "")),
        Arguments.of(
            Arrays.asList(
                new Tupel<>("001_1.Pat_2Tumoren_TumorID_1_Diagnose.xml", 1),
                new Tupel<>("003_Pat1_Tumor1_Therapie1_Behandlungsende_OP.xml", 1)),
            9,
            "9391/8",
            "2021-03-20",
            "U",
            5,
            Arrays.asList("0", "1", "0"),
            Arrays.asList("is", "0", "0")));
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
      List<String> expectedTnmPCodes)
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

    OnkoObservationProcessor observationProcessor = new OnkoObservationProcessor(fhirProps);

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

        var profLoincCode = obsv.getCode().getCodingFirstRep().getCode();
        var valueCodeableCon = (CodeableConcept) obsv.getValue();

        if (Objects.equals(profLoincCode, "59847-4")) { // Histologie

          var valueCode = valueCodeableCon.getCodingFirstRep().getCode();
          assertThat(valueCode).isEqualTo(expectedMorphCode);

          DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
          LocalDateTime expectedLocalDateTime =
              LocalDateTime.parse(expectedEffectDate + " 00:00:00", fmt);

          assertThat(obsv.getEffectiveDateTimeType().getValue().getTime())
              .isEqualTo(
                  expectedLocalDateTime
                      .atZone(ZoneId.of("Europe/Berlin"))
                      .toInstant()
                      .toEpochMilli());

        } else if (Objects.equals(profLoincCode, "59542-1")) { // Grading

          var valueCode = valueCodeableCon.getCodingFirstRep().getCode();
          assertThat(valueCode).isEqualTo(expectedGradingCode);

          DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
          LocalDateTime expectedLocalDateTime =
              LocalDateTime.parse(expectedEffectDate + " 00:00:00", fmt);

          assertThat(obsv.getEffectiveDateTimeType().getValue().getTime())
              .isEqualTo(
                  expectedLocalDateTime
                      .atZone(ZoneId.of("Europe/Berlin"))
                      .toInstant()
                      .toEpochMilli());

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

        } else {
          assertThat(true).isFalse();
        }
      }
      assertThat(fenMetaCount).isEqualTo(expectedFernMetaCount);

      assertThat(isValid(resultBundle)).isTrue();
    }
  }
}
