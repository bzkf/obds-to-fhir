package io.github.bzkf.obdstofhir;

import io.github.bzkf.obdstofhir.model.OnkoPatient;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    value = "fhir.mappings.from-onkostar-patient-data.csv.enabled",
    havingValue = "true")
public class OnkoPatientCsvImporter implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(OnkoPatientCsvImporter.class);

  // default csv structure onkostar-export "best-of-tumor"
  private static final String HEADER_PATIENT_ID = "Patienten-ID";
  private static final String HEADER_LETZTE_INFORMATION = "Letzte Information (LetzteInformation)";
  private static final String HEADER_STERBEDATUM = "Sterbedatum";
  private static final char DELIMITER = ';';
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final KafkaTemplate<String, OnkoPatient> kafkaTemplate;
  private final String csvFile;
  private final String patientTopic;

  public OnkoPatientCsvImporter(
      KafkaTemplate<String, OnkoPatient> kafkaTemplate,
      @Value("${fhir.mappings.from-onkostar-patient-data.csv.file}") String csvFile,
      @Value("${PATIENT_INPUT_TOPIC_NAME:onkostar.PATIENT}") String patientTopic) {
    this.kafkaTemplate = kafkaTemplate;
    this.csvFile = csvFile;
    this.patientTopic = patientTopic;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    var csvPath = Path.of(csvFile);
    if (!Files.exists(csvPath)) {
      throw new IllegalStateException("CSV file does not exist: " + csvPath);
    }

    try (Reader reader = Files.newBufferedReader(csvPath);
        CSVParser parser = buildCsvFormat().parse(reader)) {

      validateRequiredHeaders(parser);

      for (CSVRecord record : parser) {
        var onkoPatient = mapRecord(record);

        if (onkoPatient == null) {
          continue;
        }

        var sendResult = kafkaTemplate.send(patientTopic, onkoPatient.getPatientId(), onkoPatient);
        sendResult.get();
      }
    }
  }

  private CSVFormat buildCsvFormat() {
    return CSVFormat.DEFAULT
        .builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setDelimiter(DELIMITER)
        .setTrim(true)
        .get();
  }

  private void validateRequiredHeaders(CSVParser parser) {
    var headerMap = parser.getHeaderMap();
    for (var requiredHeader :
        Set.of(HEADER_PATIENT_ID, HEADER_LETZTE_INFORMATION, HEADER_STERBEDATUM)) {
      if (!headerMap.containsKey(requiredHeader)) {
        throw new IllegalStateException("Missing required CSV header: " + requiredHeader);
      }
    }
  }

  private OnkoPatient mapRecord(CSVRecord record) {
    var patientId = record.get(HEADER_PATIENT_ID);
    var letzteInformation = parseDate(record.get(HEADER_LETZTE_INFORMATION));
    var sterbeDatum = parseDate(record.get(HEADER_STERBEDATUM));

    if (patientId.isBlank()) {
      LOG.warn("Skip CSV row {} because Patienten-ID is missing.", record.getRecordNumber());
      return null;
    }

    if (sterbeDatum == null) {
      LOG.debug(
          "Skip CSV row {} for patientId {} because Sterbedatum is missing",
          record.getRecordNumber(),
          patientId);
      return null;
    }

    return OnkoPatient.builder()
        .patientId(patientId)
        .letzteInformation(letzteInformation)
        .sterbeDatum(sterbeDatum)
        .build();
  }

  private LocalDate parseDate(String value) {
    if (value == null) {
      return null;
    }
    return LocalDate.parse(value, DATE_FORMATTER);
  }
}
