package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.SYSTTyp;
import de.basisdatensatz.obds.v3.SYSTTyp.Meldeanlass;

import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SystemischeTherapieMedicationStatementMapper extends ObdsToFhirMapper {

  private static final Logger LOG =
      LoggerFactory.getLogger(SystemischeTherapieProcedureMapper.class);

  public SystemischeTherapieMedicationStatementMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Bundle map(SYSTTyp syst, Reference patient, Reference procedure) {
    Objects.requireNonNull(syst, "Systemtherapie must not be null");
    Objects.requireNonNull(patient, "Reference to Patient must not be null");
    Objects.requireNonNull(procedure, "Reference to Procedure must not be null");

    Validate.notBlank(syst.getSYSTID(), "Required SYST_ID is unset");
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(), ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");
    Validate.isTrue(
        Objects.equals(
            procedure.getReferenceElement().getResourceType(), ResourceType.PROCEDURE.toCode()),
        "The subject reference should point to a Procedure resource");

    var bundle = new Bundle();
    bundle.setType(Bundle.BundleType.TRANSACTION);

    for (var substanz : syst.getMengeSubstanz().getSubstanz()) {
      var systMedicationStatement = new MedicationStatement();
      systMedicationStatement
          .getMeta()
          .addProfile(fhirProperties.getProfiles().getMiiPrMedicationStatement());

      if ((null != substanz.getATC() && StringUtils.hasText(substanz.getATC().getCode()))
          || StringUtils.hasText(substanz.getBezeichnung())) {
        var substanzId = "";
        if (null != substanz.getATC() && StringUtils.hasText(substanz.getATC().getCode())) {
          substanzId = substanz.getATC().getCode();
          var atcCoding =
              new Coding(
                  fhirProperties.getSystems().getAtcBfarm(), substanz.getATC().getCode(), "");
          systMedicationStatement.setMedication(new CodeableConcept(atcCoding));
        } else {
          substanzId = substanz.getBezeichnung().replaceAll("[^A-Za-z0-9]", "");
          systMedicationStatement.setMedication(
              new CodeableConcept().setText(substanz.getBezeichnung()));
        }

        // TODO: can we be sure that this SYST-ID is globally unqiue across all SYSTs?
        // if not we may instead need to construct the ID from the patient-id + others.
        var identifier =
            new Identifier()
                .setSystem(fhirProperties.getSystems().getSystemischeTherapieProcedureId())
                .setValue(String.format("%s_%s", syst.getSYSTID(), substanzId));
        systMedicationStatement.addIdentifier(identifier);
        systMedicationStatement.setId(computeResourceIdFromIdentifier(identifier));

        // Status / Effective
        var meldeanlass = syst.getMeldeanlass();
        var period = new Period();
        if (meldeanlass == Meldeanlass.BEHANDLUNGSENDE) {
          systMedicationStatement.setStatus(
              MedicationStatement.MedicationStatementStatus.COMPLETED);
          convertObdsDatumToDateTimeType(syst.getBeginn())
              .ifPresent(start -> period.setStart(start.getValue(), start.getPrecision()));
          convertObdsDatumToDateTimeType(syst.getEnde())
              .ifPresent(end -> period.setEnd(end.getValue(), end.getPrecision()));
        } else {
          systMedicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.ACTIVE);
          convertObdsDatumToDateTimeType(syst.getBeginn())
              .ifPresent(start -> period.setStart(start.getValue(), start.getPrecision()));
        }
        systMedicationStatement.setEffective(period);

        // Subject
        systMedicationStatement.setSubject(patient);

        // Part of
        systMedicationStatement.setPartOf(List.of(procedure));

        bundle = addResourceAsEntryInBundle(bundle, systMedicationStatement);
      }
    }

    return bundle;
  }
}
