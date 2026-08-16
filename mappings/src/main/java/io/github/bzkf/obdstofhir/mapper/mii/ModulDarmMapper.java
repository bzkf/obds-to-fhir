package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.ModulDarmTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.dizuker.tofhir.FhirExtensions.DataAbsentReason;
import io.github.dizuker.tofhir.IdUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hl7.fhir.r4.model.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Maps the oBDS {@code Modul_Darm} (Kolorektales Karzinom) to the corresponding MII Onkologie FHIR
 * profiles.
 *
 * <p>Not every field of {@code Modul_Darm_Typ} has a corresponding profile in the MII IG Onkologie
 * yet: {@code RektumQualitaetTME} is modeled as {@code Specimen.condition} on the histology
 * specimen and isn't produced here, and {@code ArtEingriff} has no defined target profile. {@code
 * RASMutation} is expressed via the generic {@code MII_PR_Onko_Genetische_Variante} Observation
 * profile, the same one {@link GenetischeVarianteMapper} uses for {@code
 * Menge_Genetik.Genetische_Variante}.
 */
@Service
public class ModulDarmMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(ModulDarmMapper.class);

  protected ModulDarmMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Resource> map(
      @NonNull ModulDarmTyp modulDarm, @NonNull String meldungId, @NonNull Reference patient) {
    return map(modulDarm, meldungId, patient, null, null, null);
  }

  public List<Resource> map(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition) {
    return map(modulDarm, meldungId, patient, condition, null, null);
  }

  public List<Resource> map(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable XMLGregorianCalendar referenzDatum) {
    return map(modulDarm, meldungId, patient, condition, referenzDatum, null);
  }

  public List<Resource> map(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable XMLGregorianCalendar referenzDatum,
      @Nullable Reference op) {
    verifyReference(patient, ResourceType.Patient);
    if (condition != null) {
      verifyReference(condition, ResourceType.Condition);
    }
    if (op != null) {
      verifyReference(op, ResourceType.Procedure);
    }

    var results = new ArrayList<Resource>();
    var effective = convertObdsDatumToDateTimeType(referenzDatum).orElse(null);

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
      results.add(
          mapAnastomoseninsuffizienz(modulDarm, meldungId, patient, condition, effective, op));
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

    if (modulDarm.getArtEingriff() != null) {
      LOG.debug("ArtEingriff has no corresponding FHIR profile in the MII IG Onkologie yet.");
    }

    if (modulDarm.getRASMutation() != null) {
      results.add(mapRasMutation(modulDarm, meldungId, patient, condition, effective));
    }

    return results;
  }

  /**
   * Zahl_3stellig_Typ fields (e.g. {@code RektumAbstandAnokutanlinie}) allow either a 1-3 digit
   * number or the literal {@code "U"} (unbekannt), so a quantity value can't always be derived.
   */
  private static Quantity parseZahl3stelligOrAbsent(
      @NonNull String rawValue, @NonNull String unit) {
    if ("U".equals(rawValue)) {
      var valueQuantity = new Quantity();
      valueQuantity.addExtension(DataAbsentReason.unknown());
      return valueQuantity;
    }

    return new Quantity()
        .setValue(new BigDecimal(rawValue))
        .setUnit(unit)
        .setSystem("http://unitsofmeasure.org")
        .setCode(unit);
  }

  private static Observation createBaseObservation(
      @NonNull Reference patient, @Nullable Reference condition) {
    var observation = new Observation();
    observation.setSubject(patient);
    observation.setStatus(Observation.ObservationStatus.FINAL);
    if (condition != null) {
      observation.addFocus(condition);
    }

    return observation;
  }

  private Observation mapAbstandAnokutanlinie(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable DateTimeType effective) {
    Objects.requireNonNull(modulDarm.getRektumAbstandAnokutanlinie());
    var observation = createBaseObservation(patient, condition);
    observation.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoKrkAbstandAnokutan());

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties.getSystems().getIdentifiers().getKrkAbstandAnokutanObservationId())
            .setValue(slugifier.slugify(meldungId + "-modul-darm-abstand-anokutan"));
    observation.addIdentifier(identifier);
    observation.setId(IdUtils.fromIdentifier(identifier));

    if (effective != null) {
      observation.setEffective(effective);
    }

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("33748-5")
                .setDisplay("Distance from anal verge")));

    observation.setValue(
        parseZahl3stelligOrAbsent(modulDarm.getRektumAbstandAnokutanlinie(), "cm"));

    return observation;
  }

  private Observation mapAbstandAboral(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable DateTimeType effective) {
    Objects.requireNonNull(modulDarm.getRektumAbstandAboralerResektionsrand());
    var observation = createBaseObservation(patient, condition);
    observation.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoKrkAbstandAboral());

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties.getSystems().getIdentifiers().getKrkAbstandAboralObservationId())
            .setValue(slugifier.slugify(meldungId + "-modul-darm-abstand-aboral"));
    observation.addIdentifier(identifier);
    observation.setId(IdUtils.fromIdentifier(identifier));

    if (effective != null) {
      observation.setEffective(effective);
    }

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

    observation.setValue(
        parseZahl3stelligOrAbsent(modulDarm.getRektumAbstandAboralerResektionsrand(), "mm"));

    return observation;
  }

  private Observation mapAbstandCircumferelleResektionsebene(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable DateTimeType effective) {
    Objects.requireNonNull(modulDarm.getRektumAbstandCircResektionsebene());
    var observation = createBaseObservation(patient, condition);
    observation
        .getMeta()
        .addProfile(Onkologie.Profiles.miiPrOnkoKrkAbstandCircumferelleResektionsebene());

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties
                    .getSystems()
                    .getIdentifiers()
                    .getKrkAbstandCircumferelleResektionsebeneObservationId())
            .setValue(
                slugifier.slugify(meldungId + "-modul-darm-abstand-circumferelle-resektionsebene"));
    observation.addIdentifier(identifier);
    observation.setId(IdUtils.fromIdentifier(identifier));

    if (effective != null) {
      observation.setEffective(effective);
    }

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

    observation.setValue(
        parseZahl3stelligOrAbsent(modulDarm.getRektumAbstandCircResektionsebene(), "mm"));

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
    var observation = createBaseObservation(patient, condition);
    observation.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoKrkMrtMesorektaleFaszie());

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties
                    .getSystems()
                    .getIdentifiers()
                    .getKrkMrtMesorektaleFaszieObservationId())
            .setValue(slugifier.slugify(meldungId + "-modul-darm-mrt-mesorektale-faszie"));
    observation.addIdentifier(identifier);
    observation.setId(IdUtils.fromIdentifier(identifier));

    if (effective != null) {
      observation.setEffective(effective);
    }

    observation.setCode(
        new CodeableConcept(new Coding("http://radlex.org", "RDE96", "Distance to MRF")));

    if (rawValue.matches("\\d+")) {
      var valueQuantity =
          new Quantity()
              .setValue(new BigDecimal(rawValue))
              .setUnit("mm")
              .setSystem("http://unitsofmeasure.org")
              .setCode("mm");
      observation.setValue(valueQuantity);
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
      @Nullable Reference condition,
      @Nullable DateTimeType effective,
      @Nullable Reference op) {
    Objects.requireNonNull(modulDarm.getGradRektumAnastomoseninsuffizienz());
    var observation = createBaseObservation(patient, condition);
    observation.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoKrkAnastomoseninsuffizienz());

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties
                    .getSystems()
                    .getIdentifiers()
                    .getKrkAnastomoseninsuffizienzObservationId())
            .setValue(slugifier.slugify(meldungId + "-modul-darm-anastomoseninsuffizienz"));
    observation.addIdentifier(identifier);
    observation.setId(IdUtils.fromIdentifier(identifier));

    if (effective != null) {
      observation.setEffective(effective);
    }

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

    if (op != null) {
      observation.addFocus(op);
    } else {
      LOG.warn("OP reference is null for Anastomoseninsuffizienz observation");
    }

    return observation;
  }

  private Observation mapAsaKlassifikation(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable DateTimeType effective) {
    Objects.requireNonNull(modulDarm.getASA());
    var observation = createBaseObservation(patient, condition);
    observation.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoAsaKlassifikation());

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties.getSystems().getIdentifiers().getAsaKlassifikationObservationId())
            .setValue(slugifier.slugify(meldungId + "-modul-darm-asa-klassifikation"));
    observation.addIdentifier(identifier);
    observation.setId(IdUtils.fromIdentifier(identifier));

    if (effective != null) {
      observation.setEffective(effective);
    }

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("97816-3")
                .setDisplay("American society of anesthesiologists morbidity state")));

    var coding =
        Onkologie.CodeSystems.MiiCsOnkoAsaObds.fromValueOrThrow(modulDarm.getASA()).coding();
    observation.setValue(new CodeableConcept(coding));

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

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties.getSystems().getIdentifiers().getKrkStomaMarkierungProcedureId())
            .setValue(slugifier.slugify(meldungId + "-modul-darm-stoma-markierung"));
    procedure.addIdentifier(identifier);
    procedure.setId(IdUtils.fromIdentifier(identifier));

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
      case "D" -> procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
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

    if (effective != null) {
      procedure.setPerformed(effective);
    }

    return procedure;
  }

  private Observation mapRasMutation(
      @NonNull ModulDarmTyp modulDarm,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @Nullable Reference condition,
      @Nullable DateTimeType effective) {
    var rasMutation = modulDarm.getRASMutation();
    Objects.requireNonNull(rasMutation);
    var observation = createBaseObservation(patient, condition);
    observation.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoGenetischeVariante());

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties.getSystems().getIdentifiers().getGenetischeVarianteObservationId())
            .setValue(slugifier.slugify(meldungId + "-modul-darm-ras-mutation"));
    observation.addIdentifier(identifier);
    observation.setId(IdUtils.fromIdentifier(identifier));

    if (effective != null) {
      observation.setEffective(effective);
    }

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .loinc()
                .setCode("69548-6")
                .setDisplay("Genetic variant assessment")));

    observation.addCategory(
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getObservationCategory())
                .setCode("laboratory")
                .setDisplay("Laboratory")));
    observation.addCategory(
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getDiagnosticServiceSection())
                .setCode("GE")
                .setDisplay("Genetics")));

    observation.addNote().setText("RAS-Mutation");

    // RASMutationTyp (W/M/U/N) uses the same codes as
    // MiiCsOnkoGenetischeVarianteAuspraegung, N (Nicht untersucht) maps to N (Nicht
    // bestimmbar).
    var interpretation =
        Onkologie.CodeSystems.MiiCsOnkoGenetischeVarianteAuspraegung.fromValueOrThrow(
                rasMutation.value())
            .coding();
    observation.addInterpretation(new CodeableConcept(interpretation));

    return observation;
  }
}
