package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OPTyp;
import de.basisdatensatz.obds.v3.OPTyp.MengeOPS.OPS;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OperationMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(OperationMapper.class);

  public OperationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Procedure> map(
      @NonNull OPTyp op, @NonNull Reference subject, @NonNull Reference condition) {
    Validate.notBlank(op.getOPID(), "Required OP_ID is unset");
    verifyReference(subject, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    MDC.put("OP_ID", op.getOPID());

    if (op.getMengeOPS() == null || op.getMengeOPS().getOPS().isEmpty()) {
      LOG.warn("No OPS codes set for OP. Not creating Procedure resources.");
      return List.of();
    }

    var distinctCodes = new HashMap<String, OPS>();
    for (var ops : op.getMengeOPS().getOPS()) {
      var code = ops.getCode();
      var version = ops.getVersion();
      if (!distinctCodes.containsKey(code)) {
        distinctCodes.put(code, ops);
      } else {
        var existing = distinctCodes.get(code);

        if (version == null) {
          LOG.debug(
              "Multiple OPS with code {} found, but new version is unset. Keeping one with existing version {}.",
              code,
              existing.getVersion());
          continue;
        }

        if (version.compareTo(existing.getVersion()) > 0) {
          LOG.debug(
              "Multiple OPS with code {} found. Updating version {} over version {}.",
              code,
              version,
              existing.getVersion());
          distinctCodes.put(code, ops);
        } else {
          LOG.debug(
              "Multiple OPS with code {} found. Keeping largest version {} over version {}.",
              code,
              existing.getVersion(),
              version);
        }
      }
    }
    // create a list to hold all single Procedure resources
    var procedureList = new ArrayList<Procedure>();

    // create Parent Procedure
    var parentProcedure = new Procedure();
    // TODO: welchen Identifier für die Parent ID?
    var parentIdentifer =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getOperationProcedureId())
            .setValue(slugifier.slugify(op.getOPID()));
    parentProcedure.addIdentifier(parentIdentifer);
    parentProcedure.setId(computeResourceIdFromIdentifier(parentIdentifer));
    parentProcedure.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoOperation());
    parentProcedure.setStatus(Procedure.ProcedureStatus.COMPLETED);

    // TODO: welche Version nehmen wir für den Snomed Code?
    var parentCoding =
        new Coding().setSystem(fhirProperties.getSystems().getSnomed()).setCode("71388002");
    parentProcedure.setCode(new CodeableConcept(parentCoding));

    // TODO: category: weglassen oder auf etwas bestimmtes setzen?
    parentProcedure.setCategory(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("394841004")
                .setDisplay("Other category")));

    parentProcedure.setSubject(subject);
    var dateTime = convertObdsDatumToDateTimeType(op.getDatum());
    if (dateTime.isPresent()) {
      parentProcedure.setPerformed(dateTime.get());
    } else {
      var performed = new DateTimeType();
      performed.addExtension(
          fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
      parentProcedure.setPerformed(performed);
    }
    // ReasonReference
    parentProcedure.addReasonReference(condition);

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

    parentProcedure.addExtension(intentionExtens);

    if (op.getResidualstatus() != null
        && op.getResidualstatus().getLokaleBeurteilungResidualstatus() != null) {
      // outcome
      var outcome = new CodeableConcept();
      outcome
          .addCoding()
          .setSystem(fhirProperties.getSystems().getMiiCsOnkoOperationResidualstatus())
          .setCode(op.getResidualstatus().getLokaleBeurteilungResidualstatus().value());

      parentProcedure.setOutcome(outcome);
    }
    procedureList.add(parentProcedure);

    // create child procedures
    for (var opsCode : distinctCodes.values()) {
      var procedure = new Procedure();
      MDC.put("opsCode", opsCode.getCode());

      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getOperationProcedureId())
              .setValue(slugifier.slugify(op.getOPID() + "-" + opsCode.getCode()));
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
        LOG.debug("Unset version for OPS Code");
        var absentVersion = new StringType();
        absentVersion.addExtension(
            fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
        coding.setVersionElement(absentVersion);
      }

      procedure.setCode(new CodeableConcept(coding));

      // category: if OPS starts with 5, set 387713003; if first digit 1, set
      // 165197003; else
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
      if (dateTime.isPresent()) {
        procedure.setPerformed(dateTime.get());
      } else {
        var performed = new DateTimeType();
        performed.addExtension(
            fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
        procedure.setPerformed(performed);
      }

      // ReasonReference
      procedure.addReasonReference(condition);

      // intention
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

      MDC.remove("opsCode");

      var parentReference = new Reference("Procedure/" + parentProcedure.getId());
      procedure.addPartOf(parentReference);
      // add procedure to procedure list here
      procedureList.add(procedure);
    }

    MDC.remove("OP_ID");

    return procedureList;
  }
}
