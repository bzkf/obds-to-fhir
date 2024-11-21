package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.SYSTTyp;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SystemischeTherapieProcedureMapper extends ObdsToFhirMapper {

  private static final Logger LOG =
      LoggerFactory.getLogger(SystemischeTherapieProcedureMapper.class);

  public SystemischeTherapieProcedureMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Procedure map(SYSTTyp syst, Reference subject) {
    Objects.requireNonNull(syst, "Systemtherapie must not be null");
    Objects.requireNonNull(subject, "Reference must not be null");

    Validate.notBlank(syst.getSYSTID(), "Required SYST_ID is unset");
    Validate.isTrue(
        Objects.equals(
            subject.getReferenceElement().getResourceType(), ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");

    var procedure = new Procedure();
    procedure.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoSystemischeTherapie());

    // TODO: can we be sure that this SYST-ID is globally unqiue across all SYSTs? -
    // if not we may instead need to construct the ID from the patient-id + others.
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getSystemischeTherapieProcedureId())
            .setValue(syst.getSYSTID());
    procedure.addIdentifier(identifier);
    procedure.setId(computeResourceIdFromIdentifier(identifier));

    // Status
    if (syst.getMeldeanlass() == SYSTTyp.Meldeanlass.BEHANDLUNGSENDE) {
      procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
    } else {
      procedure.setStatus(Procedure.ProcedureStatus.INPROGRESS);
    }

    procedure.setSubject(subject);

    var dataAbsentExtension =
        new Extension(
            fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
    var dataAbsentCode = new CodeType();
    dataAbsentCode.addExtension(dataAbsentExtension);

    if (syst.getBeginn() == null && syst.getEnde() == null) {
      var performedStart = new DateTimeType();
      performedStart.addExtension(dataAbsentExtension);
      var performed = new Period().setStartElement(performedStart);
      procedure.setPerformed(performed);
    } else {
      var performed = new Period();
      convertObdsDatumToDateTimeType(syst.getBeginn()).ifPresent(performed::setStartElement);
      convertObdsDatumToDateTimeType(syst.getEnde()).ifPresent(performed::setEndElement);
      procedure.setPerformed(performed);
    }

    var code = new CodeableConcept();
    // Always add absent OPS
    code.addCoding().setSystem(fhirProperties.getSystems().getOps()).setCodeElement(dataAbsentCode);
    if (null != syst.getTherapieart()) {
      code.addCoding()
          .setSystem(fhirProperties.getSystems().getMiiCsOnkoSystemischeTherapieArt())
          .setCode(syst.getTherapieart().value());
    }

    procedure.setCode(code);

    var category = new CodeableConcept();
    category
        .addCoding()
        .setSystem(fhirProperties.getSystems().getSnomed())
        .setCodeElement(dataAbsentCode);
    procedure.setCategory(category);

    var intention = new CodeableConcept();
    intention
        .addCoding()
        .setSystem(fhirProperties.getSystems().getMiiCsOnkoIntention())
        .setCode(syst.getIntention()); // Direct mapping from oBDS value
    procedure.addExtension(
        fhirProperties.getExtensions().getMiiExOnkoSystemischeTherapieIntention(), intention);

    return procedure;
  }
}
