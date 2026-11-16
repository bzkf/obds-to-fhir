package io.github.bzkf.obdstofhir;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class SubstanzToAtcMapper {
  private static final String ATC_CODE_VALID_UNTIL_COLUMN =
      "ATC-Code g\u00FCltig bis (WHO-ATC alterations)";

  private final Map<String, String> substanzToCode = new HashMap<>();
  private static CSVFormat csvFormat =
      CSVFormat.DEFAULT
          .builder()
          .setHeader()
          .setSkipHeaderRecord(true)
          .setDelimiter(";")
          .setCommentMarker('#')
          .get();

  @Value("${fhir.mappings.substanz-to-atc.extra-mappings-file-path}")
  private Optional<Path> extraMappingsFilePath;

  @PostConstruct
  public void init() throws IOException {
    var resource = new ClassPathResource("mappings/Umsetzungsleitfaden_Substanzen_2025-08.csv");

    try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
      loadMappings(reader, false);
    }

    if (extraMappingsFilePath.isPresent()) {
      try (var reader = Files.newBufferedReader(extraMappingsFilePath.get())) {
        // site-specific mappings always take precedence
        loadMappings(reader, true);
      }
    }
  }

  void loadMappings(Reader reader, boolean override) throws IOException {
    // whether the code currently stored for a Substanz comes from an expired row
    var storedIsExpired = new HashMap<String, Boolean>();
    for (var row : csvFormat.parse(reader)) {
      var substanz = clean(row.get("Substanzbezeichnung"));
      var code = clean(row.get("ATC-Code"));

      if (code.isEmpty() || "fehlt".equals(code)) {
        continue;
      }

      // The Umsetzungsleitfaden lists a Substanz once per ATC code it had, and marks codes
      // that WHO changed with an end date. The current code is the one without such a date,
      // which isn't necessarily the last row (e.g. Romidepsin, Sorafenib, Vismodegib).
      // Later rows still overwrite earlier ones, except that an expired row never replaces a
      // current one.
      var expired =
          row.isMapped(ATC_CODE_VALID_UNTIL_COLUMN)
              && !clean(row.get(ATC_CODE_VALID_UNTIL_COLUMN)).isEmpty();
      var storedIsCurrent =
          substanzToCode.containsKey(substanz) && !storedIsExpired.getOrDefault(substanz, false);

      if (override || !expired || !storedIsCurrent) {
        substanzToCode.put(substanz, code);
        storedIsExpired.put(substanz, expired);
      }
    }
  }

  /**
   * Trims the value, including non-breaking spaces, which {@link String#trim()} and {@link
   * String#strip()} both keep.
   */
  private static String clean(String value) {
    return value.replace('\u00A0', ' ').strip();
  }

  public Optional<String> getCode(@NonNull String substanz) {
    var code = substanzToCode.get(clean(substanz));
    return Optional.ofNullable(code);
  }
}
