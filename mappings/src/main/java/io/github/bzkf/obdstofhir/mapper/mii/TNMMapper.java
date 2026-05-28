package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.TNMTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hl7.fhir.r4.model.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TNMMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(TNMMapper.class);

  public enum TnmType {
    CLINICAL,
    PATHOLOGIC,
    GENERIC,
  }

  protected TNMMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      @NonNull TNMTyp tnm,
      @NonNull TnmType tnmType,
      @NonNull String meldungsId,
      Reference patientReference,
      Reference primaryConditionReference,
      @Nullable String histologieId) {
    verifyReference(patientReference, ResourceType.Patient);
    verifyReference(primaryConditionReference, ResourceType.Condition);

    var idBase = tnm.getID();
    if (!StringUtils.hasText(idBase)) {
      if (StringUtils.hasText(histologieId)) {
        LOG.debug("TNM_ID is unset, using Histologie_ID as a fallback");
        idBase = histologieId;
      } else {
        LOG.warn("Both TNM_ID and Histologie_ID are unset. Falling back to Meldung_ID with prefix");
        idBase = meldungsId;
      }
    }

    switch (tnmType) {
      case CLINICAL:
        idBase += "-c";
        break;
      case PATHOLOGIC:
        idBase += "-p";
        break;
      case GENERIC:
        break;
    }

    var memberObservations =
        createObservations(tnm, idBase, patientReference, primaryConditionReference);
    var observationList = new ArrayList<>(memberObservations);

    var memberObservationReferences = createObservationReferences(memberObservations);

    var groupingObservation =
        createTNMGroupingObservation(
            tnm,
            tnmType,
            idBase,
            memberObservationReferences,
            patientReference,
            primaryConditionReference);
    observationList.add(groupingObservation);

    return observationList;
  }

  private List<Observation> createObservations(
      TNMTyp tnmTyp, String idBase, Reference patient, Reference primaryConditionReference) {

    var memberObservationList = new ArrayList<Observation>();

    if (tnmTyp.getT() != null) {
      memberObservationList.add(
          createTKategorieObservation(tnmTyp, idBase, patient, primaryConditionReference));
    }

    if (tnmTyp.getN() != null) {
      memberObservationList.add(
          createNKategorieObservation(tnmTyp, idBase, patient, primaryConditionReference));
    }

    if (tnmTyp.getM() != null) {
      memberObservationList.add(
          createMKategorieObservation(tnmTyp, idBase, patient, primaryConditionReference));
    }

    if (tnmTyp.getASymbol() != null) {
      memberObservationList.add(
          createASymbolObservation(tnmTyp, idBase, patient, primaryConditionReference));
    }

    if (tnmTyp.getMSymbol() != null) {
      memberObservationList.add(
          createMSymbolObservation(tnmTyp, idBase, patient, primaryConditionReference));
    }

    if (tnmTyp.getL() != null) {
      memberObservationList.add(
          createLKategorieObservation(tnmTyp, idBase, patient, primaryConditionReference));
    }

    if (tnmTyp.getPn() != null) {
      memberObservationList.add(
          createPnKategorieObservation(tnmTyp, idBase, patient, primaryConditionReference));
    }

    if (tnmTyp.getRSymbol() != null) {
      memberObservationList.add(
          createRSymbolObservation(tnmTyp, idBase, patient, primaryConditionReference));
    }

    if (tnmTyp.getS() != null) {
      memberObservationList.add(
          createSKategorieObservation(tnmTyp, idBase, patient, primaryConditionReference));
    }

    if (tnmTyp.getV() != null) {
      memberObservationList.add(
          createVKategorieObservation(tnmTyp, idBase, patient, primaryConditionReference));
    }

    if (tnmTyp.getYSymbol() != null) {
      memberObservationList.add(
          createYSymbolObservation(tnmTyp, idBase, patient, primaryConditionReference));
    }

    return memberObservationList;
  }

  private Observation createTKategorieObservation(
      TNMTyp tnmTyp, String idBase, Reference patient, Reference primaryConditionReference) {
    String identifierValue = idBase + "-T";
    var observation =
        createTNMBaseResource(
            fhirProperties.getProfiles().getMiiPrOnkoTnmTKategorie(),
            identifierValue,
            fhirProperties.getSystems().getIdentifiers().getTnmTKategorieObservationId(),
            tnmTyp.getVersion(),
            tnmTyp.getDatum(),
            patient,
            primaryConditionReference);
    // Das Weglassen des Prefix wird als "c" interpretiert
    var cpuPraefixT = Optional.ofNullable(tnmTyp.getCPUPraefixT()).orElse("c");
    observation.setCode(createTKategorieCode(cpuPraefixT));
    observation.setValue(getCodeableConceptTnmUicc("T" + tnmTyp.getT()));
    return observation;
  }

  private Observation createNKategorieObservation(
      TNMTyp tnmTyp, String idBase, Reference patient, Reference primaryConditionReference) {
    String identifierValue = idBase + "-N";
    var observation =
        createTNMBaseResource(
            fhirProperties.getProfiles().getMiiPrOnkoTnmNKategorie(),
            identifierValue,
            fhirProperties.getSystems().getIdentifiers().getTnmNKategorieObservationId(),
            tnmTyp.getVersion(),
            tnmTyp.getDatum(),
            patient,
            primaryConditionReference);
    // Das Weglassen des Prefix wird als "c" interpretiert
    var cpuPraefixN = Optional.ofNullable(tnmTyp.getCPUPraefixN()).orElse("c");
    observation.setCode(createNKategorieCode(cpuPraefixN));
    observation.setValue(createValueWithItcSnSuffixExtension("N" + tnmTyp.getN()));
    return observation;
  }

  private Observation createMKategorieObservation(
      TNMTyp tnmTyp, String idBase, Reference patient, Reference primaryConditionReference) {
    String identifierValue = idBase + "-M";
    var observation =
        createTNMBaseResource(
            fhirProperties.getProfiles().getMiiPrOnkoTnmMKategorie(),
            identifierValue,
            fhirProperties.getSystems().getIdentifiers().getTnmMKategorieObservationId(),
            tnmTyp.getVersion(),
            tnmTyp.getDatum(),
            patient,
            primaryConditionReference);
    // Das Weglassen des Prefix wird als "c" interpretiert
    var cpuPraefixM = Optional.ofNullable(tnmTyp.getCPUPraefixM()).orElse("c");
    observation.setCode(createMKategorieCode(cpuPraefixM));
    observation.setValue(createValueWithItcSnSuffixExtension("M" + tnmTyp.getM()));
    return observation;
  }

  private Observation createASymbolObservation(
      TNMTyp tnmTyp, String idBase, Reference patient, Reference primaryConditionReference) {
    String identifierValue = idBase + "-a";
    var observation =
        createTNMBaseResource(
            fhirProperties.getProfiles().getMiiPrOnkoTnmASymbol(),
            identifierValue,
            fhirProperties.getSystems().getIdentifiers().getTnmASymbolObservationId(),
            tnmTyp.getVersion(),
            tnmTyp.getDatum(),
            patient,
            primaryConditionReference);
    observation.setCode(getCodeableConceptLoinc("101660-9", "Cancer staging during autopsy"));
    observation.setValue(
        getCodeableConceptSnomed("421426001", "Tumor staging descriptor a (tumor staging)"));
    return observation;
  }

  private Observation createMSymbolObservation(
      TNMTyp tnmTyp, String idBase, Reference patient, Reference primaryConditionReference) {
    String identifierValue = idBase + "-m";
    var observation =
        createTNMBaseResource(
            fhirProperties.getProfiles().getMiiPrOnkoTnmMSymbol(),
            identifierValue,
            fhirProperties.getSystems().getIdentifiers().getTnmMSymbolObservationId(),
            tnmTyp.getVersion(),
            tnmTyp.getDatum(),
            patient,
            primaryConditionReference);
    observation.setCode(
        getCodeableConceptLoinc("42030-7", "Multiple tumors reported as single primary Cancer"));
    observation.setValue(getCodeableConceptTnmUicc(tnmTyp.getMSymbol()));
    return observation;
  }

  private Observation createLKategorieObservation(
      TNMTyp tnmTyp, String idBase, Reference patient, Reference primaryConditionReference) {
    String identifierValue = idBase + "-L";
    var observation =
        createTNMBaseResource(
            fhirProperties.getProfiles().getMiiPrOnkoTnmLKategorie(),
            identifierValue,
            fhirProperties.getSystems().getIdentifiers().getTnmLKategorieObservationId(),
            tnmTyp.getVersion(),
            tnmTyp.getDatum(),
            patient,
            primaryConditionReference);
    observation.setCode(
        getCodeableConceptSnomed(
            "395715009",
            "Status of lymphatic (small vessel) invasion by tumor (observable entity)"));
    observation.setValue(getCodeableConceptTnmUicc(tnmTyp.getL()));
    return observation;
  }

  private Observation createPnKategorieObservation(
      TNMTyp tnmTyp, String idBase, Reference patient, Reference primaryConditionReference) {
    String identifierValue = idBase + "-Pn";
    var observation =
        createTNMBaseResource(
            fhirProperties.getProfiles().getMiiPrOnkoTnmPnKategorie(),
            identifierValue,
            fhirProperties.getSystems().getIdentifiers().getTnmPnKategorieObservationId(),
            tnmTyp.getVersion(),
            tnmTyp.getDatum(),
            patient,
            primaryConditionReference);
    observation.setCode(
        getCodeableConceptSnomed(
            "371513001",
            "Presence of direct invasion by primary malignant neoplasm to nerve (observable entity)"));
    observation.setValue(getCodeableConceptTnmUicc(tnmTyp.getPn()));
    return observation;
  }

  private Observation createRSymbolObservation(
      TNMTyp tnmTyp, String idBase, Reference patient, Reference primaryConditionReference) {
    String identifierValue = idBase + "-r";
    var observation =
        createTNMBaseResource(
            fhirProperties.getProfiles().getMiiPrOnkoTnmRSymbol(),
            identifierValue,
            fhirProperties.getSystems().getIdentifiers().getTnmRSymbolObservationId(),
            tnmTyp.getVersion(),
            tnmTyp.getDatum(),
            patient,
            primaryConditionReference);
    observation.setCode(
        getCodeableConceptLoinc("101659-1", "Cancer staging after tumor recurrence"));
    observation.setValue(
        getCodeableConceptSnomed("421188008", "Tumor staging descriptor r (tumor staging)"));
    return observation;
  }

  private Observation createSKategorieObservation(
      TNMTyp tnmTyp, String idBase, Reference patient, Reference primaryConditionReference) {
    String identifierValue = idBase + "-S";
    var observation =
        createTNMBaseResource(
            fhirProperties.getProfiles().getMiiPrOnkoTnmSKategorie(),
            identifierValue,
            fhirProperties.getSystems().getIdentifiers().getTnmSKategorieObservationId(),
            tnmTyp.getVersion(),
            tnmTyp.getDatum(),
            patient,
            primaryConditionReference);
    observation.setCode(
        getCodeableConceptSnomed("399424006", "Serum tumor marker category (observable entity)"));
    observation.setValue(getCodeableConceptTnmUicc(tnmTyp.getS()));
    return observation;
  }

  private Observation createVKategorieObservation(
      TNMTyp tnmTyp, String idBase, Reference patient, Reference primaryConditionReference) {
    String identifierValue = idBase + "-V";
    var observation =
        createTNMBaseResource(
            fhirProperties.getProfiles().getMiiPrOnkoTnmVKategorie(),
            identifierValue,
            fhirProperties.getSystems().getIdentifiers().getTnmVKategorieObservationId(),
            tnmTyp.getVersion(),
            tnmTyp.getDatum(),
            patient,
            primaryConditionReference);
    observation.setCode(
        getCodeableConceptSnomed(
            "371493002", "Status of venous (large vessel) invasion by tumor (observable entity)"));
    observation.setValue(getCodeableConceptTnmUicc(tnmTyp.getV()));
    return observation;
  }

  private Observation createYSymbolObservation(
      TNMTyp tnmTyp, String idBase, Reference patient, Reference primaryConditionReference) {
    String identifierValue = idBase + "-y";
    var observation =
        createTNMBaseResource(
            fhirProperties.getProfiles().getMiiPrOnkoTnmYSymbol(),
            identifierValue,
            fhirProperties.getSystems().getIdentifiers().getTnmYSymbolObservationId(),
            tnmTyp.getVersion(),
            tnmTyp.getDatum(),
            patient,
            primaryConditionReference);
    observation.setCode(
        getCodeableConceptLoinc("101658-3", "Cancer staging after multimodality therapy"));
    observation.setValue(
        getCodeableConceptSnomed("421755005", "Tumor staging descriptor y (tumor staging)"));
    return observation;
  }

  private List<Reference> createObservationReferences(List<Observation> observations) {

    var observationReferences = new ArrayList<Reference>();

    for (Observation observation : observations) {
      observationReferences.add(new Reference("Observation/" + observation.getId()));
    }

    return observationReferences;
  }

  private Observation createTNMBaseResource(
      String profile,
      String identifierValue,
      String identifierSystem,
      String tnmVersion,
      XMLGregorianCalendar datum,
      Reference patient,
      Reference primaryConditionReference) {
    var observation = new Observation();

    observation.setMeta(new Meta().addProfile(profile));

    var identifier =
        new Identifier().setSystem(identifierSystem).setValue(slugifier.slugify(identifierValue));
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    observation.setSubject(patient);
    observation.setFocus(Collections.singletonList(primaryConditionReference));
    observation.setStatus(Observation.ObservationStatus.FINAL);
    observation.setMethod(getObservationMethod(tnmVersion));
    var dateOptional = convertObdsDatumToDateTimeType(datum);
    dateOptional.ifPresent(observation::setEffective);

    return observation;
  }

  private CodeableConcept createTKategorieCode(String cpuPraefixT) {

    var extension = getCpPraefixExtension(cpuPraefixT);

    var codeCoding = fhirProperties.getCodings().snomed();
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

    var codeCoding = fhirProperties.getCodings().snomed();
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

    var codeCoding = fhirProperties.getCodings().snomed();
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

    // suffixes = {"i-", "i+", "sn"};
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
              .setUrl(fhirProperties.getExtensions().getMiiExOnkoTnmSnSuffix())
              .setValue(
                  new CodeableConcept(
                      new Coding()
                          .setCode("sn")
                          .setSystem(fhirProperties.getSystems().getTnmUicc()))));
    }
    if (extractedSuffixes.contains("i+")) {
      codeableConcept.addExtension(
          new Extension()
              .setUrl(fhirProperties.getExtensions().getMiiExOnkoTnmItcSuffix())
              .setValue(
                  new CodeableConcept(
                      new Coding()
                          .setCode("i+")
                          .setSystem(fhirProperties.getSystems().getTnmUicc()))));
    }
    if (extractedSuffixes.contains("i-")) {
      codeableConcept.addExtension(
          new Extension()
              .setUrl(fhirProperties.getExtensions().getMiiExOnkoTnmItcSuffix())
              .setValue(
                  new CodeableConcept(
                      new Coding()
                          .setCode("i-")
                          .setSystem(fhirProperties.getSystems().getTnmUicc()))));
    }

    return codeableConcept;
  }

  private CodeableConcept getCodeableConceptSnomed(String snomedCode, String display) {
    return new CodeableConcept(
        fhirProperties.getCodings().snomed().setCode(snomedCode).setDisplay(display));
  }

  private CodeableConcept getCodeableConceptTnmUicc(String tnmValue) {

    return new CodeableConcept(
        new Coding().setSystem(fhirProperties.getSystems().getTnmUicc()).setCode(tnmValue));
  }

  private CodeableConcept getCodeableConceptLoinc(String loincCode, String display) {
    return new CodeableConcept(
        fhirProperties.getCodings().loinc().setCode(loincCode).setDisplay(display));
  }

  private Extension getCpPraefixExtension(String cpuPraefixN) {
    var extension =
        new Extension().setUrl(fhirProperties.getExtensions().getMiiExOnkoTnmCpPraefix());
    extension.setValue(
        new CodeableConcept(
            new Coding().setCode(cpuPraefixN).setSystem(fhirProperties.getSystems().getTnmUicc())));
    return extension;
  }

  private Observation createTNMGroupingObservation(
      TNMTyp tnm,
      TnmType observationType,
      String idBase,
      List<Reference> memberObservations,
      Reference patient,
      Reference primaryConditionReference) {

    var observation = new Observation();

    observation.setMeta(
        new Meta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoTnmKlassifikation()));

    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getIdentifiers().getTnmGroupingObservationId())
            .setValue(slugifier.slugify(idBase));
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    observation.setSubject(patient);

    observation.setFocus(Collections.singletonList(primaryConditionReference));

    observation.setStatus(Observation.ObservationStatus.FINAL);

    observation.setCode(getGroupingObservationCode(observationType));

    observation.setMethod(getObservationMethod(tnm.getVersion()));

    var dateOptional = convertObdsDatumToDateTimeType(tnm.getDatum());
    dateOptional.ifPresent(observation::setEffective);

    if (tnm.getUICCStadium() != null) {
      CodeableConcept observationValueUiccStadium =
          getObservationValueUiccStadium(tnm.getUICCStadium());
      if (observationValueUiccStadium != null) {
        observation.setValue(observationValueUiccStadium);
      }
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

  private CodeableConcept getGroupingObservationCode(TnmType observationType) {
    var groupingObservationCode = fhirProperties.getCodings().snomed();

    switch (observationType) {
      case CLINICAL:
        groupingObservationCode.setCode("399537006");
        groupingObservationCode.setDisplay("Clinical TNM stage grouping");
        break;
      case PATHOLOGIC:
        groupingObservationCode.setCode("399588009");
        groupingObservationCode.setDisplay("Pathologic TNM stage grouping");
        break;
      case GENERIC:
      default:
        groupingObservationCode.setCode("399390009");
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
      case "IA" -> new CodeableConcept(coding.setCode("IA").setDisplay("Stadium IA"));
      case "IIID" -> new CodeableConcept(coding.setCode("IIID").setDisplay("Stadium IIID"));
      case "IVA1" -> new CodeableConcept(coding.setCode("IVA1").setDisplay("Stadium IVA1"));
      case "IVA2" -> new CodeableConcept(coding.setCode("IVA2").setDisplay("Stadium IVA2"));
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
      default -> {
        LOG.warn("unkown uicc stadium {}", uiccStadium);
        yield null;
      }
    };
  }
}
