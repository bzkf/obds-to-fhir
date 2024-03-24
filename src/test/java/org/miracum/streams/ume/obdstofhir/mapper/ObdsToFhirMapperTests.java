package org.miracum.streams.ume.obdstofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ObdsToFhirMapperTests {
  @ParameterizedTest
  @CsvSource({
    "01.12.2003,2003-12-01",
    "00.00.2003,2003-07-01",
    "00.11.2003,2003-11-15",
    "31.03.2022,2022-03-31"
  })
  void extractDateTimeFromADTDate_withGivenObdsDate_shouldConvertToExpectedFhirDateTime(
      String obdsDate, String expectedFhirDateTimeString) {
    var fhirDate = ObdsToFhirMapper.extractDateTimeFromADTDate(obdsDate);

    assertThat(fhirDate.asStringValue()).isEqualTo(expectedFhirDateTimeString);
  }
}
