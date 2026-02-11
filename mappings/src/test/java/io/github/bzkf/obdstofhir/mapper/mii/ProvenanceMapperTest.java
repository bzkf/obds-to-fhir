package io.github.bzkf.obdstofhir.mapper.mii;

import ca.uhn.fhir.context.FhirContext;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.util.List;
import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.approvaltests.scrubbers.RegExScrubber;
import org.approvaltests.scrubbers.Scrubbers;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {FhirProperties.class, ProvenanceMapper.class},
    properties = {"lib-version=0.0.0-test"})
@EnableConfigurationProperties
class ProvenanceMapperTest extends MapperTest {

  @Autowired private ProvenanceMapper sut;

  @Test
  void map_withGivenResources_shouldCreateValidProvenanceResource() {
    var provenance = sut.map(List.of(new Reference("Condition/any")), "123");
    verify(provenance);
  }

  private static void verify(IBaseResource resource) {
    var fhirParser = FhirContext.forR4().newJsonParser().setPrettyPrint(true);
    var fhirJson = fhirParser.encodeResourceToString(resource);
    final var dateTimeScrubber =
        new RegExScrubber(
            "\"occurredDateTime\": \"(.*)\"", "\"occurredDateTime\": \"2000-01-01T11:11:11Z\"");
    final var recordedScrubber =
        new RegExScrubber("\"recorded\": \"(.*)\"", "\"recorded\": \"2000-01-01T11:11:11Z\"");
    var scrubber = Scrubbers.scrubAll(dateTimeScrubber, recordedScrubber);
    Approvals.verify(fhirJson, new Options(scrubber).forFile().withExtension(".fhir.json"));
  }
}
