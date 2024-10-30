package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import java.util.TimeZone;
import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class PatientMapperTest {
  private static PatientMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
    sut = new PatientMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"obds3/test1.xml"})
  void map_withGivenObds_shouldCreateValidPatientResource(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource(sourceFile);
    assertThat(resource).isNotNull();

    final var xmlMapper =
        XmlMapper.builder()
            .defaultUseWrapper(false)
            .addModule(new JakartaXmlBindAnnotationModule())
            .addModule(new Jdk8Module())
            .build();

    final var obds = xmlMapper.readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    final var patient =
        sut.map(obdsPatient.getPatientenStammdaten(), obdsPatient.getMengeMeldung().getMeldung());

    var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
    var fhirJson = fhirParser.encodeResourceToString(patient);
    Approvals.verify(fhirJson, new Options().forFile().withExtension(".fhir.json"));
  }
}
