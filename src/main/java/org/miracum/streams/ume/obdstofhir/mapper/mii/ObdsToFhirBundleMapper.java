package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.DiagnoseTyp;
import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung;
import de.basisdatensatz.obds.v3.OPTyp;
import de.basisdatensatz.obds.v3.PathologieTyp;
import de.basisdatensatz.obds.v3.STTyp;
import de.basisdatensatz.obds.v3.SYSTTyp;
import de.basisdatensatz.obds.v3.TumorkonferenzTyp;
import de.basisdatensatz.obds.v3.VerlaufTyp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ObdsToFhirBundleMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(ObdsToFhirBundleMapper.class);

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
  private final GleasonScoreMapper gleasonScoreMapper;
  private final WeitereKlassifikationMapper weitereKlassifikationMapper;
  private final ErstdiagnoseEvidenzListMapper erstdiagnoseEvidenzListMapper;
  private final NebenwirkungMapper nebenwirkungMapper;

  @Value("${fhir.mappings.createPatientResources.enabled}")
  private boolean createPatientResources;

  @Value("${fhir.mappings.modul.prostata.enabled}")
  private boolean isModulProstataMappingEnabled;

  @Value("${fhir.mappings.metaSource}")
  private String metaSource;

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
      GleasonScoreMapper gleasonScoreMapper,
      WeitereKlassifikationMapper weitereKlassifikationMapper,
      ErstdiagnoseEvidenzListMapper erstdiagnoseEvidenzListMapper,
      NebenwirkungMapper nebenwirkungMapper) {
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
    this.gleasonScoreMapper = gleasonScoreMapper;
    this.weitereKlassifikationMapper = weitereKlassifikationMapper;
    this.erstdiagnoseEvidenzListMapper = erstdiagnoseEvidenzListMapper;
    this.nebenwirkungMapper = nebenwirkungMapper;
  }

  /**
   * Maps a list of OBDS (Onkologischer Basisdatensatz) data to a list of FHIR Bundles.
   *
   * @param groupedObds List of OBDS data structure containing patient and medical information
   * @return List of FHIR Bundles, one for each patient in the input OBDS data
   */
  public List<Bundle> map(List<OBDS> groupedObds) {

    var bundles = new ArrayList<Bundle>();

    for (OBDS obds : groupedObds) {
      bundles.addAll(map(obds));
    }
    return bundles;
  }

  /**
   * Maps a single OBDS (Onkologischer Basisdatensatz) data to a list of FHIR Bundles. For each
   * patient in the OBDS data, creates a transaction bundle containing all their associated medical
   * records.
   *
   * @param obds Single OBDS data structure containing patient and medical information
   * @return List of FHIR Bundles, one for each patient in the input OBDS data
   */
  public List<Bundle> map(OBDS obds) {

    var bundles = new ArrayList<Bundle>();

    for (var obdsPatient : obds.getMengePatient().getPatient()) {
      var bundle = new Bundle();
      bundle.setType(BundleType.TRANSACTION);

      var meldungen = obdsPatient.getMengeMeldung().getMeldung();

      meldungen = sortMeldungen(meldungen);

      // Patient
      // XXX: this assumes that the "meldungen" contains every single Meldung for the
      // patient
      // e.g. in case of Folgepakete, this might not be the case. The main Problem is
      // the Patient.deceased information which is not present in every single Paket.
      var patient = patientMapper.map(obdsPatient, meldungen);
      var patientReference = new Reference("Patient/" + patient.getId());

      // only add patient resource to bundle if active profile is set to patient
      if (createPatientResources) {
        addToBundle(bundle, patient);
      }
      bundle.setId(patient.getId());

      for (var meldung : meldungen) {
        MDC.put("meldungId", meldung.getMeldungID());
        MDC.put("tumorId", meldung.getTumorzuordnung().getTumorID());

        // Diagnose
        // this _always_ creates a Condition resource, even if just the Tumorzuordnung
        // is known
        var condition =
            conditionMapper.map(
                meldung, patientReference, obds.getMeldedatum(), obdsPatient.getPatientID());
        addToBundle(bundle, condition);

        var primaryConditionReference = createReferenceFromResource(condition);

        if (meldung.getDiagnose() != null) {
          var resources =
              mapDiagnose(
                  meldung.getDiagnose(),
                  meldung,
                  obdsPatient.getPatientID(),
                  patientReference,
                  condition);
          addToBundle(bundle, resources);
        }

        // Verlauf
        if (meldung.getVerlauf() != null) {
          var resources =
              mapVerlauf(
                  meldung.getVerlauf(), meldung, patientReference, primaryConditionReference);
          addToBundle(bundle, resources);
        }

        // Systemtherapie
        if (meldung.getSYST() != null) {
          var resources =
              mapSyst(meldung.getSYST(), meldung, patientReference, primaryConditionReference);
          addToBundle(bundle, resources);
        }

        // Strahlenterhapie
        if (meldung.getST() != null) {
          var st = meldung.getST();
          var stProcedure =
              strahlentherapieMapper.map(
                  st, patientReference, primaryConditionReference, meldung.getMeldungID());
          addToBundle(bundle, stProcedure);

          if (st.getModulAllgemein() != null) {
            var allgemein = st.getModulAllgemein();
            if (allgemein.getStudienteilnahme() != null) {
              var studienteilnahmeObservation =
                  studienteilnahmeObservationMapper.map(
                      allgemein,
                      patientReference,
                      primaryConditionReference,
                      meldung.getMeldungID());
              addToBundle(bundle, studienteilnahmeObservation);
            }
          }

          if (st.getNebenwirkungen() != null) {
            // in the list of procedures, find the primary/bracket one by cehcking its
            // profile. It's the one the AdverseEvent should reference.
            var stProfile = fhirProperties.getProfiles().getMiiPrOnkoStrahlentherapie();
            var primaryProcedure =
                stProcedure.stream()
                    .filter(
                        p ->
                            p.getMeta().getProfile().stream()
                                .anyMatch(c -> stProfile.equals(c.getValue())))
                    .findFirst();

            if (primaryProcedure.isPresent()) {
              var stProcedureReference = createReferenceFromResource(primaryProcedure.get());
              var nebenwirkungen =
                  nebenwirkungMapper.map(
                      st.getNebenwirkungen(), patientReference, stProcedureReference, st.getSTID());
              addToBundle(bundle, nebenwirkungen);
            } else {
              LOG.error("Unable to find the primary ST procedure");
            }
          }
        }

        // Tod
        if (meldung.getTod() != null) {
          var deathObservations =
              todMapper.map(
                  meldung.getTod(),
                  meldung.getMeldungID(),
                  patientReference,
                  primaryConditionReference);
          addToBundle(bundle, deathObservations);
        }

        // Operation
        if (meldung.getOP() != null) {
          var resources =
              mapOP(meldung.getOP(), meldung, patientReference, primaryConditionReference);
          addToBundle(bundle, resources);
        }

        // Pathologie
        if (meldung.getPathologie() != null) {
          var resources =
              mapPathologie(
                  meldung.getPathologie(), meldung, patientReference, primaryConditionReference);
          addToBundle(bundle, resources);
        }

        // Tumorkonferenz
        if (meldung.getTumorkonferenz() != null) {
          var tumorkonferenz = meldung.getTumorkonferenz();
          var carePlan =
              tumorkonferenzMapper.map(tumorkonferenz, patientReference, primaryConditionReference);
          addToBundle(bundle, carePlan);
        }

        // add meta.source to all bundle entries if metaSource is not empty
        if (!metaSource.isEmpty()) {
          bundle.getEntry().forEach(entry -> entry.getResource().getMeta().setSource(metaSource));
        }
      }

      bundles.add(bundle);
      MDC.clear();
    }
    return bundles;
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
    // Note: it's important to have this Diagnose sorting at the very end of this method,
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
    if (meldeanlass == null) return 0;
    return meldeanlass.equals(ende) ? 2 : 0; // "ende" at the end, everything else at the front
  }

  private List<Resource> mapDiagnose(
      DiagnoseTyp diagnose,
      Meldung meldung,
      String patientId,
      Reference patientReference,
      Condition primaryCondition) {

    var mappedResources = new ArrayList<Resource>();

    var primaryConditionReference = createReferenceFromResource(primaryCondition);

    if (diagnose.getAllgemeinerLeistungszustand() != null) {
      var leistungszustand =
          leistungszustandMapper.map(
              diagnose.getAllgemeinerLeistungszustand(),
              meldung.getMeldungID(),
              meldung.getTumorzuordnung().getDiagnosedatum(),
              patientReference,
              primaryConditionReference);
      mappedResources.add(leistungszustand);
    }

    if (diagnose.getMengeFM() != null && diagnose.getMengeFM().getFernmetastase() != null) {
      var diagnoseFM =
          fernmetastasenMapper.map(
              diagnose, meldung.getMeldungID(), patientReference, primaryConditionReference);
      mappedResources.addAll(diagnoseFM);
    }

    if (diagnose.getHistologie() != null) {
      var histologie = diagnose.getHistologie();
      var specimen = specimenMapper.map(histologie, patientReference);
      mappedResources.add(specimen);

      var specimenReference = createReferenceFromResource(specimen);

      if (histologie.getGrading() != null) {
        var grading =
            gradingObservationMapper.map(
                histologie,
                meldung.getMeldungID(),
                patientReference,
                primaryConditionReference,
                specimenReference);
        mappedResources.add(grading);
      }

      var lymphknotenuntersuchungen =
          lymphknotenuntersuchungMapper.map(
              histologie, patientReference, primaryConditionReference, specimenReference);
      mappedResources.addAll(lymphknotenuntersuchungen);
    }

    if (diagnose.getModulAllgemein() != null) {
      var allgemein = diagnose.getModulAllgemein();
      if (allgemein.getStudienteilnahme() != null) {
        var studienteilnahmeObservation =
            studienteilnahmeObservationMapper.map(
                allgemein, patientReference, primaryConditionReference, meldung.getMeldungID());
        mappedResources.add(studienteilnahmeObservation);
      }
    }

    if (diagnose.getMengeGenetik() != null
        && diagnose.getMengeGenetik().getGenetischeVariante() != null) {
      var genetischeVarianten =
          genetischeVarianteMapper.map(
              diagnose, patientReference, primaryConditionReference, meldung.getMeldungID());
      mappedResources.addAll(genetischeVarianten);
    }

    if (diagnose.getCTNM() != null) {
      var clinicalTNMObservations =
          tnmMapper.map(
              diagnose.getCTNM(),
              "clinical",
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference);
      mappedResources.addAll(clinicalTNMObservations);
    }

    if (diagnose.getPTNM() != null) {
      var pathologicTNMObservations =
          tnmMapper.map(
              diagnose.getPTNM(),
              "pathologic",
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference);
      mappedResources.addAll(pathologicTNMObservations);
    }

    if (diagnose.getModulProstata() != null && isModulProstataMappingEnabled) {
      var modulProstata = diagnose.getModulProstata();
      if (modulProstata.getGleasonScore() != null) {
        var gleasonScore =
            gleasonScoreMapper.map(
                modulProstata, meldung.getMeldungID(), patientReference, primaryConditionReference);
        mappedResources.add(gleasonScore);
      }
    }

    if (diagnose.getMengeWeitereKlassifikation() != null) {
      var weitereKlassifikation = diagnose.getMengeWeitereKlassifikation();
      if (weitereKlassifikation.getWeitereKlassifikation() != null) {
        var weitereKlassifikationen =
            weitereKlassifikationMapper.map(
                weitereKlassifikation,
                meldung.getMeldungID(),
                patientReference,
                primaryConditionReference);
        mappedResources.addAll(weitereKlassifikationen);
      }
    }

    var evidenzReferenceList =
        mappedResources.stream().map(this::createReferenceFromResource).toList();

    var evidenzListe =
        erstdiagnoseEvidenzListMapper.map(
            patientId,
            meldung.getTumorzuordnung().getTumorID(),
            patientReference,
            evidenzReferenceList);

    // It's not ideal to modify the Condition resource after it's creation in the
    // mapper.
    primaryCondition.addEvidence().addDetail(createReferenceFromResource(evidenzListe));

    mappedResources.add(evidenzListe);

    return mappedResources;
  }

  private List<Resource> mapVerlauf(
      VerlaufTyp verlauf,
      Meldung meldung,
      Reference patientReference,
      Reference primaryConditionReference) {
    var mappedResources = new ArrayList<Resource>();
    var verlaufsObservation =
        verlaufObservationMapper.map(verlauf, patientReference, primaryConditionReference);
    mappedResources.add(verlaufsObservation);

    if (verlauf.getMengeFM() != null && verlauf.getMengeFM().getFernmetastase() != null) {
      var verlaufFM =
          fernmetastasenMapper.map(
              verlauf, meldung.getMeldungID(), patientReference, primaryConditionReference);
      mappedResources.addAll(verlaufFM);
    }

    if (verlauf.getAllgemeinerLeistungszustand() != null) {
      var leistungszustand =
          leistungszustandMapper.map(
              verlauf.getAllgemeinerLeistungszustand(),
              meldung.getMeldungID(),
              verlauf.getUntersuchungsdatumVerlauf(),
              patientReference,
              primaryConditionReference);
      mappedResources.add(leistungszustand);
    }

    if (verlauf.getHistologie() != null) {
      var histologie = verlauf.getHistologie();
      var specimen = specimenMapper.map(verlauf.getHistologie(), patientReference);
      mappedResources.add(specimen);

      var specimenReference = createReferenceFromResource(specimen);

      var verlaufsHistologie =
          verlaufshistologieObservationMapper.map(
              histologie,
              meldung.getMeldungID(),
              patientReference,
              specimenReference,
              primaryConditionReference);

      mappedResources.addAll(verlaufsHistologie);

      if (histologie.getGrading() != null) {
        var grading =
            gradingObservationMapper.map(
                histologie,
                meldung.getMeldungID(),
                patientReference,
                primaryConditionReference,
                specimenReference);
        mappedResources.add(grading);
      }

      var lymphknotenuntersuchungen =
          lymphknotenuntersuchungMapper.map(
              histologie, patientReference, primaryConditionReference, specimenReference);
      mappedResources.addAll(lymphknotenuntersuchungen);
    }

    if (verlauf.getModulAllgemein() != null) {
      var allgemein = verlauf.getModulAllgemein();
      if (allgemein.getStudienteilnahme() != null) {
        var studienteilnahmeObservation =
            studienteilnahmeObservationMapper.map(
                allgemein, patientReference, primaryConditionReference, meldung.getMeldungID());
        mappedResources.add(studienteilnahmeObservation);
      }
    }

    if (verlauf.getMengeGenetik() != null
        && verlauf.getMengeGenetik().getGenetischeVariante() != null) {
      var genetischeVarianten =
          genetischeVarianteMapper.map(
              verlauf, patientReference, primaryConditionReference, meldung.getMeldungID());
      mappedResources.addAll(genetischeVarianten);
    }

    if (verlauf.getTNM() != null) {
      var tnmObservations =
          tnmMapper.map(
              verlauf.getTNM(),
              "generic",
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference);
      mappedResources.addAll(tnmObservations);
    }

    if (verlauf.getModulProstata() != null && isModulProstataMappingEnabled) {
      var modulProstata = verlauf.getModulProstata();
      if (modulProstata.getGleasonScore() != null) {
        var gleasonScore =
            gleasonScoreMapper.map(
                modulProstata, meldung.getMeldungID(), patientReference, primaryConditionReference);
        mappedResources.add(gleasonScore);
      }
    }

    if (verlauf.getMengeWeitereKlassifikation() != null) {
      var weitereKlassifikation = verlauf.getMengeWeitereKlassifikation();
      if (weitereKlassifikation.getWeitereKlassifikation() != null) {
        var weitereKlassifikationen =
            weitereKlassifikationMapper.map(
                weitereKlassifikation,
                meldung.getMeldungID(),
                patientReference,
                primaryConditionReference);
        mappedResources.addAll(weitereKlassifikationen);
      }
    }

    return mappedResources;
  }

  private List<Resource> mapSyst(
      SYSTTyp syst,
      Meldung meldung,
      Reference patientReference,
      Reference primaryConditionReference) {
    var mappedResources = new ArrayList<Resource>();

    var systProcedure = systemischeTherapieProcedureMapper.map(syst, patientReference);
    mappedResources.add(systProcedure);

    var procedureReference = createReferenceFromResource(systProcedure);

    if (syst.getMengeSubstanz() != null) {
      var systMedicationStatements =
          systemischeTherapieMedicationStatementMapper.map(
              syst, patientReference, procedureReference);
      mappedResources.addAll(systMedicationStatements);
    }

    if (syst.getModulAllgemein() != null) {
      var allgemein = syst.getModulAllgemein();
      if (allgemein.getStudienteilnahme() != null) {
        var studienteilnahmeObservation =
            studienteilnahmeObservationMapper.map(
                allgemein, patientReference, primaryConditionReference, meldung.getMeldungID());
        mappedResources.add(studienteilnahmeObservation);
      }
    }

    if (syst.getNebenwirkungen() != null) {
      var nebenwirkungen =
          nebenwirkungMapper.map(
              syst.getNebenwirkungen(), patientReference, procedureReference, syst.getSYSTID());
      mappedResources.addAll(nebenwirkungen);
    }

    return mappedResources;
  }

  private List<Resource> mapOP(
      OPTyp op, Meldung meldung, Reference patientReference, Reference primaryConditionReference) {
    var mappedResources = new ArrayList<Resource>();
    var operations = operationMapper.map(op, patientReference, primaryConditionReference);
    mappedResources.addAll(operations);

    if (op.getResidualstatus() != null
        && op.getResidualstatus().getGesamtbeurteilungResidualstatus() != null) {
      var residualstatus =
          residualstatusMapper.map(op, patientReference, primaryConditionReference);
      mappedResources.add(residualstatus);
    }

    if (op.getHistologie() != null) {
      var histologie = op.getHistologie();
      var specimen = specimenMapper.map(op.getHistologie(), patientReference);
      mappedResources.add(specimen);

      var specimenReference = createReferenceFromResource(specimen);

      var verlaufsHistologie =
          verlaufshistologieObservationMapper.map(
              histologie,
              meldung.getMeldungID(),
              patientReference,
              specimenReference,
              primaryConditionReference);

      mappedResources.addAll(verlaufsHistologie);

      if (histologie.getGrading() != null) {
        var grading =
            gradingObservationMapper.map(
                histologie,
                meldung.getMeldungID(),
                patientReference,
                primaryConditionReference,
                specimenReference);
        mappedResources.add(grading);
      }

      var lymphknotenuntersuchungen =
          lymphknotenuntersuchungMapper.map(
              histologie, patientReference, primaryConditionReference, specimenReference);
      mappedResources.addAll(lymphknotenuntersuchungen);
    }

    if (op.getModulAllgemein() != null) {
      var allgemein = op.getModulAllgemein();
      if (allgemein.getStudienteilnahme() != null) {
        var studienteilnahmeObservation =
            studienteilnahmeObservationMapper.map(
                allgemein, patientReference, primaryConditionReference, meldung.getMeldungID());
        mappedResources.add(studienteilnahmeObservation);
      }
    }

    if (op.getMengeGenetik() != null && op.getMengeGenetik().getGenetischeVariante() != null) {
      var genetischeVarianten =
          genetischeVarianteMapper.map(
              op, patientReference, primaryConditionReference, meldung.getMeldungID());
      mappedResources.addAll(genetischeVarianten);
    }

    if (op.getTNM() != null) {
      var tnmObservations =
          tnmMapper.map(
              op.getTNM(),
              "generic",
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference);
      mappedResources.addAll(tnmObservations);
    }

    if (op.getModulProstata() != null && isModulProstataMappingEnabled) {
      var modulProstata = op.getModulProstata();
      if (modulProstata.getGleasonScore() != null) {
        var gleasonScore =
            gleasonScoreMapper.map(
                modulProstata,
                meldung.getMeldungID(),
                patientReference,
                primaryConditionReference,
                op.getDatum());
        mappedResources.add(gleasonScore);
      }
    }

    return mappedResources;
  }

  private List<Resource> mapPathologie(
      PathologieTyp pathologie,
      Meldung meldung,
      Reference patientReference,
      Reference primaryConditionReference) {
    var mappedResources = new ArrayList<Resource>();
    var specimenReference = new Reference();

    if (pathologie.getHistologie() != null) {
      var histologie = pathologie.getHistologie();
      var specimen = specimenMapper.map(pathologie.getHistologie(), patientReference);

      mappedResources.add(specimen);

      specimenReference = createReferenceFromResource(specimen);

      var verlaufsHistologie =
          verlaufshistologieObservationMapper.map(
              histologie,
              meldung.getMeldungID(),
              patientReference,
              specimenReference,
              primaryConditionReference);

      mappedResources.addAll(verlaufsHistologie);

      if (histologie.getGrading() != null) {
        var grading =
            gradingObservationMapper.map(
                histologie,
                meldung.getMeldungID(),
                patientReference,
                primaryConditionReference,
                specimenReference);
        mappedResources.add(grading);
      }

      var lymphknotenuntersuchungen =
          lymphknotenuntersuchungMapper.map(
              histologie, patientReference, primaryConditionReference, specimenReference);
      mappedResources.addAll(lymphknotenuntersuchungen);
    }

    if (pathologie.getMengeFM() != null && pathologie.getMengeFM().getFernmetastase() != null) {
      var pathologieFM =
          fernmetastasenMapper.map(
              pathologie, meldung.getMeldungID(), patientReference, primaryConditionReference);
      mappedResources.addAll(pathologieFM);
    }

    if (pathologie.getMengeGenetik() != null
        && pathologie.getMengeGenetik().getGenetischeVariante() != null) {
      var genetischeVarianten =
          genetischeVarianteMapper.map(
              pathologie, patientReference, primaryConditionReference, meldung.getMeldungID());
      mappedResources.addAll(genetischeVarianten);
    }

    if (pathologie.getCTNM() != null) {
      var clinicalTNMObservations =
          tnmMapper.map(
              pathologie.getCTNM(),
              "clinical",
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference);
      mappedResources.addAll(clinicalTNMObservations);
    }

    if (pathologie.getPTNM() != null) {
      var pathologicTNMObservations =
          tnmMapper.map(
              pathologie.getPTNM(),
              "pathologic",
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference);
      mappedResources.addAll(pathologicTNMObservations);
    }

    if (pathologie.getModulProstata() != null && isModulProstataMappingEnabled) {
      var modulProstata = pathologie.getModulProstata();
      if (modulProstata.getGleasonScore() != null) {
        var gleasonScore =
            gleasonScoreMapper.map(
                modulProstata, meldung.getMeldungID(), patientReference, primaryConditionReference);
        mappedResources.add(gleasonScore);
      }
    }

    if (pathologie.getMengeWeitereKlassifikation() != null) {
      var weitereKlassifikation = pathologie.getMengeWeitereKlassifikation();
      if (weitereKlassifikation.getWeitereKlassifikation() != null) {
        var weitereKlassifikationen =
            weitereKlassifikationMapper.map(
                weitereKlassifikation,
                meldung.getMeldungID(),
                patientReference,
                primaryConditionReference);
        mappedResources.addAll(weitereKlassifikationen);
      }
    }

    // XXX: it doesn't seem possible to reference the CarePlan/Tumorkonferenz
    // the histologiebefund is based on. Unless every befund is always a
    // result of a conference. So maybe remove the reference entirely.
    var report =
        histologiebefundMapper.map(
            meldung.getPathologie(),
            meldung.getMeldungID(),
            patientReference,
            null,
            specimenReference);

    mappedResources.add(report);

    return mappedResources;
  }

  private Bundle addToBundle(Bundle bundle, List<? extends Resource> resources) {
    for (var resource : resources) {
      addToBundle(bundle, resource);
    }
    return bundle;
  }

  private Bundle addToBundle(Bundle bundle, Resource resource) {
    var url = String.format("%s/%s", resource.getResourceType(), resource.getIdBase());
    // if a resource entry already exists, it will be replaced.
    // this should only be necessary for the Condition resource,
    // which can be created from the TumorzuordnungTyp present in all kinds of
    // Meldungen.
    var duplicateEntries =
        bundle.getEntry().stream().filter(entry -> entry.getFullUrl().equals(url)).toList();

    if (duplicateEntries.size() > 1) {
      throw new IllegalStateException("Multiple entries found in bundle matching URL: " + url);
    }

    if (!duplicateEntries.isEmpty()) {
      var duplicateEntry = duplicateEntries.getFirst();
      if (duplicateEntry.getResource().getResourceType() != ResourceType.Condition) {
        LOG.warn(
            "Duplicate entry found in bundle with URL {} and profile {}. "
                + "This should only happen for Condition resources.",
            url,
            resource.getMeta().getProfile().stream().map(p -> p.getValue()).toList());
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

  private Reference createReferenceFromResource(Resource resource) {
    Objects.requireNonNull(resource.getId());
    return new Reference(resource.getResourceType().name() + "/" + resource.getId());
  }
}
