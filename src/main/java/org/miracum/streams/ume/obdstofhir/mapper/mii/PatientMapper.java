package org.miracum.streams.ume.obdstofhir.mapper.mii;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import com.google.common.base.Strings;
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
  private final Map<PatientenStammdatenMelderTyp.Geschlecht, Enumerations.AdministrativeGender>
      genderMap;

  @Autowired
  public PatientMapper(FhirProperties fhirProperties) {
    super(fhirProperties);

    var gMap =
        new EnumMap<PatientenStammdatenMelderTyp.Geschlecht, Enumerations.AdministrativeGender>(
            PatientenStammdatenMelderTyp.Geschlecht.class);
    gMap.put(PatientenStammdatenMelderTyp.Geschlecht.W, Enumerations.AdministrativeGender.FEMALE);
    gMap.put(PatientenStammdatenMelderTyp.Geschlecht.M, Enumerations.AdministrativeGender.MALE);
    gMap.put(PatientenStammdatenMelderTyp.Geschlecht.D, Enumerations.AdministrativeGender.OTHER);
    gMap.put(PatientenStammdatenMelderTyp.Geschlecht.U, Enumerations.AdministrativeGender.UNKNOWN);
    gMap.put(PatientenStammdatenMelderTyp.Geschlecht.X, Enumerations.AdministrativeGender.OTHER);

    this.genderMap = Collections.unmodifiableMap(gMap);
  }

  public Patient map(
      OBDS.MengePatient.Patient obdsPatient,
      List<OBDS.MengePatient.Patient.MengeMeldung.Meldung> meldungen) {
    var patient = new Patient();
    patient.getMeta().addProfile(fhirProperties.getProfiles().getMiiPatientPseudonymisiert());

    if (Strings.isNullOrEmpty(obdsPatient.getPatientID())) {
      throw new IllegalArgumentException("Patient ID is unset.");
    }

    // TODO: this could be placed inside the application.yaml as well
    // and mapped to the fhir props
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
      var birthdate =
          convertObdsDatumToDateType(obdsPatient.getPatientenStammdaten().getGeburtsdatum());
      patient.setBirthDateElement(birthdate);
    }

    // check if any of the meldungen reported death
    var deathReports = meldungen.stream().filter(m -> m.getTod() != null).toList();

    if (!deathReports.isEmpty()) {
      if (deathReports.size() > 1) {
        LOG.warn("Meldungen contains more than one death report.");
      }
      // sorts ascending by default, so to most recent report ist the last one in the list
      var latestReport =
          deathReports.stream()
              .sorted(Comparator.comparing(r -> r.getTod().getSterbedatum().toGregorianCalendar()))
              .toList()
              .getLast();
      if (latestReport.getTod() != null && latestReport.getTod().getSterbedatum() != null) {
        var deceased =
            new DateTimeType(
                latestReport.getTod().getSterbedatum().toGregorianCalendar().getTime());
        deceased.setPrecision(TemporalPrecisionEnum.DAY);
        patient.setDeceased(deceased);
      }
    }

    // address
    var patAddress = obdsPatient.getPatientenStammdaten().getAdresse();

    if (patAddress != null) {
      var address = new Address().setType(Address.AddressType.BOTH);

      if (!Strings.isNullOrEmpty(patAddress.getPLZ())) {
        address.setPostalCode(patAddress.getPLZ());
      } else {
        address
            .getPostalCodeElement()
            .addExtension()
            .setUrl(fhirProperties.getExtensions().getDataAbsentReason())
            .setValue(new CodeType("unknown"));
      }

      var land = patAddress.getLand();
      if (!Strings.isNullOrEmpty(land) && land.matches("[a-zA-Z]{2,3}")) {
        address.setCountry(land.toUpperCase());
      } else {
        address
            .getCountryElement()
            .addExtension()
            .setUrl(fhirProperties.getExtensions().getDataAbsentReason())
            .setValue(new CodeType("unknown"));
      }

      patient.addAddress(address);
    }

    return patient;
  }
}
