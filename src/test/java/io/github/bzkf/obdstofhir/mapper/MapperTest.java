package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import java.text.SimpleDateFormat;
import java.util.List;
import org.approvaltests.Approvals;
import org.hl7.fhir.instance.model.api.IBaseResource;

/** Abstract class to provide common methods for mapping tests */
public abstract class MapperTest {

  /**
   * Provides default XmlMapper for oBDS v3
   *
   * @return XmlMapper configured for use with oBDS v3
   */
  protected XmlMapper xmlMapper() {
    return XmlMapper.builder()
        .defaultUseWrapper(false)
        .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd"))
        .addModule(new JakartaXmlBindAnnotationModule())
        .addModule(new Jdk8Module())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        // .enable(FromXmlParser.Feature.EMPTY_ELEMENT_AS_NULL)
        .build();
  }

  /**
   * Verifies given Fhir resource matches approved source file
   *
   * @param resource The Fhir resource to be verified
   * @param sourceFile The approved source file
   */
  protected static void verify(IBaseResource resource, String sourceFile) {
    var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
    var fhirJson = fhirParser.encodeResourceToString(resource);
    Approvals.verify(
        fhirJson, Approvals.NAMES.withParameters(sourceFile).forFile().withExtension(".fhir.json"));
  }

  /**
   * Verifies given Fhir resources match approved source file
   *
   * @param resources The Fhir resources to be verified
   * @param sourceFile The approved source file
   */
  protected static <T extends IBaseResource> void verifyAll(List<T> resources, String sourceFile) {
    for (int i = 0; i < resources.size(); i++) {
      assertThat(resources.get(i)).isNotNull();
      var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
      var fhirJson = fhirParser.encodeResourceToString(resources.get(i));
      Approvals.verify(
          fhirJson,
          Approvals.NAMES
              .withParameters(sourceFile, String.format("index_%d", i))
              .forFile()
              .withExtension(".fhir.json"));
    }

    // var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
    // var labeller = NamerFactory.useMultipleFiles();

    // for (var resource : resources) {
    //   assertThat(resource).isNotNull();
    //   var fhirJson = fhirParser.encodeResourceToString(resource);
    //   Approvals.verify(
    //       fhirJson,
    //       Approvals.NAMES.withParameters(sourceFile).forFile().withExtension(".fhir.json"));
    //   labeller.next();
    // }
  }
}
