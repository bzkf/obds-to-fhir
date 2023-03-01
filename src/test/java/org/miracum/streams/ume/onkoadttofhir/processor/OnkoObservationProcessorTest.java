package org.miracum.streams.ume.onkoadttofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
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
            Arrays.asList(new Tupel<>("001_1.Pat_2Tumoren_TumorID_1_Diagnose.xml", 1)), 9));
  }

  @ParameterizedTest
  @MethodSource("generateTestData")
  void mapObservation_withGivenAdtXml(
      List<Tupel<String, Integer>> xmlFileNames, int expectedObsvCount) throws IOException {

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
      var medicationStatementList =
          BundleUtil.toListOfResourcesOfType(ctx, resultBundle, Observation.class);

      assertThat(medicationStatementList).hasSize(expectedObsvCount);

      // TODO add missing structure definitions
      // assertThat(isValid(resultBundle)).isTrue();
    }
  }
}
