package io.github.bzkf.obdstofhir.processor;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.TodTyp;
import io.github.bzkf.obdstofhir.mapper.mii.TodMapper;
import io.github.bzkf.obdstofhir.model.OnkoPatient;
import java.util.*;
import java.util.function.Function;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle.BundleType;
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
  public Function<KTable<String, OnkoPatient>, KStream<String, Bundle>>
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
      TodTyp tod = new TodTyp();

      try {
        XMLGregorianCalendar xmlDate =
            DatatypeFactory.newInstance()
                .newXMLGregorianCalendar(onkoPatient.getSterbeDatum().toString());
        tod.setSterbedatum(xmlDate);
      } catch (DatatypeConfigurationException e) {
        throw new RuntimeException(e);
      }

      OBDS.MengePatient.Patient obdsPatient = new OBDS.MengePatient.Patient();

      obdsPatient.setPatientID(onkoPatient.getPatientId());
      var patientReferenceOptional = patientReferenceGenerator.apply(obdsPatient);

      var deathObservations =
          todMapper.map(tod, patientReferenceOptional.get(), null, true).getFirst();

      var bundle = new Bundle();
      bundle.setType(BundleType.TRANSACTION);
      var url =
          String.format(
              "%s/%s", deathObservations.getResourceType(), deathObservations.getIdBase());
      bundle
          .addEntry()
          .setFullUrl(url)
          .setResource(deathObservations)
          .getRequest()
          .setMethod(Bundle.HTTPVerb.PUT)
          .setUrl(url);

      return bundle;
    };
  }
}
