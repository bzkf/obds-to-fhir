package io.github.bzkf.obdstofhir.mapper;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import com.github.slugify.Slugify;
import de.basisdatensatz.obds.v3.DatumTagOderMonatGenauTyp;
import de.basisdatensatz.obds.v3.DatumTagOderMonatGenauTypSchaetzOptional;
import de.basisdatensatz.obds.v3.DatumTagOderMonatOderJahrOderNichtGenauTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.dizuker.tofhir.FhirExtensions.DataAbsentReason;
import io.github.dizuker.tofhir.IdUtils;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.ObservationCategory;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public abstract class ObdsToFhirMapper {
  protected final FhirProperties fhirProperties;

  protected static final Slugify slugifier =
      Slugify.builder().lowerCase(false).locale(Locale.GERMAN).build();
  private static final Logger log = LoggerFactory.getLogger(ObdsToFhirMapper.class);

  /**
   * Matches oBDS ICD-10 version strings, e.g. {@code "10 2023 GM"}, capturing the year and the
   * catalog.
   */
  protected static final Pattern ICD_VERSION_PATTERN =
      Pattern.compile("^(10 (?<versionYear>20\\d{2}) (?<catalog>GM|WHO)|Sonstige)$");

  /** The ICD-10 catalog named by an oBDS {@code *_ICD_Version}. */
  public enum Icd10Catalog {
    GM,
    WHO,
    /** {@code Sonstige}: the source states that neither GM nor WHO was used. */
    OTHER
  }

  protected ObdsToFhirMapper(final FhirProperties fhirProperties) {
    this.fhirProperties = fhirProperties;
  }

  /**
   * Builds an {@code Observation.category} concept from the HL7 observation-category code system.
   *
   * @param category the category to code, e.g. {@link ObservationCategory#LABORATORY}
   */
  protected CodeableConcept observationCategory(ObservationCategory category) {
    return new CodeableConcept(
        new Coding(
            fhirProperties.getSystems().getObservationCategory(),
            category.toCode(),
            category.getDisplay()));
  }

  /**
   * Creates an Observation in the shape the MII Onkologie module profiles share: the profile, a
   * final status, the patient as subject, the focus (usually the primary Condition), an identifier
   * the id is derived from and, when known, the effective date.
   */
  protected Observation createBaseObservation(
      @NonNull String profile,
      @NonNull String identifierSystem,
      @NonNull String identifierValue,
      @NonNull Reference patient,
      @Nullable Reference focus,
      @Nullable DateTimeType effective) {
    var observation = new Observation();
    observation.getMeta().addProfile(profile);
    observation.setSubject(patient);
    observation.setStatus(Observation.ObservationStatus.FINAL);
    setIdentifierAndId(observation, identifierSystem, identifierValue);

    if (focus != null) {
      observation.addFocus(focus);
    }

    if (effective != null) {
      observation.setEffective(effective);
    }

    return observation;
  }

  /**
   * Adds an identifier with the slugified value to the resource and derives the resource's id from
   * it.
   *
   * @param resource a resource with an {@code identifier} element
   */
  protected static void setIdentifierAndId(
      @NonNull Resource resource, @NonNull String system, @NonNull String value) {
    var identifier = new Identifier().setSystem(system).setValue(slugifier.slugify(value));
    resource.setProperty("identifier", identifier);
    resource.setId(IdUtils.fromIdentifier(identifier));
  }

  /** A dateTime for elements a profile requires, but whose value isn't known. */
  protected static DateTimeType absentDateTime() {
    var absentDateTime = new DateTimeType();
    absentDateTime.addExtension(DataAbsentReason.unknown());
    return absentDateTime;
  }

  /**
   * A data-absent-reason concept for elements typed as one, e.g. {@code
   * Observation.dataAbsentReason}. to-fhir 0.2.23 offers {@code
   * FhirExtensions.DataAbsentReason.codeableConcept} for this, but the 0.3.0 alphas used for the
   * 2027 IG don't have it yet.
   */
  protected static CodeableConcept dataAbsentReason(Enumerations.DataAbsentReason reason) {
    return new CodeableConcept(
        new Coding(reason.getSystem(), reason.toCode(), reason.getDisplay()));
  }

  /** A SNOMED CT coding in the configured edition and version. */
  protected Coding snomed(@NonNull String code, @Nullable String display) {
    return fhirProperties.getCodings().snomed().setCode(code).setDisplay(display);
  }

  /** A LOINC coding in the configured version. */
  protected Coding loinc(@NonNull String code, @Nullable String display) {
    return fhirProperties.getCodings().loinc().setCode(code).setDisplay(display);
  }

  /** A Quantity whose unit is also its UCUM code. */
  protected Quantity ucumQuantity(@NonNull BigDecimal value, @NonNull String unit) {
    return new Quantity()
        .setValue(value)
        .setUnit(unit)
        .setSystem(fhirProperties.getSystems().getUcum())
        .setCode(unit);
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
   * Extracts the catalog from an oBDS ICD-10 version string (see {@link #ICD_VERSION_PATTERN}).
   *
   * @param icdVersion the raw ICD version string, e.g. from {@code *_ICD_Version}
   * @return empty if the version is unset or doesn't match the expected format
   */
  protected static Optional<Icd10Catalog> extractIcdCatalog(String icdVersion) {
    if (!StringUtils.hasText(icdVersion)) {
      return Optional.empty();
    }

    var matcher = ICD_VERSION_PATTERN.matcher(icdVersion);
    if (!matcher.matches()) {
      return Optional.empty();
    }

    var catalog = matcher.group("catalog");
    // the pattern matched without the catalog group, so the version is "Sonstige"
    if (catalog == null) {
      return Optional.of(Icd10Catalog.OTHER);
    }
    return Optional.of(Icd10Catalog.valueOf(catalog));
  }

  /**
   * Maps an ICD code to the codings used by the primary tumor, previous tumor and OP complication
   * profiles, the first one is always the GM one. WHO retains its own coding alongside the
   * explicitly absent GM coding. An unset or unparsable catalog retains the existing GM fallback
   * policy. Text and profile-specific requirements remain the caller's responsibility.
   */
  protected List<Coding> mapIcdToCodings(
      String code, String version, String fieldName, Logger mapperLog) {
    var versionYear = extractIcdVersionYear(version, fieldName, mapperLog);
    var catalog = extractIcdCatalog(version).orElse(Icd10Catalog.GM);
    var gm = new Coding().setSystem(fhirProperties.getSystems().getIcd10gm());
    if (catalog == Icd10Catalog.GM) {
      gm.setCode(code);
      gm.setVersionElement(versionYear);
    } else {
      gm.getCodeElement().addExtension(DataAbsentReason.notApplicable());
      var absentVersion = new StringType();
      absentVersion.addExtension(DataAbsentReason.notApplicable());
      gm.setVersionElement(absentVersion);
    }
    var codings = new ArrayList<Coding>();
    codings.add(gm);
    if (catalog == Icd10Catalog.WHO) {
      codings.add(
          new Coding()
              .setSystem(fhirProperties.getSystems().getIcd10who())
              .setCode(code)
              .setVersionElement(versionYear));
    }
    return codings;
  }

  /**
   * Maps an ICD code to a concept with the codings of {@link #mapIcdToCodings}. No system can be
   * asserted for {@code Sonstige}, so its code is kept as free text.
   */
  protected CodeableConcept mapIcdToConcept(
      String code, String version, String fieldName, Logger mapperLog) {
    var concept =
        new CodeableConcept().setCoding(mapIcdToCodings(code, version, fieldName, mapperLog));
    if (extractIcdCatalog(version).orElse(Icd10Catalog.GM) == Icd10Catalog.OTHER) {
      concept.setText(code);
    }
    return concept;
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

    // go through the string representation to avoid a time zone dependent Date round trip
    var dateType = new DateType(dateTimeType.get().getValueAsString());
    return Optional.of(dateType);
  }

  /**
   * Creates a DateTimeType from the calendar date (year, month, day) of the given value. oBDS dates
   * are plain calendar dates, converting them through an instant would make the result depend on
   * the JVM's default time zone.
   */
  private static DateTimeType toDateTimeType(XMLGregorianCalendar calendar) {
    var month = calendar.getMonth() == DatatypeConstants.FIELD_UNDEFINED ? 1 : calendar.getMonth();
    var day = calendar.getDay() == DatatypeConstants.FIELD_UNDEFINED ? 1 : calendar.getDay();
    return new DateTimeType(String.format("%04d-%02d-%02d", calendar.getYear(), month, day));
  }

  public static Optional<DateTimeType> convertObdsDatumToDateTimeType(
      DatumTagOderMonatGenauTypSchaetzOptional obdsDatum) {
    if (obdsDatum == null || obdsDatum.getValue() == null) {
      return Optional.empty();
    }

    var date = toDateTimeType(obdsDatum.getValue());

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

    var date = toDateTimeType(obdsDatum.getValue());

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

    var date = toDateTimeType(obdsDatum.getValue());

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
    var date = toDateTimeType(obdsDatum);
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
      return;
    }

    Validate.isTrue(
        Objects.equals(reference.getReferenceElement().getResourceType(), resourceType.toString()),
        String.format(
            "The reference '%s' should point to a %s resource",
            reference.getReference(), resourceType));
  }
}
