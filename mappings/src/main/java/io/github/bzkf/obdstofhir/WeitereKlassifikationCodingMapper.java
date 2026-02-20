package io.github.bzkf.obdstofhir;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.csv.CSVFormat;
import org.hl7.fhir.r4.model.Coding;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WeitereKlassifikationCodingMapper {
  private final MultiKeyMap<String, ObservationCodeValue> map = new MultiKeyMap<>();
  private static CSVFormat csvFormat =
      CSVFormat.DEFAULT
          .builder()
          .setHeader()
          .setSkipHeaderRecord(true)
          .setDelimiter(";")
          .setCommentMarker('#')
          .get();

  public record ObservationCodeValue(Coding code, Coding value) {}

  @Value("${fhir.mappings.weitere-klassifikationen.extra-mappings-file-path}")
  private Optional<Path> extraMappingsFilePath;

  private final FhirProperties fhirProperties;

  public WeitereKlassifikationCodingMapper(FhirProperties fhirProperties) {
    this.fhirProperties = fhirProperties;
  }

  @PostConstruct
  public void init() throws IOException {
    var resource = new ClassPathResource("mappings/weitere-klassifikationen-mappings.csv");

    try (var reader = new InputStreamReader(resource.getInputStream())) {
      for (var row : csvFormat.parse(reader)) {
        var name = row.get("name").trim();
        var einstufung = row.get("stadium").trim();
        var snomedCode = row.get("snomed_code").trim();
        var snomedDisplay = row.get("snomed_display").trim();
        var valueSnomedCode = row.get("value_snomed_code").trim();
        var valueSnomedDisplay = row.get("value_snomed_display").trim();

        if (StringUtils.hasText(snomedCode)) {
          var code =
              fhirProperties.getCodings().snomed().setCode(snomedCode).setDisplay(snomedDisplay);
          var value =
              fhirProperties
                  .getCodings()
                  .snomed()
                  .setCode(valueSnomedCode)
                  .setDisplay(valueSnomedDisplay);

          map.put(name, einstufung, new ObservationCodeValue(code, value));
        }
      }
    }

    if (this.extraMappingsFilePath.isPresent()) {
      try (var reader = Files.newBufferedReader(extraMappingsFilePath.get())) {
        for (var row : csvFormat.parse(reader)) {
          var name = row.get("name").trim();
          var einstufung = row.get("stadium").trim();
          var snomedCode = row.get("snomed_code").trim();
          var snomedDisplay = row.get("snomed_display").trim();
          var valueSnomedCode = row.get("value_snomed_code").trim();
          var valueSnomedDisplay = row.get("value_snomed_display").trim();

          if (StringUtils.hasText(snomedCode)) {
            var code =
                fhirProperties.getCodings().snomed().setCode(snomedCode).setDisplay(snomedDisplay);
            var value =
                fhirProperties
                    .getCodings()
                    .snomed()
                    .setCode(valueSnomedCode)
                    .setDisplay(valueSnomedDisplay);

            map.put(name, einstufung, new ObservationCodeValue(code, value));
          }
        }
      }
    }
  }

  public Optional<ObservationCodeValue> lookup(@NonNull String name, @NonNull String einstufung) {
    var result = map.get(name, einstufung);
    return Optional.ofNullable(result);
  }
}
