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
      LOG.warn("Cannot map empty list of MeldungExport to {}", ResourceType.Patient);
      return null;
    }

    // get first element of meldungExportList
    var meldungExport = meldungExportList.get(0);

    LOG.debug(
        "Mapping Meldung {} (one of total {} in export list) to {}",
        getReportingIdFromAdt(meldungExport),
        meldungExportList.size(),
        ResourceType.Patient);

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
    // TODO set genderExtension if AdministrativeGender.OTHER
    var genderMap = new HashMap<String, Enumerations.AdministrativeGender>();
    genderMap.put("W", Enumerations.AdministrativeGender.FEMALE);
    genderMap.put("M", Enumerations.AdministrativeGender.MALE);
    genderMap.put("D", Enumerations.AdministrativeGender.OTHER);
    genderMap.put("U", Enumerations.AdministrativeGender.UNKNOWN);

    patient.setGender(genderMap.getOrDefault(patData.getPatienten_Geschlecht(), null));

    if (patData.getPatienten_Geburtsdatum() != null) {
      patient.setBirthDateElement(
          new DateType(getBirthDateYearMonthString(patData.getPatienten_Geburtsdatum())));
    }

    // check if any one of the meldungen reported the death
    var deathReports =
        meldungExportList.stream()
            .filter(m -> getReportingReasonFromAdt(m) == Meldeanlass.TOD)
            .toList();

    // deceased
    if (!deathReports.isEmpty()) {
      // start by setting deceased to true. If a more detailed death date is
      // available in the data, override it further down this code path.
      patient.setDeceased(new BooleanType(true));

      // get the first entry with the largest version number where the death date is set
      var reportWithSterbeDatum =
          deathReports.stream()
              .sorted(Comparator.comparingInt(MeldungExport::getVersionsnummer).reversed())
              .filter(
                  m -> {
                    var mengeVerlauf =
                        m.getXml_daten()
                            .getMenge_Patient()
                            .getPatient()
                            .getMenge_Meldung()
                            .getMeldung()
                            .getMenge_Verlauf();

                    if (mengeVerlauf == null) {
                      return false;
                    }

                    if (mengeVerlauf.getVerlauf() == null) {
                      return false;
                    }

                    if (mengeVerlauf.getVerlauf().getTod() == null) {
                      return false;
                    }

                    return StringUtils.hasLength(
                        mengeVerlauf.getVerlauf().getTod().getSterbedatum());
                  })
              .findFirst();

      if (reportWithSterbeDatum.isPresent()) {
        var deathDate =
            reportWithSterbeDatum
                .get()
                .getXml_daten()
                .getMenge_Patient()
                .getPatient()
                .getMenge_Meldung()
                .getMeldung()
                .getMenge_Verlauf()
                .getVerlauf()
                .getTod()
                .getSterbedatum();

        patient.setDeceased(convertObdsDateToDateTimeType(deathDate));
      } else {
        LOG.warn("Sterbedatum not set on any of the Tod Meldungen.");
      }
    }

    // address
    var patAddess = patData.getMenge_Adresse().getAdresse().getFirst();
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
