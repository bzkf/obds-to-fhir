package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.*;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LymphknotenuntersuchungMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(LymphknotenuntersuchungMapper.class);

  public LymphknotenuntersuchungMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      HistologieTyp histologie,
      Reference patient,
      Reference diagnosis,
      Reference specimen,
      String meldungsId) {
    Objects.requireNonNull(histologie, "HistologieTyp must not be null");
    verifyReference(patient, ResourceType.Patient);
    verifyReference(diagnosis, ResourceType.Condition);
    verifyReference(specimen, ResourceType.Specimen);

    var result = new ArrayList<Observation>();

    var effectiveDate = convertObdsDatumToDateTimeType(histologie.getTumorHistologiedatum());

    var identifierValueBase = histologie.getHistologieID();
    if (!StringUtils.hasText(identifierValueBase)) {
      LOG.debug(
          "Histologie_ID is unset. Defaulting to Meldung_ID as the identifier for the Histologie Specimen.");
      identifierValueBase = meldungsId;
    }

    if (histologie.getLKBefallen() != null) {
      result.add(
          createObservation(
              identifierValueBase + "-befallen",
              fhirProperties.getProfiles().getMiiPrOnkoAnzahlBefalleneLymphknoten(),
              "21893-3",
              "443527007",
              effectiveDate,
              histologie.getLKBefallen().intValue(),
              patient,
              diagnosis,
              specimen));
    }

    if (histologie.getLKUntersucht() != null) {
      result.add(
          createObservation(
              identifierValueBase + "-untersucht",
              fhirProperties.getProfiles().getMiiPrOnkoAnzahlUntersuchteLymphknoten(),
              "21894-1",
              "444025001",
              effectiveDate,
              histologie.getLKUntersucht().intValue(),
              patient,
              diagnosis,
              specimen));
    }

    if (histologie.getSentinelLKBefallen() != null) {
      result.add(
          createObservation(
              identifierValueBase + "-befallen-sentinel",
              fhirProperties.getProfiles().getMiiPrOnkoAnzahlBefalleneSentinelLymphknoten(),
              "92832-5",
              "1264491009",
              effectiveDate,
              histologie.getSentinelLKBefallen().intValue(),
              patient,
              diagnosis,
              specimen));
    }

    if (histologie.getSentinelLKUntersucht() != null) {
      result.add(
          createObservation(
              identifierValueBase + "-untersucht-sentinel",
              fhirProperties.getProfiles().getMiiPrOnkoAnzahlUntersuchteSentinelLymphknoten(),
              "85347-3",
              "444411008",
              effectiveDate,
              histologie.getSentinelLKUntersucht().intValue(),
              patient,
              diagnosis,
              specimen));
    }
    return result;
  }

  private Observation createObservation(
      String identifierValue,
      String profileUrl,
      String loincCode,
      String snomedCode,
      Optional<DateTimeType> effectiveDate,
      Integer valueQuantity,
      Reference patientReference,
      Reference diagnosisReference,
      Reference specimenReference) {
    Observation observation = new Observation();

    // Identifier
    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties
                    .getSystems()
                    .getIdentifiers()
                    .getLymphknotenuntersuchungObservationId())
            .setValue(slugifier.slugify(identifierValue));
    observation.addIdentifier(identifier);

    // Id
    observation.setId(computeResourceIdFromIdentifier(identifier));

    // Meta
    observation.getMeta().addProfile(profileUrl);

    // Status
    observation.setStatus(Observation.ObservationStatus.FINAL);

    // Category
    var laboratory =
        new CodeableConcept(
            new Coding(fhirProperties.getSystems().getObservationCategory(), "laboratory", ""));
    observation.setCategory(List.of(laboratory));

    // Code
    var code =
        new CodeableConcept()
            .addCoding(fhirProperties.getCodings().loinc().setCode(loincCode))
            .addCoding(fhirProperties.getCodings().snomed().setCode(snomedCode));
    observation.setCode(code);

    // Subject
    observation.setSubject(patientReference);

    // focus
    observation.addFocus(diagnosisReference);

    // Effective Date
    effectiveDate.ifPresentOrElse(
        observation::setEffective,
        () -> {
          LOG.warn(
              "TumorHistologiedatum is unset. Setting data absent extension for Observation.effective.");
          var absentDateTime = new DateTimeType();
          absentDateTime.addExtension(
              fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
          observation.setEffective(absentDateTime);
        });

    observation.setSpecimen(specimenReference);

    // Value
    var quantity =
        new Quantity()
            .setCode("1")
            .setSystem(fhirProperties.getSystems().getUcum())
            .setValue(valueQuantity)
            .setUnit("#");
    observation.setValue(quantity);

    return observation;
  }
}
