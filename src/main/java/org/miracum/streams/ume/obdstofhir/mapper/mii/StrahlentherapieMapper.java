package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.STTyp;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
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
public class StrahlentherapieMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(StrahlentherapieMapper.class);

  public StrahlentherapieMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Procedure map(STTyp st, Reference subject) {
    // Input Validation
    Objects.requireNonNull(st);
    Objects.requireNonNull(subject);

    Validate.notBlank(st.getSTID(), "Required ST_ID is unset");
    Validate.isTrue(
      Objects.equals(
        subject.getReferenceElement().getResourceType(),
        ResourceType.PATIENT.toCode()
        ),
        "The subject reference should point to a Patient resource");

    // init resource type and add fhir profile
    var procedure = new Procedure();
    procedure.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoStrahlentherapie());

    // Mapping
    // TODO: can we be sure that this ST-ID is globally unique across all STs? -
    // if not we may instead need to construct the ID from the patient-id + others.
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getStrahlentherapieProcedureId())
            .setValue(st.getSTID());
    procedure.addIdentifier(identifier);
    procedure.setId(computeResourceIdFromIdentifier(identifier));

    // Status 1..1
    if (st.getMeldeanlass() == STTyp.Meldeanlass.BEHANDLUNGSENDE) {
      procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
    } else {
      procedure.setStatus(Procedure.ProcedureStatus.INPROGRESS);
    }

    procedure.setSubject(subject);

    // Code 1..1
    // ASK: how to know I should use dataAbsentReason?
    var dataAbsentExtension =
        new Extension(
            fhirProperties.getExtensions().getDataAbsentReason(),
            new CodeType("unknown")
        );
    var dataAbsentCode = new CodeType();
    dataAbsentCode.addExtension(dataAbsentExtension);

    var code = new CodeableConcept();
    code.addCoding().setSystem(fhirProperties.getSystems().getOps()).setCodeElement(dataAbsentCode);
    procedure.setCode(code);

    // Category 0..1
    var category = new CodeableConcept(
      new Coding(
        fhirProperties.getSystems().getSnomed(),
        "277132007",
        "Therapeutic procedure"
      )
    );
    procedure.setCategory(category);

    var dataAbsentConcept = new CodeableConcept();
    dataAbsentConcept.addExtension(dataAbsentExtension);
    st.
    // Performed[x] 1..1
    var performedStart = new DateTimeType();
    performedStart.addExtension(dataAbsentExtension);
    var performed = new Period().setStartElement(performedStart);
    procedure.setPerformed(performed);

    // for now, all required extensions are filled with sample values from
    // https://simplifier.net/guide/mii-ig-modul-onkologie-2024-de/MIIIGModulOnkologie/TechnischeImplementierung/FHIR-Profile/Strahlentherapie/Strahlentherapie-Procedure.page.md?version=current

    // ---Extensions---
    // Intention
    var intention = new CodeableConcept();
    intention
        .addCoding()
        .setSystem(fhirProperties.getSystems().getMiiCsOnkoIntention())
        .setCode("P"); //ASK: why "P" (palliativ?)
    procedure.addExtension(
      fhirProperties.getExtensions().getMiiExOnkoStrahlentherapieIntention(),
      intention
    );

    // Bestrahlung
    var bestrahlung =
        new Extension(fhirProperties.getExtensions().getMiiExOnkoStrahlentherapieBestrahlung());

    var applikationsart = new CodeableConcept();
    applikationsart
        .addCoding()
        .setSystem(fhirProperties.getSystems().getMiiCsOnkoStrahlentherapieApplikationsart())
        .setCode("P-ST");
    bestrahlung.addExtension("Applikationsart", applikationsart);

    var strahlenart = new CodeableConcept();
    strahlenart
        .addCoding()
        .setSystem(fhirProperties.getSystems().getMiiCsOnkoStrahlentherapieStrahlenart())
        .setCode("UH");
    bestrahlung.addExtension("Strahlenart", strahlenart);

    var zielgebiet = new CodeableConcept();
    zielgebiet
        .addCoding()
        .setSystem(fhirProperties.getSystems().getMiiCsOnkoStrahlentherapieZielgebiet())
        .setCode("3.4");
    bestrahlung.addExtension("Zielgebiet", zielgebiet);

    /* The extensions below are optional:
    var dataAbsentQuantity = new Quantity();
    dataAbsentQuantity.setSystem(fhirProperties.getSystems().getUcum());
    dataAbsentQuantity.setCodeElement(dataAbsentCode);
    var absentUnit = new StringType();
    absentUnit.addExtension(dataAbsentExtension);
    dataAbsentQuantity.setUnitElement(absentUnit);

    bestrahlung.addExtension("Zielgebiet_Lateralitaet", dataAbsentConcept);
    bestrahlung.addExtension("Gesamtdosis", dataAbsentQuantity);
    bestrahlung.addExtension("Einzeldosis", dataAbsentQuantity);
    bestrahlung.addExtension("Boost", dataAbsentConcept);
     */
    procedure.addExtension(bestrahlung);

    return procedure;
  }

}
