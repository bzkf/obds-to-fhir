package io.github.bzkf.obdstofhir.processor;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.TodTyp;
import io.github.bzkf.obdstofhir.mapper.DeviceMapper;
import io.github.bzkf.obdstofhir.mapper.mii.TodMapper;
import io.github.bzkf.obdstofhir.mapper.mii.VitalStatusMapper;
import io.github.bzkf.obdstofhir.model.OnkoPatient;
import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import io.github.dizuker.tofhir.ReferenceUtils;
import io.github.dizuker.tofhir.TransactionBuilder;
import java.util.List;
import java.util.function.Function;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    value = "fhir.mappings.from-onkostar-patient-data.enabled",
    havingValue = "true",
    matchIfMissing = false)
@Configuration
public class PatientTodObservationProcessor {

  private final TodMapper todMapper;
  private final VitalStatusMapper vitalStatusMapper;
  private final Function<OBDS.MengePatient.Patient, PatientLookupResult> patientReferenceGenerator;
  private final DeviceMapper deviceMapper;
  private static final Logger LOG = LoggerFactory.getLogger(PatientTodObservationProcessor.class);

  @Value("${fhir.mappings.create-provenance-resources.enabled}")
  private boolean createProvenanceResources;

  public PatientTodObservationProcessor(
      TodMapper todMapper,
      VitalStatusMapper vitalStatusMapper,
      DeviceMapper deviceMapper,
      Function<OBDS.MengePatient.Patient, PatientLookupResult> patientReferenceGenerator) {
    this.todMapper = todMapper;
    this.vitalStatusMapper = vitalStatusMapper;
    this.deviceMapper = deviceMapper;
    this.patientReferenceGenerator = patientReferenceGenerator;
  }

  @Bean
  Function<KTable<String, OnkoPatient>, KStream<String, Bundle>>
      getPatientTodObservationProcessor() {
    return stringOnkoPatTable ->
        stringOnkoPatTable
            .mapValues(getTodObsBundleMapper())
            .toStream()
            .peek((key, value) -> LOG.info("Stream received key={}, value={}", key, value))
            .filter((key, value) -> value != null)
            .selectKey((key, value) -> value.getEntry().getFirst().getFullUrl());
  }

  public ValueMapper<OnkoPatient, Bundle> getTodObsBundleMapper() {
    return onkoPatient -> {
      if (onkoPatient.getPatientId() == null) {
        return null;
      }

      var obdsPatient = new OBDS.MengePatient.Patient();

      obdsPatient.setPatientID(onkoPatient.getPatientId());
      var patientLookupResult = patientReferenceGenerator.apply(obdsPatient);

      if (onkoPatient.getSterbeDatum() == null && onkoPatient.getLetzteInformation() == null) {
        LOG.warn("Skipping patient without dates: {}", onkoPatient.getPatientId());
        return null;
      }

      TransactionBuilder builder = new TransactionBuilder();
      // TODO: clarify if we should create patients if they don't already exist here

      builder.addEntry(buildVitalStatus(onkoPatient, patientLookupResult));

      if (onkoPatient.getSterbeDatum() != null) {
        builder.addEntries(
            buildTodBundle(
                onkoPatient,
                patientLookupResult)); // buildTodBundle(onkoPatient, patientLookupResult);
      }

      if (createProvenanceResources) {
        var device = deviceMapper.map();
        var who =
            ReferenceUtils.createReferenceTo(device)
                .setDisplay("oBDS-to-FHIR " + device.getVersionFirstRep().getValue());

        var sourceDisplay =
            onkoPatient.getId() != null
                ? "ONKOSTAR patient row id " + onkoPatient.getId()
                : "ONKOSTAR CSV export";

        var what = new Reference().setDisplay(sourceDisplay);
        builder.withProvenance(who, what);
      }

      return builder.build();
    };
  }

  private List<Observation> buildTodBundle(OnkoPatient onkoPatient, PatientLookupResult ref) {

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

    var deathObservations = todMapper.map(tod, ref.reference(), null, null, true);

    return deathObservations;
    //      new TransactionBuilder()
    //      .addEntries(deathObservations)
    //      .failOnDuplicateEntries();
  }

  private Observation buildVitalStatus(OnkoPatient onkoPatient, PatientLookupResult ref) {

    XMLGregorianCalendar xmlDateLastInfo = null;
    TodTyp tod = null;

    if (onkoPatient.getSterbeDatum() != null) {
      try {
        var xmlDateTod =
            DatatypeFactory.newInstance()
                .newXMLGregorianCalendar(onkoPatient.getSterbeDatum().toString());
        tod = new TodTyp();
        tod.setSterbedatum(xmlDateTod);
      } catch (DatatypeConfigurationException e) {
        throw new IllegalArgumentException(
            "Invalid date format for SterbeDatum: " + onkoPatient.getSterbeDatum(), e);
      }
    } else {
      try {
        xmlDateLastInfo =
            DatatypeFactory.newInstance()
                .newXMLGregorianCalendar(onkoPatient.getLetzteInformation().toString());

      } catch (DatatypeConfigurationException e) {
        throw new IllegalArgumentException(
            "Invalid date format for LastInfo: " + onkoPatient.getLetzteInformation(), e);
      }
    }
    return vitalStatusMapper.map(xmlDateLastInfo, ref.reference(), tod, true);
  }
}
