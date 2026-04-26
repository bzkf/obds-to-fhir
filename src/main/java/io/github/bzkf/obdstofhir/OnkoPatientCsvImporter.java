package io.github.bzkf.obdstofhir;

import io.github.bzkf.obdstofhir.model.OnkoPatient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    value = "fhir.mappings.from-onkostar-patient-data.csv.enabled",
    havingValue = "true")
public class OnkoPatientCsvImporter {

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
      @Value("${spring.cloud.stream.bindings.getPatientTodObservationProcessor-in-0.destination}")
          String patientTopic) {
    this.kafkaTemplate = kafkaTemplate;
    this.csvFile = csvFile;
    this.patientTopic = patientTopic;
  }

  @EventListener
  public void processCsvDeathData(ApplicationReadyEvent readyEvent)
      throws IOException, InterruptedException, ExecutionException {
    if (csvFile == null || csvFile.isBlank()) {
      throw new IllegalStateException(
          "CSV file path is not configured. Please set "
              + "'fhir.mappings.from-onkostar-patient-data.csv.file' to a valid file path.");
    }

    var csvPath = Path.of(csvFile);
    if (!Files.exists(csvPath)) {
      throw new IllegalStateException("CSV file does not exist: " + csvPath);
    }
    if (!Files.isRegularFile(csvPath)) {
      throw new IllegalStateException("CSV path is not a regular file: " + csvPath);
    }

    LOG.info("Loading death CSV file: {}", csvPath);

    try (var reader = Files.newBufferedReader(csvPath);
        var parser = buildCsvFormat().parse(reader)) {

      validateRequiredHeaders(parser);

      for (var row : parser) {
        var onkoPatient = mapRecord(row);

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

  private OnkoPatient mapRecord(CSVRecord row) {
    var patientId = row.get(HEADER_PATIENT_ID);
    var letzteInformation = parseDate(row, HEADER_LETZTE_INFORMATION);
    var sterbeDatum = parseDate(row, HEADER_STERBEDATUM);

    if (patientId.isBlank()) {
      LOG.warn("Skipped CSV row {} because Patienten-ID is missing.", row.getRecordNumber());
      return null;
    }

    if (sterbeDatum == null) {
      LOG.info(
          "Skipped CSV row {} for patientId {} because Sterbedatum is missing or invalid.",
          row.getRecordNumber(),
          patientId);
      return null;
    }

    return OnkoPatient.builder()
        .patientId(patientId)
        .letzteInformation(letzteInformation)
        .sterbeDatum(sterbeDatum)
        .build();
  }

  private LocalDate parseDate(CSVRecord row, String header) {
    var value = row.get(header);

    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      return LocalDate.parse(value, DATE_FORMATTER);
    } catch (DateTimeParseException e) {
      LOG.warn(
          "Invalid date in CSV row {}, column '{}': '{}'", row.getRecordNumber(), header, value);
      return null;
    }
  }
}
