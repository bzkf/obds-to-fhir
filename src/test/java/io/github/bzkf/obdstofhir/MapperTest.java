package io.github.bzkf.obdstofhir;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import java.text.SimpleDateFormat;
import java.util.List;
import org.approvaltests.Approvals;
import org.approvaltests.core.Scrubber;
import org.approvaltests.scrubbers.RegExScrubber;
import org.hl7.fhir.instance.model.api.IBaseResource;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;

/** Abstract class to provide common methods for mapping tests */
public abstract class MapperTest {

  /** Scrubs the version suffix (e.g. {@code |2026.0.3}) from canonical profile URLs. */
  protected static final Scrubber PROFILE_VERSION_SCRUBBER =
      new RegExScrubber("\\|[0-9]+\\.[0-9]+\\.[0-9]+(?=\")", "");

  /**
   * Provides default XmlMapper for oBDS v3
   *
   * @return XmlMapper configured for use with oBDS v3
   */
  protected XmlMapper xmlMapper() {
    return XmlMapper.builder()
        .defaultUseWrapper(false)
        .addModule(new JakartaXmlBindAnnotationModule())
        .build()
        .rebuild()
        .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd"))
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
        fhirJson,
        Approvals.NAMES
            .withParameters(sourceFile)
            .withScrubber(PROFILE_VERSION_SCRUBBER)
            .forFile()
            .withExtension(".fhir.json"));
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
              .withScrubber(PROFILE_VERSION_SCRUBBER)
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
