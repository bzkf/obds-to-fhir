package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.TNMTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie.CodeSystems.MiiCsOnkoTnmUicc;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

  // suffixes = {"i-", "i+", "mol-", "mol+", "sn"};
  private static final Pattern SUFFIX_PATTERN =
      Pattern.compile("\\(?(\\Qi-\\E|\\Qi+\\E|\\Qmol-\\E|\\Qmol+\\E|\\Qsn\\E)\\)?");

  private static final Set<String> M_SYMBOL_CODES =
      Set.of("m", "2", "3", "4", "5", "6", "7", "8", "9", "10");

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

    var hasPraefix =
        tnmTyp.getYSymbol() != null || tnmTyp.getRSymbol() != null || tnmTyp.getASymbol() != null;
    if (hasPraefix && tnmTyp.getT() == null && tnmTyp.getN() == null && tnmTyp.getM() == null) {
      LOG.warn(
          "TNM y-, r- or a-Symbol is set, but there is no T, N or M category to attach it to. "
              + "The prefix is not mapped.");
    }

    if (tnmTyp.getT() != null) {
      String identifierValue = idBase + "-T";
      var tKategorieObservation =
          createTNMBaseResource(
              Onkologie.Profiles.miiPrOnkoTnmTKategorie(),
              identifierValue,
              fhirProperties.getSystems().getIdentifiers().getTnmTKategorieObservationId(),
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              primaryConditionReference);

      // Das Weglassen des Prefix wird als "c" interpretiert
      var cpuPraefixT = praefixOrDefault(tnmTyp.getCPUPraefixT());
      tKategorieObservation.setCode(createTKategorieCode(cpuPraefixT));
      tKategorieObservation.setValue(getCodeableConceptTnmUicc("T" + tnmTyp.getT()));
      addPraefixModifierExtensions(tKategorieObservation, tnmTyp);

      // since KDS Onkologie 2027, the m-Symbol is no longer mapped as a separate symbol
      // observation, but as a component of the T category
      if (tnmTyp.getMSymbol() != null) {
        addMultipleTumorenComponent(tKategorieObservation, tnmTyp.getMSymbol());
      }

      memberObservationList.add(tKategorieObservation);
    } else if (tnmTyp.getMSymbol() != null) {
      LOG.warn(
          "TNM m-Symbol is set, but there is no T category to attach it to. "
              + "The m-Symbol is not mapped.");
    }

    if (tnmTyp.getN() != null) {
      String identifierValue = idBase + "-N";
      var nKategorieObservation =
          createTNMBaseResource(
              Onkologie.Profiles.miiPrOnkoTnmNKategorie(),
              identifierValue,
              fhirProperties.getSystems().getIdentifiers().getTnmNKategorieObservationId(),
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              primaryConditionReference);

      // Das Weglassen des Prefix wird als "c" interpretiert
      var cpuPraefixN = praefixOrDefault(tnmTyp.getCPUPraefixN());
      nKategorieObservation.setCode(createNKategorieCode(cpuPraefixN));
      nKategorieObservation.setValue(createValueWithItcSnSuffixExtension("N" + tnmTyp.getN()));
      addPraefixModifierExtensions(nKategorieObservation, tnmTyp);
      memberObservationList.add(nKategorieObservation);
    }

    if (tnmTyp.getM() != null) {
      String identifierValue = idBase + "-M";
      var mKategorieObservation =
          createTNMBaseResource(
              Onkologie.Profiles.miiPrOnkoTnmMKategorie(),
              identifierValue,
              fhirProperties.getSystems().getIdentifiers().getTnmMKategorieObservationId(),
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              primaryConditionReference);

      // Das Weglassen des Prefix wird als "c" interpretiert
      var cpuPraefixM = praefixOrDefault(tnmTyp.getCPUPraefixM());
      mKategorieObservation.setCode(createMKategorieCode(cpuPraefixM));
      mKategorieObservation.setValue(createValueWithItcSnSuffixExtension("M" + tnmTyp.getM()));
      addPraefixModifierExtensions(mKategorieObservation, tnmTyp);
      memberObservationList.add(mKategorieObservation);
    }

    if (tnmTyp.getL() != null) {
      String identifierValue = idBase + "-L";
      var lKategorieObservation =
          createTNMBaseResource(
              Onkologie.Profiles.miiPrOnkoTnmLKategorie(),
              identifierValue,
              fhirProperties.getSystems().getIdentifiers().getTnmLKategorieObservationId(),
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              primaryConditionReference);
      lKategorieObservation.setCode(
          getCodeableConceptSnomed(
              "395715009",
              "Status of lymphatic (small vessel) invasion by tumor (observable entity)"));
      lKategorieObservation.setValue(getCodeableConceptTnmUicc(tnmTyp.getL()));
      memberObservationList.add(lKategorieObservation);
    }

    if (tnmTyp.getPn() != null) {
      String identifierValue = idBase + "-Pn";
      var pnKategorieObservation =
          createTNMBaseResource(
              Onkologie.Profiles.miiPrOnkoTnmPnKategorie(),
              identifierValue,
              fhirProperties.getSystems().getIdentifiers().getTnmPnKategorieObservationId(),
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              primaryConditionReference);
      pnKategorieObservation.setCode(
          getCodeableConceptSnomed(
              "371513001",
              "Presence of direct invasion by primary malignant neoplasm to nerve (observable entity)"));
      pnKategorieObservation.setValue(getCodeableConceptTnmUicc(tnmTyp.getPn()));
      memberObservationList.add(pnKategorieObservation);
    }

    if (tnmTyp.getS() != null) {
      String identifierValue = idBase + "-S";
      var sKategorieObservation =
          createTNMBaseResource(
              Onkologie.Profiles.miiPrOnkoTnmSKategorie(),
              identifierValue,
              fhirProperties.getSystems().getIdentifiers().getTnmSKategorieObservationId(),
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              primaryConditionReference);
      sKategorieObservation.setCode(
          getCodeableConceptSnomed("399424006", "Serum tumor marker category (observable entity)"));
      sKategorieObservation.setValue(getCodeableConceptTnmUicc(tnmTyp.getS()));
      memberObservationList.add(sKategorieObservation);
    }

    if (tnmTyp.getV() != null) {
      String identifierValue = idBase + "-V";
      var vKategorieObservation =
          createTNMBaseResource(
              Onkologie.Profiles.miiPrOnkoTnmVKategorie(),
              identifierValue,
              fhirProperties.getSystems().getIdentifiers().getTnmVKategorieObservationId(),
              tnmTyp.getVersion(),
              tnmTyp.getDatum(),
              patient,
              primaryConditionReference);
      vKategorieObservation.setCode(
          getCodeableConceptSnomed(
              "371493002",
              "Status of venous (large vessel) invasion by tumor (observable entity)"));
      vKategorieObservation.setValue(getCodeableConceptTnmUicc(tnmTyp.getV()));
      memberObservationList.add(vKategorieObservation);
    }

    return memberObservationList;
  }

  /**
   * The value of the multipleTumoren component has a required binding to the m-Symbol ValueSet. An
   * m-Symbol outside of it (see {@link #createMSymbolValue}) is kept as a note on the T category
   * instead, with the component's dataAbsentReason set.
   */
  private void addMultipleTumorenComponent(Observation tKategorie, String mSymbol) {
    var component =
        tKategorie
            .addComponent()
            .setCode(
                getCodeableConceptLoinc(
                    "42030-7", "Multiple tumors reported as single primary Cancer"));
    var value = createMSymbolValue(mSymbol);
    if (value.hasCoding()) {
      component.setValue(value);
    } else {
      component.setDataAbsentReason(dataAbsentReason(Enumerations.DataAbsentReason.ASTEXT));
      tKategorie.addNote().setText("m-Symbol: " + value.getText());
    }
  }

  // since KDS Onkologie 2027, the y-, r- and a-prefixes are no longer mapped as separate symbol
  // observations, but as modifierExtensions on each T, N and M category since they change the
  // interpretation of the category value
  private void addPraefixModifierExtensions(Observation observation, TNMTyp tnmTyp) {
    if (tnmTyp.getYSymbol() != null) {
      observation.addModifierExtension(
          Onkologie.Extensions.miiExOnkoTnmYPraefix(
              new CodeableConcept(MiiCsOnkoTnmUicc.Y.coding())));
    }
    if (tnmTyp.getRSymbol() != null) {
      observation.addModifierExtension(
          Onkologie.Extensions.miiExOnkoTnmRPraefix(
              new CodeableConcept(MiiCsOnkoTnmUicc.R.coding())));
    }
    if (tnmTyp.getASymbol() != null) {
      observation.addModifierExtension(
          Onkologie.Extensions.miiExOnkoTnmAPraefix(
              new CodeableConcept(MiiCsOnkoTnmUicc.A.coding())));
    }
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
    var observation =
        createBaseObservation(
            profile,
            identifierSystem,
            identifierValue,
            patient,
            primaryConditionReference,
            convertObdsDatumToDateTimeType(datum).orElse(null));
    observation.setMethod(getObservationMethod(tnmVersion));

    return observation;
  }

  /** xml specifies: Das Weglassen des Prefix wird als "c" interpretiert */
  private static String praefixOrDefault(@Nullable String cpuPraefix) {
    return cpuPraefix != null ? cpuPraefix : "c";
  }

  private CodeableConcept createTKategorieCode(String cpuPraefix) {
    var coding =
        switch (cpuPraefix) {
          case "c", "u" -> getSnomedCoding("399504009", "cT category (observable entity)");
          case "p" -> getSnomedCoding("384625004", "pT category (observable entity)");
          default -> getSnomedCoding("78873005", "T category (observable entity)");
        };
    return createKategorieCode(coding, cpuPraefix);
  }

  private CodeableConcept createNKategorieCode(String cpuPraefix) {
    var coding =
        switch (cpuPraefix) {
          case "c", "u" -> getSnomedCoding("399534004", "cN category (observable entity)");
          case "p" -> getSnomedCoding("371494008", "pN category (observable entity)");
          default -> getSnomedCoding("277206009", "N category (observable entity)");
        };
    return createKategorieCode(coding, cpuPraefix);
  }

  private CodeableConcept createMKategorieCode(String cpuPraefix) {
    var coding =
        switch (cpuPraefix) {
          case "c", "u" -> getSnomedCoding("399387003", "cM category (observable entity)");
          case "p" -> getSnomedCoding("371497001", "pM category (observable entity)");
          default -> getSnomedCoding("277208005", "M category (observable entity)");
        };
    return createKategorieCode(coding, cpuPraefix);
  }

  private CodeableConcept createKategorieCode(Coding coding, String cpuPraefix) {
    var codeableConcept = new CodeableConcept(coding);
    codeableConcept.addExtension(getCpPraefixExtension(cpuPraefix));
    return codeableConcept;
  }

  protected CodeableConcept createValueWithItcSnSuffixExtension(String inputValue) {

    var matcher = SUFFIX_PATTERN.matcher(inputValue.trim());

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
                .setSystem(Onkologie.CodeSystems.miiCsOnkoTnmUicc()));

    if (extractedSuffixes.contains("sn")) {
      codeableConcept.addExtension(Onkologie.Extensions.miiExOnkoTnmSnSuffix(MiiCsOnkoTnmUicc.SN));
    }

    if (extractedSuffixes.contains("i+")) {
      codeableConcept.addExtension(
          Onkologie.Extensions.miiExOnkoTnmItcSuffix(MiiCsOnkoTnmUicc.I_POS));
    }

    if (extractedSuffixes.contains("i-")) {
      codeableConcept.addExtension(
          Onkologie.Extensions.miiExOnkoTnmItcSuffix(MiiCsOnkoTnmUicc.I_NEG));
    }

    if (extractedSuffixes.contains("mol+")) {
      codeableConcept.addExtension(
          Onkologie.Extensions.miiExOnkoTnmItcSuffix(MiiCsOnkoTnmUicc.MOL_POS));
    }

    if (extractedSuffixes.contains("mol-")) {
      codeableConcept.addExtension(
          Onkologie.Extensions.miiExOnkoTnmItcSuffix(MiiCsOnkoTnmUicc.MOL_NEG));
    }

    return codeableConcept;
  }

  /**
   * The ValueSet only has {@code m} and the counts 2 to 10, while oBDS also allows more tumors and
   * an in-situ marker ({@code m,is} or just {@code is}). What the ValueSet can't express is kept as
   * text.
   */
  private CodeableConcept createMSymbolValue(String mSymbol) {
    var code = mSymbol.replace(",is", "");
    if (!M_SYMBOL_CODES.contains(code)) {
      LOG.debug("m_Symbol {} has no code in the ValueSet, keeping it as text.", mSymbol);
      return new CodeableConcept().setText(mSymbol);
    }

    var value = getCodeableConceptTnmUicc(code);
    return code.equals(mSymbol) ? value : value.setText(mSymbol);
  }

  private Coding getSnomedCoding(String snomedCode, String display) {
    return fhirProperties.getCodings().snomed().setCode(snomedCode).setDisplay(display);
  }

  private CodeableConcept getCodeableConceptSnomed(String snomedCode, String display) {
    return new CodeableConcept(getSnomedCoding(snomedCode, display));
  }

  private CodeableConcept getCodeableConceptTnmUicc(String tnmValue) {

    return new CodeableConcept(
        new Coding().setSystem(Onkologie.CodeSystems.miiCsOnkoTnmUicc()).setCode(tnmValue));
  }

  private CodeableConcept getCodeableConceptLoinc(String loincCode, String display) {
    return new CodeableConcept(
        fhirProperties.getCodings().loinc().setCode(loincCode).setDisplay(display));
  }

  private Extension getCpPraefixExtension(String cpuPraefixN) {
    return Onkologie.Extensions.miiExOnkoTnmCpPraefix(
        Onkologie.CodeSystems.MiiCsOnkoTnmUicc.fromValueOrThrow(cpuPraefixN));
  }

  private Observation createTNMGroupingObservation(
      TNMTyp tnm,
      TnmType observationType,
      String idBase,
      List<Reference> memberObservations,
      Reference patient,
      Reference primaryConditionReference) {

    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoTnmKlassifikation(),
            fhirProperties.getSystems().getIdentifiers().getTnmGroupingObservationId(),
            idBase,
            patient,
            primaryConditionReference,
            convertObdsDatumToDateTimeType(tnm.getDatum()).orElse(null));

    observation.setCode(getGroupingObservationCode(observationType));

    observation.setMethod(getObservationMethod(tnm.getVersion()));

    if (tnm.getUICCStadium() != null) {
      observation.setValue(getObservationValueUiccStadium(tnm.getUICCStadium()));
    }

    observation.setHasMember(memberObservations);

    return observation;
  }

  private @Nullable CodeableConcept getObservationMethod(@Nullable String tnmVersion) {
    if (!StringUtils.hasText(tnmVersion)) {
      return null;
    }

    var method =
        new Coding().setCode(tnmVersion).setSystem(Onkologie.CodeSystems.miiCsOnkoTnmVersion());

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
    var coding =
        Onkologie.CodeSystems.MiiCsOnkoTnmUicc.fromValue(uiccStadium)
            .map(value -> value.coding())
            .orElseGet(
                () -> {
                  LOG.warn("Unknown UICC stadium {}, still using code as-is.", uiccStadium);
                  return new Coding()
                      .setSystem(Onkologie.CodeSystems.miiCsOnkoTnmUicc())
                      .setCode(uiccStadium);
                });

    return new CodeableConcept(coding);
  }
}
