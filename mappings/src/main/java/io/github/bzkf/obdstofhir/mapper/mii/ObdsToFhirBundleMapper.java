package io.github.bzkf.obdstofhir.mapper.mii;

import ca.uhn.fhir.context.FhirContext;
import com.github.difflib.text.DiffRow.Tag;
import com.github.difflib.text.DiffRowGenerator;
import de.basisdatensatz.obds.v3.DiagnoseTyp;
import de.basisdatensatz.obds.v3.HistologieTyp;
import de.basisdatensatz.obds.v3.MengeFMTyp;
import de.basisdatensatz.obds.v3.MengeGenetikTyp;
import de.basisdatensatz.obds.v3.MengeWeitereKlassifikationTyp;
import de.basisdatensatz.obds.v3.ModulAllgemeinTyp;
import de.basisdatensatz.obds.v3.ModulDarmTyp;
import de.basisdatensatz.obds.v3.ModulMalignesMelanomTyp;
import de.basisdatensatz.obds.v3.ModulMammaTyp;
import de.basisdatensatz.obds.v3.ModulProstataTyp;
import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung;
import de.basisdatensatz.obds.v3.OPTyp;
import de.basisdatensatz.obds.v3.PathologieTyp;
import de.basisdatensatz.obds.v3.STTyp;
import de.basisdatensatz.obds.v3.SYSTTyp;
import de.basisdatensatz.obds.v3.TNMTyp;
import de.basisdatensatz.obds.v3.TumorkonferenzTyp;
import de.basisdatensatz.obds.v3.VerlaufTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.bzkf.obdstofhir.mapper.ProvenanceMapper;
import io.github.bzkf.obdstofhir.mapper.mii.TNMMapper.TnmType;
import io.github.bzkf.obdstofhir.model.PatientLookupResult;
import io.github.dizuker.tofhir.ReferenceUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ObdsToFhirBundleMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(ObdsToFhirBundleMapper.class);
  private static final FhirContext fhirContext = FhirContext.forR4();

  private final PatientMapper patientMapper;
  private final ConditionMapper conditionMapper;
  private final FernmetastasenMapper fernmetastasenMapper;
  private final GradingObservationMapper gradingObservationMapper;
  private final HistologiebefundMapper histologiebefundMapper;
  private final LeistungszustandMapper leistungszustandMapper;
  private final LymphknotenuntersuchungMapper lymphknotenuntersuchungMapper;
  private final OperationMapper operationMapper;
  private final ResidualstatusMapper residualstatusMapper;
  private final SpecimenMapper specimenMapper;
  private final StrahlentherapieMapper strahlentherapieMapper;
  private final SystemischeTherapieMedicationStatementMapper
      systemischeTherapieMedicationStatementMapper;
  private final SystemischeTherapieProcedureMapper systemischeTherapieProcedureMapper;
  private final TodMapper todMapper;
  private final VerlaufshistologieObservationMapper verlaufshistologieObservationMapper;
  private final StudienteilnahmeObservationMapper studienteilnahmeObservationMapper;
  private final VerlaufObservationMapper verlaufObservationMapper;
  private final GenetischeVarianteMapper genetischeVarianteMapper;
  private final TumorkonferenzMapper tumorkonferenzMapper;
  private final TNMMapper tnmMapper;
  private final ModulProstataMapper modulProstataMapper;
  private final ModulDarmMapper modulDarmMapper;
  private final ModulMammaMapper modulMammaMapper;
  private final ModulMalignesMelanomMapper modulMalignesMelanomMapper;
  private final WeitereKlassifikationMapper weitereKlassifikationMapper;
  private final ErstdiagnoseEvidenzListMapper erstdiagnoseEvidenzListMapper;
  private final NebenwirkungMapper nebenwirkungMapper;
  private final FruehereTumorerkrankungenMapper fruehereTumorErkrankungenMapper;
  private final ProvenanceMapper provenanceMapper;

  @Value("${fhir.mappings.create-patient-resources.enabled}")
  private boolean createPatientResources;

  @Value("${fhir.mappings.create-patient-resources.if-already-exists:false}")
  private boolean createPatientIfAlreadyExists;

  @Value("${fhir.mappings.skip-patient-if-it-does-not-already-exist:false}")
  private boolean skipPatientIfItDoesNotAlreadyExist;

  @Value("${fhir.mappings.create-provenance-resources.enabled}")
  private boolean createProvenanceResources;

  @Value("${fhir.mappings.meta.source}")
  private String metaSource;

  @Value("${fhir.mappings.patient-id-regex}")
  private Pattern patientIdRegex;

  private final Function<OBDS.MengePatient.Patient, PatientLookupResult> patientReferenceGenerator;

  public ObdsToFhirBundleMapper(
      FhirProperties fhirProperties,
      PatientMapper patientMapper,
      ConditionMapper conditionMapper,
      SystemischeTherapieProcedureMapper systemischeTherapieProcedureMapper,
      SystemischeTherapieMedicationStatementMapper systemischeTherapieMedicationStatementMapper,
      StrahlentherapieMapper strahlentherapieMapper,
      TodMapper todMapper,
      LeistungszustandMapper leistungszustandMapper,
      OperationMapper operationMapper,
      ResidualstatusMapper residualstatusMapper,
      FernmetastasenMapper fernmetastasenMapper,
      GradingObservationMapper gradingObservationMapper,
      HistologiebefundMapper histologiebefundMapper,
      LymphknotenuntersuchungMapper lymphknotenuntersuchungMapper,
      SpecimenMapper specimenMapper,
      VerlaufshistologieObservationMapper verlaufshistologieObservationMapper,
      StudienteilnahmeObservationMapper studienteilnahmeObservationMapper,
      VerlaufObservationMapper verlaufObservationMapper,
      GenetischeVarianteMapper genetischeVarianteMapper,
      TumorkonferenzMapper tumorkonferenzMapper,
      TNMMapper tnmMapper,
      ModulProstataMapper modulProstataMapper,
      ModulDarmMapper modulDarmMapper,
      ModulMammaMapper modulMammaMapper,
      ModulMalignesMelanomMapper modulMalignesMelanomMapper,
      WeitereKlassifikationMapper weitereKlassifikationMapper,
      ErstdiagnoseEvidenzListMapper erstdiagnoseEvidenzListMapper,
      NebenwirkungMapper nebenwirkungMapper,
      FruehereTumorerkrankungenMapper fruehereTumorErkrankungenMapper,
      ProvenanceMapper provenanceMapper,
      Function<OBDS.MengePatient.Patient, PatientLookupResult> patientReferenceGenerator) {
    super(fhirProperties);
    this.patientMapper = patientMapper;
    this.conditionMapper = conditionMapper;
    this.systemischeTherapieProcedureMapper = systemischeTherapieProcedureMapper;
    this.systemischeTherapieMedicationStatementMapper =
        systemischeTherapieMedicationStatementMapper;
    this.strahlentherapieMapper = strahlentherapieMapper;
    this.todMapper = todMapper;
    this.leistungszustandMapper = leistungszustandMapper;
    this.operationMapper = operationMapper;
    this.residualstatusMapper = residualstatusMapper;
    this.fernmetastasenMapper = fernmetastasenMapper;
    this.gradingObservationMapper = gradingObservationMapper;
    this.histologiebefundMapper = histologiebefundMapper;
    this.lymphknotenuntersuchungMapper = lymphknotenuntersuchungMapper;
    this.specimenMapper = specimenMapper;
    this.verlaufshistologieObservationMapper = verlaufshistologieObservationMapper;
    this.studienteilnahmeObservationMapper = studienteilnahmeObservationMapper;
    this.verlaufObservationMapper = verlaufObservationMapper;
    this.genetischeVarianteMapper = genetischeVarianteMapper;
    this.tumorkonferenzMapper = tumorkonferenzMapper;
    this.tnmMapper = tnmMapper;
    this.modulProstataMapper = modulProstataMapper;
    this.modulDarmMapper = modulDarmMapper;
    this.modulMammaMapper = modulMammaMapper;
    this.modulMalignesMelanomMapper = modulMalignesMelanomMapper;
    this.weitereKlassifikationMapper = weitereKlassifikationMapper;
    this.erstdiagnoseEvidenzListMapper = erstdiagnoseEvidenzListMapper;
    this.nebenwirkungMapper = nebenwirkungMapper;
    this.fruehereTumorErkrankungenMapper = fruehereTumorErkrankungenMapper;
    this.provenanceMapper = provenanceMapper;
    this.patientReferenceGenerator = patientReferenceGenerator;
  }

  public List<BundleMapperResult> map(List<OBDS> groupedObds) {
    var results = new ArrayList<BundleMapperResult>();
    for (OBDS obds : groupedObds) {
      results.addAll(map(obds));
    }
    return results;
  }

  public List<BundleMapperResult> map(OBDS obds) {

    var results = new ArrayList<BundleMapperResult>();

    for (var obdsPatient : obds.getMengePatient().getPatient()) {
      var bundle = new Bundle();
      bundle.setType(BundleType.TRANSACTION);
      var bundleProvenances = new ArrayList<Provenance>();

      if (!patientIdRegex.pattern().isBlank()) {
        var matcher = patientIdRegex.matcher(obdsPatient.getPatientID().trim());
        if (matcher.matches()) {
          var patientId = matcher.group(1);
          LOG.debug(
              "Patient_ID {} matched {} as {}",
              obdsPatient.getPatientID(),
              patientIdRegex,
              patientId);
          // mutating the obdsPatient is probably not ideal
          obdsPatient.setPatientID(patientId);
        } else {
          LOG.debug("Patient_ID {} failed to match {}", obdsPatient.getPatientID(), patientIdRegex);
          throw new IllegalArgumentException(
              String.format("Regex %s failed to match the Patient_ID", patientIdRegex));
        }
      }

      var meldungen = obdsPatient.getMengeMeldung().getMeldung();

      meldungen = sortMeldungen(meldungen);

      // detection only, to find out whether this occurs in practice before mapping
      // RektumQualitaetTME to the Specimen
      for (var finding : TmeSpecimenOverwriteDetector.detect(meldungen)) {
        LOG.warn(
            "RektumQualitaetTME overwrite risk: the Specimen of Histologie_ID {} is created from"
                + " multiple Meldungen, but the one processed last ({}) has no TME quality, so a"
                + " TME quality on the Specimen would be lost. Meldungen in processing order: [{}]",
            finding.histologieId(),
            finding.last().meldungId(),
            finding.describeSections());
      }

      // Patient
      // XXX: this assumes that the "meldungen" contains every single Meldung for the
      // patient
      // e.g. in case of Folgepakete, this might not be the case. The main Problem is
      // the Patient.deceased information which is not present in every single Paket.
      var patient = patientMapper.map(obdsPatient, meldungen);

      var patientLookupResult = patientReferenceGenerator.apply(obdsPatient);

      var patientReference = patientLookupResult.reference();

      if (skipPatientIfItDoesNotAlreadyExist
          && !patientLookupResult.existsOnServer()
          && !patientReference.hasReference()) {
        LOG.warn(
            "Patient does not exist on server and skipPatientIfItDoesNotAlreadyExist is true, skipping this patient");
        LOG.debug("Skipped Patient_ID: {}", obdsPatient.getPatientID());
        continue;
      }

      var patientAddedToBundle = false;
      if (createPatientResources
          && (!patientLookupResult.existsOnServer() || createPatientIfAlreadyExists)) {
        if (patientLookupResult.existsOnServer() && createPatientIfAlreadyExists) {
          // very hacky extra handling for one specific site requirements.
          // set the Patient.id to the already referenced one. This is necessary,
          // because in the Vitalstatus processor we reference the patient by this
          // id.
          patient.setId(patientReference.getReferenceElement().getIdPart());
          if (patientReference.hasIdentifier()) {
            // really hacky, at least make sure that Patient only has one identifier.
            if (patient.getIdentifier().size() > 1) {
              throw new IllegalStateException(
                  "Patient has more than one identifier, cannot set the identifier value to the record id from database");
            }
            patient.getIdentifierFirstRep().setValue(patientReference.getIdentifier().getValue());
          }
        } else if (!patientLookupResult.existsOnServer() && !patientReference.hasReference()) {
          // patient was not found on server; update patientReference to point at
          // the locally-created Patient resource so downstream mappers can use it
          patientReference.setReference("Patient/" + patient.getId());
        }

        addToBundle(bundle, patient);
        patientAddedToBundle = true;
      }

      bundle.setId(patient.getId());

      var isFirstMeldung = true;
      for (var meldung : meldungen) {
        MDC.put("meldungId", meldung.getMeldungID());
        MDC.put("tumorId", meldung.getTumorzuordnung().getTumorID());

        var resourcesMappedFromMeldung = new ArrayList<Resource>();

        // Diagnose
        // this _always_ creates a Condition resource, even if just the Tumorzuordnung
        // is known
        var condition =
            conditionMapper.map(
                meldung, patientReference, obds.getMeldedatum(), obdsPatient.getPatientID());
        resourcesMappedFromMeldung.add(condition);

        var primaryConditionReference = ReferenceUtils.createReferenceTo(condition);
        var context =
            new MeldungContext(meldung.getMeldungID(), patientReference, primaryConditionReference);

        if (meldung.getDiagnose() != null) {
          var resources =
              mapDiagnose(
                  meldung.getDiagnose(),
                  meldung,
                  obdsPatient.getPatientID(),
                  condition,
                  obds.getMeldedatum(),
                  context);
          resourcesMappedFromMeldung.addAll(resources);
        }

        // Verlauf
        if (meldung.getVerlauf() != null) {
          resourcesMappedFromMeldung.addAll(mapVerlauf(meldung.getVerlauf(), context));
        }

        // Systemtherapie
        if (meldung.getSYST() != null) {
          resourcesMappedFromMeldung.addAll(mapSyst(meldung.getSYST(), context));
        }

        // Strahlenterhapie
        if (meldung.getST() != null) {
          resourcesMappedFromMeldung.addAll(mapSt(meldung.getST(), context));
        }

        // Tod
        if (meldung.getTod() != null) {
          var deathObservations =
              todMapper.map(
                  meldung.getTod(),
                  patientReference,
                  primaryConditionReference,
                  meldung.getTumorzuordnung().getTumorID(),
                  false);
          resourcesMappedFromMeldung.addAll(deathObservations);
        }

        // Operation
        if (meldung.getOP() != null) {
          resourcesMappedFromMeldung.addAll(mapOP(meldung.getOP(), context));
        }

        // Pathologie
        if (meldung.getPathologie() != null) {
          resourcesMappedFromMeldung.addAll(mapPathologie(meldung.getPathologie(), context));
        }

        // Tumorkonferenz
        if (meldung.getTumorkonferenz() != null) {
          var tumorkonferenz = meldung.getTumorkonferenz();
          var carePlan =
              tumorkonferenzMapper.map(tumorkonferenz, patientReference, primaryConditionReference);
          resourcesMappedFromMeldung.add(carePlan);
        }

        addToBundle(bundle, resourcesMappedFromMeldung);

        if (this.createProvenanceResources) {
          // the items in resourcesMappedFromMeldung are processed in the same order that
          // they were added to the list initially.
          var targets = new ArrayList<>(resourcesMappedFromMeldung);
          // The Patient sits outside the per-Meldung loop; include it in the first
          // Meldung's Provenance only to avoid repeating it across all Provenances.
          if (isFirstMeldung && patientAddedToBundle) {
            targets.addFirst(patient);
          }
          bundleProvenances.add(
              provenanceMapper.map(
                  targets.stream().map(ReferenceUtils::createReferenceTo).toList(),
                  meldung.getMeldungID()));
        }
        isFirstMeldung = false;
      }

      results.add(new BundleMapperResult(bundle, List.copyOf(bundleProvenances)));
      // only remove our own keys, the caller may have set others, e.g. the file name
      MDC.remove("meldungId");
      MDC.remove("tumorId");
    }
    return results;
  }

  private static List<Meldung> sortMeldungen(List<Meldung> meldungen) {
    var clonedMeldungen = new ArrayList<>(meldungen);

    // if a list of Meldungen contains either a ST or SYST with the same ID,
    // but one for the behandlungsende and one for the behandlungsbeginn,
    // we want to make sure the one describing the behandlungsende
    // is processed later than the begin.
    // simple solution: just push them to the end of the list.
    Function<Meldung, Integer> priority =
        m -> {
          if (m.getST() != null) {
            return prioritiseMeldeanlass(
                m.getST().getMeldeanlass(), STTyp.Meldeanlass.BEHANDLUNGSENDE);
          }
          if (m.getSYST() != null) {
            return prioritiseMeldeanlass(
                m.getSYST().getMeldeanlass(), SYSTTyp.Meldeanlass.BEHANDLUNGSENDE);
          }
          if (m.getTumorkonferenz() != null) {
            return prioritiseMeldeanlass(
                m.getTumorkonferenz().getMeldeanlass(),
                TumorkonferenzTyp.Meldeanlass.BEHANDLUNGSENDE,
                TumorkonferenzTyp.Meldeanlass.STATUSAENDERUNG);
          }
          if (m.getVerlauf() != null) {
            return prioritiseMeldeanlass(
                m.getVerlauf().getMeldeanlass(), VerlaufTyp.Meldeanlass.STATUSAENDERUNG);
          }
          return 0; // No meldeanlass -> normal priority
        };

    clonedMeldungen.sort(Comparator.comparing(priority));

    // Meldungen are ordered in a way such that the Meldungen with the Diagnose
    // element present are always the last ones in the list, thus overriding
    // any incomplete resources that were constructed from just the Tumorzuordung
    // Overriding happens based on the Resource ID in `addToBundle`.
    // this pushes meldungen where the getDiagnose() != null to the end
    // Note: it's important to have this Diagnose sorting at the very end of this
    // method,
    // so all diagnose meldungen are indeed at the very end.
    clonedMeldungen.sort(Comparator.comparing(m -> m.getDiagnose() != null ? 1 : 0));

    return clonedMeldungen;
  }

  private static <E extends Enum<E>> int prioritiseMeldeanlass(
      E meldeanlass, E ende, E statusaenderung) {
    if (meldeanlass == null) return 0;
    if (meldeanlass.equals(ende)) return 2; // highest priority -> at the end
    if (meldeanlass.equals(statusaenderung)) return 1; // middle priority
    return 0; // normal
  }

  private static <E extends Enum<E>> int prioritiseMeldeanlass(E meldeanlass, E ende) {
    return prioritiseMeldeanlass(meldeanlass, ende, null);
  }

  /** What every section of a Meldung is mapped with. */
  private record MeldungContext(String meldungId, Reference patient, Reference condition) {}

  /** The modules a section of a Meldung may contain. */
  private record Modules(
      @Nullable ModulProstataTyp prostata,
      @Nullable ModulDarmTyp darm,
      @Nullable ModulMammaTyp mamma,
      @Nullable ModulMalignesMelanomTyp melanom) {}

  /**
   * Maps the Histologie/Specimen/Grading/Lymphknotenuntersuchung resources shared by Diagnose,
   * Verlauf, OP and Pathologie. {@code includeVerlaufshistologie} is {@code false} only for
   * Diagnose, which doesn't produce a VerlaufshistologieObservation.
   *
   * @return a reference to the created Specimen, or {@code null} if no Histologie is present
   */
  private @Nullable Reference mapHistologie(
      @Nullable HistologieTyp histologie,
      boolean includeVerlaufshistologie,
      MeldungContext context,
      List<Resource> mappedResources) {
    if (histologie == null) {
      return null;
    }

    var specimen = specimenMapper.map(histologie, context.patient(), context.meldungId());
    mappedResources.add(specimen);

    var specimenReference = ReferenceUtils.createReferenceTo(specimen);

    if (includeVerlaufshistologie) {
      mappedResources.addAll(
          verlaufshistologieObservationMapper.map(
              histologie,
              context.meldungId(),
              context.patient(),
              specimenReference,
              context.condition()));
    }

    if (histologie.getGrading() != null) {
      mappedResources.add(
          gradingObservationMapper.map(
              histologie,
              context.meldungId(),
              context.patient(),
              context.condition(),
              specimenReference));
    }

    mappedResources.addAll(
        lymphknotenuntersuchungMapper.map(
            histologie,
            context.patient(),
            context.condition(),
            specimenReference,
            context.meldungId()));

    return specimenReference;
  }

  /** Maps the Studienteilnahme observation shared by Diagnose, Verlauf, SYST, ST and OP. */
  private void mapStudienteilnahme(
      @Nullable ModulAllgemeinTyp allgemein,
      MeldungContext context,
      List<Resource> mappedResources) {
    if (allgemein == null || allgemein.getStudienteilnahme() == null) {
      return;
    }

    mappedResources.add(
        studienteilnahmeObservationMapper.map(
            allgemein, context.patient(), context.condition(), context.meldungId()));
  }

  /** Maps the WeitereKlassifikation resources shared by Diagnose, Verlauf and Pathologie. */
  private void mapWeitereKlassifikationen(
      @Nullable MengeWeitereKlassifikationTyp menge,
      MeldungContext context,
      List<Resource> mappedResources) {
    if (menge == null) {
      return;
    }

    mappedResources.addAll(
        weitereKlassifikationMapper.map(
            menge, context.meldungId(), context.patient(), context.condition()));
  }

  /** Maps the Fernmetastasen shared by Diagnose, Verlauf and Pathologie. */
  private void mapFernmetastasen(
      @Nullable MengeFMTyp mengeFM,
      String section,
      MeldungContext context,
      List<Resource> mappedResources) {
    if (mengeFM == null) {
      return;
    }

    mappedResources.addAll(
        fernmetastasenMapper.map(
            mengeFM, section, context.meldungId(), context.patient(), context.condition()));
  }

  /** Maps the Genetische_Variante elements shared by Diagnose, Verlauf, OP and Pathologie. */
  private void mapGenetischeVarianten(
      @Nullable MengeGenetikTyp mengeGenetik,
      String section,
      MeldungContext context,
      List<Resource> mappedResources) {
    if (mengeGenetik == null) {
      return;
    }

    mappedResources.addAll(
        genetischeVarianteMapper.map(
            mengeGenetik, section, context.patient(), context.condition(), context.meldungId()));
  }

  /** Maps a TNM shared by Diagnose, Verlauf, OP and Pathologie, if present. */
  private void mapTnm(
      @Nullable TNMTyp tnm,
      TnmType tnmType,
      @Nullable HistologieTyp histologie,
      MeldungContext context,
      List<Resource> mappedResources) {
    if (tnm == null) {
      return;
    }

    mappedResources.addAll(
        tnmMapper.map(
            tnm,
            tnmType,
            context.meldungId(),
            context.patient(),
            context.condition(),
            histologie != null ? histologie.getHistologieID() : null));
  }

  /**
   * Maps the modules shared by Diagnose, Verlauf, OP and Pathologie.
   *
   * @param effective the date of the section containing the modules
   * @param op the parent OP Procedure, only available for the OP section
   * @param specimen the Specimen of the section's Histologie, if there is one
   */
  private void mapModules(
      Modules modules,
      @Nullable DateTimeType effective,
      @Nullable Reference op,
      @Nullable Reference specimen,
      MeldungContext context,
      List<Resource> mappedResources) {
    if (modules.prostata() != null) {
      mappedResources.addAll(
          modulProstataMapper.map(
              modules.prostata(),
              context.meldungId(),
              context.patient(),
              context.condition(),
              effective,
              op));
    }

    if (modules.darm() != null) {
      mappedResources.addAll(
          modulDarmMapper.map(
              modules.darm(),
              context.meldungId(),
              context.patient(),
              context.condition(),
              effective,
              op,
              specimen));
    }

    if (modules.mamma() != null) {
      mappedResources.addAll(
          modulMammaMapper.map(
              modules.mamma(),
              context.meldungId(),
              context.patient(),
              context.condition(),
              effective));
    }

    if (modules.melanom() != null) {
      mappedResources.addAll(
          modulMalignesMelanomMapper.map(
              modules.melanom(),
              context.meldungId(),
              context.patient(),
              context.condition(),
              effective));
    }
  }

  private List<Resource> mapDiagnose(
      DiagnoseTyp diagnose,
      Meldung meldung,
      String patientId,
      Condition primaryCondition,
      XMLGregorianCalendar meldedatum,
      MeldungContext context) {

    var mappedResources = new ArrayList<Resource>();

    if (diagnose.getAllgemeinerLeistungszustand() != null) {
      mappedResources.addAll(
          leistungszustandMapper.map(
              diagnose.getAllgemeinerLeistungszustand(),
              context.meldungId(),
              meldung.getTumorzuordnung().getDiagnosedatum(),
              context.patient(),
              context.condition()));
    }

    mapFernmetastasen(diagnose.getMengeFM(), "Diagnose", context, mappedResources);

    var specimenReference =
        mapHistologie(diagnose.getHistologie(), false, context, mappedResources);

    mapStudienteilnahme(diagnose.getModulAllgemein(), context, mappedResources);

    mapGenetischeVarianten(diagnose.getMengeGenetik(), "Diagnose", context, mappedResources);

    mapTnm(
        diagnose.getCTNM(), TnmType.CLINICAL, diagnose.getHistologie(), context, mappedResources);
    mapTnm(
        diagnose.getPTNM(), TnmType.PATHOLOGIC, diagnose.getHistologie(), context, mappedResources);

    // keeps the Datumsgenauigkeit of the Diagnosedatum (month, year, ...)
    var effective =
        convertObdsDatumToDateTimeType(meldung.getTumorzuordnung().getDiagnosedatum()).orElse(null);

    mapModules(
        new Modules(
            diagnose.getModulProstata(),
            diagnose.getModulDarm(),
            diagnose.getModulMamma(),
            diagnose.getModulMalignesMelanom()),
        effective,
        null,
        specimenReference,
        context,
        mappedResources);

    mapWeitereKlassifikationen(diagnose.getMengeWeitereKlassifikation(), context, mappedResources);

    var evidenzReferenceList =
        mappedResources.stream()
            .filter(r -> r instanceof Observation || r instanceof DiagnosticReport)
            .map(ReferenceUtils::createReferenceTo)
            .toList();

    var evidenzListe =
        erstdiagnoseEvidenzListMapper.map(
            patientId,
            meldung.getTumorzuordnung().getTumorID(),
            context.patient(),
            evidenzReferenceList);

    // It's not ideal to modify the Condition resource after it's creation in the
    // mapper.
    primaryCondition.addEvidence().addDetail(ReferenceUtils.createReferenceTo(evidenzListe));

    mappedResources.add(evidenzListe);

    // we map these after the evidenz list since it may only contain
    // Observation/DiagnosticReport
    // resources.
    if (diagnose.getMengeFruehereTumorerkrankung() != null) {
      var fruehereTumorErkrankungen =
          fruehereTumorErkrankungenMapper.map(
              diagnose.getMengeFruehereTumorerkrankung(),
              context.patient(),
              primaryCondition.getIdentifierFirstRep(),
              meldedatum);
      mappedResources.addAll(fruehereTumorErkrankungen);

      var fruehereTumorerkrankungenExtensions =
          fruehereTumorErkrankungen.stream()
              .map(ReferenceUtils::createReferenceTo)
              .map(
                  reference ->
                      new Extension(
                          fhirProperties.getExtensions().getConditionOccurredFollowing(),
                          reference))
              .toList();

      // again, not great to modify the resource after its creation.
      // maybe we should use the FruehereTumorerkrankungen mapper inside
      // the ConditionMapper
      for (var extension : fruehereTumorerkrankungenExtensions) {
        primaryCondition.addExtension(extension);
      }
    }

    return mappedResources;
  }

  private List<Resource> mapVerlauf(VerlaufTyp verlauf, MeldungContext context) {
    var mappedResources = new ArrayList<Resource>();
    mappedResources.add(
        verlaufObservationMapper.map(verlauf, context.patient(), context.condition()));

    mapFernmetastasen(verlauf.getMengeFM(), "Verlauf", context, mappedResources);

    if (verlauf.getAllgemeinerLeistungszustand() != null) {
      mappedResources.addAll(
          leistungszustandMapper.map(
              verlauf.getAllgemeinerLeistungszustand(),
              context.meldungId(),
              verlauf.getUntersuchungsdatumVerlauf(),
              context.patient(),
              context.condition()));
    }

    var specimenReference = mapHistologie(verlauf.getHistologie(), true, context, mappedResources);

    mapStudienteilnahme(verlauf.getModulAllgemein(), context, mappedResources);

    mapGenetischeVarianten(verlauf.getMengeGenetik(), "Verlauf", context, mappedResources);

    mapTnm(verlauf.getTNM(), TnmType.GENERIC, verlauf.getHistologie(), context, mappedResources);

    var effective =
        convertObdsDatumToDateTimeType(verlauf.getUntersuchungsdatumVerlauf()).orElse(null);

    mapModules(
        new Modules(
            verlauf.getModulProstata(),
            verlauf.getModulDarm(),
            verlauf.getModulMamma(),
            verlauf.getModulMalignesMelanom()),
        effective,
        null,
        specimenReference,
        context,
        mappedResources);

    mapWeitereKlassifikationen(verlauf.getMengeWeitereKlassifikation(), context, mappedResources);

    return mappedResources;
  }

  private List<Resource> mapSyst(SYSTTyp syst, MeldungContext context) {
    var mappedResources = new ArrayList<Resource>();

    var systProcedure =
        systemischeTherapieProcedureMapper.map(syst, context.patient(), context.condition());
    mappedResources.add(systProcedure);

    var procedureReference = ReferenceUtils.createReferenceTo(systProcedure);

    if (syst.getMengeSubstanz() != null) {
      var results =
          systemischeTherapieMedicationStatementMapper.map(
              syst, context.patient(), procedureReference, context.condition());
      for (var mapResults : results) {
        mappedResources.add(mapResults.medicationStatement());
        if (mapResults.medication().isPresent()) {
          mappedResources.add(mapResults.medication().get());
        }
      }
    }

    mapStudienteilnahme(syst.getModulAllgemein(), context, mappedResources);

    if (syst.getNebenwirkungen() != null) {
      mappedResources.addAll(
          nebenwirkungMapper.map(
              syst.getNebenwirkungen(), context.patient(), procedureReference, syst.getSYSTID()));
    }

    return mappedResources;
  }

  private List<Resource> mapSt(STTyp st, MeldungContext context) {
    var mappedResources = new ArrayList<Resource>();

    var stProcedures =
        strahlentherapieMapper.map(st, context.patient(), context.condition(), context.meldungId());
    mappedResources.addAll(stProcedures);

    mapStudienteilnahme(st.getModulAllgemein(), context, mappedResources);

    if (st.getNebenwirkungen() != null) {
      // in the list of procedures, find the primary/bracket one by cehcking its
      // profile. It's the one the AdverseEvent should reference.
      var stProfile = Onkologie.Profiles.miiPrOnkoStrahlentherapie();
      var primaryProcedure =
          stProcedures.stream()
              .filter(
                  p ->
                      p.getMeta().getProfile().stream()
                          .anyMatch(c -> stProfile.equals(c.getValue())))
              .findFirst();

      if (primaryProcedure.isPresent()) {
        mappedResources.addAll(
            nebenwirkungMapper.map(
                st.getNebenwirkungen(),
                context.patient(),
                ReferenceUtils.createReferenceTo(primaryProcedure.get()),
                st.getSTID()));
      } else {
        LOG.error("Unable to find the primary ST procedure");
      }
    }

    return mappedResources;
  }

  private List<Resource> mapOP(OPTyp op, MeldungContext context) {
    var mappedResources = new ArrayList<Resource>();
    var operations = operationMapper.map(op, context.patient(), context.condition());
    mappedResources.addAll(operations);

    if (op.getResidualstatus() != null
        && op.getResidualstatus().getGesamtbeurteilungResidualstatus() != null) {
      mappedResources.add(residualstatusMapper.map(op, context.patient(), context.condition()));
    }

    var specimenReference = mapHistologie(op.getHistologie(), true, context, mappedResources);

    mapStudienteilnahme(op.getModulAllgemein(), context, mappedResources);

    mapGenetischeVarianten(op.getMengeGenetik(), "OP", context, mappedResources);

    mapTnm(op.getTNM(), TnmType.GENERIC, op.getHistologie(), context, mappedResources);

    mapModules(
        new Modules(
            op.getModulProstata(),
            op.getModulDarm(),
            op.getModulMamma(),
            op.getModulMalignesMelanom()),
        convertObdsDatumToDateTimeType(op.getDatum()).orElse(null),
        // the first Procedure is always the parent one
        ReferenceUtils.createReferenceTo(operations.getFirst()),
        specimenReference,
        context,
        mappedResources);

    return mappedResources;
  }

  private List<Resource> mapPathologie(PathologieTyp pathologie, MeldungContext context) {
    var mappedResources = new ArrayList<Resource>();

    var specimenReference =
        mapHistologie(pathologie.getHistologie(), true, context, mappedResources);

    mapFernmetastasen(pathologie.getMengeFM(), "Pathologie", context, mappedResources);

    mapGenetischeVarianten(pathologie.getMengeGenetik(), "Pathologie", context, mappedResources);

    mapTnm(
        pathologie.getCTNM(),
        TnmType.CLINICAL,
        pathologie.getHistologie(),
        context,
        mappedResources);
    mapTnm(
        pathologie.getPTNM(),
        TnmType.PATHOLOGIC,
        pathologie.getHistologie(),
        context,
        mappedResources);

    if (pathologie.getModulProstata() != null) {
      mappedResources.addAll(
          modulProstataMapper.map(
              pathologie.getModulProstata(),
              context.meldungId(),
              context.patient(),
              context.condition()));
    }

    // the Pathologie section has no date of its own; the histology date is the closest
    var effective =
        Optional.ofNullable(pathologie.getHistologie())
            .flatMap(h -> convertObdsDatumToDateTimeType(h.getTumorHistologiedatum()))
            .orElse(null);

    mapModules(
        new Modules(
            null,
            pathologie.getModulDarm(),
            pathologie.getModulMamma(),
            pathologie.getModulMalignesMelanom()),
        effective,
        null,
        specimenReference,
        context,
        mappedResources);

    mapWeitereKlassifikationen(
        pathologie.getMengeWeitereKlassifikation(), context, mappedResources);

    // XXX: it doesn't seem possible to reference the CarePlan/Tumorkonferenz
    // the histologiebefund is based on. Unless every befund is always a
    // result of a conference. So maybe remove the reference entirely.
    mappedResources.add(
        histologiebefundMapper.map(
            pathologie, context.meldungId(), context.patient(), null, specimenReference));

    return mappedResources;
  }

  private Bundle addToBundle(Bundle bundle, List<? extends Resource> resources) {
    for (var resource : resources) {
      addToBundle(bundle, resource);
    }
    return bundle;
  }

  private Bundle addToBundle(Bundle bundle, Resource resource) {
    // add meta.source to all bundle entries if metaSource is not empty
    // XXX: we moved this here so the meta.source is always set before the diff is
    // ran.
    // Previously, it was set in the individual mappers, but then the diff
    // would always show a difference in meta.source
    if (!metaSource.isEmpty()) {
      resource.getMeta().setSource(metaSource);
    }

    var url = String.format("%s/%s", resource.getResourceType(), resource.getIdBase());
    // if a resource entry already exists, it will be replaced.
    // this should only be necessary for the Condition resource,
    // which can be created from the TumorzuordnungTyp present in all kinds of
    // Meldungen.
    var duplicateEntries =
        bundle.getEntry().stream().filter(entry -> entry.getFullUrl().equals(url)).toList();

    if (duplicateEntries.size() > 1) {
      throw new IllegalStateException(
          "More than one duplicate entry found in bundle matching URL: " + url);
    }

    if (!duplicateEntries.isEmpty()) {
      var duplicateEntry = duplicateEntries.getFirst();
      var existingResource = duplicateEntry.getResource();
      if (existingResource.getResourceType() == ResourceType.Condition) {
        LOG.debug(
            "Overwriting duplicate entry in bundle with URL {} and profile {}. "
                + "This is fine for Condition resources.",
            url,
            resource.getMeta().getProfile().stream().map(p -> p.getValue()).toList());
      } else {
        if (!existingResource.equalsDeep(resource)) {
          var parser = fhirContext.newJsonParser().setPrettyPrint(true);
          var existing = Arrays.asList(parser.encodeResourceToString(existingResource).split("\n"));
          var updated = Arrays.asList(parser.encodeResourceToString(resource).split("\n"));

          var generator =
              DiffRowGenerator.create()
                  .showInlineDiffs(true)
                  .inlineDiffByWord(true)
                  .oldTag(f -> "~")
                  .newTag(f -> "**")
                  .build();

          var rows = generator.generateDiffRows(existing, updated);

          var sj = new StringJoiner("\n");
          sj.add("|original|new|");
          sj.add("|--------|---|");
          for (var row : rows) {
            if (row.getTag() != Tag.EQUAL) {
              sj.add("|" + row.getOldLine() + "|" + row.getNewLine() + "|");
            }
          }

          // add a newline to the end for better readability/copy-pasting from the logs
          sj.add("");

          LOG.warn(
              "Overwriting non-identical, duplicate entry in bundle with URL {} and profile {}. Diff:\n{}",
              url,
              resource.getMeta().getProfile().stream().map(p -> p.getValue()).toList(),
              sj);

        } else {
          LOG.debug(
              "Duplicate entry in bundle with URL {} and profile {} is identical.",
              url,
              resource.getMeta().getProfile().stream().map(p -> p.getValue()).toList());
        }
      }

      duplicateEntry.setResource(resource);
    } else {
      bundle
          .addEntry()
          .setFullUrl(url)
          .setResource(resource)
          .getRequest()
          .setMethod(HTTPVerb.PUT)
          .setUrl(url);
    }
    return bundle;
  }
}
