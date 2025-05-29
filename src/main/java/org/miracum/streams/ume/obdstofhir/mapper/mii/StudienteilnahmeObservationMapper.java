package org.miracum.streams.ume.obdstofhir.mapper.mii;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import de.basisdatensatz.obds.v3.ModulAllgemeinTyp;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StudienteilnahmeObservationMapper extends ObdsToFhirMapper {

  private static final Logger LOG =
      LoggerFactory.getLogger(StudienteilnahmeObservationMapper.class);

  public StudienteilnahmeObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(
      ModulAllgemeinTyp modulAllgemein, Reference patient, Reference diagnose, String meldungsID) {
    // Validation
    Objects.requireNonNull(modulAllgemein, "modulAllgemein must not be null");
    verifyReference(patient, ResourceType.Patient);
    verifyReference(diagnose, ResourceType.Condition);

    // Instantiate the Observation base resources
    var observation = new Observation();

    // Meta
    observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoStudienteilnahme());

    // Identifier
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getStudienteilnahmeId())
            .setValue(meldungsID + "_Studienteilnahme");
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    // Status
    observation.setStatus(Observation.ObservationStatus.FINAL);

    // Code
    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("709491003")
                .setDisplay("Enrollment in clinical trial (procedure)")));

    // Subject
    observation.setSubject(patient);
    observation.addFocus(diagnose);

    var coding = new Coding().setSystem(fhirProperties.getSystems().getMiiCsOnkoStudienteilnahme());

    if (modulAllgemein.getStudienteilnahme().getDatum() != null) {
      // Effective Date
      var date =
          new DateTimeType(
              modulAllgemein.getStudienteilnahme().getDatum().toGregorianCalendar().getTime());
      date.setPrecision(TemporalPrecisionEnum.DAY);
      observation.setEffective(date);

      // always yes, if the date is set
      coding.setCode("J").setDisplay("Ja");
    } else {
      // either no or unknown depending on the data
      coding.setCode(modulAllgemein.getStudienteilnahme().getNU().value());
    }

    // Value
    observation.setValue(new CodeableConcept(coding));

    return observation;
  }
}
