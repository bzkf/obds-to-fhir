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
import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import io.github.dizuker.tofhir.IdUtils;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.CodeableConcept;
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
import org.springframework.dao.EmptyResultDataAccessException;
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
    RECORD_ID_DATABASE_LOOKUP
  }

  record StringId(String value) {}

  @Value("${fhir.mappings.patient-reference-generation.strategy}")
  private Strategy strategy;

  @Value("${fhir.mappings.patient-reference-generation.identifier-only-references-allowed}")
  private boolean isIdentifierOnlyReferencesAllowed;

  private final boolean isPseudonymizePatientIdEnabled;
  private final @NonNull FhirProperties fhirProperties;
  private @Nullable IGenericClient fhirClient;
  private @Nullable RetryTemplate retryTemplate;
  private @Nullable JdbcTemplate recordIdJdbcTemplate;
  private @Nullable RecordIdDbConfig recordIdDbConfig;
  private @Nullable IGenericClient fhirPseudonymizerClient;
  private final Map<String, Optional<StringId>> recordIdByPatientIdCache =
      new ConcurrentHashMap<>();

  private final CodeableConcept mrnType;

  public PatientReferenceGenerator(
      FhirProperties fhirProperties,
      Optional<FhirServerConfig> fhirServerConfig,
      Optional<JdbcTemplate> recordIdJdbcTemplate,
      Optional<RecordIdDbConfig> recordIdDbConfig,
      @Value("${fhir.mappings.patient-reference-generation.pseudonymize-patient-id.enabled}")
          boolean isPseudonymizePatientIdEnabled,
      @Value(
              "${fhir.mappings.patient-reference-generation.pseudonymize-patient-id.fhir-pseudonymizer.base-url}")
          String fhirPseudonymizerBaseUrl) {
    this.fhirProperties = fhirProperties;

    mrnType = new CodeableConcept();
    mrnType
        .addCoding()
        .setSystem(fhirProperties.getSystems().getIdentifierType())
        .setCode("MR")
        .setDisplay("Medical record number");

    fhirServerConfig.ifPresent(
        cfg -> {
          this.fhirClient = createFhirClient(cfg);
          this.retryTemplate = createRetryTemplate(fhirClient.getFhirContext());
        });

    if (recordIdJdbcTemplate.isPresent() && recordIdDbConfig.isPresent()) {
      this.recordIdJdbcTemplate = recordIdJdbcTemplate.get();
      this.recordIdDbConfig = recordIdDbConfig.get();
    }

    this.isPseudonymizePatientIdEnabled = isPseudonymizePatientIdEnabled;
    if (isPseudonymizePatientIdEnabled) {
      if (!StringUtils.hasText(fhirPseudonymizerBaseUrl)) {
        throw new IllegalArgumentException("Pseudonymizer base url is unset");
      }

      var fhirContext = FhirContext.forR4();
      fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
      this.fhirPseudonymizerClient = fhirContext.newRestfulGenericClient(fhirPseudonymizerBaseUrl);
    }
  }

  @Bean
  Function<OBDS.MengePatient.Patient, PatientLookupResult> getPatientReferenceGenerationFunction() {
    return p -> {
      Validate.notBlank(p.getPatientID());

      final var system = fhirProperties.getSystems().getIdentifiers().getPatientId();
      final var value = p.getPatientID();

      final var identifier = new Identifier().setSystem(system).setValue(value).setType(mrnType);

      switch (strategy) {
        case SHA256_HASHED_PATIENT_IDENTIFIER_SYSTEM_AND_PATIENT_ID -> {
          var id = IdUtils.fromIdentifier(identifier);
          return new PatientLookupResult(
              new Reference("Patient/" + id).setIdentifier(identifier),
              false // basically just a default value here
              );
        }

        case MD5_HASHED_PATIENT_ID -> {
          var id = DigestUtils.md5Hex(value);
          return new PatientLookupResult(
              new Reference("Patient/" + id).setIdentifier(identifier), false);
        }

        case PATIENT_ID_UNDERSCORES_REPLACED_WITH_DASHES -> {
          var id = value.replace('_', '-');
          return new PatientLookupResult(
              new Reference("Patient/" + id).setIdentifier(identifier), false);
        }

        case PATIENT_ID -> {
          return new PatientLookupResult(
              new Reference("Patient/" + value).setIdentifier(identifier), false);
        }

        case FHIR_SERVER_LOOKUP -> {
          if (fhirClient == null) {
            throw new IllegalArgumentException(
                "ID generation strategy set to FHIR_SERVER_LOOKUP, but config is unset.");
          }

          // we start by looking up the plain patient identifier
          var identifierToLookup = identifier;

          if (isPseudonymizePatientIdEnabled) {
            // if enabled, first pseudonymize the identifier and then use this as the
            // identifier to look up
            identifierToLookup = pseudonymizeIdentifier(identifierToLookup);
          }

          var id = findPatientIdByIdentifier(identifierToLookup);

          // always set the (pseudonymized) identifier as part of the reference
          var reference = new Reference().setIdentifier(identifierToLookup);

          if (id.isPresent()) {
            reference.setReference("Patient/" + id.get().getIdPart());
            return new PatientLookupResult(reference, true);
          }

          if (!isIdentifierOnlyReferencesAllowed) {
            throw new IllegalStateException(
                "Patient not found on FHIR server and identifier-only references are disabled.");
          }

          return new PatientLookupResult(reference, false);
        }

        case RECORD_ID_DATABASE_LOOKUP -> {
          if (recordIdJdbcTemplate == null) {
            throw new IllegalArgumentException(
                "ID generation strategy set to RECORD_ID_DATABASE_LOOKUP, but config is unset.");
          }

          var reference = new Reference().setIdentifier(identifier);

          var idResult = findRecordIdByPatientId(value);

          if (idResult.isPresent()) {
            reference.setReference("Patient/" + idResult.get().value());
            reference.getIdentifier().setValue(idResult.get().value());
            return new PatientLookupResult(reference, true);
          }

          LOG.warn("No record ID found for patient: {}", value);

          return new PatientLookupResult(reference, false);
        }

        default ->
            throw new IllegalStateException(
                "Unsupported patient reference generation strategy: " + strategy);
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

  private Optional<StringId> findRecordIdByPatientId(String patientId) {
    return recordIdByPatientIdCache.computeIfAbsent(patientId, this::queryRecordIdByPatientId);
  }

  private Optional<StringId> queryRecordIdByPatientId(String patientId) {
    try {
      var id =
          this.recordIdJdbcTemplate.queryForObject(
              recordIdDbConfig.query(), String.class, patientId);
      if (StringUtils.hasText(id)) {
        return Optional.of(new StringId(id));
      } else {
        return Optional.empty();
      }
    } catch (EmptyResultDataAccessException _) {
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
