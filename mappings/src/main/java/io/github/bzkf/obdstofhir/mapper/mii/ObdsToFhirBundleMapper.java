package io.github.bzkf.obdstofhir.mapper.mii;

import ca.uhn.fhir.context.FhirContext;
import com.github.difflib.text.DiffRow.Tag;
import com.github.difflib.text.DiffRowGenerator;
import de.basisdatensatz.obds.v3.DiagnoseTyp;
import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung;
import de.basisdatensatz.obds.v3.OPTyp;
import de.basisdatensatz.obds.v3.PathologieTyp;
import de.basisdatensatz.obds.v3.STTyp;
import de.basisdatensatz.obds.v3.SYSTTyp;
import de.basisdatensatz.obds.v3.TumorkonferenzTyp;
import de.basisdatensatz.obds.v3.VerlaufTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.bzkf.obdstofhir.mapper.mii.TNMMapper.TnmType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
  private final WeitereKlassifikationMapper weitereKlassifikationMapper;
  private final ErstdiagnoseEvidenzListMapper erstdiagnoseEvidenzListMapper;
  private final NebenwirkungMapper nebenwirkungMapper;
  private final FruehereTumorerkrankungenMapper fruehereTumorErkrankungenMapper;
  private final ProvenanceMapper provenanceMapper;

  @Value("${fhir.mappings.create-patient-resources.enabled}")
  private boolean createPatientResources;

  @Value("${fhir.mappings.create-provenance-resources.enabled}")
  private boolean createProvenanceResources;

  @Value("${fhir.mappings.meta.source}")
  private String metaSource;

  @Value("${fhir.mappings.patient-id-regex:^(.*)$}")
  private String patientIdRegex;

  private final Function<OBDS.MengePatient.Patient, Optional<Reference>> patientReferenceGenerator;

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
      WeitereKlassifikationMapper weitereKlassifikationMapper,
      ErstdiagnoseEvidenzListMapper erstdiagnoseEvidenzListMapper,
      NebenwirkungMapper nebenwirkungMapper,
      FruehereTumorerkrankungenMapper fruehereTumorErkrankungenMapper,
      ProvenanceMapper provenanceMapper,
      Function<OBDS.MengePatient.Patient, Optional<Reference>> patientReferenceGenerator) {
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
    this.weitereKlassifikationMapper = weitereKlassifikationMapper;
    this.erstdiagnoseEvidenzListMapper = erstdiagnoseEvidenzListMapper;
    this.nebenwirkungMapper = nebenwirkungMapper;
    this.fruehereTumorErkrankungenMapper = fruehereTumorErkrankungenMapper;
    this.provenanceMapper = provenanceMapper;
    this.patientReferenceGenerator = patientReferenceGenerator;
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

      if (StringUtils.hasText(patientIdRegex)) {
        var patientIdRegexMatcher = Pattern.compile(patientIdRegex);
        var matcher = patientIdRegexMatcher.matcher(obdsPatient.getPatientID().trim());
        if (matcher.matches()) {
          var patientId = matcher.group();
          // mutating the obdsPatient is probably not ideal
          obdsPatient.setPatientID(patientId);
        } else {
          throw new IllegalArgumentException(
              String.format(
                  "Regex %s failed to match against %s",
                  patientIdRegex, obdsPatient.getPatientID()));
        }
      }

      var meldungen = obdsPatient.getMengeMeldung().getMeldung();

      meldungen = sortMeldungen(meldungen);

      // Patient
      // XXX: this assumes that the "meldungen" contains every single Meldung for the
      // patient
      // e.g. in case of Folgepakete, this might not be the case. The main Problem is
      // the Patient.deceased information which is not present in every single Paket.
      var patient = patientMapper.map(obdsPatient, meldungen);

      var patientReferenceOptional = patientReferenceGenerator.apply(obdsPatient);

      // if the patient reference could not be generated, but the creation
      // of patient resources in enabled, we add a patient resource anyway.
      // this is helpful for cases where a patient should only be created if
      // it doesn't exist yet on the FHIR server (e.g. FHIR_SERVER_LOOKUP strategy).
      if (patientReferenceOptional.isEmpty() && !createPatientResources) {
        throw new IllegalStateException(
            "Unable to generate patient reference for patient with ID. "
                + "The patient may be missing in the FHIR server or record database.");
      } else if (patientReferenceOptional.isEmpty() && createPatientResources) {
        LOG.debug(
            "Unable to generate patient reference so using reference "
                + "to patient to be created.");
        // this uses the default mechanism of referencing the created patient resource
        patientReferenceOptional = Optional.of(createReferenceFromResource(patient));
      }

      var patientReference = patientReferenceOptional.get();

      // only add patient resource to bundle if enabled
      if (createPatientResources) {
        addToBundle(bundle, patient);
      }

      bundle.setId(patient.getId());

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
        addToBundle(bundle, condition);
        resourcesMappedFromMeldung.add(condition);

        var primaryConditionReference = createReferenceFromResource(condition);

        if (meldung.getDiagnose() != null) {
          var resources =
              mapDiagnose(
                  meldung.getDiagnose(),
                  meldung,
                  obdsPatient.getPatientID(),
                  patientReference,
                  condition,
                  obds.getMeldedatum());
          addToBundle(bundle, resources);
          resourcesMappedFromMeldung.addAll(resources);
        }

        // Verlauf
        if (meldung.getVerlauf() != null) {
          var resources =
              mapVerlauf(
                  meldung.getVerlauf(), meldung, patientReference, primaryConditionReference);
          addToBundle(bundle, resources);
          resourcesMappedFromMeldung.addAll(resources);
        }

        // Systemtherapie
        if (meldung.getSYST() != null) {
          var resources =
              mapSyst(meldung.getSYST(), meldung, patientReference, primaryConditionReference);
          addToBundle(bundle, resources);
          resourcesMappedFromMeldung.addAll(resources);
        }

        // Strahlenterhapie
        if (meldung.getST() != null) {
          var st = meldung.getST();
          var stProcedure =
              strahlentherapieMapper.map(
                  st, patientReference, primaryConditionReference, meldung.getMeldungID());
          addToBundle(bundle, stProcedure);
          resourcesMappedFromMeldung.addAll(stProcedure);

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
              resourcesMappedFromMeldung.add(studienteilnahmeObservation);
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
              resourcesMappedFromMeldung.addAll(nebenwirkungen);
            } else {
              LOG.error("Unable to find the primary ST procedure");
            }
          }
        }

        // Tod
        if (meldung.getTod() != null) {
          var deathObservations =
              todMapper.map(meldung.getTod(), patientReference, primaryConditionReference);
          addToBundle(bundle, deathObservations);
          resourcesMappedFromMeldung.addAll(deathObservations);
        }

        // Operation
        if (meldung.getOP() != null) {
          var resources =
              mapOP(meldung.getOP(), meldung, patientReference, primaryConditionReference);
          addToBundle(bundle, resources);
          resourcesMappedFromMeldung.addAll(resources);
        }

        // Pathologie
        if (meldung.getPathologie() != null) {
          var resources =
              mapPathologie(
                  meldung.getPathologie(), meldung, patientReference, primaryConditionReference);
          addToBundle(bundle, resources);
          resourcesMappedFromMeldung.addAll(resources);
        }

        // Tumorkonferenz
        if (meldung.getTumorkonferenz() != null) {
          var tumorkonferenz = meldung.getTumorkonferenz();
          var carePlan =
              tumorkonferenzMapper.map(tumorkonferenz, patientReference, primaryConditionReference);
          addToBundle(bundle, carePlan);
          resourcesMappedFromMeldung.add(carePlan);
        }

        if (this.createProvenanceResources) {
          var targets =
              resourcesMappedFromMeldung.stream().map(this::createReferenceFromResource).toList();
          var provenance = provenanceMapper.map(targets, meldung.getMeldungID());
          addToBundle(bundle, provenance);
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
    if (meldeanlass == null) return 0;
    return meldeanlass.equals(ende) ? 2 : 0; // "ende" at the end, everything else at the front
  }

  private List<Resource> mapDiagnose(
      DiagnoseTyp diagnose,
      Meldung meldung,
      String patientId,
      Reference patientReference,
      Condition primaryCondition,
      XMLGregorianCalendar meldedatum) {

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
      var specimen = specimenMapper.map(histologie, patientReference, meldung.getMeldungID());
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
              histologie,
              patientReference,
              primaryConditionReference,
              specimenReference,
              meldung.getMeldungID());
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
      String histologieId = null;
      if (diagnose.getHistologie() != null) {
        histologieId = diagnose.getHistologie().getHistologieID();
      }

      var clinicalTNMObservations =
          tnmMapper.map(
              diagnose.getCTNM(),
              TnmType.CLINICAL,
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference,
              histologieId);
      mappedResources.addAll(clinicalTNMObservations);
    }

    if (diagnose.getPTNM() != null) {
      String histologieId = null;
      if (diagnose.getHistologie() != null) {
        histologieId = diagnose.getHistologie().getHistologieID();
      }

      var pathologicTNMObservations =
          tnmMapper.map(
              diagnose.getPTNM(),
              TnmType.PATHOLOGIC,
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference,
              histologieId);
      mappedResources.addAll(pathologicTNMObservations);
    }

    if (diagnose.getModulProstata() != null) {
      var modulProstata = diagnose.getModulProstata();
      var diagnosedatum =
          meldung.getTumorzuordnung().getDiagnosedatum() != null
              ? meldung.getTumorzuordnung().getDiagnosedatum().getValue()
              : null;
      var modulProstataResources =
          modulProstataMapper.map(
              modulProstata,
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference,
              diagnosedatum);
      mappedResources.addAll(modulProstataResources);
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
        mappedResources.stream()
            .filter(r -> r instanceof Observation || r instanceof DiagnosticReport)
            .map(this::createReferenceFromResource)
            .toList();

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

    // we map these after the evidenz list since it may only contain
    // Observation/DiagnosticReport
    // resources.
    if (diagnose.getMengeFruehereTumorerkrankung() != null) {
      var fruehereTumorErkrankungen =
          fruehereTumorErkrankungenMapper.map(
              diagnose.getMengeFruehereTumorerkrankung(),
              patientReference,
              primaryCondition.getIdentifierFirstRep(),
              meldedatum);
      mappedResources.addAll(fruehereTumorErkrankungen);

      var fruehereTumorerkrankungenExtensions =
          fruehereTumorErkrankungen.stream()
              .map(this::createReferenceFromResource)
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
      var specimen =
          specimenMapper.map(verlauf.getHistologie(), patientReference, meldung.getMeldungID());
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
              histologie,
              patientReference,
              primaryConditionReference,
              specimenReference,
              meldung.getMeldungID());
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
      String histologieId = null;
      if (verlauf.getHistologie() != null) {
        histologieId = verlauf.getHistologie().getHistologieID();
      }

      var tnmObservations =
          tnmMapper.map(
              verlauf.getTNM(),
              TnmType.GENERIC,
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference,
              histologieId);
      mappedResources.addAll(tnmObservations);
    }

    if (verlauf.getModulProstata() != null) {
      var modulProstata = verlauf.getModulProstata();
      var modulProstataResources =
          modulProstataMapper.map(
              modulProstata,
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference,
              verlauf.getUntersuchungsdatumVerlauf());
      mappedResources.addAll(modulProstataResources);
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

    var systProcedure =
        systemischeTherapieProcedureMapper.map(syst, patientReference, primaryConditionReference);
    mappedResources.add(systProcedure);

    var procedureReference = createReferenceFromResource(systProcedure);

    if (syst.getMengeSubstanz() != null) {
      var systMedicationStatements =
          systemischeTherapieMedicationStatementMapper.map(
              syst, patientReference, procedureReference, primaryConditionReference);
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
      var specimen =
          specimenMapper.map(op.getHistologie(), patientReference, meldung.getMeldungID());
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
              histologie,
              patientReference,
              primaryConditionReference,
              specimenReference,
              meldung.getMeldungID());
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
      String histologieId = null;
      if (op.getHistologie() != null) {
        histologieId = op.getHistologie().getHistologieID();
      }

      var tnmObservations =
          tnmMapper.map(
              op.getTNM(),
              TnmType.GENERIC,
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference,
              histologieId);
      mappedResources.addAll(tnmObservations);
    }

    if (op.getModulProstata() != null) {
      var modulProstata = op.getModulProstata();
      var opReferences = operations.stream().map(this::createReferenceFromResource).toList();
      var modulProstataResources =
          modulProstataMapper.map(
              modulProstata,
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference,
              op.getDatum(),
              opReferences);
      mappedResources.addAll(modulProstataResources);
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
      var specimen =
          specimenMapper.map(pathologie.getHistologie(), patientReference, meldung.getMeldungID());

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
              histologie,
              patientReference,
              primaryConditionReference,
              specimenReference,
              meldung.getMeldungID());
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
      String histologieId = null;
      if (pathologie.getHistologie() != null) {
        histologieId = pathologie.getHistologie().getHistologieID();
      }

      var clinicalTNMObservations =
          tnmMapper.map(
              pathologie.getCTNM(),
              TnmType.CLINICAL,
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference,
              histologieId);
      mappedResources.addAll(clinicalTNMObservations);
    }

    if (pathologie.getPTNM() != null) {
      String histologieId = null;
      if (pathologie.getHistologie() != null) {
        histologieId = pathologie.getHistologie().getHistologieID();
      }

      var pathologicTNMObservations =
          tnmMapper.map(
              pathologie.getPTNM(),
              TnmType.PATHOLOGIC,
              meldung.getMeldungID(),
              patientReference,
              primaryConditionReference,
              histologieId);
      mappedResources.addAll(pathologicTNMObservations);
    }

    if (pathologie.getModulProstata() != null) {
      var modulProstata = pathologie.getModulProstata();
      var modulProstataResources =
          modulProstataMapper.map(
              modulProstata, meldung.getMeldungID(), patientReference, primaryConditionReference);
      mappedResources.addAll(modulProstataResources);
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

  private Reference createReferenceFromResource(Resource resource) {
    Objects.requireNonNull(resource.getId());
    return new Reference(resource.getResourceType().name() + "/" + resource.getId());
  }
}
