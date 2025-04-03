package org.miracum.streams.ume.obdstofhir.mapper.mii;

import com.github.slugify.Slugify;
import de.basisdatensatz.obds.v3.MengeWeitereKlassifikationTyp;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
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
  private static final Slugify slugify = Slugify.builder().build();
  private final WeitereKlassifikationenMappings mappings;

  public WeitereKlassifikationMapper(
      FhirProperties fhirProperties, WeitereKlassifikationenMappings mappings) {
    super(fhirProperties);
    this.mappings = mappings;
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
      var identifierValue = slugify.slugify(meldungId + "-" + klassifikation.getName());
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

      if (mappings.getWeitereKlassifikationen().containsKey(klassifikation.getName())) {
        var mapping = mappings.getWeitereKlassifikationen().get(klassifikation.getName());
        observation.setCode(new CodeableConcept(mapping.coding()));

        var value = mapping.mappings().get(klassifikation.getStadium());

        if (value != null) {
          observation.setValue(new CodeableConcept(value));
        } else {
          LOG.warn(
              "Stadium {} doesn't exist in mapping config for {}. Setting value as plain code.",
              klassifikation.getStadium(),
              klassifikation.getName());
          value = new Coding().setCode(klassifikation.getStadium());
          observation.setValue(new CodeableConcept(value));
        }

      } else {
        var code = new CodeableConcept().setText(klassifikation.getName());
        observation.setCode(code);

        var value = new CodeableConcept();
        value.addCoding().setCode(klassifikation.getStadium());
        observation.setValue(value);
      }

      observations.add(observation);
    }
    return observations;
  }
}
