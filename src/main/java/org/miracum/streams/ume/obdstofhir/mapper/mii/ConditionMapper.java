package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConditionMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ConditionMapper.class);

  @Autowired
  public ConditionMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Condition map(OBDS.MengePatient.Patient.MengeMeldung.Meldung meldung, Reference patient) {
    var condition = new Condition();
    condition.setSubject(patient);
    condition.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoDiagnosePrimaertumor());
    var tumorzuordnung = meldung.getTumorzuordnung();
    if (tumorzuordnung == null) {
      throw new RuntimeException("tumorzuordnung ist null");
    }
    condition.setRecordedDate(
        tumorzuordnung.getDiagnosedatum().getValue().toGregorianCalendar().getTime());

    condition.setCode(
        new CodeableConcept(
            new Coding(
                fhirProperties.getSystems().getIcd10gm(),
                tumorzuordnung.getPrimaertumorICD().getCode(),
                "")));
    return condition;
  }
}
