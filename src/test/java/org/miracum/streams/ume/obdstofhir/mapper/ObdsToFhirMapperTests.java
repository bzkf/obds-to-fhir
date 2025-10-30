package org.miracum.streams.ume.obdstofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID;
import org.miracum.streams.ume.obdstofhir.model.Meldeanlass;
import org.miracum.streams.ume.obdstofhir.model.MeldungExport;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportList;

class ObdsToFhirMapperTests {

  private static MeldungExport createMeldungExport(
      String meldungId, int versionsNummer, Meldeanlass meldeanlass) {
    // TODO: we might want to introduce more concise builder patterns
    var meldung = new ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung();
    meldung.setMeldung_ID("id-" + meldungId);
    meldung.setMeldeanlass(meldeanlass);

    var mengeMeldung = new ADT_GEKID.Menge_Patient.Patient.Menge_Meldung();
    mengeMeldung.setMeldung(meldung);

    var patient = new ADT_GEKID.Menge_Patient.Patient();
    patient.setMenge_Meldung(mengeMeldung);

    var mengePatient = new ADT_GEKID.Menge_Patient();
    mengePatient.setPatient(patient);

    var obdsData = new ADT_GEKID();
    obdsData.setMenge_Patient(mengePatient);

    var meldungExport = new MeldungExport();
    meldungExport.setXml_daten(obdsData);
    meldungExport.setVersionsnummer(Integer.valueOf(versionsNummer));

    return meldungExport;
  }

  @ParameterizedTest
  @CsvSource({
    "01.12.2003,2003-12-01",
    "00.00.2003,2003-07-01",
    "00.11.2003,2003-11-15",
    "31.03.2022,2022-03-31",
    "2019-12-21+02:00,2019-12-21",
    "2000-01-03-01:00,2000-01-03",
    "1999-12-31+08:00,1999-12-31",
    "2024-08-17,2024-08-17",
    "2024-08-00,2024-08-15",
    "2024-00-00,2024-07-01"
  })
  void convertObdsDateToDateTimeType_withGivenObdsDate_shouldConvertToExpectedFhirDateTime(
      String obdsDate, String expectedFhirDateTimeString) {
    var fhirDate = ObdsToFhirMapper.convertObdsDateToDateTimeType(obdsDate);

    assertThat(fhirDate).isNotNull();
    assertThat(fhirDate.asStringValue()).isEqualTo(expectedFhirDateTimeString);
  }

  @Test
  void
      prioritiseLatestMeldungExports_withSamePrioriyOrderUsedForMappingThePatientResource_shouldHandleMultipleDeathReports() {
    var expectedFirstDeathMeldung = createMeldungExport("3", 5, Meldeanlass.TOD);

    var meldungExports = new MeldungExportList();
    meldungExports.addElement(createMeldungExport("0", 1, Meldeanlass.DIAGNOSE));
    meldungExports.addElement(createMeldungExport("1", 1, Meldeanlass.BEHANDLUNGSBEGINN));
    meldungExports.addElement(createMeldungExport("2", 1, Meldeanlass.BEHANDLUNGSENDE));
    meldungExports.addElement(createMeldungExport("3", 1, Meldeanlass.TOD));
    meldungExports.addElement(createMeldungExport("4", 3, Meldeanlass.BEHANDLUNGSBEGINN));
    meldungExports.addElement(expectedFirstDeathMeldung);
    meldungExports.addElement(createMeldungExport("6", 7, Meldeanlass.BEHANDLUNGSENDE));
    meldungExports.addElement(createMeldungExport("0", 2, Meldeanlass.DIAGNOSE));
    meldungExports.addElement(createMeldungExport("3", 3, Meldeanlass.TOD));

    var prioritised =
        ObdsToFhirMapper.prioritiseLatestMeldungExports(
            meldungExports,
            Arrays.asList(
                Meldeanlass.TOD,
                Meldeanlass.BEHANDLUNGSENDE,
                Meldeanlass.STATUSAENDERUNG,
                Meldeanlass.DIAGNOSE),
            null);

    assertThat(prioritised).first().isEqualTo(expectedFirstDeathMeldung);
  }

  @Test
  void convertObdsDateToDateTimeTypeShouldReturnNullOnUnparsableDateString() {
    var parsed = ObdsToFhirMapper.convertObdsDateToDateTimeType("00.00.0000");
    assertThat(parsed).isNull();
  }

  @ParameterizedTest
  @MethodSource("icd10GmCodeValidationData")
  void checkValueToMatchIcd10CodePattern(String input, boolean valid) {
    var actual = ObdsToFhirMapper.isIcd10GmCode(input);
    assertThat(actual).isEqualTo(valid);
  }

  private static Stream<Arguments> icd10GmCodeValidationData() {
    return Stream.of(
        Arguments.of("C00.0", true),
        Arguments.of("C37", true),
        Arguments.of("C79.88", true),
        Arguments.of("C88.20", true),
        Arguments.of("C30.0", true),
        Arguments.of("D00.0", true),
        Arguments.of("D00.000", false),
        Arguments.of("CC0.0", false),
        Arguments.of("", false),
        Arguments.of(null, false));
  }

  @ParameterizedTest
  @MethodSource("icd10VersionValidationResult")
  void checkValueToMatchIcd10VersionPattern(String input, boolean valid) {
    var actual = ObdsToFhirMapper.isIcd10VersionString(input);
    assertThat(actual).isEqualTo(valid);
  }

  @ParameterizedTest
  @MethodSource("icd10VersionYearResult")
  void shouldExtractYearFromIcd10Version(String input, Optional<String> year) {
    var actual = ObdsToFhirMapper.getIcd10VersionYear(input);
    assertThat(actual).isEqualTo(year);
  }

  private static Stream<Arguments> icd10VersionValidationResult() {
    return Stream.of(
        Arguments.of("10 2019 GM", true),
        Arguments.of("10 2017 GM", true),
        Arguments.of("10 2024 WHO", true),
        Arguments.of("Sonstige", true),
        Arguments.of("10 2024 Other", false),
        Arguments.of("", false),
        Arguments.of(null, false));
  }

  private static Stream<Arguments> icd10VersionYearResult() {
    return Stream.of(
        Arguments.of("10 2019 GM", Optional.of("2019")),
        Arguments.of("10 2017 GM", Optional.of("2017")),
        Arguments.of("10 2024 WHO", Optional.of("2024")),
        Arguments.of("Sonstige", Optional.empty()),
        Arguments.of("10 2024 Other", Optional.empty()),
        Arguments.of("", Optional.empty()),
        Arguments.of(null, Optional.empty()));
  }
}
