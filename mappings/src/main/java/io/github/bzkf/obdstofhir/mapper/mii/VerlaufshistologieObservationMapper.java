package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HistologieTyp;
import de.basisdatensatz.obds.v3.MorphologieICDOTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.ObservationCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class VerlaufshistologieObservationMapper extends ObdsToFhirMapper {

  private static final Logger LOG =
      LoggerFactory.getLogger(VerlaufshistologieObservationMapper.class);

  public VerlaufshistologieObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      HistologieTyp histologie,
      String meldungsId,
      Reference patient,
      Reference specimen,
      Reference diagnose) {
    Objects.requireNonNull(histologie, "HistologieTyp must not be null");
    verifyReference(patient, ResourceType.Patient);
    verifyReference(diagnose, ResourceType.Condition);
    verifyReference(specimen, ResourceType.Specimen);

    var observations = new ArrayList<Observation>();

    var distinctCodes =
        histologie.getMorphologieICDO().stream()
            .map(MorphologieICDOTyp::getCode)
            .distinct()
            .toList();

    for (var morphologieCode : distinctCodes) {
      var observation = new Observation();

      // Meta
      observation.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoHistologieIcdo3());

      // Category
      observation.addCategory(observationCategory(ObservationCategory.LABORATORY));

      var identifierValue = histologie.getHistologieID();
      if (!StringUtils.hasText(identifierValue)) {
        LOG.debug(
            "Histologie_ID is unset. Defaulting to Meldung_ID as the identifier for the Verlaufshistologie Observation.");
        identifierValue = meldungsId;
      }

      setIdentifierAndId(
          observation,
          fhirProperties.getSystems().getIdentifiers().getVerlaufshistologieObservationId(),
          identifierValue + "-ICDO3-" + morphologieCode);

      // Status
      observation.setStatus(Observation.ObservationStatus.FINAL);

      // Code
      var code =
          new CodeableConcept(
              fhirProperties
                  .getCodings()
                  .loinc()
                  .setCode("59847-4")
                  .setDisplay("Histology and Behavior ICD-O-3 Cancer"));
      observation.setCode(code);

      // subject
      observation.setSubject(patient);

      // focus
      observation.addFocus(diagnose);

      // effective
      convertObdsDatumToDateTimeType(histologie.getTumorHistologiedatum())
          .ifPresent(observation::setEffective);

      // specimen
      observation.setSpecimen(specimen);

      var coding =
          new Coding()
              .setSystem(fhirProperties.getSystems().getIcdo3Morphologie())
              // no version: the oBDS ICD-O version is not a version of the ICD-O-3 CodeSystem
              .setCode(morphologieCode);
      var value = new CodeableConcept(coding).setText(histologie.getMorphologieFreitext());
      observation.setValue(value);

      observations.add(observation);
    }

    return observations;
  }
}
