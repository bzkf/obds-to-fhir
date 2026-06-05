package io.github.bzkf.obdstofhir.mapper.mii;

import ca.uhn.fhir.context.FhirContext;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.DeviceMapper;
import io.github.bzkf.obdstofhir.mapper.ProvenanceMapper;
import java.util.List;
import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.approvaltests.core.Scrubber;
import org.approvaltests.scrubbers.RegExScrubber;
import org.approvaltests.scrubbers.Scrubbers;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class, DeviceMapper.class, ProvenanceMapper.class})
@EnableConfigurationProperties
class ProvenanceMapperTest extends MapperTest {

  @Autowired private ProvenanceMapper sut;

  public static final Scrubber FHIR_DATE_TIME_SCRUBBER =
      Scrubbers.scrubAll(
          new RegExScrubber(
              "\"occurredDateTime\": \"(.*)\"", "\"occurredDateTime\": \"2000-01-01T11:11:11Z\""),
          new RegExScrubber("\"recorded\": \"(.*)\"", "\"recorded\": \"2000-01-01T11:11:11Z\""));

  @Test
  void map_withGivenResources_shouldCreateValidProvenanceResource() {
    var provenance = sut.map(List.of(new Reference("Condition/any")), "123");
    verify(provenance);
  }

  private static void verify(IBaseResource resource) {
    var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
    var fhirJson = fhirParser.encodeResourceToString(resource);

    Approvals.verify(
        fhirJson, new Options(FHIR_DATE_TIME_SCRUBBER).forFile().withExtension(".fhir.json"));
  }
}
