package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.MengeWeitereKlassifikationTyp;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WeitereKlassifikationMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(WeitereKlassifikationMapper.class);

  public WeitereKlassifikationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      MengeWeitereKlassifikationTyp mengeWeitereKlassifikation,
      String meldungId,
      Reference patient,
      Reference condition) {
    verifyReference(patient, ResourceType.PATIENT);
    verifyReference(condition, ResourceType.CONDITION);

    var observations = new ArrayList<Observation>();

    for (var klassifikation : mengeWeitereKlassifikation.getWeitereKlassifikation()) {

      var observation = new Observation();
      observation
          .getMeta()
          .addProfile(fhirProperties.getProfiles().getMiiPrOnkoWeitereKlassifikationen());

      // Identifiers
      var identifierValue = meldungId + "-" + slugifier.slugify(klassifikation.getName());
      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getWeitereKlassifikationObservationId())
              .setValue(identifierValue);
      observation.setId(computeResourceIdFromIdentifier(identifier));
      observation.setIdentifier(List.of(identifier));

      // Subject
      observation.setSubject(patient);

      // Status - always final
      observation.setStatus(Observation.ObservationStatus.FINAL);

      // Focus
      observation.addFocus(condition);

      // Datum
      convertObdsDatumToDateTimeType(klassifikation.getDatum())
          .ifPresent(observation::setEffective);

      var code = new CodeableConcept().setText(klassifikation.getName());
      observation.setCode(code);

      var value = new CodeableConcept();
      value.addCoding().setCode(klassifikation.getStadium());
      observation.setValue(value);

      observations.add(observation);
    }
    return observations;
  }
}
