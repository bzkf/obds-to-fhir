package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HistologieTyp;
import java.util.Objects;
import org.apache.logging.log4j.util.Strings;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GradingObservationMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(GradingObservationMapper.class);

  public GradingObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(
      HistologieTyp histologie,
      String meldungsId,
      Reference patient,
      Reference diagnose,
      Reference specimen) {
    Objects.requireNonNull(histologie, "HistologieTyp must not be null");
    verifyReference(patient, ResourceType.Patient);
    verifyReference(diagnose, ResourceType.Condition);
    verifyReference(specimen, ResourceType.Specimen);

    var observation = new Observation();
    // Meta
    observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoGrading());

    var identifierValue = histologie.getHistologieID();
    if (Strings.isBlank(identifierValue)) {
      LOG.warn(
          "Histologie_ID is unset. Defaulting to Meldung_ID as the identifier for the Grading Observation.");
      identifierValue = meldungsId;
    }

    // Identifer
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getGradingObservationId())
            .setValue(identifierValue + "-grading");
    observation.addIdentifier(identifier);

    // Id
    observation.setId(computeResourceIdFromIdentifier(identifier));

    // Status
    observation.setStatus(Observation.ObservationStatus.FINAL);

    // category
    var laboratory =
        new CodeableConcept(
            new Coding(fhirProperties.getSystems().getObservationCategory(), "laboratory", ""));
    observation.addCategory(laboratory);

    // code
    var code =
        new CodeableConcept(new Coding(fhirProperties.getSystems().getLoinc(), "33732-9", ""));
    var coding = new Coding(fhirProperties.getSystems().getSnomed(), "371469007", "");
    code.addCoding(coding);
    observation.setCode(code);

    // subject
    observation.setSubject(patient);

    // focus
    observation.addFocus(diagnose);

    // specimen
    observation.setSpecimen(specimen);

    // effective is 1..1
    convertObdsDatumToDateTimeType(histologie.getTumorHistologiedatum())
        .ifPresentOrElse(
            observation::setEffective,
            () -> {
              var absentDateTime = new DateTimeType();
              absentDateTime.addExtension(
                  fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
              observation.setEffective(absentDateTime);
            });

    // value
    var value =
        new CodeableConcept(
            new Coding(
                fhirProperties.getSystems().getMiiCsOnkoGrading(), histologie.getGrading(), ""));
    observation.setValue(value);

    return observation;
  }
}
