package org.miracum.streams.ume.obdstofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.basisdatensatz.obds.v3.OBDS;
import org.junit.jupiter.api.Test;

class MapperUtilsTest {

  @Test
  void shouldUseRunnableForNullValue() {
    String testingValue = null;
    var patient = new OBDS.MengePatient.Patient();

    MapperUtils.ifNull(
        testingValue,
        () -> {
          // Set the patient ID to "UNKNOWN", if the testing value is NULL
          patient.setPatientID("UNKNOWN");
        });

    assertThat(patient.getPatientID()).isEqualTo("UNKNOWN");
  }

  @Test
  void shouldThrowExceptionInRunnableForNullValue() {
    String testingValue = null;

    var exceptionMessage =
        assertThrows(
                RuntimeException.class,
                () ->
                    MapperUtils.ifNull(
                        testingValue,
                        () -> {
                          // throw Exception here
                          throw new RuntimeException("Expected Exception");
                        }))
            .getMessage();

    assertThat(exceptionMessage).isEqualTo("Expected Exception");
  }

  @Test
  void shouldUseConsumerForNonNullValue() {
    var existingValue = "PID0001";
    var patient = new OBDS.MengePatient.Patient();

    // Set the patient ID to be the testing value, only if it is not NULL
    MapperUtils.ifNotNull(existingValue, patient::setPatientID);

    assertThat(patient.getPatientID()).isEqualTo(existingValue);
  }

  @Test
  void shouldNotUseConsumerForNullValue() {
    var existingValue = "PID0001";
    String testingValue = null;
    var patient = new OBDS.MengePatient.Patient();
    patient.setPatientID(existingValue);

    // Do not set the patient ID to be the testing value, if it is NULL
    // Keep the existing value "PID0001"
    MapperUtils.ifNotNull(testingValue, patient::setPatientID);

    assertThat(patient.getPatientID()).isEqualTo(existingValue);
  }

  @Test
  void shouldNotUseConsumerForNullValueAndThrowException() {
    var existingValue = "PID0001";
    String testingValue = null;
    var patient = new OBDS.MengePatient.Patient();
    patient.setPatientID(existingValue);

    var exceptionMessage =
        assertThrows(
                RuntimeException.class,
                () ->
                    // Do not set the patient ID to be the testing value, if it is NULL
                    // In addition to that, throw given Exception
                    MapperUtils.ifNotNull(
                        testingValue,
                        patient::setPatientID,
                        new RuntimeException("Expected NULL occured")))
            .getMessage();

    assertThat(exceptionMessage).isEqualTo("Expected NULL occured");
  }
}
