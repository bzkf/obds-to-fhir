package org.miracum.streams.ume.obdstofhir.processor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.GleasonScoreToObservationMapper;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsConditionMapper;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsMedicationStatementMapper;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsObservationMapper;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsPatientMapper;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsProcedureMapper;
import org.miracum.streams.ume.obdstofhir.mapper.PsaToObservationMapper;
import org.miracum.streams.ume.obdstofhir.model.MeldungExport;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportList;
import org.miracum.streams.ume.obdstofhir.model.Tupel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ResourceUtils;

@SpringBootTest(
    classes = {
      FhirProperties.class,
      ObdsConditionMapper.class,
      ObdsMedicationStatementMapper.class,
      ObdsObservationMapper.class,
      ObdsProcedureMapper.class,
      ObdsPatientMapper.class,
      ObdsConditionMapper.class,
      GleasonScoreToObservationMapper.class,
      PsaToObservationMapper.class,
    },
    properties = {"app.version=0.0.0-test"})
@EnableConfigurationProperties()
public abstract class ObdsProcessorTest {

  private static final Logger log = LoggerFactory.getLogger(ObdsProcessorTest.class);

  protected final FhirContext ctx = FhirContext.forR4();
  protected final IParser fhirParser = ctx.newJsonParser().setPrettyPrint(true);

  @BeforeAll
  static void setUp() {}

  protected MeldungExportList buildMeldungExportList(List<Tupel<String, Integer>> xmlFileNames)
      throws IOException, NumberFormatException {
    var meldungExportList = new MeldungExportList();

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
      payloadOnkoRessource.put("LKR_MELDUNG", meldungsId.replace(melderId, ""));
      payloadOnkoRessource.put("VERSIONSNUMMER", xmlTupel.getSecond());
      payloadOnkoRessource.put("XML_DATEN", xmlContent);

      MeldungExport meldungExport = new MeldungExport();
      meldungExport.getPayload(payloadOnkoRessource);
      meldungExportList.addElement(meldungExport);

      payloadId++;
    }

    return meldungExportList;
  }
}
