package io.github.bzkf.obdstofhir.mapper.mii;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.util.FhirTerser;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.DictionaryFile;
import com.code_intelligence.jazzer.junit.FuzzTest;
import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.ProfileTestConfig;
import io.github.bzkf.obdstofhir.serde.Obdsv3Deserializer;
import io.github.bzkf.obdstofhir.serde.Obdsv3Serializer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.jspecify.annotations.Nullable;
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
 * with a {@link JacksonException}. Only inputs that are valid against the oBDS XSD are then mapped,
 * because the mappers rely on the schema's mandatory elements. For those, besides not throwing, the
 * mapping has to:
 *
 * <ul>
 *   <li>give the same result when repeated, so it is deterministic and leaves the oBDS unchanged
 *   <li>give the same result after a round trip through {@link Obdsv3Serializer}, which the Kafka
 *       state store does with every oBDS before it is mapped
 *   <li>create bundles with unique entries and references that resolve within the bundle
 * </ul>
 *
 * <p>The serialized oBDS also has to be valid against the XSD, as it is written to Kafka when
 * {@code write-grouped-obds-to-kafka} is enabled.
 *
 * <p>{@code ./gradlew test} runs this in regression mode against the inputs in {@code
 * src/test/resources/.../ObdsToFhirBundleMapperFuzzTestInputs}. To fuzz, run {@code ./gradlew
 * :mappings:fuzz}, see DEVELOPMENT.md.
 */
@SpringBootTest(classes = {ObdsToFhirBundleMapperTestConfig.class, ProfileTestConfig.class})
@EnableConfigurationProperties
@Configuration
class ObdsToFhirBundleMapperFuzzTest {
  private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();
  private static final IParser FHIR_PARSER = FHIR_CONTEXT.newJsonParser().setPrettyPrint(true);
  private static final FhirTerser FHIR_TERSER = FHIR_CONTEXT.newTerser();
  private static final Schema OBDS_SCHEMA = loadObdsSchema();
  private static final Obdsv3Serializer SERIALIZER = new Obdsv3Serializer();

  private static ObdsToFhirBundleMapper sut;

  @BeforeAll
  static void beforeAll(@Autowired ObdsToFhirBundleMapper bundleMapper) {
    sut = bundleMapper;
  }

  @FuzzTest(maxDuration = "10m")
  @DictionaryFile(resourcePath = "obds.dict")
  void mapObds(FuzzedDataProvider data) {
    var xml = data.consumeRemainingAsBytes();

    var obds = parse(xml);
    if (obds == null || findSchemaViolation(xml) != null) {
      return;
    }

    var expected = map(obds);
    assertSameMapping("mapping the same oBDS again", expected, map(obds));

    var serialized = SERIALIZER.serialize("fuzz", obds);
    var violation = findSchemaViolation(serialized);
    if (violation != null) {
      throw new AssertionError(
          String.format(
              "The serialized oBDS isn't valid against the XSD: %s%n%s",
              violation, new String(serialized, StandardCharsets.UTF_8)));
    }
    var roundTripped = parse(serialized);
    if (roundTripped == null) {
      throw new AssertionError("The serialized oBDS can't be parsed:\n" + new String(serialized));
    }
    assertSameMapping(
        "mapping after a round trip through the serializer", expected, map(roundTripped));

    // the Kafka route maps all oBDS of a patient at once, which takes a different code path
    for (var result : sut.map(List.of(obds))) {
      assertValidBundle(result.bundle());
    }
  }

  private static @Nullable OBDS parse(byte[] xml) {
    try {
      return Obdsv3Deserializer.xmlMapper().readValue(xml, OBDS.class);
    } catch (JacksonException e) {
      return null;
    }
  }

  private static String map(OBDS obds) {
    var json = new StringBuilder();
    for (var result : sut.map(obds)) {
      assertValidBundle(result.bundle());
      json.append(FHIR_PARSER.encodeResourceToString(result.bundle())).append('\n');
      for (var provenance : result.provenances()) {
        json.append(FHIR_PARSER.encodeResourceToString(provenance)).append('\n');
      }
    }
    return json.toString();
  }

  /** Compares line by line, as printing two complete sets of bundles would be unreadable. */
  private static void assertSameMapping(String description, String expected, String actual) {
    var expectedLines = expected.split("\n", -1);
    var actualLines = actual.split("\n", -1);
    for (int i = 0; i < Math.max(expectedLines.length, actualLines.length); i++) {
      var expectedLine = i < expectedLines.length ? expectedLines[i] : "<end>";
      var actualLine = i < actualLines.length ? actualLines[i] : "<end>";
      if (!expectedLine.equals(actualLine)) {
        throw new AssertionError(
            String.format(
                "%s changed the result at line %d:%n  expected: %s%n  actual:   %s",
                description, i + 1, expectedLine.strip(), actualLine.strip()));
      }
    }
  }

  private static void assertValidBundle(@Nullable Bundle bundle) {
    if (bundle == null) {
      return;
    }
    var fullUrls = new HashSet<String>();
    var ids = new HashSet<String>();
    for (var entry : bundle.getEntry()) {
      if (!fullUrls.add(entry.getFullUrl())) {
        throw new AssertionError("Duplicate fullUrl in bundle: " + entry.getFullUrl());
      }
      ids.add(relativeReference(entry.getResource()));
    }
    for (var entry : bundle.getEntry()) {
      for (var reference :
          FHIR_TERSER.getAllPopulatedChildElementsOfType(entry.getResource(), Reference.class)) {
        var target = reference.getReference();
        if (target != null && !ids.contains(target)) {
          throw new AssertionError(
              String.format(
                  "%s references %s, which isn't in the bundle",
                  relativeReference(entry.getResource()), target));
        }
      }
    }
  }

  private static String relativeReference(Resource resource) {
    return resource.fhirType() + "/" + resource.getIdElement().getIdPart();
  }

  /** Returns why the XML isn't valid against the oBDS XSD, or null if it is. */
  private static @Nullable String findSchemaViolation(byte[] xml) {
    var validator = OBDS_SCHEMA.newValidator();
    try {
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      validator.validate(new StreamSource(new ByteArrayInputStream(xml)));
      return null;
    } catch (SAXException | IOException e) {
      // the input is in memory, so an IOException means it can't be decoded, e.g. because it
      // declares an unknown encoding that Jackson ignores
      return e.getMessage();
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
