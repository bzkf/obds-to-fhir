package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import java.util.ArrayList;
import java.util.List;
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

    if (meldung.getDiagnose().getDiagnosesicherung() != null) {
      condition.setVerificationStatus(
          new CodeableConcept(
              new Coding(
                  "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-primaertumor-diagnosesicherung",
                  meldung.getDiagnose().getDiagnosesicherung().value(),
                  "")));
    }

    condition.setSubject(patient);
    condition.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoDiagnosePrimaertumor());

    var tumorzuordnung = meldung.getTumorzuordnung();
    if (tumorzuordnung == null) {
      throw new RuntimeException("Tumorzuordnung ist null");
    }

    Coding icd =
        new Coding(
                fhirProperties.getSystems().getIcd10gm(),
                tumorzuordnung.getPrimaertumorICD().getCode(),
                "")
            .setVersion(tumorzuordnung.getPrimaertumorICD().getVersion());

    Coding morphologie =
        new Coding(
                fhirProperties.getSystems().getIcdo3Morphologie(),
                tumorzuordnung.getMorphologieICDO().getCode(),
                "")
            .setVersion(tumorzuordnung.getMorphologieICDO().getVersion());

    CodeableConcept code = new CodeableConcept().addCoding(icd).addCoding(morphologie);
    condition.setCode(code);

    List<CodeableConcept> bodySite = new ArrayList<>();

    if (meldung.getDiagnose().getPrimaertumorTopographieICDO() != null) {
      CodeableConcept topographie =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getIcdo3Morphologie())
                  .setCode(meldung.getDiagnose().getPrimaertumorTopographieICDO().getCode())
                  .setVersion(meldung.getDiagnose().getPrimaertumorTopographieICDO().getVersion()));
      bodySite.add(topographie);
    }

    if (tumorzuordnung.getSeitenlokalisation() != null) {
      CodeableConcept seitenlokalisation =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getMiiCsOnkoSeitenlokalisation())
                  .setCode(tumorzuordnung.getSeitenlokalisation().value()));
      bodySite.add(seitenlokalisation);
    }
    condition.setBodySite(bodySite);

    condition.setRecordedDate(
        tumorzuordnung.getDiagnosedatum().getValue().toGregorianCalendar().getTime());
    return condition;
  }
}
