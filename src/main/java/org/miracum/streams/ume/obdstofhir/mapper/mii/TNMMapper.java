package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.TNMTyp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.xml.datatype.XMLGregorianCalendar;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.springframework.stereotype.Service;

// Identifier
// gruppierungs-obs: TNM_ID
// member-obs: TNM_ID + "_" + Typ z.B. T

// class clinical/pathologic/.. nur bei gruppierungs obs gesetzt

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

    observationList.clear();

    Objects.requireNonNull(meldung);

    Objects.requireNonNull(patient);
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");
    // ---------------------------------------------------------------

    // there can be multiple occurrences for TNM in a Meldung
    if (meldung.getDiagnose() != null) {
      if (meldung.getDiagnose().getCTNM() != null) {
        mapClinicalObservations(meldung.getDiagnose().getCTNM(), patient, condition);
      }
      if (meldung.getDiagnose().getPTNM() != null) {
        mapPathologicalObservations(meldung.getDiagnose().getPTNM(), patient, condition);
      }
    }

    if (meldung.getOP() != null && meldung.getOP().getTNM() != null) {
      mapGenericObservations(meldung.getOP().getTNM(), patient, condition);
    }

    if (meldung.getPathologie() != null) {
      if (meldung.getPathologie().getCTNM() != null) {
        mapClinicalObservations(meldung.getPathologie().getCTNM(), patient, condition);
      }
      if (meldung.getPathologie().getPTNM() != null) {
        mapPathologicalObservations(meldung.getPathologie().getPTNM(), patient, condition);
      }
    }

    if (meldung.getVerlauf() != null && meldung.getVerlauf().getTNM() != null) {
      mapGenericObservations(meldung.getVerlauf().getTNM(), patient, condition);
    }

    return observationList;
  }

  private void mapClinicalObservations(TNMTyp cTnm, Reference patient, Reference condition) {
    var clinicalObservationRefs = createObservations(cTnm, "c", patient, condition);
    var groupingObservationClinical =
        createTNMGroupingObservation(cTnm, "clinical", clinicalObservationRefs, patient, condition);
    observationList.add(groupingObservationClinical);
  }

  private void mapPathologicalObservations(TNMTyp pTnm, Reference patient, Reference condition) {
    var pathologicObservationRefs = createObservations(pTnm, "p", patient, condition);
    var groupingObservationPathologic =
        createTNMGroupingObservation(
            pTnm, "pathologic", pathologicObservationRefs, patient, condition);
    observationList.add(groupingObservationPathologic);
  }

  private void mapGenericObservations(TNMTyp tnm, Reference patient, Reference condition) {
    var genericObservationRefs = createObservations(tnm, "", patient, condition);
    var groupingObservationGeneric =
        createTNMGroupingObservation(tnm, "generic", genericObservationRefs, patient, condition);
    observationList.add(groupingObservationGeneric);
  }

  private List<Reference> createObservations(
      TNMTyp tnmTyp, String prefix, Reference patient, Reference condition) {

    List<Reference> memberObservationReferences = new ArrayList<>();

    if (tnmTyp.getT() != null) {
      var tnmBaseResource =
          createTNMBaseResource(tnmTyp.getVersion(), tnmTyp.getDatum(), patient, condition);
      var tKategorieObservation = addTKategorieSpecificAttributes(tnmBaseResource, tnmTyp);
      observationList.add(tKategorieObservation);
      memberObservationReferences.add(
          new Reference("Observation/" + tKategorieObservation.getId()));
    }

    if (tnmTyp.getN() != null) {
      var tnmBaseResource =
          createTNMBaseResource(tnmTyp.getVersion(), tnmTyp.getDatum(), patient, condition);
      var nKategorieObservation = addNKategorieSpecificAttributes(tnmBaseResource, tnmTyp);
      observationList.add(nKategorieObservation);
      memberObservationReferences.add(
          new Reference("Observation/" + nKategorieObservation.getId()));
    }

//    if (tnmTyp.getM() != null) {}
//
//    if (tnmTyp.getASymbol() != null) {}
//
//    if (tnmTyp.getMSymbol() != null) {}
//
//    if (tnmTyp.getL() != null) {}
//
//    if (tnmTyp.getPn() != null) {}
//
//    if (tnmTyp.getRSymbol() != null) {}
//
//    if (tnmTyp.getS() != null) {}
//
//    if (tnmTyp.getV() != null) {}
//
//    if (tnmTyp.getYSymbol() != null) {}

    return memberObservationReferences;
  }

  private Observation createTNMBaseResource(
      String tnmVersion, XMLGregorianCalendar datum, Reference patient, Reference condition) {
    var observation = new Observation();

    observation.setSubject(patient);
    observation.setFocus(Collections.singletonList(condition));
    observation.setStatus(Observation.ObservationStatus.FINAL);
    observation.setMethod(getObservationMethod(tnmVersion));
    var dateOptional = convertObdsDatumToDateTimeType(datum);
    dateOptional.ifPresent(observation::setEffective);

    return observation;
  }

  private Observation addTKategorieSpecificAttributes(Observation observation, TNMTyp tnmTyp) {

    observation.setMeta(
        new Meta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoTnmTKategorie()));

    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getTnmTKategorieObservationId())
            .setValue(tnmTyp.getID() + "_T");
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    observation.setCode(createTKategorieCode(tnmTyp.getCPUPraefixT()));

    observation.setValue(
        new CodeableConcept(
            new Coding()
                .setCode(tnmTyp.getT())
                .setSystem(fhirProperties.getSystems().getTnmUicc())));

    return observation;
  }

  private CodeableConcept createTKategorieCode(String cpuPraefixT) {

    var extension = new Extension().setUrl(fhirProperties.getProfiles().getMiiExOnkoTnmCpPraefix());
    extension.setValue(
        new CodeableConcept(
            new Coding().setCode(cpuPraefixT).setSystem(fhirProperties.getSystems().getTnmUicc())));

    var codeCoding = new Coding().setSystem(fhirProperties.getSystems().getSnomed());
    switch (cpuPraefixT) {
      case "c":
        codeCoding.setCode("399504009").setDisplay("cT category (observable entity)");
        break;
      case "p":
        codeCoding.setCode("384625004").setDisplay("pT category (observable entity)");
        break;
      default:
        codeCoding.setCode("78873005").setDisplay("T category (observable entity)");
        break;
        // the xml specifies: Das Weglassen des Prefix wird als "c" interpretiert but this differs
        // from the IG
    }

    var codeableConcept = new CodeableConcept();
    codeableConcept.setCoding(Collections.singletonList(codeCoding));
    codeableConcept.setExtension(Collections.singletonList(extension));

    return codeableConcept;
  }

  private Observation addNKategorieSpecificAttributes(Observation observation, TNMTyp tnmTyp) {

    observation.setMeta(
        new Meta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoTnmNKategorie()));

    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getTnmTKategorieObservationId())
            .setValue(tnmTyp.getID() + "_N");
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    observation.setCode(createNKategorieCode(tnmTyp.getCPUPraefixN()));

    observation.setValue(createNKategorieValue(tnmTyp.getN()));

    return observation;
  }

  private CodeableConcept createNKategorieCode(String cpuPraefixN) {

    var extension = new Extension().setUrl(fhirProperties.getProfiles().getMiiExOnkoTnmCpPraefix());
    extension.setValue(
        new CodeableConcept(
            new Coding().setCode(cpuPraefixN).setSystem(fhirProperties.getSystems().getTnmUicc())));

    var codeCoding = new Coding().setSystem(fhirProperties.getSystems().getSnomed());
    switch (cpuPraefixN) {
      case "c":
        codeCoding.setCode("399534004").setDisplay("cN category (observable entity)");
        break;
      case "p":
        codeCoding.setCode("371494008").setDisplay("pN category (observable entity)");
        break;
      default:
        codeCoding.setCode("277206009").setDisplay("N category (observable entity)");
        break;
    }

    var codeableConcept = new CodeableConcept();
    codeableConcept.setCoding(Collections.singletonList(codeCoding));
    codeableConcept.setExtension(Collections.singletonList(extension));

    return codeableConcept;
  }

  private CodeableConcept createNKategorieValue(String nValue) {

    var codeableConcept =
        new CodeableConcept(
            new Coding().setCode(nValue).setSystem(fhirProperties.getSystems().getTnmUicc()));

    //    if(){
    //      codeableConcept.addExtension(
    //        new Extension()
    //          .setUrl(fhirProperties.getProfiles().getMiiExOnkoTnmItcSuffix())
    //          .setValue(
    //            new CodeableConcept(
    //              new Coding().setCode("i-").setSystem(fhirProperties.getSystems().getTnmUicc())))
    //      );
    //
    //      if(){
    //        codeableConcept.addExtension(
    //          new Extension()
    //            .setUrl(fhirProperties.getProfiles().getMiiExOnkoTnmSnSuffix())
    //            .setValue(
    //              new CodeableConcept(
    //                new
    // Coding().setCode("sn").setSystem(fhirProperties.getSystems().getTnmUicc())))
    //        );
    return codeableConcept;
  }

  private Observation createTNMGroupingObservation(
      TNMTyp tnm,
      String observationType,
      List<Reference> memberObservations,
      Reference patient,
      Reference condition) {

    var observation = new Observation();

    observation.setMeta(
        new Meta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoTnmKlassifikation()));

    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getTnmGroupingObservationId())
            .setValue(tnm.getID());
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    observation.setSubject(patient);
    //    observation.setEncounter();
    observation.setFocus(Collections.singletonList(condition));

    observation.setStatus(Observation.ObservationStatus.FINAL);

    observation.setCode(getGroupingObservationCode(observationType));

    // tnm version
    observation.setMethod(getObservationMethod(tnm.getVersion()));

    var dateOptional = convertObdsDatumToDateTimeType(tnm.getDatum());
    dateOptional.ifPresent(observation::setEffective);

    observation.setValue(getObservationValueUiccStadium(tnm.getUICCStadium()));

    observation.setHasMember(memberObservations);

    return observation;
  }

  private CodeableConcept getObservationMethod(String tnmVersion) {
    var method =
        new Coding()
            .setCode(tnmVersion)
            .setSystem(fhirProperties.getSystems().getMiiCsOnkoTnmVersion());

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
