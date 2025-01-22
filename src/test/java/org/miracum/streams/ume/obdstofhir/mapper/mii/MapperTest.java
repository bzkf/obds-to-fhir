package org.miracum.streams.ume.obdstofhir.mapper.mii;

import static org.miracum.streams.ume.obdstofhir.Verification.verifyThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import java.text.SimpleDateFormat;
import java.util.List;
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
    verifyThat(resource).matches(sourceFile);
  }

  /**
   * Verifies given Fhir resources match approved source file
   *
   * @param resources The Fhir resources to be verified
   * @param sourceFile The approved source file
   */
  protected static <T extends IBaseResource> void verifyAll(List<T> resources, String sourceFile) {
    verifyThat(resources).matches(sourceFile);
  }
}
