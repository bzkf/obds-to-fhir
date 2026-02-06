package io.github.bzkf.obdstofhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.ResourceVersionConflictException;
import ca.uhn.fhir.util.BundleUtil;
import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.config.FhirServerConfig;
import io.github.bzkf.obdstofhir.config.RecordIdDbConfig;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Service
public class PatientReferenceGenerator {
  private static final Logger LOG = LoggerFactory.getLogger(PatientReferenceGenerator.class);

  public enum Strategy {
    SHA256_HASHED_PATIENT_IDENTIFIER_SYSTEM_AND_PATIENT_ID,
    MD5_HASHED_PATIENT_ID,
    PATIENT_ID_UNDERSCORES_REPLACED_WITH_DASHES,
    PATIENT_ID,
    FHIR_SERVER_LOOKUP,
    RECORD_ID_DATABASE_LOOKUP,
    PSEUDONYMIZE_AND_LOOKUP_IN_FHIR_SERVER,
  }

  @Value("${fhir.mappings.patient-reference-generation.strategy}")
  private Strategy strategy;

  private final @NonNull FhirProperties fhirProperties;
  private @Nullable IGenericClient fhirClient;
  private @Nullable RetryTemplate retryTemplate;
  private @Nullable JdbcTemplate recordIdJdbcTemplate;
  private @Nullable RecordIdDbConfig recordIdDbConfig;
  private @Nullable IGenericClient fhirPseudonymizerClient;

  public PatientReferenceGenerator(
      FhirProperties fhirProperties,
      Optional<FhirServerConfig> fhirServerConfig,
      Optional<JdbcTemplate> recordIdJdbcTemplate,
      Optional<RecordIdDbConfig> recordIdDbConfig,
      @Value("${fhir.mappings.patient-reference-generation.fhir-pseudonymizer.base-url}")
          String fhirPseudonymizerBaseUrl) {
    this.fhirProperties = fhirProperties;

    fhirServerConfig.ifPresent(
        cfg -> {
          this.fhirClient = createFhirClient(cfg);
          this.retryTemplate = createRetryTemplate(fhirClient.getFhirContext());
        });

    if (recordIdJdbcTemplate.isPresent() && recordIdDbConfig.isPresent()) {
      this.recordIdJdbcTemplate = recordIdJdbcTemplate.get();
      this.recordIdDbConfig = recordIdDbConfig.get();
    }

    if (fhirPseudonymizerBaseUrl != null) {
      var fhirContext = FhirContext.forR4();
      fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
      this.fhirPseudonymizerClient = fhirContext.newRestfulGenericClient(fhirPseudonymizerBaseUrl);
    }
  }

  @Bean
  public Function<OBDS.MengePatient.Patient, Optional<Reference>>
      getPatientReferenceGenerationFunction() {
    Function<OBDS.MengePatient.Patient, Optional<String>> idGenerator;
    switch (strategy) {
      case SHA256_HASHED_PATIENT_IDENTIFIER_SYSTEM_AND_PATIENT_ID:
        idGenerator =
            p -> {
              var system = fhirProperties.getSystems().getIdentifiers().getPatientId();
              var value = p.getPatientID();
              return Optional.of(DigestUtils.sha256Hex(system + "|" + value));
            };
        break;
      case MD5_HASHED_PATIENT_ID:
        idGenerator = p -> Optional.of(DigestUtils.md5Hex(p.getPatientID()));
        break;
      case PATIENT_ID_UNDERSCORES_REPLACED_WITH_DASHES:
        idGenerator = p -> Optional.of(p.getPatientID().replace('_', '-'));
        break;
      case PATIENT_ID:
        idGenerator = p -> Optional.of(p.getPatientID());
        break;
      case FHIR_SERVER_LOOKUP:
        if (fhirClient == null) {
          throw new IllegalArgumentException(
              "ID generation strategy set to FHIR_SERVER_LOOKUP, but config is unset.");
        }

        idGenerator =
            p -> {
              var system = fhirProperties.getSystems().getIdentifiers().getPatientId();
              var value = p.getPatientID();

              var id =
                  findPatientIdByIdentifier(new Identifier().setSystem(system).setValue(value));
              if (id.isPresent()) {
                return Optional.of(id.get().getIdPart());
              } else {
                return Optional.empty();
              }
            };

        break;
      case RECORD_ID_DATABASE_LOOKUP:
        if (recordIdJdbcTemplate == null) {
          throw new IllegalArgumentException(
              "ID generation strategy set to RECORD_ID_DATABASE_LOOKUP, but config is unset.");
        }

        idGenerator = p -> findRecordIdByPatientId(p.getPatientID());
        break;
      case PSEUDONYMIZE_AND_LOOKUP_IN_FHIR_SERVER:
        if (fhirClient == null) {
          throw new IllegalArgumentException(
              "ID generation strategy set to PSEUDONYMIZE_AND_LOOKUP_IN_FHIR_SERVER, "
                  + "but config is unset.");
        }

        if (this.fhirPseudonymizerClient == null) {
          throw new IllegalArgumentException(
              "ID generation strategy set to PSEUDONYMIZE_AND_LOOKUP_IN_FHIR_SERVER, "
                  + "but fhirPseudonymizerBaseUrl is unset.");
        }

        idGenerator =
            p -> {
              var system = fhirProperties.getSystems().getIdentifiers().getPatientId();
              var value = p.getPatientID();

              var pseudonymizedIdentifier =
                  pseudonymizeIdentifier(new Identifier().setSystem(system).setValue(value));

              var id = findPatientIdByIdentifier(pseudonymizedIdentifier);
              if (id.isPresent()) {
                return Optional.of(id.get().getIdPart());
              } else {
                return Optional.empty();
              }
            };
        break;
      default:
        throw new IllegalStateException(
            "Unsupported patient reference generation strategy: " + strategy);
    }

    return p -> {
      Validate.notBlank(p.getPatientID());
      var idPart = idGenerator.apply(p);

      if (idPart.isPresent()) {
        return Optional.of(new Reference("Patient/" + idPart.get()));
      } else {
        return Optional.empty();
      }
    };
  }

  static IGenericClient createFhirClient(FhirServerConfig fhirServerConfig) {
    var fhirContext = FhirContext.forR4();
    fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
    var client = fhirContext.newRestfulGenericClient(fhirServerConfig.baseUrl().toString());

    if (fhirServerConfig.auth().basic().enabled()) {
      client.registerInterceptor(
          new BasicAuthInterceptor(
              fhirServerConfig.auth().basic().username(),
              fhirServerConfig.auth().basic().password()));
    }

    return client;
  }

  private Optional<String> findRecordIdByPatientId(String patientId) {
    var id =
        this.recordIdJdbcTemplate.queryForObject(recordIdDbConfig.query(), String.class, patientId);
    if (StringUtils.hasText(id)) {
      return Optional.of(id);
    } else {
      return Optional.empty();
    }
  }

  private Optional<IIdType> findPatientIdByIdentifier(Identifier patientIdentifier) {
    var result =
        retryTemplate.execute(
            context ->
                fhirClient
                    .search()
                    .forResource(Patient.class)
                    .where(
                        Patient.IDENTIFIER
                            .exactly()
                            .systemAndIdentifier(
                                patientIdentifier.getSystem(), patientIdentifier.getValue()))
                    .execute());

    var patients =
        BundleUtil.toListOfResourcesOfType(fhirClient.getFhirContext(), result, Patient.class);

    if (patients.isEmpty()) {
      return Optional.empty();
    }

    if (patients.size() > 1) {
      throw new IllegalArgumentException("More than one patient resource matches the identifier.");
    }

    return Optional.of(patients.getFirst().getIdElement());
  }

  private Identifier pseudonymizeIdentifier(Identifier patientId) {
    var patient = new Patient();
    patient.addIdentifier(patientId);
    var param = new Parameters();
    param.addParameter().setName("resource").setResource(patient);
    var result =
        retryTemplate.execute(
            context ->
                fhirPseudonymizerClient
                    .operation()
                    .onServer()
                    .named("de-identify")
                    .withParameters(param)
                    .returnResourceType(Patient.class)
                    .execute());
    return result.getIdentifierFirstRep();
  }

  private static RetryTemplate createRetryTemplate(FhirContext fhirContext) {
    var retryTemplate = new RetryTemplate();

    var backOffPolicy = new ExponentialRandomBackOffPolicy();
    backOffPolicy.setInitialInterval(10_000); // 10 seconds
    backOffPolicy.setMaxInterval(300_000); // 5 minutes

    retryTemplate.setBackOffPolicy(backOffPolicy);

    var retryableExceptions = new HashMap<Class<? extends Throwable>, Boolean>();
    retryableExceptions.put(HttpServerErrorException.class, true);
    retryableExceptions.put(ResourceAccessException.class, true);
    retryableExceptions.put(FhirClientConnectionException.class, true);
    retryableExceptions.put(InternalErrorException.class, true);
    retryableExceptions.put(ResourceNotFoundException.class, false);
    retryableExceptions.put(ResourceVersionConflictException.class, false);
    retryableExceptions.put(NoSuchAlgorithmException.class, false);
    retryableExceptions.put(DataFormatException.class, false);
    retryableExceptions.put(IOException.class, true);

    retryTemplate.setRetryPolicy(new SimpleRetryPolicy(10, retryableExceptions));

    retryTemplate.registerListener(
        new RetryListener() {
          @Override
          public <T, E extends Throwable> void onError(
              RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
            LOG.warn(
                "Trying to query FHIR server caused error. Attempt: {}.",
                context.getRetryCount(),
                throwable);
            if (throwable instanceof BaseServerResponseException fhirException) {
              var operationOutcome = fhirException.getOperationOutcome();
              if (operationOutcome != null) {
                LOG.warn(
                    fhirContext
                        .newJsonParser()
                        .setPrettyPrint(true)
                        .encodeResourceToString(operationOutcome));
              }
            }
          }
        });

    return retryTemplate;
  }
}
