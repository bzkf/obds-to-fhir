package io.github.bzkf.obdstofhir;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.hl7.fhir.r4.model.Coding;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WeitereKlassifikationCodingMapper {
  private final Map<MappingKey, ObservationCodeValue> map = new HashMap<>();

  private static final CSVFormat CSV_FORMAT =
      CSVFormat.DEFAULT
          .builder()
          .setHeader()
          .setSkipHeaderRecord(true)
          .setDelimiter(';')
          .setCommentMarker('#')
          .get();

  private record MappingKey(String name, String einstufung) {}

  public record ObservationCodeValue(Coding code, Optional<Coding> value) {}

  @Value("${fhir.mappings.weitere-klassifikationen.extra-mappings-file-path}")
  private Optional<Path> extraMappingsFilePath;

  public WeitereKlassifikationCodingMapper() {
    // no-args constructor
  }

  @PostConstruct
  public void init() throws IOException {
    var resource = new ClassPathResource("mappings/weitere-klassifikationen-mappings.csv");

    try (var reader = new InputStreamReader(resource.getInputStream())) {
      loadMappings(reader);
    }

    if (extraMappingsFilePath.isPresent()) {
      try (var reader = Files.newBufferedReader(extraMappingsFilePath.get())) {
        loadMappings(reader);
      }
    }
  }

  private void loadMappings(Reader reader) throws IOException {
    for (var row : CSV_FORMAT.parse(reader)) {
      var name = row.get("name").trim();
      var einstufung = row.get("stadium").trim();

      var codeSystem = row.get("code_system").trim();
      var codeVersion = row.get("code_version").trim();
      var codeCode = row.get("code_code").trim();
      var codeDisplay = row.get("code_display").trim();

      var valueSystem = row.get("value_system").trim();
      var valueVersion = row.get("value_version").trim();
      var valueCode = row.get("value_code").trim();
      var valueDisplay = row.get("value_display").trim();

      if (!StringUtils.hasText(codeCode)) {
        continue;
      }

      var code = new Coding(codeSystem, codeCode, codeDisplay).setVersion(codeVersion);

      Coding value = null;
      if (StringUtils.hasText(valueCode)) {
        value = new Coding(valueSystem, valueCode, valueDisplay).setVersion(valueVersion);
      }

      map.put(
          new MappingKey(name, einstufung),
          new ObservationCodeValue(code, Optional.ofNullable(value)));
    }
  }

  public Optional<ObservationCodeValue> lookup(@NonNull String name, @NonNull String einstufung) {
    return Optional.ofNullable(map.get(new MappingKey(name, einstufung)));
  }
}
