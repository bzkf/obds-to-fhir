package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HistologieTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

/**
 * Composite mapper that groups histologie-related mappers (SpecimenMapper,
 * GradingObservationMapper, VerlaufshistologieObservationMapper, LymphknotenuntersuchungMapper) to
 * reduce constructor injection bloat in ObdsToFhirBundleMapper.
 */
@Service
public class HistologieCompositeMapper extends ObdsToFhirMapper {

  private final SpecimenMapper specimenMapper;
  private final GradingObservationMapper gradingObservationMapper;
  private final VerlaufshistologieObservationMapper verlaufshistologieObservationMapper;
  private final LymphknotenuntersuchungMapper lymphknotenuntersuchungMapper;

  public HistologieCompositeMapper(
      FhirProperties fhirProperties,
      SpecimenMapper specimenMapper,
      GradingObservationMapper gradingObservationMapper,
      VerlaufshistologieObservationMapper verlaufshistologieObservationMapper,
      LymphknotenuntersuchungMapper lymphknotenuntersuchungMapper) {
    super(fhirProperties);
    this.specimenMapper = specimenMapper;
    this.gradingObservationMapper = gradingObservationMapper;
    this.verlaufshistologieObservationMapper = verlaufshistologieObservationMapper;
    this.lymphknotenuntersuchungMapper = lymphknotenuntersuchungMapper;
  }

  /**
   * Maps histologie data producing specimen, grading, and lymphknoten resources. Used in
   * mapDiagnose where no VerlaufshistologieObservation is needed.
   */
  public List<Resource> mapHistologie(
      HistologieTyp histologie,
      String meldungId,
      Reference patientReference,
      Reference primaryConditionReference) {
    var mappedResources = new ArrayList<Resource>();

    var specimen = specimenMapper.map(histologie, patientReference, meldungId);
    mappedResources.add(specimen);

    var specimenReference = createReferenceFromResource(specimen);

    if (histologie.getGrading() != null) {
      var grading =
          gradingObservationMapper.map(
              histologie,
              meldungId,
              patientReference,
              primaryConditionReference,
              specimenReference);
      mappedResources.add(grading);
    }

    var lymphknotenuntersuchungen =
        lymphknotenuntersuchungMapper.map(
            histologie, patientReference, primaryConditionReference, specimenReference, meldungId);
    mappedResources.addAll(lymphknotenuntersuchungen);

    return mappedResources;
  }

  /**
   * Maps histologie data producing specimen, verlaufshistologie, grading, and lymphknoten
   * resources. Used in mapVerlauf, mapOP, and mapPathologie.
   */
  public List<Resource> mapHistologieWithVerlauf(
      HistologieTyp histologie,
      String meldungId,
      Reference patientReference,
      Reference primaryConditionReference) {
    var mappedResources = new ArrayList<Resource>();

    var specimen = specimenMapper.map(histologie, patientReference, meldungId);
    mappedResources.add(specimen);

    var specimenReference = createReferenceFromResource(specimen);

    var verlaufsHistologie =
        verlaufshistologieObservationMapper.map(
            histologie, meldungId, patientReference, specimenReference, primaryConditionReference);
    mappedResources.addAll(verlaufsHistologie);

    if (histologie.getGrading() != null) {
      var grading =
          gradingObservationMapper.map(
              histologie,
              meldungId,
              patientReference,
              primaryConditionReference,
              specimenReference);
      mappedResources.add(grading);
    }

    var lymphknotenuntersuchungen =
        lymphknotenuntersuchungMapper.map(
            histologie, patientReference, primaryConditionReference, specimenReference, meldungId);
    mappedResources.addAll(lymphknotenuntersuchungen);

    return mappedResources;
  }
}
