package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.SYSTTyp;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationStatement;
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
        } else {
          substanzId = substanz.getBezeichnung().replaceAll("[^A-Za-z0-9]", "");
        }

        // TODO: can we be sure that this SYST-ID is globally unqiue across all SYSTs?
        // if not we may instead need to construct the ID from the patient-id + others.
        var identifier =
            new Identifier()
                .setSystem(fhirProperties.getSystems().getSystemischeTherapieProcedureId())
                .setValue(String.format("%s_%s", syst.getSYSTID(), substanzId));
        systMedicationStatement.addIdentifier(identifier);
        systMedicationStatement.setId(computeResourceIdFromIdentifier(identifier));

        bundle = addResourceAsEntryInBundle(bundle, systMedicationStatement);
      }
    }

    return bundle;
  }
}
