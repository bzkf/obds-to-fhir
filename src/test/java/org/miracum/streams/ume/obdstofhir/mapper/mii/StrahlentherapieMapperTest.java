package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import org.approvaltests.Approvals;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class StrahlentherapieMapperTest {
  private static StrahlentherapieMapper sut;

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new StrahlentherapieMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({"Testpatient_1.xml", "Testpatient_2.xml", "Testpatient_3.xml"})
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
    var stMeldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getST() != null)
            .findFirst()
            .get();
    var procedure = sut.map(stMeldung.getST(), subject);

    var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
    var fhirJson = fhirParser.encodeResourceToString(procedure);
    Approvals.verify(
        fhirJson, Approvals.NAMES.withParameters(sourceFile).forFile().withExtension(".fhir.json"));
  }

  @Test
  void shouldNotVerifyNullReferences() throws Exception {
    final var resource = this.getClass().getClassLoader().getResource("obds3/Testpatient_1.xml");
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

    var wrongPatient = (Reference) null;
    var stMeldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getST() != null)
            .findFirst()
            .get();

    var ex =
        assertThrows(NullPointerException.class, () -> sut.map(stMeldung.getST(), wrongPatient));
    assertThat(ex).hasMessage("Reference to PATIENT must not be null");
  }

  @Test
  void shouldNotVerifyWrongReferencesType() throws Exception {
    final var resource = this.getClass().getClassLoader().getResource("obds3/Testpatient_1.xml");
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

    var wrongPatient = new Reference("Procedure/any");
    var stMeldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getST() != null)
            .findFirst()
            .get();

    var ex =
        assertThrows(
            IllegalArgumentException.class, () -> sut.map(stMeldung.getST(), wrongPatient));

    assertThat(ex).hasMessage("The reference should point to a PATIENT resource");
  }
}
