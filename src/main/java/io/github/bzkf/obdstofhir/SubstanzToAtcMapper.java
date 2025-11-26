package io.github.bzkf.obdstofhir;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class SubstanzToAtcMapper {
  private final Map<String, String> substanzToCode = new HashMap<>();

  @PostConstruct
  public void init() throws IOException {
    var resource = new ClassPathResource("mappings/Umsetzungsleitfaden_Substanzen_2025-08.csv");

    try (var reader = new InputStreamReader(resource.getInputStream())) {

      var format =
          CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setDelimiter(";").get();

      for (var row : format.parse(reader)) {
        var substanz = row.get("Substanzbezeichnung");
        var code = row.get("ATC-Code");

        // this will automatically overwrite duplicates with the latest entry in the
        // CSV, which should be the most recent one.
        if (!code.equals("fehlt")) {
          substanzToCode.put(substanz, code);
        }
      }
    }
  }

  public Optional<String> getCode(String substanz) {
    var code = substanzToCode.get(substanz);
    return Optional.ofNullable(code);
  }
}
