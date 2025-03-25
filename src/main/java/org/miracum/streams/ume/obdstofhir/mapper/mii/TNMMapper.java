package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.TNMTyp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.xml.datatype.XMLGregorianCalendar;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TNMMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(TNMMapper.class);

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
    var clinicalObservationRefs = createObservations(cTnm, patient, condition);
    var groupingObservationClinical =
        createTNMGroupingObservation(cTnm, "clinical", clinicalObservationRefs, patient, condition);
    observationList.add(groupingObservationClinical);
  }

  private void mapPathologicalObservations(TNMTyp pTnm, Reference patient, Reference condition) {
    var pathologicObservationRefs = createObservations(pTnm, patient, condition);
    var groupingObservationPathologic =
        createTNMGroupingObservation(
            pTnm, "pathologic", pathologicObservationRefs, patient, condition);
    observationList.add(groupingObservationPathologic);
  }

  private void mapGenericObservations(TNMTyp tnm, Reference patient, Reference condition) {
    var genericObservationRefs = createObservations(tnm, patient, condition);
    var groupingObservationGeneric =
        createTNMGroupingObservation(tnm, "generic", genericObservationRefs, patient, condition);
    observationList.add(groupingObservationGeneric);
  }

  private List<Reference> createObservations(
      TNMTyp tnmTyp, Reference patient, Reference condition) {

    List<Reference> memberObservationReferences = new ArrayList<>();

    if (tnmTyp.getT() != null) {
      String identifierValue = tnmTyp.getID() + "_T";
      var tKategorieObservation =
          createTNMBaseResource(
              fhirProperties.getProfiles().getMiiPrOnkoTnmTKategorie(),
              fhirProperties.getSystems().getTnmTKategorieObservationId(),
              identifierValue,
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              condition);
      tKategorieObservation.setCode(createTKategorieCode(tnmTyp.getCPUPraefixT()));
      tKategorieObservation.setValue(getCodeableConceptTnmUicc(tnmTyp.getT()));
      observationList.add(tKategorieObservation);
      memberObservationReferences.add(
          new Reference("Observation/" + tKategorieObservation.getId()));
    }

    if (tnmTyp.getN() != null) {
      String identifierValue = tnmTyp.getID() + "_N";
      var nKategorieObservation =
          createTNMBaseResource(
              fhirProperties.getProfiles().getMiiPrOnkoTnmNKategorie(),
              fhirProperties.getSystems().getTnmNKategorieObservationId(),
              identifierValue,
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              condition);
      nKategorieObservation.setCode(createNKategorieCode(tnmTyp.getCPUPraefixN()));
      nKategorieObservation.setValue(createValueWithItcSnSuffixExtension(tnmTyp.getN()));
      observationList.add(nKategorieObservation);
      memberObservationReferences.add(
          new Reference("Observation/" + nKategorieObservation.getId()));
    }

    if (tnmTyp.getM() != null) {
      String identifierValue = tnmTyp.getID() + "_M";
      var mKategorieObservation =
          createTNMBaseResource(
              fhirProperties.getProfiles().getMiiPrOnkoTnmMKategorie(),
              fhirProperties.getSystems().getTnmMKategorieObservationId(),
              identifierValue,
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              condition);
      mKategorieObservation.setCode(createMKategorieCode(tnmTyp.getCPUPraefixM()));
      mKategorieObservation.setValue(createValueWithItcSnSuffixExtension(tnmTyp.getM()));
      observationList.add(mKategorieObservation);
      memberObservationReferences.add(
          new Reference("Observation/" + mKategorieObservation.getId()));
    }

    if (tnmTyp.getASymbol() != null) {
      String identifierValue = tnmTyp.getID() + "_a";
      var aSymbolObservation =
          createTNMBaseResource(
              fhirProperties.getProfiles().getMiiPrOnkoTnmASymbol(),
              fhirProperties.getSystems().getTnmASymbolObservationId(),
              identifierValue,
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              condition);
      aSymbolObservation.setCode(getCodeableConceptLoinc("101660-9"));
      aSymbolObservation.setValue(getCodeableConceptSnomed("421426001"));
      observationList.add(aSymbolObservation);
      memberObservationReferences.add(new Reference("Observation/" + aSymbolObservation.getId()));
    }

    if (tnmTyp.getMSymbol() != null) {
      String identifierValue = tnmTyp.getID() + "_m";
      var mSymbolObservation =
          createTNMBaseResource(
              fhirProperties.getProfiles().getMiiPrOnkoTnmMSymbol(),
              fhirProperties.getSystems().getTnmMSymbolObservationId(),
              identifierValue,
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              condition);
      mSymbolObservation.setCode(getCodeableConceptLoinc("42030-7"));
      mSymbolObservation.setValue(getCodeableConceptTnmUicc(tnmTyp.getMSymbol()));
      observationList.add(mSymbolObservation);
      memberObservationReferences.add(new Reference("Observation/" + mSymbolObservation.getId()));
    }

    if (tnmTyp.getL() != null) {
      String identifierValue = tnmTyp.getID() + "_L";
      var lKategorieObservation =
          createTNMBaseResource(
              fhirProperties.getProfiles().getMiiPrOnkoTnmLKategorie(),
              fhirProperties.getSystems().getTnmLKategorieObservationId(),
              identifierValue,
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              condition);
      lKategorieObservation.setCode(getCodeableConceptSnomed("395715009"));
      lKategorieObservation.setValue(getCodeableConceptTnmUicc(tnmTyp.getL()));
      observationList.add(lKategorieObservation);
      memberObservationReferences.add(
          new Reference("Observation/" + lKategorieObservation.getId()));
    }

    if (tnmTyp.getPn() != null) {
      String identifierValue = tnmTyp.getID() + "_Pn";
      var pnKategorieObservation =
          createTNMBaseResource(
              fhirProperties.getProfiles().getMiiPrOnkoTnmPnKategorie(),
              fhirProperties.getSystems().getTnmPnKategorieObservationId(),
              identifierValue,
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              condition);
      pnKategorieObservation.setCode(getCodeableConceptSnomed("371513001"));
      pnKategorieObservation.setValue(getCodeableConceptTnmUicc(tnmTyp.getPn()));
      observationList.add(pnKategorieObservation);
      memberObservationReferences.add(
          new Reference("Observation/" + pnKategorieObservation.getId()));
    }

    if (tnmTyp.getRSymbol() != null) {
      String identifierValue = tnmTyp.getID() + "_r";
      var rSymbolObservation =
          createTNMBaseResource(
              fhirProperties.getProfiles().getMiiPrOnkoTnmRSymbol(),
              fhirProperties.getSystems().getTnmRSymbolObservationId(),
              identifierValue,
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              condition);
      rSymbolObservation.setCode(getCodeableConceptLoinc("101659-1"));
      rSymbolObservation.setValue(getCodeableConceptSnomed("421188008"));
      observationList.add(rSymbolObservation);
      memberObservationReferences.add(new Reference("Observation/" + rSymbolObservation.getId()));
    }

    if (tnmTyp.getS() != null) {
      String identifierValue = tnmTyp.getID() + "_S";
      var sKategorieObservation =
          createTNMBaseResource(
              fhirProperties.getProfiles().getMiiPrOnkoTnmSKategorie(),
              fhirProperties.getSystems().getTnmSKategorieObservationId(),
              identifierValue,
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              condition);
      sKategorieObservation.setCode(getCodeableConceptSnomed("399424006"));
      sKategorieObservation.setValue(getCodeableConceptTnmUicc(tnmTyp.getS()));
      observationList.add(sKategorieObservation);
      memberObservationReferences.add(
          new Reference("Observation/" + sKategorieObservation.getId()));
    }

    if (tnmTyp.getV() != null) {
      String identifierValue = tnmTyp.getID() + "_V";
      var vKategorieObservation =
          createTNMBaseResource(
              fhirProperties.getProfiles().getMiiPrOnkoTnmVKategorie(),
              fhirProperties.getSystems().getTnmVKategorieObservationId(),
              identifierValue,
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              condition);
      vKategorieObservation.setCode(getCodeableConceptSnomed("371493002"));
      vKategorieObservation.setValue(getCodeableConceptTnmUicc(tnmTyp.getV()));
      observationList.add(vKategorieObservation);
      memberObservationReferences.add(
          new Reference("Observation/" + vKategorieObservation.getId()));
    }

    if (tnmTyp.getYSymbol() != null) {
      String identifierValue = tnmTyp.getID() + "_y";
      var ySymbolObservation =
          createTNMBaseResource(
              fhirProperties.getProfiles().getMiiPrOnkoTnmYSymbol(),
              fhirProperties.getSystems().getTnmYSymbolObservationId(),
              identifierValue,
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              condition);
      ySymbolObservation.setCode(getCodeableConceptLoinc("101658-3"));
      ySymbolObservation.setValue(getCodeableConceptSnomed("421755005"));
      observationList.add(ySymbolObservation);
      memberObservationReferences.add(new Reference("Observation/" + ySymbolObservation.getId()));
    }

    return memberObservationReferences;
  }

  private Observation createTNMBaseResource(
      String profile,
      String identifierValue,
      String identifierSystem,
      String tnmVersion,
      XMLGregorianCalendar datum,
      Reference patient,
      Reference condition) {
    var observation = new Observation();

    observation.setMeta(new Meta().addProfile(profile));

    var identifier = new Identifier().setSystem(identifierSystem).setValue(identifierValue);
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    observation.setSubject(patient);
    observation.setFocus(Collections.singletonList(condition));
    observation.setStatus(Observation.ObservationStatus.FINAL);
    observation.setMethod(getObservationMethod(tnmVersion));
    var dateOptional = convertObdsDatumToDateTimeType(datum);
    dateOptional.ifPresent(observation::setEffective);

    return observation;
  }

  private CodeableConcept createTKategorieCode(String cpuPraefixT) {

    var extension = getCpPraefixExtension(cpuPraefixT);

    var codeCoding = new Coding().setSystem(fhirProperties.getSystems().getSnomed());
    switch (cpuPraefixT) {
      case null: // xml specifies: Das Weglassen des Prefix wird als "c" interpretiert
      case "u":
      case "c":
        codeCoding.setCode("399504009").setDisplay("cT category (observable entity)");
        break;
      case "p":
        codeCoding.setCode("384625004").setDisplay("pT category (observable entity)");
        break;
      default: // this should never happen
        LOG.warn("No valid TNM T category c/p prefix value set. Provided value {}", cpuPraefixT);
        codeCoding.setCode("78873005").setDisplay("T category (observable entity)");
        break;
    }

    var codeableConcept = new CodeableConcept();
    codeableConcept.setCoding(Collections.singletonList(codeCoding));
    codeableConcept.setExtension(Collections.singletonList(extension));

    return codeableConcept;
  }

  private CodeableConcept createNKategorieCode(String cpuPraefixN) {

    var extension = getCpPraefixExtension(cpuPraefixN);

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

  private CodeableConcept createMKategorieCode(String cpuPraefixM) {

    var extension = getCpPraefixExtension(cpuPraefixM);

    var codeCoding = new Coding().setSystem(fhirProperties.getSystems().getSnomed());
    switch (cpuPraefixM) {
      case "c":
        codeCoding.setCode("399387003").setDisplay("cM category (observable entity)");
        break;
      case "p":
        codeCoding.setCode("371497001").setDisplay("pM category (observable entity)");
        break;
      default:
        codeCoding.setCode("277208005").setDisplay("M category (observable entity)");
        break;
    }

    var codeableConcept = new CodeableConcept();
    codeableConcept.setCoding(Collections.singletonList(codeCoding));
    codeableConcept.setExtension(Collections.singletonList(extension));

    return codeableConcept;
  }

  protected CodeableConcept createValueWithItcSnSuffixExtension(String inputValue) {

    //  suffixes = {"i-", "i+", "sn"};
    var suffixPattern = "\\(?(\\Qi-\\E|\\Qi+\\E|\\Qsn\\E)\\)?";
    var pattern = Pattern.compile(suffixPattern);
    var matcher = pattern.matcher(inputValue.trim());

    List<String> extractedSuffixes = new ArrayList<>();
    int firstSuffixIndex = -1;

    while (matcher.find()) {
      extractedSuffixes.add(matcher.group(1));
      if (firstSuffixIndex == -1) firstSuffixIndex = matcher.start();
    }

    var processedValue =
        (firstSuffixIndex != -1)
            ? inputValue.substring(0, firstSuffixIndex).trim()
            : inputValue.trim();

    var codeableConcept =
        new CodeableConcept(
            new Coding()
                .setCode(processedValue)
                .setSystem(fhirProperties.getSystems().getTnmUicc()));

    if (extractedSuffixes.contains("sn")) {
      codeableConcept.addExtension(
          new Extension()
              .setUrl(fhirProperties.getProfiles().getMiiExOnkoTnmSnSuffix())
              .setValue(
                  new CodeableConcept(
                      new Coding()
                          .setCode("sn")
                          .setSystem(fhirProperties.getSystems().getTnmUicc()))));
    }
    if (extractedSuffixes.contains("i+")) {
      codeableConcept.addExtension(
          new Extension()
              .setUrl(fhirProperties.getProfiles().getMiiExOnkoTnmItcSuffix())
              .setValue(
                  new CodeableConcept(
                      new Coding()
                          .setCode("i+")
                          .setSystem(fhirProperties.getSystems().getTnmUicc()))));
    }
    if (extractedSuffixes.contains("i-")) {
      codeableConcept.addExtension(
          new Extension()
              .setUrl(fhirProperties.getProfiles().getMiiExOnkoTnmItcSuffix())
              .setValue(
                  new CodeableConcept(
                      new Coding()
                          .setCode("i-")
                          .setSystem(fhirProperties.getSystems().getTnmUicc()))));
    }

    return codeableConcept;
  }

  private CodeableConcept getCodeableConceptSnomed(String snomedCode) {

    return new CodeableConcept(
        new Coding().setSystem(fhirProperties.getSystems().getSnomed()).setCode(snomedCode));
  }

  private CodeableConcept getCodeableConceptTnmUicc(String tnmValue) {

    return new CodeableConcept(
        new Coding().setSystem(fhirProperties.getSystems().getTnmUicc()).setCode(tnmValue));
  }

  private CodeableConcept getCodeableConceptLoinc(String loincCode) {
    return new CodeableConcept(
        new Coding().setSystem(fhirProperties.getSystems().getLoinc()).setCode(loincCode));
  }

  private Extension getCpPraefixExtension(String cpuPraefixN) {
    var extension = new Extension().setUrl(fhirProperties.getProfiles().getMiiExOnkoTnmCpPraefix());
    extension.setValue(
        new CodeableConcept(
            new Coding().setCode(cpuPraefixN).setSystem(fhirProperties.getSystems().getTnmUicc())));
    return extension;
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

    observation.setFocus(Collections.singletonList(condition));

    observation.setStatus(Observation.ObservationStatus.FINAL);

    observation.setCode(getGroupingObservationCode(observationType));

    observation.setMethod(getObservationMethod(tnm.getVersion()));

    var dateOptional = convertObdsDatumToDateTimeType(tnm.getDatum());
    dateOptional.ifPresent(observation::setEffective);

    if (tnm.getUICCStadium() != null) {
      observation.setValue(getObservationValueUiccStadium(tnm.getUICCStadium()));
    }

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
