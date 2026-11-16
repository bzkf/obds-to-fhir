package io.github.bzkf.obdstofhir.mapper.mii;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.ProfileTestConfig;
import io.github.bzkf.obdstofhir.serde.Obdsv3Deserializer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.xml.sax.SAXException;
import tools.jackson.core.JacksonException;

/**
 * Fuzzes the path from raw oBDS XML to FHIR bundles.
 *
 * <p>Every input is parsed with the production XML mapper, so malformed XML only has to be rejected
 * with a {@link JacksonException}. Only inputs that are valid against the oBDS XSD are then mapped
 * and serialized, because the mappers rely on the schema's mandatory elements. Any exception thrown
 * for a schema-valid oBDS is a finding.
 *
 * <p>{@code ./gradlew test} runs this in regression mode against the inputs in {@code
 * src/test/resources/.../ObdsToFhirBundleMapperFuzzTestInputs}. To fuzz, run {@code ./gradlew
 * :mappings:fuzz}, see DEVELOPMENT.md.
 */
@SpringBootTest(classes = {ObdsToFhirBundleMapperTestConfig.class, ProfileTestConfig.class})
@EnableConfigurationProperties
@Configuration
class ObdsToFhirBundleMapperFuzzTest {
  private static final IParser FHIR_PARSER = FhirContext.forR4().newJsonParser();
  private static final Schema OBDS_SCHEMA = loadObdsSchema();

  private static ObdsToFhirBundleMapper sut;

  @BeforeAll
  static void beforeAll(@Autowired ObdsToFhirBundleMapper bundleMapper) {
    sut = bundleMapper;
  }

  @FuzzTest(maxDuration = "10m")
  void mapObds(FuzzedDataProvider data) {
    var xml = data.consumeRemainingAsBytes();

    OBDS obds;
    try {
      obds = Obdsv3Deserializer.xmlMapper().readValue(xml, OBDS.class);
    } catch (JacksonException e) {
      return;
    }

    if (obds == null || !isSchemaValid(xml)) {
      return;
    }

    for (var result : sut.map(obds)) {
      FHIR_PARSER.encodeResourceToString(result.bundle());
    }
    // the Kafka route maps all oBDS of a patient at once, which takes a different code path
    for (var result : sut.map(List.of(obds))) {
      FHIR_PARSER.encodeResourceToString(result.bundle());
    }
  }

  private static boolean isSchemaValid(byte[] xml) {
    var validator = OBDS_SCHEMA.newValidator();
    try {
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      validator.validate(new StreamSource(new ByteArrayInputStream(xml)));
      return true;
    } catch (SAXException | IOException e) {
      // the input is in memory, so an IOException means it can't be decoded, e.g. because it
      // declares an unknown encoding that Jackson ignores
      return false;
    }
  }

  private static Schema loadObdsSchema() {
    var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    // the XSD ships with the obds2-to-obds3 library that also contains the oBDS model classes
    var xsd = ObdsToFhirBundleMapperFuzzTest.class.getResource("/schema/oBDS_v3.0.4.xsd");
    try {
      return factory.newSchema(xsd);
    } catch (SAXException e) {
      throw new IllegalStateException("Failed to load the oBDS XSD", e);
    }
  }
}
