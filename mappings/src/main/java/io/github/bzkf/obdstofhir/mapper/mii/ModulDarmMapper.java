package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.ModulDarmTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.ObservationCategory;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Maps the oBDS {@code Modul_Darm} (Kolorektales Karzinom) to the corresponding MII Onkologie FHIR
 * profiles.
 *
 * <p>Not every field of {@code Modul_Darm_Typ} is mapped here:
 *
 * <ul>
 *   <li>{@code ArtEingriff} (KR6) is mapped by {@link OperationMapper} as the {@code
 *       MII_EX_Onko_Operation_Urgency} extension of the OP Procedure. Outside of an OP Meldung
 *       there is no Procedure to attach it to.
 *   <li>{@code RektumQualitaetTME} (KR4) targets {@code Specimen.condition} of the {@code
 *       MII_PR_Onko_KRK_Specimen} profile and isn't produced here.
 * </ul>
 *
 * <p>{@code RASMutation} is expressed via the generic {@code MII_PR_Onko_Genetische_Variante}
 * Observation profile, the same one {@link GenetischeVarianteMapper} uses for {@code
 * Menge_Genetik.Genetische_Variante}.
 *
 * <p>The {@code RektumAbstand*}/{@code Anokutanlinie} distance fields additionally allow the
 * literal {@code "U"} (unbekannt) instead of a number. Since the corresponding profiles require
 * {@code valueQuantity.value/unit/system/code} whenever a value is present, those Observations are
 * emitted with {@code Observation.dataAbsentReason} set instead of {@code value[x]}.
 */
@Service
public class ModulDarmMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(ModulDarmMapper.class);

  private record LoincAnswer(String code, String display) {}

  /** The oBDS ASA codes (1-5) as LOINC answers, from the MII_CM_Onko_ASA_oBDS_LOINC ConceptMap. */
  private static final Map<String, LoincAnswer> ASA_LOINC_ANSWERS =
      Map.of(
          "1", new LoincAnswer("LA28813-6", "ASA I"),
          "2", new LoincAnswer("LA28814-4", "ASA II"),
          "3", new LoincAnswer("LA28815-1", "ASA III"),
          "4", new LoincAnswer("LA28816-9", "ASA IV"),
          "5", new LoincAnswer("LA28817-7", "ASA V"));

  protected ModulDarmMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  /**
   * @param effective the date of the section {@code Modul_Darm} is part of, e.g. the OP date
   * @param op the (parent) OP Procedure. Only available in an OP Meldung. Used as the focus of the
   *     Anastomoseninsuffizienz.
   * @param specimen the Specimen of the section's Histologie, if there is one. Used as the specimen
   *     of the RAS-Mutation.
   */
  public List<Resource> map(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable DateTimeType effective,
      @Nullable Reference op,
      @Nullable Reference specimen) {
    verifyReference(patient, ResourceType.Patient);
    if (condition != null) {
      verifyReference(condition, ResourceType.Condition);
    }
    if (op != null) {
      verifyReference(op, ResourceType.Procedure);
    }
    if (specimen != null) {
      verifyReference(specimen, ResourceType.Specimen);
    }

    var results = new ArrayList<Resource>();

    if (modulDarm.getRektumAbstandAnokutanlinie() != null) {
      results.add(mapAbstandAnokutanlinie(modulDarm, meldungId, patient, condition, effective));
    }

    if (modulDarm.getRektumAbstandAboralerResektionsrand() != null) {
      results.add(mapAbstandAboral(modulDarm, meldungId, patient, condition, effective));
    }

    if (modulDarm.getRektumAbstandCircResektionsebene() != null) {
      results.add(
          mapAbstandCircumferelleResektionsebene(
              modulDarm, meldungId, patient, condition, effective));
    }

    if (modulDarm.getRektumMRTDuennschichtAngabemesorektaleFaszie() != null) {
      results.add(mapMrtMesorektaleFaszie(modulDarm, meldungId, patient, condition, effective));
    }

    if (modulDarm.getGradRektumAnastomoseninsuffizienz() != null) {
      results.add(mapAnastomoseninsuffizienz(modulDarm, meldungId, patient, effective, op));
    }

    if (modulDarm.getASA() != null) {
      results.add(mapAsaKlassifikation(modulDarm, meldungId, patient, condition, effective));
    }

    if (modulDarm.getRektumAnzeichnungStomaposition() != null) {
      results.add(mapStomaMarkierung(modulDarm, meldungId, patient, condition, effective));
    }

    if (modulDarm.getRektumQualitaetTME() != null) {
      LOG.debug(
          "RektumQualitaetTME is not mapped, as it targets Specimen.condition, which is out of"
              + " scope for this mapper.");
    }

    if (modulDarm.getArtEingriff() != null && op == null) {
      LOG.debug(
          "ArtEingriff is only mapped as part of an OP Meldung, as an extension of the OP"
              + " Procedure.");
    }

    if (modulDarm.getRASMutation() != null) {
      results.add(mapRasMutation(modulDarm, meldungId, patient, condition, effective, specimen));
    }

    return results;
  }

  /**
   * Zahl_3stellig_Typ fields (e.g. {@code RektumAbstandAnokutanlinie}) allow either a 1-3 digit
   * number or the literal {@code "U"} (unbekannt), so a quantity value can't always be derived.
   * Since the corresponding profiles require {@code valueQuantity.value/unit/system/code} whenever
   * {@code value[x]} is present, "U" is expressed via {@code Observation.dataAbsentReason} instead
   * of a partially populated quantity.
   */
  private void setDistanceValue(
      @NonNull Observation observation, @NonNull String rawValue, @NonNull String unit) {
    if ("U".equals(rawValue)) {
      observation.setDataAbsentReason(dataAbsentReason(Enumerations.DataAbsentReason.UNKNOWN));
      return;
    }

    observation.setValue(ucumQuantity(new BigDecimal(rawValue), unit));
  }

  private Observation mapAbstandAnokutanlinie(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable DateTimeType effective) {
    Objects.requireNonNull(modulDarm.getRektumAbstandAnokutanlinie());
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoKrkAbstandAnokutan(),
            fhirProperties.getSystems().getIdentifiers().getKrkAbstandAnokutanObservationId(),
            meldungId + "-modul-darm-abstand-anokutan",
            patient,
            condition,
            effective);

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("33748-5")
                .setDisplay("Distance from anal verge")));

    setDistanceValue(observation, modulDarm.getRektumAbstandAnokutanlinie(), "cm");

    return observation;
  }

  private Observation mapAbstandAboral(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable DateTimeType effective) {
    Objects.requireNonNull(modulDarm.getRektumAbstandAboralerResektionsrand());
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoKrkAbstandAboral(),
            fhirProperties.getSystems().getIdentifiers().getKrkAbstandAboralObservationId(),
            meldungId + "-modul-darm-abstand-aboral",
            patient,
            condition,
            effective);

    // a measurement on the resected specimen, i.e. by the pathology
    observation.addCategory(observationCategory(ObservationCategory.LABORATORY));

    // the oBDS field doesn't distinguish macroscopic/microscopic assessment, so we
    // default to the macroscopy code, matching the official MII example.
    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("81175-2")
                .setDisplay(
                    "Distance of tumor from closest margin [Length] in Specimen by Macroscopy")));

    setDistanceValue(observation, modulDarm.getRektumAbstandAboralerResektionsrand(), "mm");

    return observation;
  }

  private Observation mapAbstandCircumferelleResektionsebene(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable DateTimeType effective) {
    Objects.requireNonNull(modulDarm.getRektumAbstandCircResektionsebene());
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoKrkAbstandCircumferelleResektionsebene(),
            fhirProperties
                .getSystems()
                .getIdentifiers()
                .getKrkAbstandCircumferelleResektionsebeneObservationId(),
            meldungId + "-modul-darm-abstand-circumferelle-resektionsebene",
            patient,
            condition,
            effective);

    // a measurement on the resected specimen, i.e. by the pathology
    observation.addCategory(observationCategory(ObservationCategory.LABORATORY));

    // the oBDS field doesn't distinguish macroscopic/microscopic assessment, so we
    // default to the macroscopy code, matching the official MII example.
    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("81176-0")
                .setDisplay(
                    "Distance of tumor from circumferential resection margin [Length] in Specimen"
                        + " by Macroscopy")));

    setDistanceValue(observation, modulDarm.getRektumAbstandCircResektionsebene(), "mm");

    return observation;
  }

  private Observation mapMrtMesorektaleFaszie(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable DateTimeType effective) {
    var rawValue = modulDarm.getRektumMRTDuennschichtAngabemesorektaleFaszie();
    Objects.requireNonNull(rawValue);
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoKrkMrtMesorektaleFaszie(),
            fhirProperties.getSystems().getIdentifiers().getKrkMrtMesorektaleFaszieObservationId(),
            meldungId + "-modul-darm-mrt-mesorektale-faszie",
            patient,
            condition,
            effective);

    observation.addCategory(observationCategory(ObservationCategory.IMAGING));

    observation.setCode(
        new CodeableConcept(new Coding("http://radlex.org", "RDE96", "Distance to MRF")));

    if (rawValue.matches("\\d+")) {
      observation.setValue(ucumQuantity(new BigDecimal(rawValue), "mm"));
    } else {
      var coding =
          Onkologie.CodeSystems.MiiCsOnkoKrkMrtMesorektaleFaszieStatus.fromValueOrThrow(rawValue)
              .coding();
      observation.setValue(new CodeableConcept(coding));
    }

    return observation;
  }

  private Observation mapAnastomoseninsuffizienz(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable DateTimeType effective,
      @Nullable Reference op) {
    Objects.requireNonNull(modulDarm.getGradRektumAnastomoseninsuffizienz());
    // the profile only allows the (KRK) OP Procedure as the focus, not the Condition. Outside of
    // an OP Meldung there's no Procedure to reference, so the Observation isn't linked to the
    // tumor at all.
    if (op == null) {
      LOG.debug(
          "No OP Procedure available for the Anastomoseninsuffizienz Observation. Leaving its"
              + " focus unset.");
    }
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoKrkAnastomoseninsuffizienz(),
            fhirProperties
                .getSystems()
                .getIdentifiers()
                .getKrkAnastomoseninsuffizienzObservationId(),
            meldungId + "-modul-darm-anastomoseninsuffizienz",
            patient,
            op,
            effective);

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("236091002")
                .setDisplay("Large intestine anastomotic leak (disorder)")));

    var coding =
        Onkologie.CodeSystems.MiiCsOnkoKrkAnastomoseninsuffizienz.fromValueOrThrow(
                modulDarm.getGradRektumAnastomoseninsuffizienz())
            .coding();
    observation.setValue(new CodeableConcept(coding));

    return observation;
  }

  private Observation mapAsaKlassifikation(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable DateTimeType effective) {
    Objects.requireNonNull(modulDarm.getASA());
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoAsaKlassifikation(),
            fhirProperties.getSystems().getIdentifiers().getAsaKlassifikationObservationId(),
            meldungId + "-modul-darm-asa-klassifikation",
            patient,
            condition,
            effective);

    // a scored assessment, like the ECOG performance status
    observation.addCategory(observationCategory(ObservationCategory.SURVEY));

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("97816-3")
                .setDisplay("American society of anesthesiologists morbidity state")));

    var value =
        new CodeableConcept(
            Onkologie.CodeSystems.MiiCsOnkoAsaObds.fromValueOrThrow(modulDarm.getASA()).coding());

    var loincAnswer = ASA_LOINC_ANSWERS.get(modulDarm.getASA());
    if (loincAnswer != null) {
      value.addCoding(
          fhirProperties
              .getCodings()
              .loinc()
              .setCode(loincAnswer.code())
              .setDisplay(loincAnswer.display()));
    }

    observation.setValue(value);

    return observation;
  }

  private Procedure mapStomaMarkierung(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable DateTimeType effective) {
    var rawValue = modulDarm.getRektumAnzeichnungStomaposition();
    Objects.requireNonNull(rawValue);
    var procedure = new Procedure();
    procedure.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoKrkStomaMarkierung());
    procedure.setSubject(patient);
    if (condition != null) {
      procedure.addReasonReference(condition);
    }

    setIdentifierAndId(
        procedure,
        fhirProperties.getSystems().getIdentifiers().getKrkStomaMarkierungProcedureId(),
        meldungId + "-modul-darm-stoma-markierung");

    procedure.setCategory(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("387713003")
                .setDisplay("Surgical procedure")));

    procedure.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("225134005")
                .setDisplay("Marking position of planned stoma site (procedure)")));

    // the oBDS code only documents whether/how the marking was done, so we derive
    // status/statusReason from it, based on the MII_CM_Onko_KRK_Stoma_oBDS_SCT ConceptMap.
    switch (rawValue) {
      case "D" -> {
        procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
        // the date of the marking itself isn't documented, the date of the section (e.g. the
        // OP date) is the closest. Only set if the marking was actually done.
        if (effective != null) {
          procedure.setPerformed(effective);
        }
      }
      case "N" -> {
        procedure.setStatus(Procedure.ProcedureStatus.NOTDONE);
        procedure.setStatusReason(
            new CodeableConcept(
                fhirProperties
                    .getCodings()
                    .snomed()
                    .setCode("262008008")
                    .setDisplay("Not performed (qualifier value)")));
      }
      case "K" -> {
        procedure.setStatus(Procedure.ProcedureStatus.NOTDONE);
        procedure.setStatusReason(
            new CodeableConcept(
                fhirProperties
                    .getCodings()
                    .snomed()
                    .setCode("428119001")
                    .setDisplay("Procedure not indicated (situation)")));
      }
      case "S" -> {
        procedure.setStatus(Procedure.ProcedureStatus.UNKNOWN);
        procedure.setStatusReason(
            new CodeableConcept(
                fhirProperties
                    .getCodings()
                    .snomed()
                    .setCode("373068000")
                    .setDisplay("Undetermined (qualifier value)")));
      }
      default -> {
        procedure.setStatus(Procedure.ProcedureStatus.UNKNOWN);
        procedure.setStatusReason(
            new CodeableConcept(
                fhirProperties.getCodings().snomed().setCode("261665006").setDisplay("Unknown")));
      }
    }

    return procedure;
  }

  private Observation mapRasMutation(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable DateTimeType effective,
      @Nullable Reference specimen) {
    var rasMutation = modulDarm.getRASMutation();
    Objects.requireNonNull(rasMutation);
    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoGenetischeVariante(),
            fhirProperties.getSystems().getIdentifiers().getGenetischeVarianteObservationId(),
            meldungId + "-modul-darm-ras-mutation",
            patient,
            condition,
            effective);

    if (specimen != null) {
      observation.setSpecimen(specimen);
    }

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("69548-6")
                .setDisplay("Genetic variant assessment")));

    observation.addCategory(observationCategory(ObservationCategory.LABORATORY));
    observation.addCategory(
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getDiagnosticServiceSection())
                .setCode("GE")
                .setDisplay("Genetics")));

    observation.addNote().setText("RAS-Mutation");

    if ("N".equals(rasMutation.value())) {
      // in the oBDS, N means "Nicht untersucht", while N in the
      // MiiCsOnkoGenetischeVarianteAuspraegung means "Nicht bestimmbar", i.e. tested but
      // without a result. So we don't set an interpretation and mark the test as not performed.
      observation.setDataAbsentReason(dataAbsentReason(Enumerations.DataAbsentReason.NOTPERFORMED));
    } else {
      // the remaining RASMutationTyp codes (W/M/U) have the same meaning in the
      // MiiCsOnkoGenetischeVarianteAuspraegung
      var interpretation =
          Onkologie.CodeSystems.MiiCsOnkoGenetischeVarianteAuspraegung.fromValueOrThrow(
                  rasMutation.value())
              .coding();
      observation.addInterpretation(new CodeableConcept(interpretation));
    }

    return observation;
  }
}
