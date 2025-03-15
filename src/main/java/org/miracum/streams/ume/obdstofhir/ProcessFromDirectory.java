package org.miracum.streams.ume.obdstofhir;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.miracum.streams.ume.obdstofhir.mapper.mii.ObdsToFhirBundleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    value = "obds.process-from-directory.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class ProcessFromDirectory {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessFromDirectory.class);

  private ProcessFromDirectoryConfig config;
  private KafkaTemplate<String, IBaseResource> kafkaTemplate;
  private ObdsToFhirBundleMapper mapper;

  private static final FhirContext fhirContext = FhirContext.forR4();

  public ProcessFromDirectory(
      ProcessFromDirectoryConfig config,
      KafkaTemplate<String, IBaseResource> kafkaTemplate,
      ObdsToFhirBundleMapper mapper) {
    this.config = config;
    this.kafkaTemplate = kafkaTemplate;
    this.mapper = mapper;
  }

  @EventListener
  public void processFromFileSystem(ApplicationReadyEvent readyEvent) throws IOException {
    final var xmlMapper =
        XmlMapper.builder()
            .defaultUseWrapper(false)
            .addModule(new JakartaXmlBindAnnotationModule())
            .addModule(new Jdk8Module())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    LOG.info("Processing oBDS files in folder {}", config.path());

    try (var stream = Files.list(Paths.get(config.path()))) {
      var files =
          stream.filter(file -> !Files.isDirectory(file)).sorted().collect(Collectors.toSet());

      for (var file : files) {
        LOG.info("Mapping file {}", file.getFileName());
        var obdsString = Files.readString(file);
        final var obdsMeldung = xmlMapper.readValue(obdsString, OBDS.class);
        final var bundles = mapper.map(obdsMeldung);
        for (var bundle : bundles) {
          LOG.info("Created FHIR bundle {}", bundle.getId());

          if (config.outputToKafka().enabled()) {
            kafkaTemplate.send(config.outputToKafka().topic(), bundle.getId(), bundle);
          }

          if (config.outputToDirectory().enabled()) {
            var filename = "bundle-" + bundle.getId() + ".fhir.json";
            var outputPath = Path.of(config.outputToDirectory().path(), filename);

            var bundleJson =
                fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);

            Files.writeString(
                outputPath,
                bundleJson,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
          }
        }
      }
    }
    readyEvent.getApplicationContext().close();
  }
}
