package io.github.bzkf.obdstofhir.processor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.GleasonScoreToObservationMapper;
import io.github.bzkf.obdstofhir.mapper.ObdsConditionMapper;
import io.github.bzkf.obdstofhir.mapper.ObdsMedicationStatementMapper;
import io.github.bzkf.obdstofhir.mapper.ObdsObservationMapper;
import io.github.bzkf.obdstofhir.mapper.ObdsPatientMapper;
import io.github.bzkf.obdstofhir.mapper.ObdsProcedureMapper;
import io.github.bzkf.obdstofhir.mapper.PsaToObservationMapper;
import io.github.bzkf.obdstofhir.model.MeldungExport;
import io.github.bzkf.obdstofhir.model.MeldungExportList;
import io.github.bzkf.obdstofhir.model.Tupel;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
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
    properties = {"app-version=0.0.0-test", "fhir.mappings.modul.prostata.enabled=true"})
@EnableConfigurationProperties()
public abstract class ObdsProcessorTest {

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
      String xmlContent = new String(Files.readAllBytes(xmlFile.toPath()), StandardCharsets.UTF_8);

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
