package io.github.bzkf.obdstofhir.mapper;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import com.github.slugify.Slugify;
import de.basisdatensatz.obds.v3.DatumTagOderMonatGenauTyp;
import de.basisdatensatz.obds.v3.DatumTagOderMonatGenauTypSchaetzOptional;
import de.basisdatensatz.obds.v3.DatumTagOderMonatOderJahrOderNichtGenauTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.dizuker.tofhir.FhirExtensions.DataAbsentReason;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.xml.datatype.XMLGregorianCalendar;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public abstract class ObdsToFhirMapper {
  protected final FhirProperties fhirProperties;
  static String idHashAlgorithm;

  protected static final Slugify slugifier =
      Slugify.builder().lowerCase(false).locale(Locale.GERMAN).build();
  private static final Logger log = LoggerFactory.getLogger(ObdsToFhirMapper.class);

  /** Matches oBDS ICD-10 version strings, e.g. {@code "10 2023 GM"}, capturing the year. */
  protected static final Pattern ICD_VERSION_PATTERN =
      Pattern.compile("^(10 (?<versionYear>20\\d{2}) ((GM)|(WHO))|Sonstige)$");

  protected ObdsToFhirMapper(final FhirProperties fhirProperties) {
    this.fhirProperties = fhirProperties;
  }

  /**
   * Extracts the version year from an oBDS ICD-10 version string (see {@link
   * #ICD_VERSION_PATTERN}), falling back to a data-absent-reason extension if it is unset or
   * doesn't match the expected format.
   *
   * @param icdVersion the raw ICD version string, e.g. from {@code *_ICD_Version}
   * @param fieldName the oBDS field name, used only for the log message on a failed match
   * @param mapperLog the calling mapper's logger, so the message is attributed correctly
   */
  protected StringType extractIcdVersionYear(
      String icdVersion, String fieldName, Logger mapperLog) {
    if (StringUtils.hasText(icdVersion)) {
      var matcher = ICD_VERSION_PATTERN.matcher(icdVersion);
      if (matcher.matches() && StringUtils.hasText(matcher.group("versionYear"))) {
        return new StringType(matcher.group("versionYear"));
      }
      mapperLog.debug(
          "Unable to extract year from {} via RegEx '{}', actual: '{}'",
          fieldName,
          ICD_VERSION_PATTERN.pattern(),
          icdVersion);
    } else {
      mapperLog.debug("{} is unset or contains only whitespaces", fieldName);
    }

    var versionElement = new StringType();
    versionElement.addExtension(DataAbsentReason.unknown());
    return versionElement;
  }

  /**
   * Deduplicates {@code items} by their code, keeping only the highest version per code. Used where
   * oBDS allows multiple entries of the same code (e.g. ICD-O morphologies, OPS codes) but only the
   * newest version per code should be mapped to FHIR.
   *
   * @param items the raw, possibly duplicate-by-code items
   * @param codeOf extracts the code items are deduplicated by
   * @param versionOf extracts the lexicographically comparable version of an item
   */
  protected static <T> Collection<T> keepHighestVersionByCode(
      List<T> items, Function<T, String> codeOf, Function<T, String> versionOf) {
    var distinctCodes = new HashMap<String, T>();
    for (var item : items) {
      var code = codeOf.apply(item);
      var version = versionOf.apply(item);
      if (!distinctCodes.containsKey(code)) {
        distinctCodes.put(code, item);
        continue;
      }

      var existing = distinctCodes.get(code);
      var existingVersion = versionOf.apply(existing);
      if (version == null) {
        log.debug(
            "Multiple items with code {} found, but new version is unset. "
                + "Keeping one with existing version {}.",
            code,
            existingVersion);
      } else if (version.compareTo(existingVersion) > 0) {
        log.debug(
            "Multiple items with code {} found. Updating version {} over version {}.",
            code,
            version,
            existingVersion);
        distinctCodes.put(code, item);
      } else {
        log.debug(
            "Multiple items with code {} found. Keeping largest version {} over version {}.",
            code,
            existingVersion,
            version);
      }
    }
    return distinctCodes.values();
  }

  /**
   * Falls back to {@code meldungsId} as the identifier base when the oBDS-provided id field is
   * unset.
   *
   * @param value the oBDS id field, e.g. {@code Histologie_ID}
   * @param meldungsId the enclosing Meldung's id, used as a fallback
   */
  protected static String orMeldungId(String value, String meldungsId) {
    if (StringUtils.hasText(value)) {
      return value;
    }
    log.debug("Identifier field is unset, falling back to Meldung_ID '{}'.", meldungsId);
    return meldungsId;
  }

  public static Optional<DateType> convertObdsDatumToDateType(
      DatumTagOderMonatOderJahrOderNichtGenauTyp obdsDatum) {
    if (obdsDatum == null) {
      return Optional.empty();
    }
    var dateTimeType = convertObdsDatumToDateTimeType(obdsDatum);
    if (dateTimeType.isEmpty()) {
      return Optional.empty();
    }

    var dateType = new DateType(dateTimeType.get().getValue());
    dateType.setPrecision(dateTimeType.get().getPrecision());
    return Optional.of(dateType);
  }

  public static Optional<DateTimeType> convertObdsDatumToDateTimeType(
      DatumTagOderMonatGenauTypSchaetzOptional obdsDatum) {
    if (obdsDatum == null || obdsDatum.getValue() == null) {
      return Optional.empty();
    }

    var date = new DateTimeType(obdsDatum.getValue().toGregorianCalendar().getTime());

    // see the code for obdsDatum.getDatumsgenauigkeit(), the default value is 'E'
    // (exakt)
    // if the datumsgenauigkeit is not set.

    switch (obdsDatum.getDatumsgenauigkeit()) {
      // exakt (entspricht taggenau)
      case E:
        date.setPrecision(TemporalPrecisionEnum.DAY);
        break;
      // Tag geschätzt (entspricht monatsgenau)
      case T:
        date.setPrecision(TemporalPrecisionEnum.MONTH);
        break;
    }

    return Optional.of(date);
  }

  public static Optional<DateTimeType> convertObdsDatumToDateTimeType(
      DatumTagOderMonatGenauTyp obdsDatum) {
    if (null == obdsDatum || obdsDatum.getValue() == null) {
      return Optional.empty();
    }

    var date = new DateTimeType(obdsDatum.getValue().toGregorianCalendar().getTime());

    if (obdsDatum.getDatumsgenauigkeit() == null) {
      log.warn("Datumsgenauigkeit is unset. Assuming 'Day' precision.");
      // we set the precision to ensure that in FHIR the datetime
      // doesn't include the time part.
      date.setPrecision(TemporalPrecisionEnum.DAY);
      return Optional.of(date);
    }

    switch (obdsDatum.getDatumsgenauigkeit()) {
      // exakt (entspricht taggenau)
      case E:
        date.setPrecision(TemporalPrecisionEnum.DAY);
        break;
      // Tag geschätzt (entspricht monatsgenau)
      case T:
        date.setPrecision(TemporalPrecisionEnum.MONTH);
        break;
    }

    return Optional.of(date);
  }

  public static Optional<DateTimeType> convertObdsDatumToDateTimeType(
      DatumTagOderMonatOderJahrOderNichtGenauTyp obdsDatum) {
    if (obdsDatum == null || obdsDatum.getValue() == null) {
      return Optional.empty();
    }

    var date = new DateTimeType(obdsDatum.getValue().toGregorianCalendar().getTime());

    if (obdsDatum.getDatumsgenauigkeit() == null) {
      log.warn("Datumsgenauigkeit is unset. Assuming 'Day' precision.");
      // we set the precision to ensure that in FHIR the datetime
      // doesn't include the time part.
      date.setPrecision(TemporalPrecisionEnum.DAY);
      return Optional.of(date);
    }

    switch (obdsDatum.getDatumsgenauigkeit()) {
      // exakt (entspricht taggenau)
      case E:
        date.setPrecision(TemporalPrecisionEnum.DAY);
        break;
      // Tag geschätzt (entspricht monatsgenau)
      case T:
        date.setPrecision(TemporalPrecisionEnum.MONTH);
        break;
      // Monat geschätzt (entspricht jahrgenau)
      case M:
        date.setPrecision(TemporalPrecisionEnum.YEAR);
        break;
      // vollständig geschätzt (genaue Angabe zum Jahr nicht möglich)
      case V:
        log.warn(
            "Input date precision is completely estimated. "
                + "Not setting a date value in FHIR resource.");
        return Optional.empty();
    }

    return Optional.of(date);
  }

  public static Optional<DateTimeType> convertObdsDatumToDateTimeType(
      XMLGregorianCalendar obdsDatum) {
    if (null == obdsDatum) {
      return Optional.empty();
    }
    var date = new DateTimeType(obdsDatum.toGregorianCalendar().getTime());
    date.setPrecision(TemporalPrecisionEnum.DAY);
    return Optional.of(date);
  }

  /**
   * Default implementation of reference validation. This does not check the existance of the
   * referenced resource!
   *
   * @param reference The reference to be validated
   * @param resourceType The required resource type of the reference
   * @throws NullPointerException if reference is null
   * @throws IllegalArgumentException if reference is not of required reesource type.
   */
  public static void verifyReference(Reference reference, ResourceType resourceType)
      throws NullPointerException, IllegalArgumentException {
    Objects.requireNonNull(
        reference,
        String.format("Reference to a %s resource must not be null", resourceType.toString()));

    // extra handling for patients: allow logical-only references
    if (resourceType == ResourceType.Patient && !StringUtils.hasText(reference.getReference())) {
      Objects.requireNonNull(reference.getIdentifier());
      Validate.isTrue(StringUtils.hasText(reference.getIdentifier().getValue()));
    }

    Validate.isTrue(
        Objects.equals(reference.getReferenceElement().getResourceType(), resourceType.toString()),
        String.format(
            "The reference '%s' should point to a %s resource",
            reference.getReference(), resourceType));
  }
}
