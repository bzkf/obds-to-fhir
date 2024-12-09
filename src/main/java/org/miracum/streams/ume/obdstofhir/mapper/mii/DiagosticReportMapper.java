package org.miracum.streams.ume.obdstofhir.mapper.mii;

import com.google.common.hash.Hashing;
import de.basisdatensatz.obds.v3.HistologieTyp;
import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.SYSTTyp;
import de.basisdatensatz.obds.v3.SYSTTyp.Meldeanlass;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DiagosticReportMapper extends ObdsToFhirMapper {

  private static final Logger LOG =
    LoggerFactory.getLogger(SystemischeTherapieProcedureMapper.class);

  public DiagosticReportMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<DiagnosticReport> map(
    OBDS.MengePatient.Patient.MengeMeldung meldungen, Reference patient) {
    Objects.requireNonNull(meldungen, "Meldungen must not be null");
    Objects.requireNonNull(patient, "Reference to Patient must not be null");
    Validate.isTrue(
      Objects.equals(
        patient.getReferenceElement().getResourceType(),
        Enumerations.ResourceType.PATIENT.toCode()),
      "The subject reference should point to a Patient resource");

    var result = new ArrayList<DiagnosticReport>();
    var diagnosticReport = new DiagnosticReport();
    for (var meldung : meldungen.getMeldung()) {
      if (meldung.getPathologie() != null && meldung.getPathologie().getBefundtext() != null) {
        // DiagnosticReport füllen (evt. ID setzen, basedOb nicht befüllbar?)

        //Meta
        diagnosticReport.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoBefund());

        //Status
        diagnosticReport.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);

        //code patholopgy-report
        var pathology_report = new CodeableConcept(new Coding(fhirProperties.getSystems().getLoinc(),"22034-3",""));
        diagnosticReport.setCode(pathology_report);

        //Subject
        diagnosticReport.setSubject(patient);

        //conclusion
        diagnosticReport.setConclusion(meldung.getPathologie().getBefundtext());
      }
    }
    return result;
  }
}
