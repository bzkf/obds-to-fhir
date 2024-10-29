package org.miracum.streams.ume.obdstofhir.mapper;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import com.google.common.hash.Hashing;
import de.basisdatensatz.obds.v3.DatumTagOderMonatGenauTyp;
import de.basisdatensatz.obds.v3.DatumTagOderMonatOderJahrOderNichtGenauTyp;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.model.Meldeanlass;
import org.miracum.streams.ume.obdstofhir.model.MeldungExport;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

public abstract class ObdsToFhirMapper {
  protected final FhirProperties fhirProperties;
  static boolean checkDigitConversion;
  static Pattern localPatientIdPattern = Pattern.compile("[^0]\\d{8}");

  private static final Logger log = LoggerFactory.getLogger(ObdsToFhirMapper.class);

  @Value("${app.patient-id-pattern:[^0]\\d{8}}")
  void setStringPattern(String value) {
    try {
      ObdsToFhirMapper.localPatientIdPattern = Pattern.compile(value);
    } catch (Exception e) {
      log.error("Not a valid patient ID pattern: {}. Use valid RegExp instead.", value);
      throw e;
    }
  }

  @Value("${app.enableCheckDigitConv:false}")
  void setCheckDigitConversion(boolean checkDigitConversion) {
    ObdsToFhirMapper.checkDigitConversion = checkDigitConversion;
  }

  protected ObdsToFhirMapper(final FhirProperties fhirProperties) {
    this.fhirProperties = fhirProperties;
  }

  protected String getHash(ResourceType type, String id) {
    String idToHash;
    switch (type.toString()) {
      case "Patient":
        idToHash = fhirProperties.getSystems().getPatientId();
        break;
      case "Condition":
        idToHash = fhirProperties.getSystems().getConditionId();
        break;
      case "Observation":
        idToHash = fhirProperties.getSystems().getObservationId();
        break;
      case "MedicationStatement":
        idToHash = fhirProperties.getSystems().getMedicationStatementId();
        break;
      case "Procedure":
        idToHash = fhirProperties.getSystems().getProcedureId();
        break;
      case "Surrogate":
        return Hashing.sha256().hashString(id, StandardCharsets.UTF_8).toString();
      default:
        return null;
    }
    return Hashing.sha256().hashString(idToHash + "|" + id, StandardCharsets.UTF_8).toString();
  }

  protected String computeResourceIdFromIdentifier(Identifier identifier) {
    return Hashing.sha256()
        .hashString(identifier.getSystem() + "|" + identifier.getValue(), StandardCharsets.UTF_8)
        .toString();
  }

  protected static String getConvertedPatIdFromMeldung(MeldungExport meldung) {
    var patId = getPatIdFromMeldung(meldung);
    if (checkDigitConversion) {
      return convertId(patId);
    }
    return patId;
  }

  protected static String convertId(String id) {
    Matcher matcher = ObdsToFhirMapper.localPatientIdPattern.matcher(id);
    if (matcher.find()) {
      if (matcher.groupCount() == 0) {
        return matcher.group();
      }
      var resultBuilder = new StringBuilder();
      for (int i = 1; i <= matcher.groupCount(); i++) {
        var x = matcher.group(i);
        resultBuilder.append(x);
      }
      return resultBuilder.toString();
    } else {
      log.warn("Identifier to convert does not match pattern: {}", matcher.pattern().toString());
      return id;
    }
  }

  public static List<MeldungExport> prioritiseLatestMeldungExports(
      MeldungExportList meldungExports, List<Meldeanlass> priorityOrder, List<Meldeanlass> filter) {
    var meldungen = meldungExports.getElements();

    var meldungExportMap = new HashMap<String, MeldungExport>();
    // meldeanlass bleibt in LKR Meldung immer gleich
    for (var meldung : meldungen) {
      if (filter == null || filter.contains(getReportingReasonFromAdt(meldung))) {
        var lkrId = getReportingIdFromAdt(meldung);
        var currentMeldungVersion = meldungExportMap.get(lkrId);
        if (currentMeldungVersion == null
            || meldung.getVersionsnummer() > currentMeldungVersion.getVersionsnummer()) {
          meldungExportMap.put(lkrId, meldung);
        }
      }
    }

    List<MeldungExport> meldungExportList = new ArrayList<>(meldungExportMap.values());

    Comparator<MeldungExport> meldungComparator =
        Comparator.comparingInt(
            m -> {
              int index = priorityOrder.indexOf(getReportingReasonFromAdt(m));
              return index == -1 ? Integer.MAX_VALUE : index;
            });

    return meldungExportList.stream().sorted(meldungComparator).collect(Collectors.toList());
  }

  public static String getPatIdFromMeldung(MeldungExport meldung) {
    return meldung
        .getXml_daten()
        .getMenge_Patient()
        .getPatient()
        .getPatienten_Stammdaten()
        .getPatient_ID();
  }

  public static String getTumorIdFromMeldung(MeldungExport meldung) {
    return meldung
        .getXml_daten()
        .getMenge_Patient()
        .getPatient()
        .getMenge_Meldung()
        .getMeldung()
        .getTumorzuordnung()
        .getTumor_ID();
  }

  public static Meldeanlass getReportingReasonFromAdt(MeldungExport meldung) {
    return meldung
        .getXml_daten()
        .getMenge_Patient()
        .getPatient()
        .getMenge_Meldung()
        .getMeldung()
        .getMeldeanlass();
  }

  public static String getReportingIdFromAdt(MeldungExport meldung) {
    return meldung
        .getXml_daten()
        .getMenge_Patient()
        .getPatient()
        .getMenge_Meldung()
        .getMeldung()
        .getMeldung_ID();
  }

  protected Bundle addResourceAsEntryInBundle(Bundle bundle, DomainResource resource) {
    bundle
        .addEntry()
        .setFullUrl(
            new Reference(
                    String.format("%s/%s", resource.getResourceType().name(), resource.getId()))
                .getReference())
        .setResource(resource)
        .setRequest(
            new Bundle.BundleEntryRequestComponent()
                .setMethod(Bundle.HTTPVerb.PUT)
                .setUrl(
                    String.format("%s/%s", resource.getResourceType().name(), resource.getId())));

    return bundle;
  }

  protected String generateProfileMetaSource(
      String senderId, String softwareId, String appVersion) {
    if (senderId != null && softwareId != null) {
      return String.format("%s.%s:obds-to-fhir:%s", senderId, softwareId, appVersion);
    } else {
      return "obds-to-fhir:" + appVersion;
    }
  }

  public static DateTimeType convertObdsDateToDateTimeType(String obdsDate) {

    if (!StringUtils.hasText(obdsDate)) {
      return null;
    }

    // default formatter for xs:date
    var formatter = DateTimeFormatter.ISO_OFFSET_DATE;

    // if it's a Datum_Typ
    if (obdsDate.matches("(([0-2]\\d)|(3[01]))\\.((0\\d)|(1[0-2]))\\.(18|19|20)\\d\\d")) {
      // 00.00.2022 -> 01.07.2022
      // 00.04.2022 -> 15.04.2022
      if (obdsDate.matches("^00.00.\\d{4}$")) {
        obdsDate = "01.07." + obdsDate.substring(obdsDate.length() - 4);
      } else if (obdsDate.matches("^00.\\d{2}.\\d{4}$")) {
        obdsDate = "15." + obdsDate.substring(3);
      }
      formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    }

    // if already in FHIR format 'yyyy-MM-dd' e.g. if it is in oBDS 3.x format
    if (obdsDate.matches("^(18|19|20)\\d\\d-((0\\d)|(1[0-2]))-([0-2]\\d)|(3[01])$")) {
      // 2022-00-00 -> 2022-07-01
      // 2022-04-00 -> 2022-04-15
      if (obdsDate.matches("^\\d{4}-00-00$")) {
        obdsDate = obdsDate.substring(0, 4) + "-07-01";
      } else if (obdsDate.matches("^\\d{4}-\\d{2}-00$")) {
        obdsDate = obdsDate.substring(0, 7) + "-15";
      }
      formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    }

    try {
      var adtLocalDate = LocalDate.parse(obdsDate, formatter);
      var adtLocalDateTime = adtLocalDate.atStartOfDay();
      var adtDateTime =
          new DateTimeType(Date.from(adtLocalDateTime.atZone(ZoneOffset.UTC).toInstant()));
      adtDateTime.setPrecision(TemporalPrecisionEnum.DAY);
      return adtDateTime;
    } catch (DateTimeException e) {
      log.error("Cannot parse '{}' as date", obdsDate);
      throw e;
    }
  }

  public static boolean isIcd10GmCode(String value) {
    return null != value && value.matches("[A-Z][0-9]{2}(\\.[0-9]{1,2})?");
  }

  public static DateType convertObdsDatumToDateType(
      DatumTagOderMonatOderJahrOderNichtGenauTyp obdsDatum) {
    var dateTime = new DateType(obdsDatum.getValue().toGregorianCalendar().getTime());
    switch (obdsDatum.getDatumsgenauigkeit()) {
        // exakt (entspricht taggenau)
      case E:
        dateTime.setPrecision(TemporalPrecisionEnum.DAY);
        break;
        // Tag geschätzt (entspricht monatsgenau)
      case T:
        dateTime.setPrecision(TemporalPrecisionEnum.MONTH);
        break;
        // Monat geschätzt (entspricht jahrgenau)
      case M:
        dateTime.setPrecision(TemporalPrecisionEnum.YEAR);
        break;
        // vollständig geschätzt (genaue Angabe zum Jahr nicht möglich)
      case V:
        log.warn("Date precision is completely estimated. Likely not a correct value.");
    }
    return dateTime;
  }

  public static DateType convertObdsDatumToDateTimeType(DatumTagOderMonatGenauTyp obdsDatum) {
    return null;
  }
}
