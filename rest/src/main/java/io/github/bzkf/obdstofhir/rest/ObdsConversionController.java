package io.github.bzkf.obdstofhir.rest;

import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obds2toobds3.ObdsMapper;
import io.github.bzkf.obdstofhir.config.RestServiceConfig;
import io.github.bzkf.obdstofhir.mapper.mii.ObdsToFhirBundleMapper;
import io.github.bzkf.obdstofhir.serde.Obdsv3Deserializer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;

/** Converts a single oBDS v3 (or ADT_GEKID v2) XML export into a FHIR Bundle. */
@RestController
public class ObdsConversionController {
  private static final Logger LOG = LoggerFactory.getLogger(ObdsConversionController.class);

  private final Obdsv3Deserializer deserializer;
  private final ObdsMapper obdsV2ToV3Mapper;
  private final ObdsToFhirBundleMapper mapper;
  private final RestServiceConfig config;
  private final KafkaTemplate<String, IBaseResource> kafkaTemplate;

  public ObdsConversionController(
      Obdsv3Deserializer deserializer,
      ObdsMapper obdsV2ToV3Mapper,
      ObdsToFhirBundleMapper mapper,
      RestServiceConfig config,
      KafkaTemplate<String, IBaseResource> kafkaTemplate) {
    this.deserializer = deserializer;
    this.obdsV2ToV3Mapper = obdsV2ToV3Mapper;
    this.mapper = mapper;
    this.config = config;
    this.kafkaTemplate = kafkaTemplate;
  }

  @PostMapping(
      path = "/fhir/convert",
      consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE},
      produces = {"application/fhir+json", "application/fhir+xml"})
  public ResponseEntity<Bundle> convert(@RequestBody String xml) {
    var obdsOrAdt = deserializer.deserializeAsObdsOrAdt(xml);

    OBDS obds;
    if (obdsOrAdt.hasADT()) {
      LOG.info("Mapping ADT_GEKID to oBDS v3 first.");
      obds = obdsV2ToV3Mapper.map(obdsOrAdt.getAdt());
    } else if (obdsOrAdt.hasOBDS()) {
      obds = obdsOrAdt.getObds();
    } else {
      throw new IllegalArgumentException("No OBDS or ADT_GEKID element found in the request body.");
    }

    var bundles = mapper.map(obds);

    if (config.outputToKafka().enabled()) {
      bundles.forEach(this::sendToKafka);
    }

    // the common case is a single patient per request; only wrap the per-patient transaction
    // Bundles in an outer collection Bundle when there's more than one, unless configured to
    // always wrap for a consistent response shape.
    Bundle response;
    if (bundles.size() == 1 && !config.wrapSinglePatientBundle()) {
      response = bundles.getFirst();
    } else {
      response = new Bundle();
      response.setType(Bundle.BundleType.COLLECTION);
      bundles.forEach(bundle -> response.addEntry().setResource(bundle));
    }

    return ResponseEntity.ok(response);
  }

  private void sendToKafka(Bundle bundle) {
    try {
      var future = kafkaTemplate.send(config.outputToKafka().topic(), bundle.getId(), bundle);
      future.get(60, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      LOG.error("Sending message to Kafka failed", e);
    } catch (TimeoutException e) {
      LOG.error("Sending message to Kafka timed out", e);
    } catch (InterruptedException e) {
      LOG.error("Sending message to Kafka was interrupted", e);
      Thread.currentThread().interrupt();
    }
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
    LOG.warn("Rejecting request: {}", e.getMessage());
    return ResponseEntity.badRequest().body(e.getMessage());
  }

  @ExceptionHandler(JacksonException.class)
  public ResponseEntity<String> handleUnparsableXml(JacksonException e) {
    LOG.warn("Rejecting request: unparsable XML", e);
    return ResponseEntity.badRequest()
        .body("Unable to parse the oBDS/ADT_GEKID XML: " + e.getMessage());
  }
}
