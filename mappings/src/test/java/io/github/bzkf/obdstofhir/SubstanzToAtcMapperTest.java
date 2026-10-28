package io.github.bzkf.obdstofhir;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

class SubstanzToAtcMapperTest {
  private static final String UMSETZUNGSLEITFADEN =
      "mappings/Umsetzungsleitfaden_Substanzen_2025-08.csv";
  private static final Pattern ATC_CODE =
      Pattern.compile("^[A-Z][0-9]{2}(?:[A-Z](?:[A-Z](?:[0-9]{2})?)?)?$");

  private SubstanzToAtcMapper sut;

  @BeforeEach
  void setUp() throws IOException {
    sut = new SubstanzToAtcMapper();
    try (var reader = umsetzungsleitfaden()) {
      sut.loadMappings(reader, false);
    }
  }

  private static InputStreamReader umsetzungsleitfaden() throws IOException {
    return new InputStreamReader(
        new ClassPathResource(UMSETZUNGSLEITFADEN).getInputStream(), StandardCharsets.UTF_8);
  }

  @ParameterizedTest
  @CsvSource({
    // listed with their current code first and an expired one (end date 31122020) after it
    "Romidepsin,L01XH02",
    "Sorafenib,L01EX02",
    "Vismodegib,L01XJ01",
    // only one code
    "Abarelix,L02BX01",
  })
  void getCode_withSeveralCodesForOneSubstanz_shouldReturnTheCurrentCode(
      String substanz, String expected) {
    assertThat(sut.getCode(substanz)).hasValue(expected);
  }

  @Test
  void getCode_forEverySubstanzOfTheUmsetzungsleitfaden_shouldReturnAValidAtcCode()
      throws IOException {
    var format =
        CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setDelimiter(";").get();
    try (var reader = umsetzungsleitfaden()) {
      for (var row : format.parse(reader)) {
        var substanz = row.get("Substanzbezeichnung");
        sut.getCode(substanz)
            .ifPresent(code -> assertThat(code).as("ATC code of %s", substanz).matches(ATC_CODE));
      }
    }
  }

  @Test
  void loadMappings_withNonBreakingSpaces_shouldRemoveThem() throws IOException {
    sut.loadMappings(
        new StringReader("Substanzbezeichnung;ATC-Code\nTestsubstanz\u00A0;\u00A0L01XX99\u00A0\n"),
        true);

    assertThat(sut.getCode("Testsubstanz")).hasValue("L01XX99");
  }

  @Test
  void loadMappings_withExtraMappings_shouldOverrideTheUmsetzungsleitfaden() throws IOException {
    sut.loadMappings(new StringReader("Substanzbezeichnung;ATC-Code\nRomidepsin;L01XX39\n"), true);

    assertThat(sut.getCode("Romidepsin")).hasValue("L01XX39");
  }
}
