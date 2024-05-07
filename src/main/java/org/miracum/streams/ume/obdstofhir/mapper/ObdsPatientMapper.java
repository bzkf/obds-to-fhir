package org.miracum.streams.ume.obdstofhir.mapper;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.model.Meldeanlass;
import org.miracum.streams.ume.obdstofhir.model.MeldungExport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class ObdsPatientMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ObdsPatientMapper.class);

  @Value("${app.version}")
  private String appVersion;

  @Value("${app.enableCheckDigitConv}")
  private boolean checkDigitConversion;

  public ObdsPatientMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Bundle mapOnkoResourcesToPatient(List<MeldungExport> meldungExportList) {

    if (meldungExportList.isEmpty()) {
      return null;
    }

    // get first element of meldungExportList
    var meldungExport = meldungExportList.get(0);

    LOG.debug(
        "Mapping Meldung {} to {}", getReportingIdFromAdt(meldungExport), ResourceType.Patient);

    var patient = new Patient();

    // id
    var patId = getPatIdFromMeldung(meldungExport);
    var pid = patId;
    if (checkDigitConversion) {
      pid = convertId(patId);
    }
    var id = this.getHash(ResourceType.Patient, pid);
    patient.setId(id);

    // meta.source
    var senderInfo = meldungExport.getXml_daten().getAbsender();
    patient
        .getMeta()
        .setSource(
            generateProfileMetaSource(
                senderInfo.getAbsender_ID(), senderInfo.getSoftware_ID(), appVersion));

    // meta.profile
    patient
        .getMeta()
        .setProfile(
            Collections.singletonList(
                new CanonicalType(fhirProperties.getProfiles().getMiiPatientPseudonymisiert())));

    // MII identifier
    var pseudonym = new Identifier();
    pseudonym
        .getType()
        .addCoding(new Coding(fhirProperties.getSystems().getObservationValue(), "PSEUDED", null))
        .addCoding(
            new Coding(
                fhirProperties.getSystems().getIdentifierType(), "MR", "Medical·record·number"));
    pseudonym.setSystem(fhirProperties.getSystems().getPatientId()).setValue(pid);
    patient.addIdentifier(pseudonym);

    var patData =
        meldungExport.getXml_daten().getMenge_Patient().getPatient().getPatienten_Stammdaten();

    // gender
    var genderMap =
        new HashMap<String, Enumerations.AdministrativeGender>() {
          {
            put("W", Enumerations.AdministrativeGender.FEMALE);
            put("M", Enumerations.AdministrativeGender.MALE);
            put("D", Enumerations.AdministrativeGender.OTHER); // TODO set genderExtension
            put("U", Enumerations.AdministrativeGender.UNKNOWN);
          }
        };

    patient.setGender(genderMap.getOrDefault(patData.getPatienten_Geschlecht(), null));

    if (patData.getPatienten_Geburtsdatum() != null) {
      patient.setBirthDateElement(
          new DateType(getBirthDateYearMonthString(patData.getPatienten_Geburtsdatum())));
    }

    var reportingReason =
        meldungExport
            .getXml_daten()
            .getMenge_Patient()
            .getPatient()
            .getMenge_Meldung()
            .getMeldung()
            .getMeldeanlass();

    // deceased
    if (reportingReason == Meldeanlass.TOD) {
      var mengeVerlauf =
          meldungExport
              .getXml_daten()
              .getMenge_Patient()
              .getPatient()
              .getMenge_Meldung()
              .getMeldung()
              .getMenge_Verlauf();

      if (mengeVerlauf != null && mengeVerlauf.getVerlauf() != null) {

        var death = mengeVerlauf.getVerlauf().getTod();

        if (death.getSterbedatum() != null) {
          patient.setDeceased(convertObdsDateToDateTimeType(death.getSterbedatum()));
        }
      }
    }

    // address
    var patAddess = patData.getMenge_Adresse().getAdresse().get(0);
    if (StringUtils.hasLength(patAddess.getPatienten_PLZ())) {
      var address = new Address();
      address.setPostalCode(patAddess.getPatienten_PLZ()).setType(Address.AddressType.BOTH);
      if (patAddess.getPatienten_Land() != null
          && patAddess.getPatienten_Land().matches("[a-zA-Z]{2,3}")) {
        address.setCountry(patAddess.getPatienten_Land().toUpperCase());
      } else {
        address
            .addExtension()
            .setUrl(fhirProperties.getExtensions().getDataAbsentReason())
            .setValue(new CodeType("unknown"));
      }
      patient.addAddress(address);
    }

    var bundle = new Bundle();
    bundle.setType(Bundle.BundleType.TRANSACTION);
    bundle = addResourceAsEntryInBundle(bundle, patient);

    return bundle;
  }

  private static String getBirthDateYearMonthString(String gebdatum) {

    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(Locale.GERMANY);
    LocalDate localBirthDate = LocalDate.parse(gebdatum, formatter);

    var quarterMonth = ((localBirthDate.getMonthValue() - 1) / 3 + 1) * 3 - 2;

    return YearMonth.of(localBirthDate.getYear(), quarterMonth).toString();
  }
}
