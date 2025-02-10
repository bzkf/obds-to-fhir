package org.miracum.streams.ume.obdstofhir.mapper.mii;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import de.basisdatensatz.obds.v3.OBDS;
import java.util.ArrayList;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
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

  public ArrayList<Observation> map(
      OBDS.MengePatient.Patient.MengeMeldung meldungen, Reference patient, Reference diagnose) {
    // Validation
    Objects.requireNonNull(meldungen, "Meldungen must not be null");
    Objects.requireNonNull(patient, "Reference to Patient must not be null");
    Objects.requireNonNull(diagnose, "Reference to Condition must not be null");

    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The patient reference should point to a Patient resource");
    Validate.isTrue(
        Objects.equals(
            diagnose.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.CONDITION.toCode()),
        "The diagnose reference should point to a Condition resource");

    // TODO: Im Falle einer pharmakologischen Studie SOLLTE am besten eine Referenz zu einer
    // Procedure /
    //  Systemischen Therapie bestehen, entweder über Observation.partOf = Reference
    // (SystemischeTherapie),
    //  Observation.basedOn = Reference (MedicationRequest); oder Procedure.basedOn.
    //  Außerdem fehlen noch Informationen über die genaue Studie (Organisation, StudienID etc).

    // Instantiate the Observation base resources
    var observations = new ArrayList<Observation>();

    for (var meldung : meldungen.getMeldung()) {
      if (meldung.getDiagnose() == null
          || meldung.getDiagnose().getModulAllgemein() == null
          || meldung.getDiagnose().getModulAllgemein().getStudienteilnahme() == null) {
        continue;
      }

      var observation = new Observation();

      // Meta
      observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoStudienteilnahme());

      // TODO: no identifier present in sample data. Should we create one like this?
      // Identifier
      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoStudienteilnahme())
              .setValue(meldung.getMeldungID() + "_Studienteilnahme");
      observation.addIdentifier(identifier);
      observation.setId(computeResourceIdFromIdentifier(identifier));

      // TODO: How to decide which status to choose? FINAL taken from example at the end of the
      // simplifier guideline
      //  registered | preliminary | final | amended + are valid values in guideline, but docs says:
      // Ja, Nein, Unbekannt
      // Status
      observation.setStatus(Observation.ObservationStatus.FINAL);

      // Code
      observation.setCode(
          new CodeableConcept(
              new Coding(
                  fhirProperties.getSystems().getSnomed(),
                  "70709491003",
                  "Enrollment in clinical trial (procedure)")));

      // Subject
      observation.setSubject(patient);
      observation.setFocus(diagnose);
      // Effective Date
      var date =
          new DateTimeType(
              meldung
                  .getDiagnose()
                  .getModulAllgemein()
                  .getStudienteilnahme()
                  .getDatum()
                  .toGregorianCalendar()
                  .getTime());
      date.setPrecision(TemporalPrecisionEnum.DAY);
      observation.setEffective(date);

      // Value
      // TODO: No value present in sample data / which code to set?
      observation.setValue(
          new CodeableConcept(
              new Coding(fhirProperties.getSystems().getMiiCsOnkoStudienteilnahme(), "J", "Ja")));

      observations.add(observation);
    }

    return observations;
  }
}
