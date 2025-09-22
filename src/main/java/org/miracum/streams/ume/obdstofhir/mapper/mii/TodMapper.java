package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.AllgemeinICDTyp;
import de.basisdatensatz.obds.v3.TodTyp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TodMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(TodMapper.class);
  private static final Pattern icdVersionPattern =
      Pattern.compile("^(10 (?<versionYear>20\\d{2}) ((GM)|(WHO))|Sonstige)$");

  public TodMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  private Observation createBaseObservation(
      Reference patient, Reference condition, Optional<DateTimeType> todesZeitpunkt, TodTyp tod) {

    var observation = new Observation();

    observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoTod());
    observation.setStatus(Observation.ObservationStatus.FINAL);

    // Code | 184305005 | Cause of death (observable entity)
    var snomedCode =
        fhirProperties
            .getCodings()
            .snomed()
            .setCode("184305005")
            .setDisplay("Cause of death (observable entity)");
    observation.setCode(new CodeableConcept(snomedCode));

    // Subject
    observation.setSubject(patient);

    // Effective | Sterbedatum
    if (todesZeitpunkt.isPresent()) {
      observation.setEffective(todesZeitpunkt.get());
    }

    // Focus | Bezugsdiagnose
    observation.addFocus(condition);

    // Interpretation | Tod Tumorbedingt
    if (tod.getTodTumorbedingt() != null) {
      var interpretation = new CodeableConcept();
      interpretation
          .addCoding()
          .setSystem(fhirProperties.getSystems().getMiiCsOnkoTodInterpretation())
          .setCode(tod.getTodTumorbedingt().value());
      observation.setInterpretation(Arrays.asList(interpretation));
    }

    return observation;
  }

  public List<Observation> map(
      TodTyp tod, String meldungId, Reference patient, Reference condition) {
    // Validation
    Objects.requireNonNull(tod);

    String identifierValue;

    if (tod.getAbschlussID() != null) {
      identifierValue = tod.getAbschlussID();
    } else {
      identifierValue = meldungId;
    }

    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    var observationList = new ArrayList<Observation>();

    var todesZeitpunkt = convertObdsDatumToDateTimeType(tod.getSterbedatum());

    if (tod.getMengeTodesursachen() != null) {
      for (AllgemeinICDTyp todesursache : tod.getMengeTodesursachen().getTodesursacheICD()) {

        var observation = createBaseObservation(patient, condition, todesZeitpunkt, tod);

        // Identifier: set identifier per todesursache
        Identifier identifier =
            new Identifier()
                .setSystem(fhirProperties.getSystems().getIdentifiers().getTodObservationId())
                .setValue(slugifier.slugify(identifierValue + "-" + todesursache.getCode()));
        observation.addIdentifier(identifier);
        observation.setId(computeResourceIdFromIdentifier(identifier));

        // Value | Todesursache(n) ICD10GM
        var icd10Version = todesursache.getVersion();
        StringType versionElement = null;
        if (StringUtils.hasText(icd10Version)) {
          var matcher = icdVersionPattern.matcher(icd10Version);
          if (matcher.matches() && StringUtils.hasText(matcher.group("versionYear"))) {
            versionElement = new StringType(matcher.group("versionYear"));
          } else {
            LOG.warn(
                "Todesursachen_ICD_Version doesn't match expected format. Expected: '{}', actual: '{}'",
                icdVersionPattern.pattern(),
                icd10Version);
          }
        } else {
          LOG.warn("Todesursachen_ICD_Version is unset or contains only whitespaces");
        }

        if (versionElement == null) {
          versionElement = new StringType();
          versionElement.addExtension(
              fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
        }

        var todesursacheConcept = new CodeableConcept();
        todesursacheConcept
            .addCoding()
            .setSystem(fhirProperties.getSystems().getIcd10gm())
            .setCode(todesursache.getCode())
            .setVersionElement(versionElement);

        observation.setValue(todesursacheConcept);

        observationList.add(observation);
      }

    } else {
      var observation = createBaseObservation(patient, condition, todesZeitpunkt, tod);

      // Identifier: in this case there should only be one single death observation
      // adding "sterbedatum" here because Abschluss_ID (if present, else Meldung_ID)
      // might not be unique enough
      String dateOnly = null;
      if (todesZeitpunkt.isPresent()) {
        DateTimeType sterbedatum = todesZeitpunkt.get();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        dateOnly = sdf.format(sterbedatum.getValue());
      }
      Identifier identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getIdentifiers().getTodObservationId())
              .setValue(slugifier.slugify(identifierValue + "-" + dateOnly));
      observation.addIdentifier(identifier);
      observation.setId(computeResourceIdFromIdentifier(identifier));

      observationList.add(observation);
    }

    return observationList;
  }
}
