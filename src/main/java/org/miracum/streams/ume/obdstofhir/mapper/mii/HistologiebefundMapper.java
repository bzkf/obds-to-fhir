package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.PathologieTyp;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HistologiebefundMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(HistologiebefundMapper.class);

  public HistologiebefundMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public DiagnosticReport map(
      PathologieTyp pathologie,
      String meldungsId,
      Reference patient,
      Reference tumorkonferenz,
      Reference specimen) {
    Validate.notBlank(meldungsId);
    Objects.requireNonNull(pathologie, "pathologie must not be null");
    Objects.requireNonNull(pathologie.getBefundtext(), "Befundtext must not be null");
    verifyReference(patient, ResourceType.Patient);

    // DiagnosticReport füllen
    var diagnosticReport = new DiagnosticReport();

    var identifierValue = pathologie.getBefundID();

    if (!StringUtils.hasText(identifierValue)) {
      LOG.warn(
          "Befund_ID is unset. Defaulting to Meldung_ID as the identifier for the Histologiebefund.");

      identifierValue = meldungsId;
    }

    // Identifier
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getHistologiebefundDiagnosticReportId())
            .setValue(identifierValue);
    diagnosticReport.addIdentifier(identifier);
    // Id
    diagnosticReport.setId(computeResourceIdFromIdentifier(identifier));

    // basedOn Tumorkonferenz
    if (tumorkonferenz != null) {
      diagnosticReport.addBasedOn(tumorkonferenz);
    }
    // Meta
    diagnosticReport.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoBefund());

    // Status
    diagnosticReport.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);

    // code patholopgy-report
    var pathologyReport =
        new CodeableConcept(new Coding(fhirProperties.getSystems().getLoinc(), "22034-3", ""));
    diagnosticReport.setCode(pathologyReport);

    // Subject
    diagnosticReport.setSubject(patient);

    // Specimen
    diagnosticReport.addSpecimen(specimen);

    // conclusion
    diagnosticReport.setConclusion(pathologie.getBefundtext());

    return diagnosticReport;
  }
}
