package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import org.approvaltests.Approvals;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class TodMapperTest {
  private static TodMapper tm;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    tm = new TodMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml"})
  void map_withGivenObds_shouldCreateValidProcedure(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

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
    var tMeldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getTod() != null)
            .findFirst()
            .get();
    var observationList = tm.map(tMeldung, subject, condition);

    var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);

    var observListString = "[";
    for (Observation observation : observationList) {
      var fhirJson = fhirParser.encodeResourceToString(observation);
      observListString += fhirJson + ",\n";
    }
    observListString = observListString.substring(0, observListString.length() - 2);
    observListString += "]";

    Approvals.verify(
        observListString,
        Approvals.NAMES.withParameters(sourceFile).forFile().withExtension(".fhir.json"));
  }
}
