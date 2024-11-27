package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

@Service
public class ObdsToFhirBundleMapper {

  private final PatientMapper patientMapper;
  private final ConditionMapper conditionMapper;
  private final StrahlentherapieMapper strahlentherapieMapper;
  private final SystemischeTherapieProcedureMapper systemischeTherapieProcedureMapper;
  private final SystemischeTherapieMedicationStatementMapper
      systemischeTherapieMedicationStatementMapper;

  public ObdsToFhirBundleMapper(
      PatientMapper patientMapper,
      ConditionMapper conditionMapper,
      SystemischeTherapieProcedureMapper systemischeTherapieProcedureMapper,
      SystemischeTherapieMedicationStatementMapper systemischeTherapieMedicationStatementMapper,
      StrahlentherapieMapper strahlentherapieMapper) {
    this.patientMapper = patientMapper;
    this.conditionMapper = conditionMapper;
    this.systemischeTherapieProcedureMapper = systemischeTherapieProcedureMapper;
    this.systemischeTherapieMedicationStatementMapper =
        systemischeTherapieMedicationStatementMapper;
    this.strahlentherapieMapper = strahlentherapieMapper;
  }

  public Bundle map(OBDS obds) {
    var bundle = new Bundle();
    bundle.setType(BundleType.TRANSACTION);

    // TODO: set bundle id... to the patient id? sum of all ids?
    // TODO: or one bundle per Patient instead?
    for (var obdsPatient : obds.getMengePatient().getPatient()) {
      var meldungen = obdsPatient.getMengeMeldung().getMeldung();

      // Patient
      var patient = patientMapper.map(obdsPatient, meldungen);
      var patientReference = new Reference("Patient/" + patient.getId());
      addEntryToBundle(bundle, patient);

      for (var meldung : meldungen) {
        // Diagnose
        if (meldung.getDiagnose() != null) {
          var condition = conditionMapper.map(meldung, patientReference);
          addEntryToBundle(bundle, condition);
        }

        // Systemtherapie
        if (meldung.getSYST() != null) {
          var syst = meldung.getSYST();

          var systProcedure = systemischeTherapieProcedureMapper.map(syst, patientReference);
          addEntryToBundle(bundle, systProcedure);

          var procedureReference = new Reference("Procedure/" + systProcedure.getId());

          if (syst.getMengeSubstanz() != null) {
            var systMedicationStatement =
                systemischeTherapieMedicationStatementMapper.map(
                    syst, patientReference, procedureReference);
            // TODO: updated if the mapper returnss a List<> instead
            for (var resource :
                systMedicationStatement.getEntry().stream().map(e -> e.getResource()).toList()) {
              addEntryToBundle(bundle, resource);
            }
          }
        }

        // Strahlenterhapie
        if (meldung.getST() != null) {
          var stProcedure = strahlentherapieMapper.map(meldung.getST(), patientReference);
          addEntryToBundle(bundle, stProcedure);
        }
      }
    }

    return bundle;
  }

  private static Bundle addEntryToBundle(Bundle bundle, Resource resource) {
    var url = String.format("%s/%s", resource.getResourceType(), resource.getIdBase());
    bundle.addEntry().setResource(resource).getRequest().setMethod(HTTPVerb.PUT).setUrl(url);
    return bundle;
  }
}
