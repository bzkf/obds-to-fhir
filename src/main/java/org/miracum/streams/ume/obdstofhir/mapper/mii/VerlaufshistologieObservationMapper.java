package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HistologieTyp;
import de.basisdatensatz.obds.v3.MorphologieICDOTyp;
import java.util.ArrayList;
import java.util.HashMap;
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

    var distinctCodes = new HashMap<String, MorphologieICDOTyp>();
    for (var morph : histologie.getMorphologieICDO()) {
      var code = morph.getCode();
      var version = morph.getVersion();
      if (!distinctCodes.containsKey(code)) {
        distinctCodes.put(code, morph);
      } else {
        var existing = distinctCodes.get(code);
        if (version.compareTo(existing.getVersion()) > 0) {
          LOG.warn(
              "Multiple ICDO3 morphologies with code {} found. Updating version {} over version {}.",
              code,
              version,
              existing.getVersion());
          distinctCodes.put(code, morph);
        } else {
          LOG.warn(
              "Multiple ICDO3 morphologies with code {} found. Keeping largest version {} over version {}.",
              code,
              existing.getVersion(),
              version);
        }
      }
    }

    for (var morph : distinctCodes.values()) {
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
              .setCode(morph.getCode())
              .setVersion(morph.getVersion());
      var value = new CodeableConcept(coding).setText(histologie.getMorphologieFreitext());
      observation.setValue(value);

      observations.add(observation);
    }

    return observations;
  }
}
