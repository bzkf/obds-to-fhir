package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.TumorzuordnungTyp;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.springframework.stereotype.Service;

@Service
public class ObdsToFhirBundleMapper extends ObdsToFhirMapper {

  private final ConditionMapper conditionMapper;
  private final FernmetastasenMapper fernmetastasenMapper;
  private final GradingObservationMapper gradingObservationMapper;
  private final HistologiebefundMapper histologiebefundMapper;
  private final LeistungszustandMapper leistungszustandMapper;
  private final LymphknotenuntersuchungMapper lymphknotenuntersuchungMapper;
  private final OperationMapper operationMapper;
  private final PatientMapper patientMapper;
  private final ResidualstatusMapper residualstatusMapper;
  private final SpecimenMapper specimenMapper;
  private final StrahlentherapieMapper strahlentherapieMapper;
  private final SystemischeTherapieMedicationStatementMapper
      systemischeTherapieMedicationStatementMapper;
  private final SystemischeTherapieProcedureMapper systemischeTherapieProcedureMapper;
  private final TodMapper todMapper;
  private final VerlaufshistorieObservationMapper verlaufshistorieObservationMapper;

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
      VerlaufshistorieObservationMapper verlaufshistorieObservationMapper) {
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
    this.verlaufshistorieObservationMapper = verlaufshistorieObservationMapper;
  }

  /**
   * Maps OBDS (Onkologischer Basisdatensatz) data to a list of FHIR Bundles. For each patient in
   * the OBDS data, creates a transaction bundle containing all their associated medical records.
   *
   * @param obds The OBDS data structure containing patient and medical information
   * @return List of FHIR Bundles, one for each patient in the input OBDS data
   */
  public List<Bundle> map(OBDS obds) {

    var bundles = new ArrayList<Bundle>();

    for (var obdsPatient : obds.getMengePatient().getPatient()) {
      var bundle = new Bundle();
      bundle.setType(BundleType.TRANSACTION);

      var meldungen = obdsPatient.getMengeMeldung().getMeldung();

      // Patient
      // XXX: this assumes that the "meldungen" contains every single Meldung for the patient
      //      e.g. in case of Folgepakete, this might not be the case. The main Problem is
      //      the Patient.deceased information which is not present in every single Paket.
      var patient = patientMapper.map(obdsPatient, meldungen);
      var patientReference = new Reference("Patient/" + patient.getId());
      addResourceToBundle(bundle, patient);

      bundle.setId(patient.getId());

      for (var meldung : meldungen) {
        // - GradingObservationMapper
        // - lymphknotenuntersuchungMapper
        // - specimenMapper
        // - verlaufshistorieObservationMapper

        var primaryConditionReference =
            createPrimaryConditionReference(meldung.getTumorzuordnung());

        // Diagnose
        if (meldung.getDiagnose() != null) {
          var condition = conditionMapper.map(meldung, patientReference, obds.getMeldedatum());
          addResourceToBundle(bundle, condition);

          if (meldung.getDiagnose().getAllgemeinerLeistungszustand() != null) {
            var leistungszustand =
                leistungszustandMapper.map(meldung, patientReference, primaryConditionReference);
            addResourceToBundle(bundle, leistungszustand);
          }

          if (meldung.getDiagnose().getMengeFM() != null
              && meldung.getDiagnose().getMengeFM().getFernmetastase() != null) {
            var diagnoseFM =
                fernmetastasenMapper.map(
                    meldung.getDiagnose(), patientReference, primaryConditionReference);
            addResourcesToBundle(bundle, diagnoseFM);
          }
        }

        if (meldung.getVerlauf() != null
            && meldung.getVerlauf().getMengeFM() != null
            && meldung.getVerlauf().getMengeFM().getFernmetastase() != null) {
          var verlaufFM =
              fernmetastasenMapper.map(
                  meldung.getVerlauf(), patientReference, primaryConditionReference);
          addResourcesToBundle(bundle, verlaufFM);
        }

        // Systemtherapie
        if (meldung.getSYST() != null) {
          var syst = meldung.getSYST();

          var systProcedure = systemischeTherapieProcedureMapper.map(syst, patientReference);
          addResourceToBundle(bundle, systProcedure);

          var procedureReference = new Reference("Procedure/" + systProcedure.getId());

          if (syst.getMengeSubstanz() != null) {
            var systMedicationStatements =
                systemischeTherapieMedicationStatementMapper.map(
                    syst, patientReference, procedureReference);

            for (var resource : systMedicationStatements) {
              addResourceToBundle(bundle, resource);
            }
          }
        }

        // Strahlenterhapie
        if (meldung.getST() != null) {
          var stProcedure = strahlentherapieMapper.map(meldung.getST(), patientReference);
          addResourceToBundle(bundle, stProcedure);
        }

        // Tod
        if (meldung.getTod() != null) {
          var deathObservations =
              todMapper.map(meldung.getTod(), patientReference, primaryConditionReference);
          for (var resource : deathObservations) {
            addResourceToBundle(bundle, resource);
          }
        }

        // Operation
        if (meldung.getOP() != null) {
          var operations =
              operationMapper.map(meldung.getOP(), patientReference, primaryConditionReference);
          addResourcesToBundle(bundle, operations);

          if (meldung.getOP().getResidualstatus() != null
              && meldung.getOP().getResidualstatus().getGesamtbeurteilungResidualstatus() != null) {
            var residualstatus =
                residualstatusMapper.map(
                    meldung.getOP(), patientReference, primaryConditionReference);
            addResourceToBundle(bundle, residualstatus);
          }
        }

        if (meldung.getPathologie() != null) {
          // TODO: change specimenMapper to accept PathologieTyp and use the created Specimen as a
          // reference for the histologiebefundMapper
          // var specimen = specimenMapper.map(null, null)
          var report =
              histologiebefundMapper.map(
                  meldung.getPathologie(), meldung.getMeldungID(), patientReference, null, null);
          addResourceToBundle(bundle, report);
        }
      }

      bundles.add(bundle);
    }

    return bundles;
  }

  private static Bundle addResourcesToBundle(Bundle bundle, List<? extends Resource> resources) {
    for (var resource : resources) {
      addResourceToBundle(bundle, resource);
    }
    return bundle;
  }

  private static Bundle addResourceToBundle(Bundle bundle, Resource resource) {
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

  private Reference createPrimaryConditionReference(TumorzuordnungTyp tumorzuordnung) {
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getConditionId())
            .setValue(tumorzuordnung.getTumorID());

    var conditionId = computeResourceIdFromIdentifier(identifier);

    return new Reference("Condition/" + conditionId);
  }
}
