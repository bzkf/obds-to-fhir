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
import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.config.FhirServerConfig;
import io.github.bzkf.obdstofhir.config.RecordIdDbConfig;
import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import io.github.bzkf.obdstofhir.patientreference.FhirServerLookupPatientReferenceStrategy;
import io.github.bzkf.obdstofhir.patientreference.IdentifierPseudonymizer;
import io.github.bzkf.obdstofhir.patientreference.Md5HashedPatientReferenceStrategy;
import io.github.bzkf.obdstofhir.patientreference.OAuth2ClientCredentialsAuthInterceptor;
import io.github.bzkf.obdstofhir.patientreference.PatientReferenceStrategy;
import io.github.bzkf.obdstofhir.patientreference.PlainPatientIdReferenceStrategy;
import io.github.bzkf.obdstofhir.patientreference.RecordIdDatabaseLookupPatientReferenceStrategy;
import io.github.bzkf.obdstofhir.patientreference.Sha256HashedPatientReferenceStrategy;
import io.github.bzkf.obdstofhir.patientreference.UnderscoresToDashesPatientReferenceStrategy;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
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

/**
 * Builds the {@code Resource.subject.reference} to the Patient resource for every mapped oBDS
 * patient, according to the configured {@link Strategy}. Each strategy is implemented as its own
 * {@link PatientReferenceStrategy} in the {@code patientreference} package; this class only wires
 * up the strategy instances and picks the active one.
 */
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

  @Value("${fhir.mappings.patient-reference-generation.strategy}")
  private Strategy strategy;

  private final @NonNull FhirProperties fhirProperties;
  private final CodeableConcept mrnType;

  private final PatientReferenceStrategy sha256HashedStrategy =
      new Sha256HashedPatientReferenceStrategy();
  private final PatientReferenceStrategy md5HashedStrategy =
      new Md5HashedPatientReferenceStrategy();
  private final PatientReferenceStrategy underscoresToDashesStrategy =
      new UnderscoresToDashesPatientReferenceStrategy();
  private final PatientReferenceStrategy plainPatientIdStrategy =
      new PlainPatientIdReferenceStrategy();

  private @Nullable FhirServerLookupPatientReferenceStrategy fhirServerLookupStrategy;
  private @Nullable RecordIdDatabaseLookupPatientReferenceStrategy recordIdDatabaseLookupStrategy;

  public PatientReferenceGenerator(
      FhirProperties fhirProperties,
      Optional<FhirServerConfig> fhirServerConfig,
      Optional<JdbcTemplate> recordIdJdbcTemplate,
      Optional<RecordIdDbConfig> recordIdDbConfig,
      @Value("${fhir.mappings.patient-reference-generation.identifier-only-references-allowed}")
          boolean isIdentifierOnlyReferencesAllowed,
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
          var fhirClient = createFhirClient(cfg);
          var retryTemplate = createRetryTemplate(fhirClient.getFhirContext());

          IdentifierPseudonymizer pseudonymizer = null;
          if (isPseudonymizePatientIdEnabled) {
            if (!StringUtils.hasText(fhirPseudonymizerBaseUrl)) {
              throw new IllegalArgumentException("Pseudonymizer base url is unset");
            }

            var pseudonymizerContext = FhirContext.forR4();
            pseudonymizerContext
                .getRestfulClientFactory()
                .setServerValidationMode(ServerValidationModeEnum.NEVER);
            var fhirPseudonymizerClient =
                pseudonymizerContext.newRestfulGenericClient(fhirPseudonymizerBaseUrl);
            pseudonymizer = new IdentifierPseudonymizer(fhirPseudonymizerClient, retryTemplate);
          }

          this.fhirServerLookupStrategy =
              new FhirServerLookupPatientReferenceStrategy(
                  fhirClient, retryTemplate, isIdentifierOnlyReferencesAllowed, pseudonymizer);
        });

    if (recordIdJdbcTemplate.isPresent() && recordIdDbConfig.isPresent()) {
      this.recordIdDatabaseLookupStrategy =
          new RecordIdDatabaseLookupPatientReferenceStrategy(
              recordIdJdbcTemplate.get(), recordIdDbConfig.get());
    }
  }

  @Bean
  Function<OBDS.MengePatient.Patient, PatientLookupResult> getPatientReferenceGenerationFunction() {
    return p -> {
      Validate.notBlank(p.getPatientID());

      final var system = fhirProperties.getSystems().getIdentifiers().getPatientId();
      final var identifier =
          new Identifier().setSystem(system).setValue(p.getPatientID()).setType(mrnType);

      return resolveStrategy().resolve(identifier);
    };
  }

  private PatientReferenceStrategy resolveStrategy() {
    return switch (strategy) {
      case SHA256_HASHED_PATIENT_IDENTIFIER_SYSTEM_AND_PATIENT_ID -> sha256HashedStrategy;
      case MD5_HASHED_PATIENT_ID -> md5HashedStrategy;
      case PATIENT_ID_UNDERSCORES_REPLACED_WITH_DASHES -> underscoresToDashesStrategy;
      case PATIENT_ID -> plainPatientIdStrategy;
      case FHIR_SERVER_LOOKUP -> {
        if (fhirServerLookupStrategy == null) {
          throw new IllegalArgumentException(
              "ID generation strategy set to FHIR_SERVER_LOOKUP, but config is unset.");
        }
        yield fhirServerLookupStrategy;
      }
      case RECORD_ID_DATABASE_LOOKUP -> {
        if (recordIdDatabaseLookupStrategy == null) {
          throw new IllegalArgumentException(
              "ID generation strategy set to RECORD_ID_DATABASE_LOOKUP, but config is unset.");
        }
        yield recordIdDatabaseLookupStrategy;
      }
    };
  }

  static IGenericClient createFhirClient(FhirServerConfig fhirServerConfig) {
    var fhirContext = FhirContext.forR4();
    fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
    var client = fhirContext.newRestfulGenericClient(fhirServerConfig.baseUrl().toString());

    var basicAuth = fhirServerConfig.auth().basic();
    var oauth2 = fhirServerConfig.auth().oauth2();

    if (basicAuth.enabled() && oauth2.enabled()) {
      throw new IllegalArgumentException(
          "Only one of fhir-server.auth.basic or fhir-server.auth.oauth2 can be enabled at the"
              + " same time.");
    }

    if (basicAuth.enabled()) {
      client.registerInterceptor(
          new BasicAuthInterceptor(basicAuth.username(), basicAuth.password()));
    } else if (oauth2.enabled()) {
      client.registerInterceptor(
          new OAuth2ClientCredentialsAuthInterceptor(
              oauth2.tokenUrl(), oauth2.clientId(), oauth2.clientSecret(), oauth2.scope()));
    }

    return client;
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
