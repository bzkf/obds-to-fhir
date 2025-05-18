package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HistologieTyp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
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

    for (var morph : histologie.getMorphologieICDO()) {
      var observation = new Observation();

      // Meta
      observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoHistologieIcdo3());

      var identifierValue = histologie.getHistologieID();
      if (!StringUtils.hasText(identifierValue)) {
        LOG.warn(
            "Histologie_ID is unset. Defaulting to Meldung_ID as the identifier for the Verlaufshistologie Observation.");
        identifierValue = meldungsId;
      }

      identifierValue += "-ICDO3-" + morph.getCode();

      // Identifer
      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getObservationHistologieId())
              .setValue(identifierValue);
      observation.addIdentifier(identifier);
      // Id
      observation.setId(computeResourceIdFromIdentifier(identifier));

      // Status
      observation.setStatus(Observation.ObservationStatus.FINAL);

      // Code
      var code =
          new CodeableConcept(new Coding(fhirProperties.getSystems().getLoinc(), "59847-4", ""));
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
              .setCode(morph.getCode())
              .setVersion(morph.getVersion());
      var value = new CodeableConcept(coding).setText(histologie.getMorphologieFreitext());
      observation.setValue(value);

      observations.add(observation);
    }

    return observations;
  }
}
