package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.TodTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import javax.xml.datatype.XMLGregorianCalendar;
import java.util.Date;


public class VitalStatusMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(TodMapper.class);

  public VitalStatusMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  Observation observation = new Observation();

public Observation map(@NonNull XMLGregorianCalendar meldeDatum ,
                       @NonNull Reference patient,
                       TodTyp tod){

  observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiVitalStatus());
  observation.setSubject(patient);
  //TODO: Identifer überlegen
  Identifier identifier =
    new Identifier()
      .setSystem(fhirProperties.getSystems().getIdentifiers().getVitalStatusId())
      .setValue("");
  observation.addIdentifier(identifier);
  //TODO: status amended, bei Tod final
  observation.setStatus(Observation.ObservationStatus.AMENDED);

  //set category to survey
  CodeableConcept category = new CodeableConcept(new Coding().setSystem(fhirProperties.getSystems().getObservationCategory()).setCode("survey"));
  observation.addCategory(category);

  //set code
  CodeableConcept code = new CodeableConcept(new Coding().setSystem(fhirProperties.getSystems().getLoinc()).setCode("67162-8"));

  //set effectiveDateTime aus Meldung bzw. aus Onkostar-Tabelle
  //var dateOptional = convertObdsDatumToDateTimeType(meldeDatum);
  //dateOptional.ifPresent(observation::setEffective);

  // set value CodeableConcept  T or L
  //if Todesdatum aus Meldung, dann "T"

  if(tod.getSterbedatum() != null){
    var todesdatum = convertObdsDatumToDateTimeType(tod.getSterbedatum());
    todesdatum.ifPresent(observation::setEffective);

    CodeableConcept value = new CodeableConcept(new Coding().setSystem(fhirProperties.getSystems().getMiiVSVitalStatus()).setCode("T"));
    observation.setValue(value);
  }

  CodeableConcept value = new CodeableConcept(new Coding().setSystem(fhirProperties.getSystems().getMiiVSVitalStatus()).setCode(""));




  /*
  TODO: if Todesdatum aus oBDS -> T; ansonsten erstmal Meldedatum
  TODO: dann prüfen, ob aus bestOfTumor letzteInformation oder Todesdatum vorhanden ist
   */

  return null;
}


}
