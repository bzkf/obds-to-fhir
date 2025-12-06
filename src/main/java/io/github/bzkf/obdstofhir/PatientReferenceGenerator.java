package io.github.bzkf.obdstofhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.ResourceVersionConflictException;
import ca.uhn.fhir.util.BundleUtil;
import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.config.FhirServerConfig;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
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
  }

  @Value("${fhir.mappings.patient-reference-generation.strategy}")
  private Strategy strategy;

  private final @NonNull FhirProperties fhirProperties;
  private @Nullable IGenericClient fhirClient = null;
  private @Nullable RetryTemplate retryTemplate = null;

  public PatientReferenceGenerator(
      FhirProperties fhirProperties, Optional<FhirServerConfig> fhirServerConfig) {
    this.fhirProperties = fhirProperties;

    if (fhirServerConfig.isPresent()) {
      this.fhirClient = createFhirClient(fhirServerConfig.get());
      this.retryTemplate = createRetryTemplate(fhirClient.getFhirContext());
    }
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

              return id.getIdPart();
            };

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

  static IGenericClient createFhirClient(FhirServerConfig fhirServerConfig) {
    var fhirContext = FhirContext.forR4();

    var client = fhirContext.newRestfulGenericClient(fhirServerConfig.baseUrl().toString());

    if (fhirServerConfig.auth().basic().enabled()) {
      client.registerInterceptor(
          new BasicAuthInterceptor(
              fhirServerConfig.auth().basic().username(),
              fhirServerConfig.auth().basic().password()));
    }

    return client;
  }

  private IIdType findPatientIdByIdentifier(Identifier patientIdentifier) {
    var result =
        retryTemplate.execute(
            context -> {
              return fhirClient
                  .search()
                  .forResource(Patient.class)
                  .where(
                      Patient.IDENTIFIER
                          .exactly()
                          .systemAndIdentifier(
                              patientIdentifier.getSystem(), patientIdentifier.getValue()))
                  .execute();
            });

    var patients =
        BundleUtil.toListOfResourcesOfType(fhirClient.getFhirContext(), result, Patient.class);

    if (patients.isEmpty()) {
      throw new IllegalArgumentException(
          "Unable to find any patient resources on the FHIR server for the given identifier.");
    }

    if (patients.size() > 1) {
      throw new IllegalArgumentException("More than one patient resource matches the identifier.");
    }

    return patients.getFirst().getIdElement();
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
