package io.github.bzkf.obdstofhir.processor;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.TodTyp;
import io.github.bzkf.obdstofhir.mapper.mii.TodMapper;
import io.github.bzkf.obdstofhir.model.OnkoPatient;
import io.github.dizuker.tofhir.TransactionBuilder;
import java.util.*;
import java.util.function.Function;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    value = "obds.process-from-directory.enabled",
    havingValue = "false",
    matchIfMissing = false)
@ConditionalOnProperty(
    value = "fhir.mappings.from-onkostar-patient-table.enabled",
    havingValue = "true",
    matchIfMissing = false)
@Configuration
public class PatientTodObservationProcessor {

  private final TodMapper todMapper;
  private final Function<OBDS.MengePatient.Patient, Optional<Reference>> patientReferenceGenerator;

  public PatientTodObservationProcessor(
      TodMapper todMapper,
      Function<OBDS.MengePatient.Patient, Optional<Reference>> patientReferenceGenerator) {
    this.todMapper = todMapper;
    this.patientReferenceGenerator = patientReferenceGenerator;
  }

  @Bean
  Function<KTable<String, OnkoPatient>, KStream<String, Bundle>>
      getPatientTodObservationProcessor() {
    return stringOnkoPatTable ->
        stringOnkoPatTable
            .mapValues(getTodObsBundleMapper())
            .toStream()
            .filter((key, value) -> value != null)
            .selectKey((key, value) -> value.getEntry().getFirst().getFullUrl());
  }

  public ValueMapper<OnkoPatient, Bundle> getTodObsBundleMapper() {
    return onkoPatient -> {
      if (onkoPatient.getPatientId() == null || onkoPatient.getSterbeDatum() == null) {
        return null;
      }
      var tod = new TodTyp();

      try {
        var xmlDate =
            DatatypeFactory.newInstance()
                .newXMLGregorianCalendar(onkoPatient.getSterbeDatum().toString());
        tod.setSterbedatum(xmlDate);
      } catch (DatatypeConfigurationException e) {
        throw new IllegalArgumentException(
            "Invalid date format for SterbeDatum: " + onkoPatient.getSterbeDatum(), e);
      }

      var obdsPatient = new OBDS.MengePatient.Patient();

      obdsPatient.setPatientID(onkoPatient.getPatientId());
      var patientReferenceOptional = patientReferenceGenerator.apply(obdsPatient);

      if (patientReferenceOptional.isEmpty()) {
        throw new IllegalArgumentException(
            "Unable to build patient reference for patient from ONKOSTAR patient table. "
                + "The patient may not exist in the FHIR server or record database. "
                + "Creating dedicated Patient resources is not yet implemented.");
      }

      var deathObservations = todMapper.map(tod, patientReferenceOptional.get(), null, null, true);

      return new TransactionBuilder()
          .addEntries(deathObservations)
          .failOnDuplicateEntries()
          .build();
    };
  }
}
