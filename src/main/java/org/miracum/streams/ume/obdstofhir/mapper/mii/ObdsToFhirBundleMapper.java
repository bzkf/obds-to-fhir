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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ObdsToFhirBundleMapper extends ObdsToFhirMapper {

  @Value("${fhir.createPatientResources}")
  private boolean createPatientResources;

  private final PatientMapper patientMapper;
  private final ConditionMapper conditionMapper;
  private final StrahlentherapieMapper strahlentherapieMapper;
  private final SystemischeTherapieProcedureMapper systemischeTherapieProcedureMapper;
  private final SystemischeTherapieMedicationStatementMapper
      systemischeTherapieMedicationStatementMapper;
  private final TodMapper todMapper;
  private final LeistungszustandMapper leistungszustandMapper;
  private final OperationMapper operationMapper;
  private final ResidualstatusMapper residualstatusMapper;

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
      ResidualstatusMapper residualstatusMapper) {
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
  }

  /**
   * Maps OBDS (Onkologischer Basisdatensatz) data to a list of FHIR Bundles. For each patient in
   * the OBDS data, creates a transaction bundle containing all their associated medical records.
   *
   * @param groupedObds List of OBDS data structure containing patient and medical information
   * @return List of FHIR Bundles, one for each patient in the input OBDS data
   */
  public List<Bundle> map(List<OBDS> groupedObds) {

    var bundles = new ArrayList<Bundle>();

    for (OBDS obds : groupedObds) {

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

        // only add patient resource to bundle if active profile is set to patient
        if (createPatientResources) {
          addResourceToBundle(bundle, patient);
        }
        bundle.setId(patient.getId());

        for (var meldung : meldungen) {
          // Diagnose
          if (meldung.getDiagnose() != null) {
            var condition = conditionMapper.map(meldung, patientReference, obds.getMeldedatum());
            addResourceToBundle(bundle, condition);

            var conditionReference = new Reference("Condition/" + condition.getId());

            if (meldung.getDiagnose().getAllgemeinerLeistungszustand() != null) {
              var leistungszustand =
                  leistungszustandMapper.map(meldung, patientReference, conditionReference);
              addResourceToBundle(bundle, leistungszustand);
            }
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

          var primaryConditionReference =
              createPrimaryConditionReference(meldung.getTumorzuordnung());

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
                && meldung.getOP().getResidualstatus().getGesamtbeurteilungResidualstatus()
                    != null) {
              var residualstatus =
                  residualstatusMapper.map(
                      meldung.getOP(), patientReference, primaryConditionReference);
              addResourceToBundle(bundle, residualstatus);
            }
          }
        }

        bundles.add(bundle);
      }
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
