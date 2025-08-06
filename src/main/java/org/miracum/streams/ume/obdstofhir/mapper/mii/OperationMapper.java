package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OPTyp;
import de.basisdatensatz.obds.v3.OPTyp.MengeOPS.OPS;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
public class OperationMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(OperationMapper.class);

  public OperationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Procedure> map(OPTyp op, Reference subject, Reference condition) {

    Objects.requireNonNull(op, "OP must not be null");
    Validate.notBlank(op.getOPID(), "Required OP_ID is unset");

    verifyReference(subject, ResourceType.Patient);

    var distinctCodes = new HashMap<String, OPS>();
    for (var ops : op.getMengeOPS().getOPS()) {
      var code = ops.getCode();
      var version = ops.getVersion();
      if (!distinctCodes.containsKey(code)) {
        distinctCodes.put(code, ops);
      } else {
        var existing = distinctCodes.get(code);
        if (version.compareTo(existing.getVersion()) > 0) {
          LOG.warn(
              "Multiple OPS with code {} found. Updating version {} over version {}.",
              code,
              version,
              existing.getVersion());
          distinctCodes.put(code, ops);
        } else {
          LOG.warn(
              "Multiple OPS with code {} found. Keeping largest version {} over version {}.",
              code,
              existing.getVersion(),
              version);
        }
      }
    }

    // create a list to hold all single Procedure resources
    var procedureList = new ArrayList<Procedure>();

    for (var opsCode : distinctCodes.values()) {
      var procedure = new Procedure();

      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getOperationProcedureId())
              .setValue(op.getOPID() + opsCode.getCode());
      procedure.addIdentifier(identifier);
      procedure.setId(computeResourceIdFromIdentifier(identifier));

      procedure.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoOperation());

      // status
      procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);

      // code
      var coding =
          new Coding().setSystem(fhirProperties.getSystems().getOps()).setCode(opsCode.getCode());

      if (StringUtils.hasText(opsCode.getVersion())) {
        coding.setVersion(opsCode.getVersion());
      } else {
        LOG.warn("Unset version for OPS Code {} in OP {}", opsCode.getCode(), op.getOPID());
        var absentVersion = new StringType();
        absentVersion.addExtension(
            fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
        coding.setVersionElement(absentVersion);
      }

      procedure.setCode(new CodeableConcept(coding));

      // category: if OPS starts with 5, set 387713003; if first digit 1, set 165197003; else
      // 394841004
      // https://simplifier.net/packages/de.medizininformatikinitiative.kerndatensatz.prozedur/2024.0.0-ballot/files/2253000
      String firstDigitOPS = opsCode.getCode().substring(0, 1);
      String categoryCode;
      String categoryDisplay;

      switch (firstDigitOPS) {
        case "5":
          categoryCode = "387713003";
          categoryDisplay = "Surgical procedure";
          break;
        case "1":
          categoryCode = "165197003";
          categoryDisplay = "Diagnostic assessment";
          break;
        default:
          categoryCode = "394841004";
          categoryDisplay = "Other category";
          break;
      }

      procedure.setCategory(
          new CodeableConcept(
              fhirProperties
                  .getCodings()
                  .snomed()
                  .setCode(categoryCode)
                  .setDisplay(categoryDisplay)));

      // subject reference
      procedure.setSubject(subject);

      // performed
      var dataAbsentExtension =
          new Extension(
              fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
      var dataAbsentCode = new CodeType();
      dataAbsentCode.addExtension(dataAbsentExtension);

      var dateTime = convertObdsDatumToDateTimeType(op.getDatum());
      if (dateTime.isPresent()) {
        procedure.setPerformed(dateTime.get());
      } else {
        var performed = new DateTimeType();
        performed.addExtension(dataAbsentExtension);
        procedure.setPerformed(performed);
      }

      // ReasonReference
      procedure.addReasonReference(condition);

      // intention
      var intention = new CodeableConcept();
      intention
          .addCoding()
          .setSystem(fhirProperties.getSystems().getMiiCsOnkoIntention())
          .setCode(op.getIntention());

      var intentionExtens =
          new Extension()
              .setUrl(fhirProperties.getExtensions().getMiiExOnkoOPIntention())
              .setValue(intention);

      procedure.addExtension(intentionExtens);

      if (op.getResidualstatus() != null
          && op.getResidualstatus().getLokaleBeurteilungResidualstatus() != null) {
        // outcome
        var outcome = new CodeableConcept();
        outcome
            .addCoding()
            .setSystem(fhirProperties.getSystems().getMiiCsOnkoOperationResidualstatus())
            .setCode(op.getResidualstatus().getLokaleBeurteilungResidualstatus().value());

        procedure.setOutcome(outcome);
      }

      // add procedure to procedure list here
      procedureList.add(procedure);
    }

    return procedureList;
  }
}
