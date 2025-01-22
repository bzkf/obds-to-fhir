package org.miracum.streams.ume.obdstofhir.mapper.mii;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import java.text.SimpleDateFormat;
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
}
