package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OPTyp;
import de.basisdatensatz.obds.v3.OPTyp.MengeOPS.OPS;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.dizuker.tofhir.FhirExtensions.DataAbsentReason;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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

  private static final Comparator<OPS> BY_VERSION =
      Comparator.comparing(OPS::getVersion, Comparator.nullsFirst(Comparator.naturalOrder()));

  public OperationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  /**
   * @return the parent Procedure, followed by one Procedure per distinct OPS code
   */
  public List<Procedure> map(
      @NonNull OPTyp op, @NonNull Reference subject, @NonNull Reference condition) {
    Validate.notBlank(op.getOPID(), "Required OP_ID is unset");
    verifyReference(subject, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    MDC.put("OP_ID", op.getOPID());

    // create a list to hold all single Procedure resources
    var procedureList = new ArrayList<Procedure>();

    // create Parent Procedure
    var parentProcedure = createBaseProcedure(op, op.getOPID(), subject, condition);

    parentProcedure.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("387713003")
                .setDisplay("Surgical procedure (procedure)")));

    if (op.getModulDarm() != null) {
      // the KRK Anastomoseninsuffizienz Observation references this (parent) Procedure as its
      // focus, which the profile restricts to MII_PR_Onko_KRK_Operation.
      parentProcedure.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoKrkOperation());
    }

    procedureList.add(parentProcedure);

    // create child procedures
    for (var opsCode : distinctOps(op)) {
      MDC.put("opsCode", opsCode.getCode());

      var procedure =
          createBaseProcedure(op, op.getOPID() + "-" + opsCode.getCode(), subject, condition);

      // code
      var coding =
          new Coding().setSystem(fhirProperties.getSystems().getOps()).setCode(opsCode.getCode());

      if (StringUtils.hasText(opsCode.getVersion())) {
        coding.setVersion(opsCode.getVersion());
      } else {
        LOG.debug("Unset version for OPS Code");
        var absentVersion = new StringType();
        absentVersion.addExtension(DataAbsentReason.unknown());
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

      MDC.remove("opsCode");

      procedure.addPartOf(new Reference("Procedure/" + parentProcedure.getId()));
      // add procedure to procedure list here
      procedureList.add(procedure);
    }

    MDC.remove("OP_ID");

    return procedureList;
  }

  /** Creates a Procedure with what the parent one and the ones per OPS code have in common. */
  private Procedure createBaseProcedure(
      OPTyp op, String identifierValue, Reference subject, Reference condition) {
    var procedure = new Procedure();

    setIdentifierAndId(
        procedure,
        fhirProperties.getSystems().getIdentifiers().getOperationProcedureId(),
        identifierValue);
    procedure.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoOperation());
    procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
    procedure.setSubject(subject);
    procedure.setPerformed(
        convertObdsDatumToDateTimeType(op.getDatum()).orElseGet(ObdsToFhirMapper::absentDateTime));

    // ReasonReference
    procedure.addReasonReference(condition);

    // intention
    var intention = Onkologie.CodeSystems.MiiCsOnkoIntention.fromValueOrThrow(op.getIntention());
    procedure.addExtension(Onkologie.Extensions.miiExOnkoOperationIntention(intention));

    // urgency (KR6 ArtEingriff)
    if (op.getModulDarm() != null && op.getModulDarm().getArtEingriff() != null) {
      var urgency =
          Onkologie.CodeSystems.MiiCsOnkoOperationUrgency.fromValueOrThrow(
              op.getModulDarm().getArtEingriff());
      procedure.addExtension(Onkologie.Extensions.miiExOnkoOperationUrgency(urgency));
    }

    // outcome
    if (op.getResidualstatus() != null
        && op.getResidualstatus().getLokaleBeurteilungResidualstatus() != null) {
      procedure.setOutcome(
          new CodeableConcept()
              .addCoding(
                  Onkologie.CodeSystems.MiiCsOnkoResidualstatus.fromValueOrThrow(
                          op.getResidualstatus().getLokaleBeurteilungResidualstatus().value())
                      .coding()));
    }

    return procedure;
  }

  /**
   * One OPS per code, the one with the highest version if there are duplicates. If no OPS codes are
   * set, it's fine for this to be empty: then only the parent procedure is created.
   */
  private static Collection<OPS> distinctOps(OPTyp op) {
    var distinctCodes = new HashMap<String, OPS>();
    if (op.getMengeOPS() != null) {
      for (var ops : op.getMengeOPS().getOPS()) {
        var existing = distinctCodes.get(ops.getCode());
        if (existing == null || BY_VERSION.compare(ops, existing) > 0) {
          distinctCodes.put(ops.getCode(), ops);
        }
      }
    }
    return distinctCodes.values();
  }
}
