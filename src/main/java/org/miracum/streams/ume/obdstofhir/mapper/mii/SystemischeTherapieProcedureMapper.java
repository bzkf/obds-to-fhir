package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.SYSTTyp;
import de.basisdatensatz.obds.v3.SYSTTyp.Therapieart;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.lookup.obds.OPSTherapietypLookup;
import org.miracum.streams.ume.obdstofhir.lookup.obds.SnomedCtTherapietypLookup;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SystemischeTherapieProcedureMapper extends ObdsToFhirMapper {

  private static final Logger LOG =
      LoggerFactory.getLogger(SystemischeTherapieProcedureMapper.class);

  private static final String SNOMED_CT_SYST_CATEGORY = "18629005";

  public SystemischeTherapieProcedureMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Procedure map(SYSTTyp syst, Reference subject) {
    Objects.requireNonNull(syst, "Systemtherapie must not be null");
    Validate.notBlank(syst.getSYSTID(), "Required SYST_ID is unset");
    verifyReference(subject, ResourceType.Patient);

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
    if (null != syst.getTherapieart()
        && List.of(Therapieart.WW, Therapieart.WS, Therapieart.AS)
            .contains(syst.getTherapieart())) {
      code.addCoding(
          fhirProperties
              .getCodings()
              .snomed()
              .setCode(SnomedCtTherapietypLookup.lookupCode(syst.getTherapieart())));
    } else if (null != syst.getTherapieart()
        && null != OPSTherapietypLookup.lookupCode(syst.getTherapieart())) {
      code.addCoding(
          fhirProperties
              .getCodings()
              .ops()
              .setCode(OPSTherapietypLookup.lookupCode(syst.getTherapieart())));
    } else {
      LOG.warn("Unknown, unset or unsupported Therapieart: {}", syst.getTherapieart());
      code.addCoding(fhirProperties.getCodings().ops().setCodeElement(dataAbsentCode));
    }

    if (null != syst.getTherapieart()) {
      code.addCoding()
          .setSystem(fhirProperties.getSystems().getMiiCsOnkoSystemischeTherapieArt())
          .setCode(syst.getTherapieart().value());
    }

    procedure.setCode(code);

    /*
     * Kategorie als SNOMED - Code
     * Kategorie für Systemische Therapien 18629005 | Administration of drug or medicament (procedure)
     * Kategorie für Abwartende Therapien : keine (kein geeignetes Parent-Konzept, Suche direkt über Kodierung empfohlen)
     */
    if (syst.getTherapieart() != null
        && !List.of(Therapieart.WW, Therapieart.WS, Therapieart.AS)
            .contains(syst.getTherapieart())) {
      var category = fhirProperties.getCodings().snomed().setCode(SNOMED_CT_SYST_CATEGORY);
      // category is optional, so it's fine to keep it unset if no appropriate snomed code is found
      // for the therapy type
      procedure.setCategory(new CodeableConcept(category));
    }

    var intention = new CodeableConcept();
    intention
        .addCoding()
        .setSystem(fhirProperties.getSystems().getMiiCsOnkoIntention())
        .setCode(syst.getIntention()); // Direct mapping from oBDS value
    procedure.addExtension(
        fhirProperties.getExtensions().getMiiExOnkoSystemischeTherapieIntention(), intention);

    var stellungZurOp =
        new Coding()
            .setSystem(fhirProperties.getSystems().getMiiCsOnkoTherapieStellungzurop())
            .setCode(syst.getStellungOP());
    procedure
        .addExtension()
        .setUrl(fhirProperties.getExtensions().getMiiExOnkoStrahlentherapieStellungzurop())
        .setValue(new CodeableConcept(stellungZurOp));

    if (null != syst.getEndeGrund()) {
      var outcome = new CodeableConcept();
      outcome
          .addCoding()
          .setSystem(fhirProperties.getSystems().getMiiCsTherapieGrundEnde())
          .setCode(syst.getEndeGrund().value());
      procedure.setOutcome(outcome);
    }

    return procedure;
  }
}
