package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OBDS;
import java.util.ArrayList;
import java.util.List;
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

  public List<Bundle> map(OBDS obds) {

    var bundles = new ArrayList<Bundle>();

    for (var obdsPatient : obds.getMengePatient().getPatient()) {
      var bundle = new Bundle();
      bundle.setType(BundleType.TRANSACTION);

      var meldungen = obdsPatient.getMengeMeldung().getMeldung();

      // Patient
      var patient = patientMapper.map(obdsPatient, meldungen);
      var patientReference = new Reference("Patient/" + patient.getId());
      addEntryToBundle(bundle, patient);

      bundle.setId(patient.getId());

      for (var meldung : meldungen) {
        // Diagnose
        if (meldung.getDiagnose() != null) {
          var condition = conditionMapper.map(meldung, patientReference, obds.getMeldedatum());
          addEntryToBundle(bundle, condition);
        }

        // Systemtherapie
        if (meldung.getSYST() != null) {
          var syst = meldung.getSYST();

          var systProcedure = systemischeTherapieProcedureMapper.map(syst, patientReference);
          addEntryToBundle(bundle, systProcedure);

          var procedureReference = new Reference("Procedure/" + systProcedure.getId());

          if (syst.getMengeSubstanz() != null) {
            var systMedicationStatements =
                systemischeTherapieMedicationStatementMapper.map(
                    syst, patientReference, procedureReference);

            for (var resource : systMedicationStatements) {
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

      bundles.add(bundle);
    }

    return bundles;
  }

  private static Bundle addEntryToBundle(Bundle bundle, Resource resource) {
    var url = String.format("%s/%s", resource.getResourceType(), resource.getIdBase());
    bundle.addEntry().setResource(resource).getRequest().setMethod(HTTPVerb.PUT).setUrl(url);
    return bundle;
  }
}
