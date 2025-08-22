package org.miracum.streams.ume.obdstofhir;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.basisdatensatz.obds.v3.OBDS;
import dev.pcvolkmer.onko.obds2to3.ObdsMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportListV3;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportV3;
import org.miracum.streams.ume.obdstofhir.model.ObdsOrAdt;
import org.miracum.streams.ume.obdstofhir.processor.MeldungTransformationService;
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

  private final ObjectMapper objectMapper = new ObjectMapper();

  private ProcessFromDirectoryConfig config;
  private KafkaTemplate<String, IBaseResource> kafkaTemplate;
  private Obdsv3Deserializer deserializer;
  private MeldungTransformationService meldungTransformationService;
  private ObdsMapper obdsV2ToV3Mapper;

  public ProcessFromDirectory(
      ProcessFromDirectoryConfig config,
      KafkaTemplate<String, IBaseResource> kafkaTemplate,
      ObdsMapper obdsMapper,
      Obdsv3Deserializer deserializer,
      MeldungTransformationService meldungTransformationService) {
    this.config = config;
    this.kafkaTemplate = kafkaTemplate;
    this.obdsV2ToV3Mapper = obdsMapper;
    this.meldungTransformationService = meldungTransformationService;
    this.deserializer = deserializer;
  }

  @EventListener
  public void processFromFileSystem(ApplicationReadyEvent readyEvent)
      throws IOException, InterruptedException {
    LOG.info("Processing oBDS files in folder {}", config.path());

    try (var stream = Files.list(Paths.get(config.path()))) {
      var files =
          stream.filter(file -> !Files.isDirectory(file)).sorted().collect(Collectors.toSet());

      MeldungExportListV3 meldungExportV3List = new MeldungExportListV3();

      for (var file : files) {
        MDC.put("fileName", file.getFileName().toString());
        LOG.info("Processing file");

        var xmlString = Files.readString(file);

        var obdsOrAdt = deserializer.deserializeAsObdsOrAdt(xmlString);

        MeldungExportV3 meldung = new MeldungExportV3();
        if (obdsOrAdt.hasOBDS()) {
          meldung.setObdsOrAdt(obdsOrAdt);
        } else if (obdsOrAdt.hasADT()) {
          var adt = obdsOrAdt.getAdt();
          var obds = obdsV2ToV3Mapper.map(adt);
          meldung.setObdsOrAdt(ObdsOrAdt.from(obds));
        } else {
          LOG.warn("No OBDS or ADT_GEKID found in file. Ignoring and continuing.");
          continue;
        }

        for (var einzelMeldung : splitSammelmeldungen(meldung)) {
          meldungExportV3List.addElement(einzelMeldung);
        }
      }

      List<Bundle> bundles = meldungTransformationService.processDirMeldungen(meldungExportV3List);

      for (var bundle : bundles) {
        LOG.info("Created FHIR bundle {}", bundle.getId());

        if (config.outputToKafka().enabled()) {
          try {
            var future = kafkaTemplate.send(config.outputToKafka().topic(), bundle.getId(), bundle);
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
      MDC.clear();
    }
    readyEvent.getApplicationContext().close();
  }

  private List<MeldungExportV3> splitSammelmeldungen(MeldungExportV3 sammelMeldung) {
    List<MeldungExportV3> einzelMeldungResult = new ArrayList<>();

    if (sammelMeldung.getObdsOrAdt().hasOBDS() && sammelMeldung.getObdsOrAdt().hasADT()) {
      return einzelMeldungResult; // no obds or adt
    }

    var obds = sammelMeldung.getObds();
    var adt = sammelMeldung.getObdsOrAdt().getAdt();
    if ((adt == null || adt.getMengePatient() == null)
        && (obds == null || obds.getMengePatient() == null)) {
      return einzelMeldungResult; // no patients
    }

    for (var patient : sammelMeldung.getObdsOrAdt().getObds().getMengePatient().getPatient()) {
      for (var meldung : patient.getMengeMeldung().getMeldung()) {

        // copy obds
        var obdsCopy = objectMapper.convertValue(obds, OBDS.class);

        // find corresponding patient in copy
        var patientCopy =
            obdsCopy.getMengePatient().getPatient().stream()
                .filter(p -> p.getPatientID().equals(patient.getPatientID()))
                .findFirst()
                .orElseThrow();

        // replace MengeMeldung with single meldung
        patientCopy.getMengeMeldung().getMeldung().clear();
        patientCopy.getMengeMeldung().getMeldung().add(meldung);

        // replace patient
        obdsCopy.getMengePatient().getPatient().clear();
        obdsCopy.getMengePatient().getPatient().add(patientCopy);

        var newMeldung =
            MeldungExportV3.builder()
                .id(String.valueOf(Math.random()))
                .referenzNummer(patient.getPatientID())
                .lkrMeldung(meldung.getMeldungID())
                .versionsnummer(1)
                .obdsOrAdt(ObdsOrAdt.from(obdsCopy))
                .build();

        einzelMeldungResult.add(newMeldung);
      }
    }
    return einzelMeldungResult;
  }
}
