package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.MengeWeitereKlassifikationTyp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.springframework.stereotype.Service;

@Service
public class WeitereKlassifikationMapper extends ObdsToFhirMapper {

  public WeitereKlassifikationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      MengeWeitereKlassifikationTyp mengeWeitereKlassifikation,
      String meldungId,
      Reference patient,
      Reference condition) {
    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    var observations = new ArrayList<Observation>();

    for (var klassifikation : mengeWeitereKlassifikation.getWeitereKlassifikation()) {
      var observation = new Observation();
      observation
          .getMeta()
          .addProfile(fhirProperties.getProfiles().getMiiPrOnkoWeitereKlassifikationen());

      // Identifiers
      var identifierBuilder = new StringBuilder();
      identifierBuilder.append(meldungId).append("-").append(klassifikation.getName());
      if (klassifikation.getDatum() != null) {
        var date =
            new SimpleDateFormat("yyyy-MM-dd")
                .format(klassifikation.getDatum().getValue().toGregorianCalendar().getTime());
        identifierBuilder.append("-").append(date);
      }

      var identifierValue = slugifier.slugify(identifierBuilder.toString());

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
