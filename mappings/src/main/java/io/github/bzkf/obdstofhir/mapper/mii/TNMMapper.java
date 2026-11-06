package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.TNMTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie.CodeSystems.MiiCsOnkoTnmUicc;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
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

  // suffixes = {"i-", "i+", "sn"};
  private static final Pattern SUFFIX_PATTERN =
      Pattern.compile("\\(?(\\Qi-\\E|\\Qi+\\E|\\Qsn\\E)\\)?");

  public enum TnmType {
    CLINICAL,
    PATHOLOGIC,
    GENERIC,
  }

  /** A member of the TNM classification, which is only created if its source value is set. */
  private record TnmMember(
      @Nullable String source,
      String identifierSuffix,
      String profile,
      String identifierSystem,
      Supplier<CodeableConcept> code,
      Supplier<CodeableConcept> value) {}

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
    var identifiers = fhirProperties.getSystems().getIdentifiers();

    var members =
        List.of(
            new TnmMember(
                tnmTyp.getT(),
                "-T",
                Onkologie.Profiles.miiPrOnkoTnmTKategorie(),
                identifiers.getTnmTKategorieObservationId(),
                () -> createTKategorieCode(praefixOrDefault(tnmTyp.getCPUPraefixT())),
                () -> getCodeableConceptTnmUicc("T" + tnmTyp.getT())),
            new TnmMember(
                tnmTyp.getN(),
                "-N",
                Onkologie.Profiles.miiPrOnkoTnmNKategorie(),
                identifiers.getTnmNKategorieObservationId(),
                () -> createNKategorieCode(praefixOrDefault(tnmTyp.getCPUPraefixN())),
                () -> createValueWithItcSnSuffixExtension("N" + tnmTyp.getN())),
            new TnmMember(
                tnmTyp.getM(),
                "-M",
                Onkologie.Profiles.miiPrOnkoTnmMKategorie(),
                identifiers.getTnmMKategorieObservationId(),
                () -> createMKategorieCode(praefixOrDefault(tnmTyp.getCPUPraefixM())),
                () -> createValueWithItcSnSuffixExtension("M" + tnmTyp.getM())),
            new TnmMember(
                tnmTyp.getASymbol(),
                "-a",
                Onkologie.Profiles.miiPrOnkoTnmASymbol(),
                identifiers.getTnmASymbolObservationId(),
                () -> getCodeableConceptLoinc("101660-9", "Cancer staging during autopsy"),
                () ->
                    getCodeableConceptSnomed(
                        "421426001", "Tumor staging descriptor a (tumor staging)")),
            new TnmMember(
                tnmTyp.getMSymbol(),
                "-m",
                Onkologie.Profiles.miiPrOnkoTnmMSymbol(),
                identifiers.getTnmMSymbolObservationId(),
                () ->
                    getCodeableConceptLoinc(
                        "42030-7", "Multiple tumors reported as single primary Cancer"),
                () -> getCodeableConceptTnmUicc(tnmTyp.getMSymbol())),
            new TnmMember(
                tnmTyp.getL(),
                "-L",
                Onkologie.Profiles.miiPrOnkoTnmLKategorie(),
                identifiers.getTnmLKategorieObservationId(),
                () ->
                    getCodeableConceptSnomed(
                        "395715009",
                        "Status of lymphatic (small vessel) invasion by tumor (observable entity)"),
                () -> getCodeableConceptTnmUicc(tnmTyp.getL())),
            new TnmMember(
                tnmTyp.getPn(),
                "-Pn",
                Onkologie.Profiles.miiPrOnkoTnmPnKategorie(),
                identifiers.getTnmPnKategorieObservationId(),
                () ->
                    getCodeableConceptSnomed(
                        "371513001",
                        "Presence of direct invasion by primary malignant neoplasm to nerve (observable entity)"),
                () -> getCodeableConceptTnmUicc(tnmTyp.getPn())),
            new TnmMember(
                tnmTyp.getRSymbol(),
                "-r",
                Onkologie.Profiles.miiPrOnkoTnmRSymbol(),
                identifiers.getTnmRSymbolObservationId(),
                () -> getCodeableConceptLoinc("101659-1", "Cancer staging after tumor recurrence"),
                () ->
                    getCodeableConceptSnomed(
                        "421188008", "Tumor staging descriptor r (tumor staging)")),
            new TnmMember(
                tnmTyp.getS(),
                "-S",
                Onkologie.Profiles.miiPrOnkoTnmSKategorie(),
                identifiers.getTnmSKategorieObservationId(),
                () ->
                    getCodeableConceptSnomed(
                        "399424006", "Serum tumor marker category (observable entity)"),
                () -> getCodeableConceptTnmUicc(tnmTyp.getS())),
            new TnmMember(
                tnmTyp.getV(),
                "-V",
                Onkologie.Profiles.miiPrOnkoTnmVKategorie(),
                identifiers.getTnmVKategorieObservationId(),
                () ->
                    getCodeableConceptSnomed(
                        "371493002",
                        "Status of venous (large vessel) invasion by tumor (observable entity)"),
                () -> getCodeableConceptTnmUicc(tnmTyp.getV())),
            new TnmMember(
                tnmTyp.getYSymbol(),
                "-y",
                Onkologie.Profiles.miiPrOnkoTnmYSymbol(),
                identifiers.getTnmYSymbolObservationId(),
                () ->
                    getCodeableConceptLoinc(
                        "101658-3", "Cancer staging after multimodality therapy"),
                () ->
                    getCodeableConceptSnomed(
                        "421755005", "Tumor staging descriptor y (tumor staging)")));

    return members.stream()
        .filter(member -> member.source() != null)
        .map(
            member -> {
              var observation =
                  createTNMBaseResource(
                      member.profile(),
                      idBase + member.identifierSuffix(),
                      member.identifierSystem(),
                      tnmTyp.getVersion(),
                      tnmTyp.getDatum(),
                      patient,
                      primaryConditionReference);
              observation.setCode(member.code().get());
              observation.setValue(member.value().get());
              return observation;
            })
        .toList();
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
          case "c" -> getSnomedCoding("399534004", "cN category (observable entity)");
          case "p" -> getSnomedCoding("371494008", "pN category (observable entity)");
          default -> getSnomedCoding("277206009", "N category (observable entity)");
        };
    return createKategorieCode(coding, cpuPraefix);
  }

  private CodeableConcept createMKategorieCode(String cpuPraefix) {
    var coding =
        switch (cpuPraefix) {
          case "c" -> getSnomedCoding("399387003", "cM category (observable entity)");
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

    return codeableConcept;
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

  private CodeableConcept getObservationMethod(String tnmVersion) {
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
