package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HistologieTyp;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    if (!StringUtils.hasText(identifierValue)) {
      LOG.warn(
          "Histologie_ID is unset. Defaulting to Meldung_ID as the identifier for the Grading Observation.");
      identifierValue = meldungsId;
    }

    // Identifer
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getIdentifiers().getGradingObservationId())
            .setValue(slugifier.slugify(identifierValue + "-grading"));
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
        new CodeableConcept()
            .addCoding(
                fhirProperties
                    .getCodings()
                    .loinc()
                    .setCode("33732-9")
                    .setDisplay("Histology grade [Identifier] in Cancer specimen"))
            .addCoding(
                fhirProperties
                    .getCodings()
                    .snomed()
                    .setCode("371469007")
                    .setDisplay("Histologic grade of neoplasm (observable entity)"));
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
