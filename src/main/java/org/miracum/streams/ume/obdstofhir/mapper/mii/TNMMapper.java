package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.TNMTyp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.springframework.stereotype.Service;

@Service
public class TNMMapper extends ObdsToFhirMapper {

  protected TNMMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  List<Observation> observationList = new ArrayList<>();
  List<Reference> clinicalObservations = new ArrayList<>();
  List<Reference> pathologicObservations = new ArrayList<>();
  List<Reference> genericObservations = new ArrayList<>();

  public List<Observation> map(
      OBDS.MengePatient.Patient.MengeMeldung.Meldung meldung,
      Reference patient,
      Reference condition) {

    Objects.requireNonNull(meldung);

    // TODO could be in the mapper that calls all the services?  ---
    Objects.requireNonNull(patient);
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");
    // ---------------------------------------------------------------

    // there can be multiple occurences for TNM in one meldung
    if (meldung.getDiagnose() != null) {
      if (meldung.getDiagnose().getCTNM() != null) {
        createObservations(meldung.getDiagnose().getCTNM(), "c");
      }
      if (meldung.getDiagnose().getPTNM() != null) {
        createObservations(meldung.getDiagnose().getPTNM(), "p");
      }
    }
    if (meldung.getOP() != null && meldung.getOP().getTNM() != null) {
      createObservations(meldung.getOP().getTNM(), "");
    }
    if (meldung.getPathologie() != null) {
      if (meldung.getPathologie().getCTNM() != null) {
        createObservations(meldung.getPathologie().getCTNM(), "c");
      }
      if (meldung.getPathologie().getPTNM() != null) {
        createObservations(meldung.getPathologie().getPTNM(), "p");
      }
    }
    if (meldung.getVerlauf() != null && meldung.getVerlauf().getTNM() != null) {
      createObservations(meldung.getVerlauf().getTNM(), "");
    }

    // this is used to group the different TNM resources
    var groupingObservationClinical =
        createTNMGroupingObservation(meldung, "clinical", clinicalObservations, patient, condition);
    var groupingObservationPathologic =
        createTNMGroupingObservation(
            meldung, "pathologic", pathologicObservations, patient, condition);
    var groupingObservationGeneric =
        createTNMGroupingObservation(meldung, "generic", genericObservations, patient, condition);

    //    observationList.add(groupingObservationClinical);
    //    observationList.add(groupingObservationPathologic);
    //    observationList.add(groupingObservationGeneric);
    return observationList;
  }

  private void createObservations(TNMTyp tnmTyp, String prefix) {

    // check and if information is available create obs

    //    createTNMTObservation();
    //    createTNMNObservation();
    //    createTNMMObservation();
    //    createTNMaObservation();
    //    createTNMmObservation();
    //    createTNMLObservation();
    //    createTNMPnObservation();
    //    createTNMrObservation();
    //    createTNMSObservation();
    //    createTNMVObservation();
    //    createTNMyObservation();

  }

  private void createTNMTObservation(TNMTyp tnmTyp) {}

  private Observation createTNMGroupingObservation(
      OBDS.MengePatient.Patient.MengeMeldung.Meldung meldung,
      String observationType,
      List<Reference> memberObservations,
      Reference patient,
      Reference condition) {

    var observation = new Observation();

    observation.setMeta(
        new Meta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoTnmKlassifikation()));

    var identifier =
        new Identifier()
            // Todo Benennung deutsch oder englisch?
            .setSystem(fhirProperties.getSystems().getTnmGroupingObservationId())
            // TODO what would be suitable?
            .setValue("TNM-" + meldung.getMeldungID());
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    observation.setSubject(patient);
    //    observation.setEncounter();
    observation.setFocus(
        Collections.singletonList(condition)); // TODO only set focus for the member-conditions?

    // if meldeanlass = statusänderung -> amend ; else -> final ?
    observation.setStatus(Observation.ObservationStatus.FINAL);

    observation.setCode(getGroupingObservationCode(observationType));

    // tnm version
    //    observation.setMethod();

    // TODO
    //    observation.setEffective();

    //  TODO UICC-Staging kodiert, dass von den untergeordneten TNM-Beobachtungen abgeleitet ist
    //    observation.setValue();

    observation.setHasMember(memberObservations);

    return observation;
  }

  private CodeableConcept getGroupingObservationCode(String observationType) {
    var groupingObservationCode = new Coding().setSystem(fhirProperties.getSystems().getSnomed());

    switch (observationType) {
      case "clinical":
        groupingObservationCode.setCode("399537006");
        groupingObservationCode.setDisplay("Clinical TNM stage grouping");
        break;
      case "pathologic":
        groupingObservationCode.setCode("399588009");
        groupingObservationCode.setDisplay("Pathologic TNM stage grouping");
        break;
      case "generic":
      default:
        groupingObservationCode.setCode("399588009");
        groupingObservationCode.setDisplay("TNM stage grouping");
        break;
    }
    return new CodeableConcept(groupingObservationCode);
  }
}
