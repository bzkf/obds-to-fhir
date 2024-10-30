package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConditionMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ConditionMapper.class);

  @Autowired
  public ConditionMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Condition map(OBDS.MengePatient.Patient.MengeMeldung.Meldung meldung, Reference patient) {
    var condition = new Condition();

    condition.setVerificationStatus(
      new CodeableConcept(
        new Coding(
          "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-primaertumor-diagnosesicherung",
          meldung.getDiagnose().getDiagnosesicherung(),
          "")));

    condition.setSubject(patient);
    condition.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoDiagnosePrimaertumor());

    var tumorzuordnung = meldung.getTumorzuordnung();
    if (tumorzuordnung == null) {
      throw new RuntimeException("Tumorzuordnung ist null");
    }

    condition.setCode(
      new CodeableConcept(
        new Coding(
          fhirProperties.getSystems().getIcd10gm(),
          tumorzuordnung.getPrimaertumorICD().getCode(),
          "").setVersion(tumorzuordnung.getPrimaertumorICD().getVersion())));

    if(tumorzuordnung.getMorphologieICDO() != null){
      condition.setCode(new CodeableConcept(
        new Coding(
          "http://terminology.hl7.org/CodeSystem/icd-o-3",
          tumorzuordnung.getMorphologieICDO().getCode(), ""
        ).setVersion(tumorzuordnung.getMorphologieICDO().getVersion())));
    }
    List<CodeableConcept> bodySites = new ArrayList<>();
    var seitenlokalisation = tumorzuordnung.getSeitenlokalisation();

    if(meldung.getDiagnose().getPrimaertumorTopographieICDO() != null){
      CodeableConcept topographie = new CodeableConcept(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/icd-o-3")
        .setCode(meldung.getDiagnose().getPrimaertumorTopographieICDO().getCode()));
      bodySites.add(topographie);
    }

    if (seitenlokalisation != null) {
      CodeableConcept bodySite = new CodeableConcept(new Coding().setSystem("https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-seitenlokalisation")
        .setCode(seitenlokalisation.value()));
      bodySites.add(bodySite);
    }
    condition.setBodySite(bodySites);

    condition.setCode(
      new CodeableConcept(
        new Coding(
          fhirProperties.getSystems().getIcd10gm(),
          tumorzuordnung.getPrimaertumorICD().getCode(),
          "")));

    condition.setRecordedDate(
      tumorzuordnung.getDiagnosedatum().getValue().toGregorianCalendar().getTime());


    return condition;
  }
}
