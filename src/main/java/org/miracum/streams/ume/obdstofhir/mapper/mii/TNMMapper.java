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

  public List<Observation> map(
      OBDS.MengePatient.Patient.MengeMeldung.Meldung meldung,
      Reference patient,
      Reference condition) {

    Objects.requireNonNull(meldung);

    Objects.requireNonNull(patient);
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");
    // ---------------------------------------------------------------

    // there can be multiple occurrences for TNM in one meldung
    if (meldung.getDiagnose() != null) {
      if (meldung.getDiagnose().getCTNM() != null) {
        mapClinicalObservations(
            meldung.getDiagnose().getCTNM(), meldung.getMeldungID(), patient, condition);
      }
      if (meldung.getDiagnose().getPTNM() != null) {
        mapPathologicalObservations(
            meldung.getDiagnose().getPTNM(), meldung.getMeldungID(), patient, condition);
      }
    }

    if (meldung.getOP() != null && meldung.getOP().getTNM() != null) {
      mapGenericObservations(meldung.getOP().getTNM(), meldung.getMeldungID(), patient, condition);
    }

    if (meldung.getPathologie() != null) {
      if (meldung.getPathologie().getCTNM() != null) {
        mapClinicalObservations(
            meldung.getPathologie().getCTNM(), meldung.getMeldungID(), patient, condition);
      }
      if (meldung.getPathologie().getPTNM() != null) {
        mapPathologicalObservations(
            meldung.getPathologie().getPTNM(), meldung.getMeldungID(), patient, condition);
      }
    }

    if (meldung.getVerlauf() != null && meldung.getVerlauf().getTNM() != null) {
      mapGenericObservations(
          meldung.getVerlauf().getTNM(), meldung.getMeldungID(), patient, condition);
    }

    return observationList;
  }

  private void mapClinicalObservations(
      TNMTyp ctnm, String meldungId, Reference patient, Reference condition) {
    var clinicalObservationRefs = createObservations(ctnm, "c");
    var groupingObservationClinical =
        createTNMGroupingObservation(
            ctnm, meldungId, "clinical", clinicalObservationRefs, patient, condition);
    observationList.add(groupingObservationClinical);
  }

  private void mapPathologicalObservations(
      TNMTyp ptnm, String meldungId, Reference patient, Reference condition) {
    var pathologicObservationRefs = createObservations(ptnm, "p");
    var groupingObservationPathologic =
        createTNMGroupingObservation(
            ptnm, meldungId, "pathologic", pathologicObservationRefs, patient, condition);
    observationList.add(groupingObservationPathologic);
  }

  private void mapGenericObservations(
      TNMTyp tnm, String meldungId, Reference patient, Reference condition) {
    var genericObservationRefs = createObservations(tnm, "");
    var groupingObservationGeneric =
        createTNMGroupingObservation(
            tnm, meldungId, "generic", genericObservationRefs, patient, condition);
    observationList.add(groupingObservationGeneric);
  }

  private List<Reference> createObservations(TNMTyp tnmTyp, String prefix) {

    List<Reference> memberObservations = new ArrayList<>();

    //    if (tnmTyp.get) {
    //
    //    }

    // check and if information is available create obs

    //    createTNMTObservation();
    //    observationList.add();
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

    return memberObservations;
  }

//  private void createTNMTObservation(TNMTyp tnmTyp) {}

  private Observation createTNMGroupingObservation(
      TNMTyp tnm,
      String meldungsId,
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
            // TODO what would be suitable? could also be TNM_ID
            .setValue("TNM-" + meldungsId);
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
    observation.setMethod(getObservationMethod(tnm));

    var dateOptional = convertObdsDatumToDateTimeType(tnm.getDatum());
    dateOptional.ifPresent(observation::setEffective);

    observation.setValue(getObservationValueUiccStadium(tnm.getUICCStadium()));

    observation.setHasMember(memberObservations);

    return observation;
  }

  private CodeableConcept getObservationMethod(TNMTyp tnm) {
    var method =
        new Coding()
            .setCode(tnm.getVersion())
            .setSystem(fhirProperties.getSystems().getMiiCsOnkoTnmVersion());
    // optional: .setDisplay
    return new CodeableConcept(method);
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

  private CodeableConcept getObservationValueUiccStadium(String uiccStadium) {

    var coding = new Coding().setSystem(fhirProperties.getSystems().getTnmUicc());

    return switch (uiccStadium) {
      case "okk" -> new CodeableConcept(coding.setCode("okk").setDisplay("Stadium X"));
      case "0" -> new CodeableConcept(coding.setCode("0").setDisplay("Stadium 0"));
      case "0a" -> new CodeableConcept(coding.setCode("0a").setDisplay("Stadium 0a"));
      case "0is" -> new CodeableConcept(coding.setCode("0is").setDisplay("Stadium 0is"));
      case "I" -> new CodeableConcept(coding.setCode("I").setDisplay("Stadium I"));
      case "IA1" -> new CodeableConcept(coding.setCode("IA1").setDisplay("Stadium IA1"));
      case "IA2" -> new CodeableConcept(coding.setCode("IA2").setDisplay("Stadium IA2"));
      case "IA3" -> new CodeableConcept(coding.setCode("IA3").setDisplay("Stadium IA3"));
      case "IB" -> new CodeableConcept(coding.setCode("IB").setDisplay("Stadium IB"));
      case "IB1" -> new CodeableConcept(coding.setCode("IB1").setDisplay("Stadium IB1"));
      case "IB2" -> new CodeableConcept(coding.setCode("IB2").setDisplay("Stadium IB2"));
      case "IC" -> new CodeableConcept(coding.setCode("IC").setDisplay("Stadium IC"));
      case "IS" -> new CodeableConcept(coding.setCode("IS").setDisplay("Stadium IS"));
      case "II" -> new CodeableConcept(coding.setCode("II").setDisplay("Stadium II"));
      case "IIA" -> new CodeableConcept(coding.setCode("IIA").setDisplay("Stadium IIA"));
      case "IIA1" -> new CodeableConcept(coding.setCode("IIA1").setDisplay("Stadium IIA1"));
      case "IIA2" -> new CodeableConcept(coding.setCode("IIA2").setDisplay("Stadium IIA2"));
      case "IIB" -> new CodeableConcept(coding.setCode("IIB").setDisplay("Stadium IIB"));
      case "IIC" -> new CodeableConcept(coding.setCode("IIC").setDisplay("Stadium IIC"));
      case "III" -> new CodeableConcept(coding.setCode("III").setDisplay("Stadium III"));
      case "IIIA" -> new CodeableConcept(coding.setCode("IIIA").setDisplay("Stadium IIIA"));
      case "IIIA1" -> new CodeableConcept(coding.setCode("IIIA1").setDisplay("Stadium IIIA1"));
      case "IIIA2" -> new CodeableConcept(coding.setCode("IIIA2").setDisplay("Stadium IIIA2"));
      case "IIIB" -> new CodeableConcept(coding.setCode("IIIB").setDisplay("Stadium IIIB"));
      case "IIIC" -> new CodeableConcept(coding.setCode("IIIC").setDisplay("Stadium IIIC"));
      case "IIIC1" -> new CodeableConcept(coding.setCode("IIIC1").setDisplay("Stadium IIIC1"));
      case "IIIC2" -> new CodeableConcept(coding.setCode("IIIC2").setDisplay("Stadium IIIC2"));
      case "IV" -> new CodeableConcept(coding.setCode("IV").setDisplay("Stadium IV"));
      case "IVA" -> new CodeableConcept(coding.setCode("IVA").setDisplay("Stadium IVA"));
      case "IVB" -> new CodeableConcept(coding.setCode("IVB").setDisplay("Stadium IVB"));
      case "IVC" -> new CodeableConcept(coding.setCode("IVC").setDisplay("Stadium IVC"));
      default -> new CodeableConcept(coding.setCode("unknown").setDisplay("Unknown Stage"));
    };
  }
}
