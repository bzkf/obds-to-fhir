package org.miracum.streams.ume.onkoadttofhir.processor;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class OnkoProcessor {
  protected final FhirProperties fhirProperties;

  private static final Logger log = LoggerFactory.getLogger(OnkoProcessor.class);

  protected OnkoProcessor(final FhirProperties fhirProperties) {
    this.fhirProperties = fhirProperties;
  }

  protected String getHash(String type, String id) {
    String idToHash;
    switch (type) {
      case "Patient":
        idToHash = fhirProperties.getSystems().getPatientId();
        break;
      case "Condition":
        idToHash = fhirProperties.getSystems().getConditionId();
        break;
      case "Observation":
        idToHash = fhirProperties.getSystems().getObservationId();
        break;
      case "Surrogate":
        return Hashing.sha256().hashString(id, StandardCharsets.UTF_8).toString();
      default:
        return null;
    }
    return Hashing.sha256().hashString(idToHash + "|" + id, StandardCharsets.UTF_8).toString();
  }

  protected static String convertId(String id) {
    Pattern pattern = Pattern.compile("[^0]\\d{8}");
    Matcher matcher = pattern.matcher(id);
    var convertedId = "";
    if (matcher.find()) {
      convertedId = matcher.group();
    } else {
      log.warn("Identifier to convert does not have 9 digits without leading '0': " + id);
    }
    return convertedId;
  }
}
