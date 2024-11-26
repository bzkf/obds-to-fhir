package org.miracum.streams.ume.obdstofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.basisdatensatz.obds.v3.AllgemeinerLeistungszustand;
import de.basisdatensatz.obds.v3.DatumTagOderMonatGenauTyp;
import de.basisdatensatz.obds.v3.DatumTagOderMonatGenauTyp.DatumsgenauigkeitTagOderMonatGenau;
import java.time.DateTimeException;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hl7.fhir.r4.model.DateTimeType;
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
  void extractDateTimeFromADTDate_withGivenObdsDate_shouldConvertToExpectedFhirDateTime(
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
  void convertObdsDateToDateTimeTypeShouldThrowExceptionOnUnparsableDateString() {
    assertThrows(
        DateTimeException.class,
        () -> ObdsToFhirMapper.convertObdsDateToDateTimeType("some shiny day somewere"));
  }

  @ParameterizedTest
  @CsvSource({
    "12345,12345",
    "123456789,123456789",
    "1234567890,123456789", // Max 9 digits, remove last digit '0'
    "1234567891,123456789", // Max 9 digits, remove last digit '1'
    "0000012345,0000012345", // Not mathching pattern - keep as is
    "G1234,G1234", // Not mathching pattern - keep as is
    "G123456,G123456", // Not matching pattern - keep as is
    "G123456789,G12345678", // return Non-zero and 8 digits
  })
  void convertPatientIdWithDefaultPattern(String input, String output) {
    var actual = ObdsToFhirMapper.convertId(input);
    assertThat(actual).isEqualTo(output);
  }

  @ParameterizedTest
  @MethodSource("icd10GmCodeValidationData")
  void checkValueToMatchIcd10Pattern(String input, boolean valid) {
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
  @MethodSource("obdsDatumToDateTimeTypeData")
  void shouldConvertDatumTagOderMonatGenauTypToDateTimeType(
      XMLGregorianCalendar sourceDate,
      DatumsgenauigkeitTagOderMonatGenau genauigkeit,
      DateTimeType expected) {
    var data = new DatumTagOderMonatGenauTyp();
    data.setValue(sourceDate);
    data.setDatumsgenauigkeit(genauigkeit);

    var actual = ObdsToFhirMapper.convertObdsDatumToDateTimeType(data);

    assertThat(actual)
        .hasValueSatisfying(
            dateTime ->
                assertThat(dateTime.getValueAsString()).isEqualTo(expected.getValueAsString()));
  }

  private static Stream<Arguments> obdsDatumToDateTimeTypeData() {
    return Stream.of(
        Arguments.of(
            DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar("2024-11-21"),
            DatumsgenauigkeitTagOderMonatGenau.E,
            DateTimeType.parseV3("20241121")),
        Arguments.of(
            DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar("2024-11-21"),
            DatumsgenauigkeitTagOderMonatGenau.T,
            DateTimeType.parseV3("202411")));
  }

  @Test
  void shouldConvertCalendarToDateTimeType() {
    var actual =
        ObdsToFhirMapper.convertObdsDatumToDateTimeType(
            DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar("2024-11-21"));

    assertThat(actual)
        .hasValueSatisfying(
            dateTime ->
                assertThat(dateTime.getValueAsString())
                    .isEqualTo(DateTimeType.parseV3("20241121").getValueAsString()));
  }

  @Test
  void shouldNotConvertCalendarToDateTimeTypeFromNull() {
    var actual = ObdsToFhirMapper.convertObdsDatumToDateTimeType((XMLGregorianCalendar) null);

    assertThat(actual).isEmpty();
  }

  @Test
  void shouldNotConvertDatumTagOderMonatGenauTypToDateTimeTypeFromNull() {
    var actual = ObdsToFhirMapper.convertObdsDatumToDateTimeType((DatumTagOderMonatGenauTyp) null);

    assertThat(actual).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("allgemeinerLeistungszustandTestData")
  void shouldMapAllegemeinerLeistungszustandToEcog(
      AllgemeinerLeistungszustand in, AllgemeinerLeistungszustand expected) {
    var actual = ObdsConditionMapper.allgemeinerLeistungszustandToEcog(in);
    assertThat(actual).isEqualTo(expected);
  }

  static Stream<Arguments> allgemeinerLeistungszustandTestData() {
    return Stream.of(
        Arguments.of(AllgemeinerLeistungszustand.KARNOFSKY_100, AllgemeinerLeistungszustand.ECOG_0),
        Arguments.of(AllgemeinerLeistungszustand.KARNOFSKY_90, AllgemeinerLeistungszustand.ECOG_0),
        Arguments.of(AllgemeinerLeistungszustand.KARNOFSKY_80, AllgemeinerLeistungszustand.ECOG_1),
        Arguments.of(AllgemeinerLeistungszustand.KARNOFSKY_70, AllgemeinerLeistungszustand.ECOG_1),
        Arguments.of(AllgemeinerLeistungszustand.KARNOFSKY_60, AllgemeinerLeistungszustand.ECOG_2),
        Arguments.of(AllgemeinerLeistungszustand.KARNOFSKY_50, AllgemeinerLeistungszustand.ECOG_2),
        Arguments.of(AllgemeinerLeistungszustand.KARNOFSKY_40, AllgemeinerLeistungszustand.ECOG_3),
        Arguments.of(AllgemeinerLeistungszustand.KARNOFSKY_30, AllgemeinerLeistungszustand.ECOG_3),
        Arguments.of(AllgemeinerLeistungszustand.KARNOFSKY_20, AllgemeinerLeistungszustand.ECOG_4),
        Arguments.of(AllgemeinerLeistungszustand.KARNOFSKY_10, AllgemeinerLeistungszustand.ECOG_4),
        Arguments.of(AllgemeinerLeistungszustand.KARNOFSKY_40, AllgemeinerLeistungszustand.ECOG_3),
        Arguments.of(AllgemeinerLeistungszustand.U, AllgemeinerLeistungszustand.U),
        Arguments.of(AllgemeinerLeistungszustand.ECOG_0, AllgemeinerLeistungszustand.ECOG_0),
        Arguments.of(AllgemeinerLeistungszustand.ECOG_1, AllgemeinerLeistungszustand.ECOG_1),
        Arguments.of(AllgemeinerLeistungszustand.ECOG_2, AllgemeinerLeistungszustand.ECOG_2),
        Arguments.of(AllgemeinerLeistungszustand.ECOG_3, AllgemeinerLeistungszustand.ECOG_3),
        Arguments.of(AllgemeinerLeistungszustand.ECOG_4, AllgemeinerLeistungszustand.ECOG_4));
  }
}
