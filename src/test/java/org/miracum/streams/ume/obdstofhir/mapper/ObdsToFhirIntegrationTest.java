package org.miracum.streams.ume.obdstofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import({ObdsTestMapper.class})
public class ObdsToFhirIntegrationTest {

  @MockBean FhirProperties fhirProperties;

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
  class References {

    @Test
    void shouldCreateReferenceString() {
      doAnswer(
              invocationOnMock -> {
                final var fhirSystems = new FhirProperties.FhirSystems();
                fhirSystems.setPatientId("https://fhir.diz.uk-erlangen.de/identifiers/patient-id");
                return fhirSystems;
              })
          .when(fhirProperties)
          .getSystems();

      final var actual = mapper.getReference(ResourceType.Patient, "1");
      assertThat(actual)
          .isEqualTo("Patient/82a86769573e519a1fa2d79911fb19fbdceabe2416a5c9230b503618a23a31c1");
    }
  }
}

@Component
class ObdsTestMapper extends ObdsToFhirMapper {

  protected ObdsTestMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }
}
