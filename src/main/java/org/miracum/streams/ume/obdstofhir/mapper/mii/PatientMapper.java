package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.PatientenStammdatenMelderTyp;
import java.util.*;
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
  private Map<PatientenStammdatenMelderTyp.Geschlecht, Enumerations.AdministrativeGender> genderMap;

  @Autowired
  public PatientMapper(FhirProperties fhirProperties) {
    super(fhirProperties);

    genderMap = new HashMap<>();
    genderMap.put(
        PatientenStammdatenMelderTyp.Geschlecht.W, Enumerations.AdministrativeGender.FEMALE);
    genderMap.put(
        PatientenStammdatenMelderTyp.Geschlecht.M, Enumerations.AdministrativeGender.MALE);
    genderMap.put(
        PatientenStammdatenMelderTyp.Geschlecht.D, Enumerations.AdministrativeGender.OTHER);
    genderMap.put(
        PatientenStammdatenMelderTyp.Geschlecht.U, Enumerations.AdministrativeGender.UNKNOWN);
    genderMap.put(
        PatientenStammdatenMelderTyp.Geschlecht.X, Enumerations.AdministrativeGender.OTHER);

    genderMap = Collections.unmodifiableMap(genderMap);
  }

  public Patient map(
      OBDS.MengePatient.Patient obdsPatient,
      List<OBDS.MengePatient.Patient.MengeMeldung.Meldung> meldungen) {
    var patient = new Patient();
    patient.getMeta().addProfile(fhirProperties.getProfiles().getMiiPatientPseudonymisiert());

    // TODO: this could be placed inside the application.yaml as well
    //       and mapped to the fhir props
    var mrTypeConcept = new CodeableConcept();
    mrTypeConcept
        .addCoding()
        .setSystem(fhirProperties.getSystems().getIdentifierType())
        .setCode("MR")
        .setDisplay("Medical record number");
    mrTypeConcept
        .addCoding()
        .setSystem(fhirProperties.getSystems().getObservationValue())
        .setCode("PSEUDED")
        .setDisplay("pseudonymized");

    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getPatientId())
            .setValue(obdsPatient.getPatientID())
            .setType(mrTypeConcept);
    patient.addIdentifier(identifier);
    patient.setId(computeResourceIdFromIdentifier(identifier));

    patient.setGender(
        genderMap.getOrDefault(obdsPatient.getPatientenStammdaten().getGeschlecht(), null));

    if (obdsPatient.getPatientenStammdaten().getGeburtsdatum().getValue() != null) {
      // TODO: likely has to take Datumsgenauigkeit into considerations
      patient.setBirthDateElement(
          new DateType(
              obdsPatient
                  .getPatientenStammdaten()
                  .getGeburtsdatum()
                  .getValue()
                  .toGregorianCalendar()
                  .getTime()));
    }

    // check if any of the meldungen reported death
    var deathReports = meldungen.stream().filter(m -> m.getTod() != null).toList();

    if (!deathReports.isEmpty()) {
      if (deathReports.size() > 1) {
        LOG.warn("Meldungen contains more than one death type.");
      }
      patient.setDeceased(getDeceased(deathReports));
    }

    return patient;
  }

  private Type getDeceased(List<OBDS.MengePatient.Patient.MengeMeldung.Meldung> deathReports) {
    // If a more detailed death date is not available in the data, return true.
    return new BooleanType(true);
  }
}
