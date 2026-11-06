package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HistologieTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.ObservationCategory;
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
    observation.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoGrading());

    var identifierValue = histologie.getHistologieID();
    if (!StringUtils.hasText(identifierValue)) {
      LOG.debug(
          "Histologie_ID is unset. Defaulting to Meldung_ID as the identifier for the Grading Observation.");
      identifierValue = meldungsId;
    }

    setIdentifierAndId(
        observation,
        fhirProperties.getSystems().getIdentifiers().getGradingObservationId(),
        identifierValue + "-grading");

    // Status
    observation.setStatus(Observation.ObservationStatus.FINAL);

    // category
    observation.addCategory(observationCategory(ObservationCategory.LABORATORY));

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
            observation::setEffective, () -> observation.setEffective(absentDateTime()));

    // value is 1..1
    var coding =
        Onkologie.CodeSystems.MiiCsOnkoGrading.fromValueOrThrow(histologie.getGrading()).coding();

    observation.setValue(new CodeableConcept().addCoding(coding));

    return observation;
  }
}
