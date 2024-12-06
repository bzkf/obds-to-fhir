package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import java.util.TimeZone;
import org.approvaltests.Approvals;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
public class OperationMapperTest {
  private static OperationMapper sut;

  private static final Logger LOG = LoggerFactory.getLogger(OperationMapper.class);

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
    sut = new OperationMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_2.xml"})
  void map_withGivenObds_shouldCreateValidProcedure(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    System.out.println("Loaded resource: " + resource.getPath());

    final var xmlMapper =
        XmlMapper.builder()
            .defaultUseWrapper(false)
            .addModule(new JakartaXmlBindAnnotationModule())
            .addModule(new Jdk8Module())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    final var obds = xmlMapper.readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    var subject = new Reference("Patient/any");
    var condition = new Reference("Condition/any");

    var opMeldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getOP() != null)
            .findFirst()
            .get();

    // Map and get the list of procedures
    final var resultResources = sut.map(opMeldung.getOP(), subject, condition);

    assertThat(resultResources).isNotEmpty();
    LOG.info("Length of resultResources {}", resultResources.size());

    var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);

    LOG.info("Number of OPS codes: {}", opMeldung);

    for (int i = 0; i < resultResources.size(); i++) {
      assertThat(resultResources.get(i)).isNotNull();
      var fhirJson = fhirParser.encodeResourceToString(resultResources.get(i));
      System.out.println("Verifying resource: index_" + i);
      Approvals.verify(
          fhirJson,
          Approvals.NAMES
              .withParameters(sourceFile, "index_" + i)
              .forFile()
              .withExtension(".fhir.json"));
    }
  }
}
