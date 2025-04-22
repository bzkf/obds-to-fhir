package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.TumorzuordnungTyp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ObdsToFhirBundleMapper extends ObdsToFhirMapper {
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

  @Value("${fhir.mappings.createPatientResources.enabled}")
  private boolean createPatientResources;

  @Value("${fhir.mappings.modul.prostata.enabled}")
  private boolean isModulProstataMappingEnabled;

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
      WeitereKlassifikationMapper weitereKlassifikationMapper) {
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

        var primaryConditionReference =
            createPrimaryConditionReference(
                meldung.getTumorzuordnung(), obdsPatient.getPatientID());

        // Diagnose
        if (meldung.getDiagnose() != null) {
          var condition =
              conditionMapper.map(
                  meldung, patientReference, obds.getMeldedatum(), obdsPatient.getPatientID());
          addToBundle(bundle, condition);

          var diagnose = meldung.getDiagnose();

          if (diagnose.getAllgemeinerLeistungszustand() != null) {
            var leistungszustand =
                leistungszustandMapper.map(
                    diagnose.getAllgemeinerLeistungszustand(),
                    meldung.getMeldungID(),
                    meldung.getTumorzuordnung().getDiagnosedatum(),
                    patientReference,
                    primaryConditionReference);
            addToBundle(bundle, leistungszustand);
          }

          if (diagnose.getMengeFM() != null && diagnose.getMengeFM().getFernmetastase() != null) {
            var diagnoseFM =
                fernmetastasenMapper.map(
                    diagnose, meldung.getMeldungID(), patientReference, primaryConditionReference);
            addToBundle(bundle, diagnoseFM);
          }

          if (diagnose.getHistologie() != null) {
            var histologie = diagnose.getHistologie();
            var specimen = specimenMapper.map(histologie, patientReference);
            addToBundle(bundle, specimen);

            var specimenReference = createReferenceFromResource(specimen);

            var verlaufsHistologie =
                verlaufshistologieObservationMapper.map(
                    histologie,
                    meldung.getMeldungID(),
                    patientReference,
                    specimenReference,
                    primaryConditionReference);

            addToBundle(bundle, verlaufsHistologie);

            if (histologie.getGrading() != null) {
              var grading =
                  gradingObservationMapper.map(
                      histologie,
                      meldung.getMeldungID(),
                      patientReference,
                      primaryConditionReference,
                      specimenReference);
              addToBundle(bundle, grading);
            }

            var lymphknotenuntersuchungen =
                lymphknotenuntersuchungMapper.map(
                    histologie, patientReference, primaryConditionReference, specimenReference);
            addToBundle(bundle, lymphknotenuntersuchungen);
          }

          if (diagnose.getModulAllgemein() != null) {
            var allgemein = diagnose.getModulAllgemein();
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

          if (diagnose.getMengeGenetik() != null
              && diagnose.getMengeGenetik().getGenetischeVariante() != null) {
            var genetischeVarianten =
                genetischeVarianteMapper.map(
                    diagnose, patientReference, primaryConditionReference, meldung.getMeldungID());
            addToBundle(bundle, genetischeVarianten);
          }

          if (diagnose.getCTNM() != null) {
            var clinicalTNMObservations =
                tnmMapper.map(
                    diagnose.getCTNM(),
                    "clinical",
                    meldung.getMeldungID(),
                    patientReference,
                    primaryConditionReference);
            addToBundle(bundle, clinicalTNMObservations);
          }
          if (diagnose.getPTNM() != null) {
            var pathologicTNMObservations =
                tnmMapper.map(
                    diagnose.getPTNM(),
                    "pathologic",
                    meldung.getMeldungID(),
                    patientReference,
                    primaryConditionReference);
            addToBundle(bundle, pathologicTNMObservations);
          }

          if (diagnose.getModulProstata() != null && isModulProstataMappingEnabled) {
            var modulProstata = diagnose.getModulProstata();
            if (modulProstata.getGleasonScore() != null) {
              var gleasonScore =
                  gleasonScoreMapper.map(
                      modulProstata,
                      meldung.getMeldungID(),
                      patientReference,
                      primaryConditionReference);
              addToBundle(bundle, gleasonScore);
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
              addToBundle(bundle, weitereKlassifikationen);
            }
          }
        }

        // Verlauf
        if (meldung.getVerlauf() != null) {
          var verlauf = meldung.getVerlauf();

          var verlaufsObservation =
              verlaufObservationMapper.map(verlauf, patientReference, primaryConditionReference);
          addToBundle(bundle, verlaufsObservation);

          if (verlauf.getMengeFM() != null && verlauf.getMengeFM().getFernmetastase() != null) {
            var verlaufFM =
                fernmetastasenMapper.map(
                    verlauf, meldung.getMeldungID(), patientReference, primaryConditionReference);
            addToBundle(bundle, verlaufFM);
          }

          if (verlauf.getAllgemeinerLeistungszustand() != null) {
            var leistungszustand =
                leistungszustandMapper.map(
                    verlauf.getAllgemeinerLeistungszustand(),
                    meldung.getMeldungID(),
                    verlauf.getUntersuchungsdatumVerlauf(),
                    patientReference,
                    primaryConditionReference);
            addToBundle(bundle, leistungszustand);
          }

          if (verlauf.getHistologie() != null) {
            var histologie = verlauf.getHistologie();
            var specimen = specimenMapper.map(verlauf.getHistologie(), patientReference);
            addToBundle(bundle, specimen);

            var specimenReference = createReferenceFromResource(specimen);

            var verlaufsHistologie =
                verlaufshistologieObservationMapper.map(
                    histologie,
                    meldung.getMeldungID(),
                    patientReference,
                    specimenReference,
                    primaryConditionReference);

            addToBundle(bundle, verlaufsHistologie);

            if (histologie.getGrading() != null) {
              var grading =
                  gradingObservationMapper.map(
                      histologie,
                      meldung.getMeldungID(),
                      patientReference,
                      primaryConditionReference,
                      specimenReference);
              addToBundle(bundle, grading);
            }

            var lymphknotenuntersuchungen =
                lymphknotenuntersuchungMapper.map(
                    histologie, patientReference, primaryConditionReference, specimenReference);
            addToBundle(bundle, lymphknotenuntersuchungen);
          }

          if (verlauf.getModulAllgemein() != null) {
            var allgemein = verlauf.getModulAllgemein();
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

          if (verlauf.getMengeGenetik() != null
              && verlauf.getMengeGenetik().getGenetischeVariante() != null) {
            var genetischeVarianten =
                genetischeVarianteMapper.map(
                    verlauf, patientReference, primaryConditionReference, meldung.getMeldungID());
            addToBundle(bundle, genetischeVarianten);
          }

          if (verlauf.getTNM() != null) {
            var tnmObservations =
                tnmMapper.map(
                    verlauf.getTNM(),
                    "generic",
                    meldung.getMeldungID(),
                    patientReference,
                    primaryConditionReference);
            addToBundle(bundle, tnmObservations);
          }

          if (verlauf.getModulProstata() != null && isModulProstataMappingEnabled) {
            var modulProstata = verlauf.getModulProstata();
            if (modulProstata.getGleasonScore() != null) {
              var gleasonScore =
                  gleasonScoreMapper.map(
                      modulProstata,
                      meldung.getMeldungID(),
                      patientReference,
                      primaryConditionReference);
              addToBundle(bundle, gleasonScore);
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
              addToBundle(bundle, weitereKlassifikationen);
            }
          }
        }

        // Systemtherapie
        if (meldung.getSYST() != null) {
          var syst = meldung.getSYST();

          var systProcedure = systemischeTherapieProcedureMapper.map(syst, patientReference);
          addToBundle(bundle, systProcedure);

          var procedureReference = createReferenceFromResource(systProcedure);

          if (syst.getMengeSubstanz() != null) {
            var systMedicationStatements =
                systemischeTherapieMedicationStatementMapper.map(
                    syst, patientReference, procedureReference);

            for (var resource : systMedicationStatements) {
              addToBundle(bundle, resource);
            }
          }

          if (syst.getModulAllgemein() != null) {
            var allgemein = syst.getModulAllgemein();
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
        }

        // Strahlenterhapie
        if (meldung.getST() != null) {
          var st = meldung.getST();
          var stProcedure =
              strahlentherapieMapper.map(st, patientReference, primaryConditionReference);
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
        }

        // Tod
        if (meldung.getTod() != null) {
          var deathObservations =
              todMapper.map(meldung.getTod(), patientReference, primaryConditionReference);
          for (var resource : deathObservations) {
            addToBundle(bundle, resource);
          }
        }

        // Operation
        if (meldung.getOP() != null) {
          var op = meldung.getOP();
          var operations = operationMapper.map(op, patientReference, primaryConditionReference);
          addToBundle(bundle, operations);

          if (op.getResidualstatus() != null
              && op.getResidualstatus().getGesamtbeurteilungResidualstatus() != null) {
            var residualstatus =
                residualstatusMapper.map(op, patientReference, primaryConditionReference);
            addToBundle(bundle, residualstatus);
          }

          if (op.getHistologie() != null) {
            var histologie = op.getHistologie();
            var specimen = specimenMapper.map(op.getHistologie(), patientReference);
            addToBundle(bundle, specimen);

            var specimenReference = createReferenceFromResource(specimen);

            var verlaufsHistologie =
                verlaufshistologieObservationMapper.map(
                    histologie,
                    meldung.getMeldungID(),
                    patientReference,
                    specimenReference,
                    primaryConditionReference);

            addToBundle(bundle, verlaufsHistologie);

            if (histologie.getGrading() != null) {
              var grading =
                  gradingObservationMapper.map(
                      histologie,
                      meldung.getMeldungID(),
                      patientReference,
                      primaryConditionReference,
                      specimenReference);
              addToBundle(bundle, grading);
            }

            var lymphknotenuntersuchungen =
                lymphknotenuntersuchungMapper.map(
                    histologie, patientReference, primaryConditionReference, specimenReference);
            addToBundle(bundle, lymphknotenuntersuchungen);
          }

          if (op.getModulAllgemein() != null) {
            var allgemein = op.getModulAllgemein();
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

          if (op.getMengeGenetik() != null
              && op.getMengeGenetik().getGenetischeVariante() != null) {
            var genetischeVarianten =
                genetischeVarianteMapper.map(
                    op, patientReference, primaryConditionReference, meldung.getMeldungID());
            addToBundle(bundle, genetischeVarianten);
          }

          if (op.getTNM() != null) {
            var tnmObservations =
                tnmMapper.map(
                    op.getTNM(),
                    "generic",
                    meldung.getMeldungID(),
                    patientReference,
                    primaryConditionReference);
            addToBundle(bundle, tnmObservations);
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
              addToBundle(bundle, gleasonScore);
            }
          }
        }

        // Pathologie
        if (meldung.getPathologie() != null) {
          var pathologie = meldung.getPathologie();
          var specimenReference = new Reference();

          if (pathologie.getHistologie() != null) {
            var histologie = pathologie.getHistologie();
            var specimen = specimenMapper.map(pathologie.getHistologie(), patientReference);
            addToBundle(bundle, specimen);

            specimenReference = createReferenceFromResource(specimen);

            var verlaufsHistologie =
                verlaufshistologieObservationMapper.map(
                    histologie,
                    meldung.getMeldungID(),
                    patientReference,
                    specimenReference,
                    primaryConditionReference);

            addToBundle(bundle, verlaufsHistologie);

            if (histologie.getGrading() != null) {
              var grading =
                  gradingObservationMapper.map(
                      histologie,
                      meldung.getMeldungID(),
                      patientReference,
                      primaryConditionReference,
                      specimenReference);
              addToBundle(bundle, grading);
            }

            var lymphknotenuntersuchungen =
                lymphknotenuntersuchungMapper.map(
                    histologie, patientReference, primaryConditionReference, specimenReference);
            addToBundle(bundle, lymphknotenuntersuchungen);
          }

          if (pathologie.getMengeFM() != null
              && pathologie.getMengeFM().getFernmetastase() != null) {
            var pathologieFM =
                fernmetastasenMapper.map(
                    pathologie,
                    meldung.getMeldungID(),
                    patientReference,
                    primaryConditionReference);
            addToBundle(bundle, pathologieFM);
          }

          if (pathologie.getMengeGenetik() != null
              && pathologie.getMengeGenetik().getGenetischeVariante() != null) {
            var genetischeVarianten =
                genetischeVarianteMapper.map(
                    pathologie,
                    patientReference,
                    primaryConditionReference,
                    meldung.getMeldungID());
            addToBundle(bundle, genetischeVarianten);
          }

          if (pathologie.getCTNM() != null) {
            var clinicalTNMObservations =
                tnmMapper.map(
                    pathologie.getCTNM(),
                    "clinical",
                    meldung.getMeldungID(),
                    patientReference,
                    primaryConditionReference);
            addToBundle(bundle, clinicalTNMObservations);
          }
          if (pathologie.getPTNM() != null) {
            var pathologicTNMObservations =
                tnmMapper.map(
                    pathologie.getPTNM(),
                    "pathologic",
                    meldung.getMeldungID(),
                    patientReference,
                    primaryConditionReference);
            addToBundle(bundle, pathologicTNMObservations);
          }

          if (pathologie.getModulProstata() != null && isModulProstataMappingEnabled) {
            var modulProstata = pathologie.getModulProstata();
            if (modulProstata.getGleasonScore() != null) {
              var gleasonScore =
                  gleasonScoreMapper.map(
                      modulProstata,
                      meldung.getMeldungID(),
                      patientReference,
                      primaryConditionReference);
              addToBundle(bundle, gleasonScore);
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
              addToBundle(bundle, weitereKlassifikationen);
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

          addToBundle(bundle, report);
        }

        // Tumorkonferenz
        if (meldung.getTumorkonferenz() != null) {
          var tumorkonferenz = meldung.getTumorkonferenz();
          var carePlan =
              tumorkonferenzMapper.map(tumorkonferenz, patientReference, primaryConditionReference);
          addToBundle(bundle, carePlan);
        }
      }

      bundles.add(bundle);
      MDC.clear();
    }
    return bundles;
  }

  private static Bundle addToBundle(Bundle bundle, List<? extends Resource> resources) {
    for (var resource : resources) {
      addToBundle(bundle, resource);
    }
    return bundle;
  }

  private static Bundle addToBundle(Bundle bundle, Resource resource) {
    var url = String.format("%s/%s", resource.getResourceType(), resource.getIdBase());
    bundle
        .addEntry()
        .setFullUrl(url)
        .setResource(resource)
        .getRequest()
        .setMethod(HTTPVerb.PUT)
        .setUrl(url);
    return bundle;
  }

  private Reference createPrimaryConditionReference(
      TumorzuordnungTyp tumorzuordnung, String patientId) {
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getConditionId())
            .setValue(patientId + "-" + tumorzuordnung.getTumorID());

    var conditionId = computeResourceIdFromIdentifier(identifier);

    return new Reference("Condition/" + conditionId);
  }

  private Reference createReferenceFromResource(Resource resource) {
    Objects.requireNonNull(resource.getId());
    return new Reference(resource.getResourceType().name() + "/" + resource.getId());
  }
}
