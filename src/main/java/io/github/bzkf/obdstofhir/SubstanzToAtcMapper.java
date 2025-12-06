package io.github.bzkf.obdstofhir;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStreamReader;
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
  private final Map<String, String> substanzToCode = new HashMap<>();
  private static CSVFormat csvFormat =
      CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setDelimiter(";").get();

  @Value("${fhir.mappings.substanz-to-atc.extra-mappings-file-path}")
  private Optional<Path> extraMappingsFilePath;

  @PostConstruct
  public void init() throws IOException {
    var resource = new ClassPathResource("mappings/Umsetzungsleitfaden_Substanzen_2025-08.csv");

    try (var reader = new InputStreamReader(resource.getInputStream())) {
      for (var row : csvFormat.parse(reader)) {
        var substanz = row.get("Substanzbezeichnung").trim();
        var code = row.get("ATC-Code").trim();

        if (!code.equals("fehlt")) {
          // this will automatically overwrite duplicates with the latest entry in the
          // CSV, which should be the most recent one.
          substanzToCode.put(substanz, code);
        }
      }
    }

    if (this.extraMappingsFilePath.isPresent()) {
      try (var reader = Files.newBufferedReader(extraMappingsFilePath.get())) {
        for (var row : csvFormat.parse(reader)) {
          var substanz = row.get("Substanzbezeichnung").trim();
          var code = row.get("ATC-Code").trim();

          if (!code.equals("fehlt")) {
            // this will automatically overwrite duplicates with the latest entry in the
            // CSV, which should be the most recent one.
            substanzToCode.put(substanz, code);
          }
        }
      }
    }
  }

  public Optional<String> getCode(@NonNull String substanz) {
    var code = substanzToCode.get(substanz.trim());
    return Optional.ofNullable(code);
  }
}
