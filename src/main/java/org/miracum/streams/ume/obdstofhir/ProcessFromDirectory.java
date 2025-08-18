package org.miracum.streams.ume.obdstofhir;

import ca.uhn.fhir.context.FhirContext;
import de.basisdatensatz.obds.v3.OBDS;
import dev.pcvolkmer.onko.obds2to3.ObdsMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.miracum.streams.ume.obdstofhir.mapper.mii.ObdsToFhirBundleMapper;
import org.miracum.streams.ume.obdstofhir.serde.Obdsv3Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
  private static final FhirContext fhirContext = FhirContext.forR4();

  private ProcessFromDirectoryConfig config;
  private KafkaTemplate<String, IBaseResource> kafkaTemplate;
  private ObdsToFhirBundleMapper mapper;
  private ObdsMapper obdsV2ToV3Mapper;
  private Obdsv3Deserializer deserializer;

  public ProcessFromDirectory(
      ProcessFromDirectoryConfig config,
      KafkaTemplate<String, IBaseResource> kafkaTemplate,
      ObdsToFhirBundleMapper mapper,
      ObdsMapper obdsMapper,
      Obdsv3Deserializer deserializer) {
    this.config = config;
    this.kafkaTemplate = kafkaTemplate;
    this.mapper = mapper;
    this.obdsV2ToV3Mapper = obdsMapper;
    this.deserializer = deserializer;
  }

  @EventListener
  public void processFromFileSystem(ApplicationReadyEvent readyEvent)
      throws IOException, InterruptedException {
    LOG.info("Processing oBDS files in folder {}", config.path());

    try (var stream = Files.list(Paths.get(config.path()))) {
      var files =
          stream.filter(file -> !Files.isDirectory(file)).sorted().collect(Collectors.toSet());

      for (var file : files) {
        MDC.put("fileName", file.getFileName().toString());
        LOG.info("Processing file");

        var xmlString = Files.readString(file);

        var obdsOrAdt = deserializer.deserializeAsObdsOrAdt(xmlString);

        OBDS obds = null;
        if (obdsOrAdt.hasADT()) {
          LOG.info("Mapping ADT_GEKID to oBDS v3 first.");
          var adt = obdsOrAdt.getAdt();
          obds = obdsV2ToV3Mapper.map(adt);
        } else if (obdsOrAdt.hasOBDS()) {
          obds = obdsOrAdt.getObds();
        } else {
          LOG.warn("No OBDS or ADT_GEKID found in file. Ignoring and continuing.");
          continue;
        }

        final var bundles = mapper.map(obds);
        for (var bundle : bundles) {
          LOG.info("Created FHIR bundle {}", bundle.getId());

          if (config.outputToKafka().enabled()) {
            try {
              var future =
                  kafkaTemplate.send(config.outputToKafka().topic(), bundle.getId(), bundle);
              future.get(60, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
              LOG.error("Sending message to Kafka failed", e);
            } catch (TimeoutException e) {
              LOG.error("Sending message to Kafka timed out", e);
            } catch (InterruptedException e) {
              LOG.error("Sending message to Kafka was interrupted", e);
              throw e;
            }
          }

          if (config.outputToDirectory().enabled()) {
            var filename = file.getFileName() + "-bundle-" + bundle.getId() + ".fhir.json";
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
      MDC.clear();
    }
    readyEvent.getApplicationContext().close();
  }
}
