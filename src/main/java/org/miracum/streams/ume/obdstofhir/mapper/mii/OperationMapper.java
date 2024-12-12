package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OPTyp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OperationMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(OperationMapper.class);

  @Autowired
  public OperationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Procedure> map(OPTyp op, Reference subject, Reference condition) {

    Objects.requireNonNull(op, "OP must not be null");
    Objects.requireNonNull(subject, "Reference must not be null");
    Validate.notBlank(op.getOPID(), "Required OP_ID is unset");

    // create a list to hold all single Procedure resources
    var procedureList = new ArrayList<Procedure>();

    LOG.info("Number of OPS codes: {}", op.getMengeOPS().getOPS().size());

    for (var opsCode : op.getMengeOPS().getOPS()) {
      var i = 0;
      System.out.println(opsCode);
      var procedure = new Procedure();

      // identifier, meta
      // to do: brauch ich hier die patid? dann muss ich meine Parameter nochmal überdenken weil im
      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getProcedureId())
              .setValue(op.getOPID() + opsCode.getCode());
      procedure.addIdentifier(identifier);
      procedure.setId(
          computeResourceIdFromIdentifier(
              identifier)); // das hier macht den Hash vom identifier.value

      procedure.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoOperation());

      // status
      procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);

      // code
      CodeableConcept opsCodeableConcept =
          new CodeableConcept()
              .addCoding(
                  new Coding()
                      .setSystem(fhirProperties.getSystems().getOps())
                      .setCode(opsCode.getCode())
                      .setVersion(opsCode.getVersion()));

      procedure.setCode(opsCodeableConcept);

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
          new CodeableConcept()
              .addCoding(
                  new Coding()
                      .setSystem(fhirProperties.getSystems().getSnomed())
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

      // add procedure to procedure list here
      procedureList.add(procedure);
      LOG.info("Created Procedure with ID: {} and Code: {}", procedure.getId(), opsCode.getCode());
    }
    LOG.debug("Mapped procedures: {}", procedureList.size());
    return procedureList;
  }
}
