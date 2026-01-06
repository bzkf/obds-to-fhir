package io.github.bzkf.obdstofhir.mapper;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import com.github.slugify.Slugify;
import de.basisdatensatz.obds.v3.DatumTagOderMonatGenauTyp;
import de.basisdatensatz.obds.v3.DatumTagOderMonatGenauTypSchaetzOptional;
import de.basisdatensatz.obds.v3.DatumTagOderMonatOderJahrOderNichtGenauTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.security.MessageDigest;
import java.util.*;
import javax.xml.datatype.XMLGregorianCalendar;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public abstract class ObdsToFhirMapper {
  protected final FhirProperties fhirProperties;
  static String idHashAlgorithm;

  protected static final Slugify slugifier =
      Slugify.builder().lowerCase(false).locale(Locale.GERMAN).build();
  private static final Logger log = LoggerFactory.getLogger(ObdsToFhirMapper.class);

  private MessageDigest getMessageDigest() {
    if (null != idHashAlgorithm && idHashAlgorithm.equalsIgnoreCase("md5")) {
      return DigestUtils.getMd5Digest();
    }
    return DigestUtils.getSha256Digest();
  }

  @Value("${fhir.mappings.idHashAlgorithm:sha256}")
  void setIdHashAlgorithm(String idHashAlgorithm) {
    ObdsToFhirMapper.idHashAlgorithm = idHashAlgorithm;
  }

  protected ObdsToFhirMapper(final FhirProperties fhirProperties) {
    this.fhirProperties = fhirProperties;
  }

  protected String computeResourceIdFromIdentifier(Identifier identifier) {
    Validate.notBlank(identifier.getSystem());
    Validate.notBlank(
        identifier.getValue(),
        "Identifier value must not be blank. System: %s",
        identifier.getSystem());
    return new DigestUtils(getMessageDigest())
        .digestAsHex(identifier.getSystem() + "|" + identifier.getValue());
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

    // see the code for obdsDatum.getDatumsgenauigkeit(), the default value is 'E' (exakt)
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
   * @return Will return `true` if reference is usable
   * @throws NullPointerException if reference is null
   * @throws IllegalArgumentException if reference is not of required reesource type.
   */
  public static boolean verifyReference(Reference reference, ResourceType resourceType)
      throws NullPointerException, IllegalArgumentException {
    Objects.requireNonNull(
        reference,
        String.format("Reference to a %s resource must not be null", resourceType.toString()));
    Validate.isTrue(
        Objects.equals(reference.getReferenceElement().getResourceType(), resourceType.toString()),
        String.format("The reference should point to a %s resource", resourceType));

    return true;
  }
}
