package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.SYSTTyp;
import de.basisdatensatz.obds.v3.SYSTTyp.Therapieart;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.dizuker.tofhir.FhirExtensions.DataAbsentReason;
import io.github.dizuker.tofhir.IdUtils;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
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

  public Procedure map(SYSTTyp syst, Reference subject, Reference condition) {
    Objects.requireNonNull(syst, "Systemtherapie must not be null");
    Validate.notBlank(syst.getSYSTID(), "Required SYST_ID is unset");
    verifyReference(subject, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    var procedure = new Procedure();
    procedure.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoSystemischeTherapie());

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties.getSystems().getIdentifiers().getSystemischeTherapieProcedureId())
            .setValue(slugifier.slugify(syst.getSYSTID()));
    procedure.addIdentifier(identifier);
    procedure.setId(IdUtils.fromIdentifier(identifier));

    // Status
    if (syst.getMeldeanlass() == SYSTTyp.Meldeanlass.BEHANDLUNGSENDE) {
      procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
    } else {
      procedure.setStatus(Procedure.ProcedureStatus.INPROGRESS);
    }

    procedure.setSubject(subject);

    procedure.addReasonReference(condition);

    var dataAbsentExtension = DataAbsentReason.unknown();

    if (syst.getBeginn() == null && syst.getEnde() == null) {
      var performedStart = new DateTimeType();
      performedStart.addExtension(dataAbsentExtension);
      var performed = new Period().setStartElement(performedStart);
      procedure.setPerformed(performed);
    } else {
      var performed = new Period();
      convertObdsDatumToDateTimeType(syst.getBeginn())
          .ifPresentOrElse(
              performed::setStartElement,
              () -> LOG.warn("No start date set for SYST_ID={}", syst.getSYSTID()));
      convertObdsDatumToDateTimeType(syst.getEnde()).ifPresent(performed::setEndElement);
      procedure.setPerformed(performed);
    }

    if (syst.getTherapieart() != null) {
      var categoryAndCode = lookupCategoryAndCode(syst.getTherapieart());

      if (categoryAndCode.category() != null) {
        procedure.setCategory(new CodeableConcept(categoryAndCode.category()));
      }

      if (categoryAndCode.code() != null) {
        procedure.setCode(new CodeableConcept(categoryAndCode.code()));
      }

      var therapieartCodeableConcept = procedure.getCode();
      therapieartCodeableConcept.addCoding(
          Onkologie.CodeSystems.MiiCsOnkoTherapieTyp.fromValue(syst.getTherapieart().value())
              .coding());
    } else {
      LOG.warn("Therapieart is unset for SYST_ID={}", syst.getSYSTID());
      var absentOpsCoding = fhirProperties.getCodings().ops();
      absentOpsCoding.getCodeElement().addExtension(dataAbsentExtension);
      procedure.setCode(new CodeableConcept().addCoding(absentOpsCoding));

      var absentSnomedCoding = fhirProperties.getCodings().snomed();
      absentSnomedCoding.getCodeElement().addExtension(dataAbsentExtension);
      procedure.setCategory(new CodeableConcept().addCoding(absentSnomedCoding));
    }

    if (syst.getProtokoll() != null) {
      procedure.addUsedCode().setText(syst.getProtokoll());
    }

    var intention =
        new CodeableConcept()
            .addCoding(
                Onkologie.CodeSystems.MiiCsOnkoIntention.fromValue(syst.getIntention()).coding());
    procedure.addExtension(Onkologie.Extensions.miiExOnkoSystemischeTherapieIntention(), intention);

    var stellungZurOp =
        Onkologie.CodeSystems.MiiCsOnkoTherapieStellungzurop.fromValue(syst.getStellungOP());
    procedure
        .addExtension()
        .setUrl(Onkologie.Extensions.miiExOnkoSystemischeTherapieStellungzurop())
        .setValue(new CodeableConcept(stellungZurOp.coding()));

    if (null != syst.getEndeGrund()) {
      var outcome =
          new CodeableConcept()
              .addCoding(
                  Onkologie.CodeSystems.MiiCsOnkoTherapieEndeGrund.fromValue(
                          syst.getEndeGrund().value())
                      .coding());
      procedure.setOutcome(outcome);
    }

    return procedure;
  }

  private static record CategoryAndCode(Coding category, Coding code) {}

  private CategoryAndCode lookupCategoryAndCode(Therapieart therapieart) {
    Coding category = null;
    Coding code = null;

    var medicationCategory =
        fhirProperties
            .getCodings()
            .snomed()
            .setCode("18629005")
            .setDisplay("Administration of drug or medicament (procedure)");

    switch (therapieart) {
      case CH, IM, CI, CIZ, CZ, IZ, ZS -> {
        category = medicationCategory;
        code =
            fhirProperties
                .getCodings()
                .ops()
                .setCode("8-54")
                .setDisplay(
                    "Zytostatische Chemotherapie, Immuntherapie und antiretrovirale Therapie");
      }
      case SZ -> {
        category = medicationCategory;
        code =
            fhirProperties
                .getCodings()
                .ops()
                .setCode("8-86")
                .setDisplay("Therapie mit besonderen Zellen und Blutbestandteilen");
      }
      case HO -> {
        category = medicationCategory;
        code =
            fhirProperties
                .getCodings()
                .ops()
                .setCode("6-00")
                .setDisplay("Applikation von Medikamenten");
      }
      case WW ->
          code =
              fhirProperties
                  .getCodings()
                  .snomed()
                  .setCode("373818007")
                  .setDisplay("No anti-cancer treatment - watchful waiting (finding)");
      case AS ->
          code =
              fhirProperties
                  .getCodings()
                  .snomed()
                  .setCode("424313000")
                  .setDisplay("Active surveillance (regime/therapy)");
      case WS ->
          code =
              fhirProperties
                  .getCodings()
                  .snomed()
                  .setCode("310341009")
                  .setDisplay("Follow-up (wait and see) (finding)");
      case SO -> {
        category =
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("394841004")
                .setDisplay("Other category (qualifier value)");
        code =
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("74964007")
                .setDisplay("Other (qualifier value)");
      }
    }

    return new CategoryAndCode(category, code);
  }
}
