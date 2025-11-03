package io.github.bzkf.obdstofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.model.ADT_GEKID;
import io.github.bzkf.obdstofhir.model.MeldungExport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import({ObdsTestMapper.class})
@MockitoBean(types = {FhirProperties.class})
public class ObdsToFhirIntegrationTest {

  ObdsTestMapper mapper;

  @BeforeEach
  void setUp(@Autowired ObdsTestMapper mapper) {
    this.mapper = mapper;
  }

  @Nested
  @TestPropertySource(properties = {""})
  class UseDefaultPatternWithoutConfig {

    @ParameterizedTest
    @CsvSource({
      "12345,12345",
      "123456789,123456789",
      "1234567890,123456789", // Max 9 digits, remove last digit '0'
      "1234567891,123456789", // Max 9 digits, remove last digit '1'
      "0000012345,0000012345", // Not mathching pattern - keep as is
    })
    void applyDefaultPattern(String input, String output) {
      var actual = ObdsToFhirMapper.convertId(input);
      assertThat(actual).isEqualTo(output);
    }
  }

  @Nested
  @TestPropertySource(properties = {"app.patient-id-pattern=\\\\w*"})
  class AllWordCharactersAllowedPattern {

    @ParameterizedTest
    @CsvSource({
      "12345,12345",
      "123456789,123456789",
      "1234567890,1234567890",
      "1234567891,1234567891",
      "0000012345,0000012345"
    })
    void applyLocalPatientIdPattern(String input, String output) {
      var actual = ObdsToFhirMapper.convertId(input);
      assertThat(actual).isEqualTo(output);
    }
  }

  @Nested
  @TestPropertySource(properties = {"app.patient-id-pattern=G([0-9]{8})"})
  class UsePatternWithCaptureGroups {

    @ParameterizedTest
    @CsvSource({
      "12345,12345", // not matching, return as is ...
      // else return first complete group of 8 numbers found in input after "G"
      "G1234567890,12345678",
      "G12345678,12345678",
    })
    void applyPatientIdPattern(String input, String output) {
      var actual = ObdsToFhirMapper.convertId(input);
      assertThat(actual).isEqualTo(output);
    }
  }

  @Nested
  @TestPropertySource(properties = {"app.patient-id-pattern=G/([0-9]{4})-([0-9]{4})"})
  class UsePatternWithMultipleCaptureGroups {

    @ParameterizedTest
    @CsvSource({
      "12345,12345", // not matching, return as is ...
      // else return complete groups of 4 numbers found in input after "G/" and seperated by "-"
      "G/1234-56789,12345678",
      "G/1234-5678,12345678",
    })
    void applyPatientIdPattern(String input, String output) {
      var actual = ObdsToFhirMapper.convertId(input);
      assertThat(actual).isEqualTo(output);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "app.patient-id-pattern=G/([0-9]{4})-([0-9]{4})",
        "app.enableCheckDigitConv=true"
      })
  class UseEnabledCheckDigitConf {

    @ParameterizedTest
    @CsvSource({
      "12345,12345", // not matching, return as is ...
      // else return complete groups of 4 numbers found in input after "G/" and seperated by "-"
      "G/1234-56789,12345678",
      "G/1234-5678,12345678",
    })
    void shouldCheckDigitConf(String input, String output) {
      var actual = ObdsToFhirMapper.getConvertedPatIdFromMeldung(createMeldungExport(input));
      assertThat(actual).isEqualTo(output);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "app.patient-id-pattern=G/([0-9]{4})-([0-9]{4})",
        "app.enableCheckDigitConv=false"
      })
  class UseDisabledCheckDigitConf {

    @ParameterizedTest
    @CsvSource({
      "12345,12345", // not matching, return as is ...
      // else return complete groups of 4 numbers found in input after "G/" and seperated by "-"
      "G/1234-56789,G/1234-56789",
      "G/1234-5678,G/1234-5678",
    })
    void shouldCheckDigitConf(String input, String output) {
      var actual = ObdsToFhirMapper.getConvertedPatIdFromMeldung(createMeldungExport(input));
      assertThat(actual).isEqualTo(output);
    }
  }

  private static MeldungExport createMeldungExport(String patientId) {
    var meldungExport = new MeldungExport();
    var data = new ADT_GEKID();
    var patientenStammdaten = new ADT_GEKID.Menge_Patient.Patient.Patienten_Stammdaten();
    patientenStammdaten.setPatient_ID(patientId);
    var patient = new ADT_GEKID.Menge_Patient.Patient();
    patient.setPatienten_Stammdaten(patientenStammdaten);
    var mengePatient = new ADT_GEKID.Menge_Patient();
    mengePatient.setPatient(patient);
    data.setMenge_Patient(mengePatient);
    meldungExport.setXml_daten(data);
    return meldungExport;
  }
}

@Component
class ObdsTestMapper extends ObdsToFhirMapper {

  protected ObdsTestMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }
}
