package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.PatientenStammdatenMelderTyp;
import java.util.List;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PatientMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(PatientMapper.class);

  @Autowired
  public PatientMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Patient map(
      PatientenStammdatenMelderTyp stammdaten,
      List<OBDS.MengePatient.Patient.MengeMeldung.Meldung> meldungen) {
    var patient = new Patient();
    patient.getMeta().addProfile(fhirProperties.getProfiles().getMiiPatientPseudonymisiert());
    return patient;
  }
}
