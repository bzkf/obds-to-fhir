package io.github.bzkf.obdstofhir;

import de.basisdatensatz.obds.v3.OBDS;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class PatientReferenceGenerator {
  public enum Strategy {
    SHA256_HASHED_PATIENT_IDENTIFIER_SYSTEM_AND_PATIENT_ID,
    MD5_HASHED_PATIENT_ID,
    PATIENT_ID_UNDERSCORES_REPLACED_WITH_DASHES,
    PATIENT_ID,
  }

  @Value("${fhir.mappings.patient-reference-generation.strategy}")
  private Strategy strategy;

  private final FhirProperties fhirProperties;

  public PatientReferenceGenerator(FhirProperties fhirProperties) {
    this.fhirProperties = fhirProperties;
  }

  @Bean
  public Function<OBDS.MengePatient.Patient, Reference> getPatientReferenceGenerationFunction() {
    Function<OBDS.MengePatient.Patient, String> idGenerator;
    switch (strategy) {
      case SHA256_HASHED_PATIENT_IDENTIFIER_SYSTEM_AND_PATIENT_ID:
        idGenerator =
            p -> {
              var system = fhirProperties.getSystems().getIdentifiers().getPatientId();
              var value = p.getPatientID();
              return DigestUtils.sha256Hex(system + "|" + value);
            };
        break;
      case MD5_HASHED_PATIENT_ID:
        idGenerator = p -> DigestUtils.md5Hex(p.getPatientID());
        break;
      case PATIENT_ID_UNDERSCORES_REPLACED_WITH_DASHES:
        idGenerator = p -> p.getPatientID().replace('_', '-');
        break;
      case PATIENT_ID:
        idGenerator = OBDS.MengePatient.Patient::getPatientID;
        break;
      default:
        throw new IllegalStateException(
            "Unsupported patient reference generation strategy: " + strategy);
    }

    return p -> {
      Validate.notBlank(p.getPatientID());
      return new Reference("Patient/" + idGenerator.apply(p));
    };
  }
}
