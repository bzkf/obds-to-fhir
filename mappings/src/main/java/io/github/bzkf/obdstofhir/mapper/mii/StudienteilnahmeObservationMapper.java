package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.ModulAllgemeinTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

@Service
public class StudienteilnahmeObservationMapper extends ObdsToFhirMapper {

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
    observation.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoStudienteilnahme());

    setIdentifierAndId(
        observation,
        fhirProperties.getSystems().getIdentifiers().getStudienteilnahmeObservationId(),
        meldungsID + "-Studienteilnahme");

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

    Coding coding;

    if (modulAllgemein.getStudienteilnahme().getDatum() != null) {
      // Effective Date
      var date =
          convertObdsDatumToDateTimeType(modulAllgemein.getStudienteilnahme().getDatum())
              .orElseThrow();
      observation.setEffective(date);

      // always yes, if the date is set
      coding = Onkologie.CodeSystems.MiiCsOnkoStudienteilnahme.J.coding();
    } else {
      // either no or unknown depending on the data
      coding =
          Onkologie.CodeSystems.MiiCsOnkoStudienteilnahme.fromValueOrThrow(
                  modulAllgemein.getStudienteilnahme().getNU().value())
              .coding();
    }

    // Value
    observation.setValue(new CodeableConcept(coding));

    return observation;
  }
}
