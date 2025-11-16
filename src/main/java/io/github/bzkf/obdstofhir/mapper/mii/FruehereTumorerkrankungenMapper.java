package io.github.bzkf.obdstofhir.mapper.mii;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import com.google.common.hash.Hashing;
import de.basisdatensatz.obds.v3.DiagnoseTyp.MengeFruehereTumorerkrankung;
import de.basisdatensatz.obds.v3.DiagnoseTyp.MengeFruehereTumorerkrankung.FruehereTumorerkrankung;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FruehereTumorerkrankungenMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(FruehereTumorerkrankungenMapper.class);

  public FruehereTumorerkrankungenMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public @NonNull List<Condition> map(
      @NonNull MengeFruehereTumorerkrankung mengeFruehereTumorerkrankung,
      @NonNull Reference patient,
      @NonNull Reference primaerdiagnose,
      @NonNull Identifier primaerdiagnoseIdentifier,
      @NonNull XMLGregorianCalendar meldeDatum) {
    Objects.requireNonNull(mengeFruehereTumorerkrankung.getFruehereTumorerkrankung());
    verifyReference(patient, ResourceType.Patient);
    verifyReference(primaerdiagnose, ResourceType.Condition);
    var result = new ArrayList<Condition>();

    for (var fruehereTumorerkrankung : mengeFruehereTumorerkrankung.getFruehereTumorerkrankung()) {
      // check if there is either at least an ICD code or the free text
      if ((fruehereTumorerkrankung.getICD() != null
          && fruehereTumorerkrankung.getICD().getCode() != null)
          || StringUtils.hasText(fruehereTumorerkrankung.getFreitext())) {
        result.add(
            map(
                fruehereTumorerkrankung,
                patient,
                primaerdiagnose,
                primaerdiagnoseIdentifier,
                meldeDatum));
      } else {
        LOG.warn(
            "Fruehere_Tumorerkrankung contains neither an ICD code nor a free text element. "
                + "Unable to map to a Condition resource.");
      }
    }

    return result;
  }

  private @NonNull Condition map(
      FruehereTumorerkrankung fruehereTumorerkrankung,
      Reference patient,
      Reference primaerdiagnose,
      Identifier primaerdiagnoseIdentifier,
      XMLGregorianCalendar meldeDatum) {
    var condition = new Condition();
    var identifier = buildConditionIdentifier(fruehereTumorerkrankung, primaerdiagnoseIdentifier);
    condition.addIdentifier(identifier);
    condition.setId(computeResourceIdFromIdentifier(identifier));

    condition.setSubject(patient);
    // TODO: there should be a dedicated profile for the fruehere tumorerkrankung

    condition.addExtension(fhirProperties.getExtensions().getConditionRelated(), primaerdiagnose);

    var icd = new Coding().setSystem(fhirProperties.getSystems().getIcd10gm());

    if (fruehereTumorerkrankung.getICD() != null
        && StringUtils.hasText(fruehereTumorerkrankung.getICD().getCode())) {
      icd.setCode(fruehereTumorerkrankung.getICD().getCode());
      var icd10Version = fruehereTumorerkrankung.getICD().getVersion();
      StringType versionElement = null;
      if (StringUtils.hasText(icd10Version)) {
        var matcher = ConditionMapper.ICD_VERSION_PATTERN.matcher(icd10Version);
        if (matcher.matches() && StringUtils.hasText(matcher.group("versionYear"))) {
          versionElement = new StringType(matcher.group("versionYear"));
        } else {
          LOG.warn(
              "Unable to extract year from ICD_Version via RegEx '{}', actual: '{}'",
              ConditionMapper.ICD_VERSION_PATTERN.pattern(),
              icd10Version);
        }
      } else {
        LOG.warn("Fruehere_Tumorerkrankung ICD_Version is unset or contains only whitespaces");
      }

      if (versionElement == null) {
        versionElement = new StringType();
        versionElement.addExtension(
            fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
      }

      icd.setVersionElement(versionElement);
      condition.setCode(new CodeableConcept(icd));
    }

    if (StringUtils.hasText(fruehereTumorerkrankung.getFreitext())) {
      if (condition.hasCode()) {
        condition.getCode().setText(fruehereTumorerkrankung.getFreitext());
      } else {
        // if there's no Condition.code yet, it means that there's no ICD Code set but
        // only
        // the freitext element.
        var codingElement = new Coding();
        codingElement.addExtension(
            fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
        condition.setCode(
            new CodeableConcept(codingElement).setText(fruehereTumorerkrankung.getFreitext()));
      }
    }

    convertObdsDatumToDateTimeType(fruehereTumorerkrankung.getDiagnosedatum())
        .ifPresentOrElse(
            diagnoseDatum -> {
              // even though fruehereTumorerkrankung.getDiagnosedatum returns a
              // XMLGregorianCalender, per the oBDS Schema, it can only contain the year.
              diagnoseDatum.setPrecision(TemporalPrecisionEnum.YEAR);
              condition.addExtension(
                  fhirProperties.getExtensions().getConditionAssertedDate(), diagnoseDatum);
            },
            () -> {
              LOG.warn("Diagnosedatum is unset. Setting data absent extension.");
              var absentDateTime = new DateTimeType();
              absentDateTime.addExtension(
                  fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
              condition.addExtension(
                  fhirProperties.getExtensions().getConditionAssertedDate(), absentDateTime);
            });

    convertObdsDatumToDateTimeType(meldeDatum)
        .ifPresentOrElse(
            condition::setRecordedDateElement,
            () -> {
              LOG.warn("MeldeDatum is unset. Setting data absent extension.");
              var absentDateTime = new DateTimeType();
              absentDateTime.addExtension(
                  fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
              condition.setRecordedDateElement(absentDateTime);
            });

    return condition;
  }

  private Identifier buildConditionIdentifier(
      FruehereTumorerkrankung fruehereTumorerkrankung, Identifier primaerdiagnoseIdentifier) {

    // the identifier base is the primary condition identifier value
    var valueBuilder = new StringJoiner("-");
    valueBuilder.add(primaerdiagnoseIdentifier.getValue());

    if (fruehereTumorerkrankung.getICD() != null
        && StringUtils.hasText(fruehereTumorerkrankung.getICD().getCode())) {
      valueBuilder.add(fruehereTumorerkrankung.getICD().getCode());
    } else if (StringUtils.hasText(fruehereTumorerkrankung.getFreitext())) {
      // if we only have the free text, use its hash as part of the identifier
      var hashedFreeText = Hashing.sha256()
          .hashString(fruehereTumorerkrankung.getFreitext(), StandardCharsets.UTF_8)
          .toString();
      valueBuilder.add(hashedFreeText);
    } else {
      throw new IllegalArgumentException(
          "FruehereTumorerkrankung element doesn't contain "
              + "enough information to create an identifier.");
    }

    if (fruehereTumorerkrankung.getDiagnosedatum() != null) {
      valueBuilder.add(fruehereTumorerkrankung.getDiagnosedatum().toString());
    } else {
      LOG.warn(
          "The Fruehere_Tumorerkrankung element doesn't have the Diagnosedatum set, "
              + "this may cause duplicate Conditions to be created.");
    }

    var identifierValue = valueBuilder.toString();

    return new Identifier()
        .setSystem(
            fhirProperties.getSystems().getIdentifiers().getFruehereTumorerkrankungConditionId())
        .setValue(slugifier.slugify(identifierValue));
  }
}
