package io.github.bzkf.obdstofhir.mapper.mii;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import de.basisdatensatz.obds.v3.ModulAllgemeinTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.dizuker.tofhir.IdUtils;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
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
    observation.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoStudienteilnahme());

    // Identifier
    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties.getSystems().getIdentifiers().getStudienteilnahmeObservationId())
            .setValue(slugifier.slugify(meldungsID + "-Studienteilnahme"));
    observation.addIdentifier(identifier);
    observation.setId(IdUtils.fromIdentifier(identifier));

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
          new DateTimeType(
              modulAllgemein.getStudienteilnahme().getDatum().toGregorianCalendar().getTime());
      date.setPrecision(TemporalPrecisionEnum.DAY);
      observation.setEffective(date);

      // always yes, if the date is set
      coding = Onkologie.CodeSystems.MiiCsOnkoStudienteilnahme.J.coding();
    } else {
      // either no or unknown depending on the data
      coding =
          Onkologie.CodeSystems.MiiCsOnkoStudienteilnahme.fromValue(
                  modulAllgemein.getStudienteilnahme().getNU().value())
              .coding();
    }

    // Value
    observation.setValue(new CodeableConcept(coding));

    return observation;
  }
}
