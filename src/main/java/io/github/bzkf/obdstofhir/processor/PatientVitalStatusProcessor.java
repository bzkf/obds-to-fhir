package io.github.bzkf.obdstofhir.processor;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.TodTyp;
import io.github.bzkf.obdstofhir.mapper.DeviceMapper;
import io.github.bzkf.obdstofhir.mapper.mii.VitalStatusMapper;
import io.github.bzkf.obdstofhir.model.OnkoPatient;
import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import io.github.dizuker.tofhir.ReferenceUtils;
import io.github.dizuker.tofhir.TransactionBuilder;
import java.util.*;
import java.util.function.Function;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.*;
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
public class PatientVitalStatusProcessor {

  private final VitalStatusMapper vitalStatusMapper;
  private final Function<OBDS.MengePatient.Patient, PatientLookupResult> patientReferenceGenerator;
  private final DeviceMapper deviceMapper;

  @Value("${fhir.mappings.create-provenance-resources.enabled}")
  private boolean createProvenanceResources;

  public PatientVitalStatusProcessor(
    VitalStatusMapper vitalStatusMapper,
    DeviceMapper deviceMapper,
    Function<OBDS.MengePatient.Patient, PatientLookupResult> patientReferenceGenerator) {
    this.vitalStatusMapper = vitalStatusMapper;
    this.deviceMapper = deviceMapper;
    this.patientReferenceGenerator = patientReferenceGenerator;
  }

  @Bean
  Function<KTable<String, OnkoPatient>, KStream<String, Bundle>>
  getPatientVitalStatusProcessor() {
    return stringOnkoPatTable ->
      stringOnkoPatTable
        .mapValues(getVitalStatusObsBundleMapper())
        .toStream()
        .filter((key, value) -> value != null)
        .selectKey((key, value) -> value.getEntry().getFirst().getFullUrl());
  }

  public ValueMapper<OnkoPatient, Bundle> getVitalStatusObsBundleMapper() {
    return onkoPatient -> {
      if (onkoPatient.getPatientId() == null || (onkoPatient.getSterbeDatum() == null && onkoPatient.getLetzteInformation() == null)) {
        return null;
      }
      var tod = new TodTyp();
      XMLGregorianCalendar xmlDateLastInfo;
      if(onkoPatient.getSterbeDatum() != null) {
        try {
          var xmlDateTod =
            DatatypeFactory.newInstance()
              .newXMLGregorianCalendar(onkoPatient.getSterbeDatum().toString());
          tod.setSterbedatum(xmlDateTod);
          xmlDateLastInfo = null;

        } catch (DatatypeConfigurationException e) {
          throw new IllegalArgumentException(
            "Invalid date format for SterbeDatum: " + onkoPatient.getSterbeDatum(), e);
        }
      }else {
        try {
          xmlDateLastInfo = DatatypeFactory.newInstance()
            .newXMLGregorianCalendar(onkoPatient.getLetzteInformation().toString());

        } catch (DatatypeConfigurationException e) {
          throw new IllegalArgumentException(
            "Invalid date format for LastInfo: " + onkoPatient.getSterbeDatum(), e);
        }
      }

      var obdsPatient = new OBDS.MengePatient.Patient();

      obdsPatient.setPatientID(onkoPatient.getPatientId());
      var patientLookupResult = patientReferenceGenerator.apply(obdsPatient);

      var vitalStatus = vitalStatusMapper.map(xmlDateLastInfo, patientLookupResult.reference(), tod, true);
      var builder = new TransactionBuilder().addEntry(vitalStatus).failOnDuplicateEntries();

      if (createProvenanceResources) {
        var device = deviceMapper.map();
        var who =
          ReferenceUtils.createReferenceTo(device)
            .setDisplay("oBDS-to-FHIR " + device.getVersion());

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
}
