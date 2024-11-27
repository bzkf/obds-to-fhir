package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OPTyp;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.Validate;

@Service
public class OperationMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(OperationMapper.class);

  @Autowired
  public OperationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Procedure map(OPTyp op, Reference subject, Reference condition) {

    Objects.requireNonNull(op, "OP must not be null");
    Objects.requireNonNull(subject, "Reference must not be null");
    Validate.notBlank(op.getOPID(), "Required OP_ID is unset");

    var procedure = new Procedure();

    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getProcedureId())
            .setValue(op.getOPID());
    procedure.addIdentifier(identifier);
    procedure.setId(computeResourceIdFromIdentifier(identifier));

    procedure.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoOperation());

    // I would suggestion to always use COMPLETED here, any objections?
    procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);

    // there could be multiple OPS codes here --> create one coding for each, iterate through ops
    // codes+
    // do I need distinct values = remove duplicates?
    CodeableConcept opsCodeableConcept = new CodeableConcept();
    for (var opsCode : op.getMengeOPS().getOPS()) {

      Coding ops = new Coding(fhirProperties.getSystems().getOps(), opsCode.getCode(), "");
      ops.setVersion(opsCode.getVersion());
      opsCodeableConcept.addCoding(ops);
    }

    // set all Codings gathered in the codeableconcept to code
    procedure.setCode(opsCodeableConcept);

    procedure.setSubject(subject);

    // taken from SystTherapieProcedureMapper
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

    var intention = new CodeableConcept();
    intention
        .addCoding()
        .setSystem(fhirProperties.getSystems().getMiiCsOnkoIntention())
        .setCode(op.getIntention()); // Direct mapping from oBDS value
    procedure.addExtension(fhirProperties.getExtensions().getMiiExOnkoOPIntention(), intention);

    return procedure;
  }
}
