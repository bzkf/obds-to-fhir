package org.miracum.streams.ume.obdstofhir.processor;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import org.approvaltests.Approvals;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.miracum.streams.ume.obdstofhir.mapper.*;
import org.miracum.streams.ume.obdstofhir.model.Tupel;
import org.springframework.beans.factory.annotation.Autowired;

class ObdsPatientMapperTest extends ObdsProcessorTest {

  private final ObdsPatientMapper onkoPatientMapper;

  @Autowired
  public ObdsPatientMapperTest(ObdsPatientMapper onkoPatientMapper) {
    this.onkoPatientMapper = onkoPatientMapper;
  }

  private static Stream<Arguments> generateTestData() {
    return Stream.of(
        Arguments.of(List.of(new Tupel<>("003_Pat1_Tumor1_Therapie1_Behandlungsende_OP.xml", 1))),
        Arguments.of(List.of(new Tupel<>("007_Pat2_Tumor1_Behandlungsende_ST.xml", 1))));
  }

  @ParameterizedTest
  @MethodSource("generateTestData")
  void mapOnkoResourcesToPatient_withGivenAdtXml(List<Tupel<String, Integer>> xmlFileNames)
      throws IOException {

    var meldungExportList = buildMeldungExportList(xmlFileNames);

    var resultBundle = onkoPatientMapper.mapOnkoResourcesToPatient(meldungExportList.getElements());

    var fhirJson = fhirParser.encodeResourceToString(resultBundle);
    Approvals.verify(
        fhirJson,
        Approvals.NAMES
            .withParameters(
                xmlFileNames.stream().map(t -> t.getFirst().substring(0, 5)).toArray(String[]::new))
            .forFile()
            .withExtension(".fhir.json"));
  }
}
