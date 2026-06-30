package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OPTyp;
import de.basisdatensatz.obds.v3.OPTyp.MengeOPS.OPS;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.dizuker.tofhir.IdUtils;
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

    var distinctCodes = new HashMap<String, OPS>();
    // if no OPS codes are set, it's fine for the distinctCodes list to be empty
    // we will then only create the parent procedure, not the child ones with
    // any codes.
    if (op.getMengeOPS() != null) {
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
    }
    // create a list to hold all single Procedure resources
    var procedureList = new ArrayList<Procedure>();

    // create Parent Procedure
    var parentProcedure = new Procedure();

    var parentIdentifer =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getIdentifiers().getOperationProcedureId())
            .setValue(slugifier.slugify(op.getOPID()));
    parentProcedure.addIdentifier(parentIdentifer);
    parentProcedure.setId(IdUtils.fromIdentifier(parentIdentifer));
    parentProcedure.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoOperation());
    parentProcedure.setStatus(Procedure.ProcedureStatus.COMPLETED);

    parentProcedure.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("387713003")
                .setDisplay("Surgical procedure (procedure)")));

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
    var intention =
        new CodeableConcept()
            .addCoding(
                Onkologie.CodeSystems.MiiCsOnkoIntention.fromValue(op.getIntention()).coding());

    var intentionExtens =
        new Extension()
            .setUrl(Onkologie.Extensions.miiExOnkoOperationIntention())
            .setValue(intention);

    parentProcedure.addExtension(intentionExtens);

    if (op.getResidualstatus() != null
        && op.getResidualstatus().getLokaleBeurteilungResidualstatus() != null) {
      // outcome
      var outcome =
          new CodeableConcept()
              .addCoding(
                  Onkologie.CodeSystems.MiiCsOnkoResidualstatus.fromValue(
                          op.getResidualstatus().getLokaleBeurteilungResidualstatus().value())
                      .coding());

      parentProcedure.setOutcome(outcome);
    }
    procedureList.add(parentProcedure);

    // create child procedures
    for (var opsCode : distinctCodes.values()) {
      var procedure = new Procedure();
      MDC.put("opsCode", opsCode.getCode());

      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getIdentifiers().getOperationProcedureId())
              .setValue(slugifier.slugify(op.getOPID() + "-" + opsCode.getCode()));
      procedure.addIdentifier(identifier);
      procedure.setId(IdUtils.fromIdentifier(identifier));

      procedure.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoOperation());

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
        var outcome =
            new CodeableConcept()
                .addCoding(
                    Onkologie.CodeSystems.MiiCsOnkoResidualstatus.fromValue(
                            op.getResidualstatus().getLokaleBeurteilungResidualstatus().value())
                        .coding());

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
