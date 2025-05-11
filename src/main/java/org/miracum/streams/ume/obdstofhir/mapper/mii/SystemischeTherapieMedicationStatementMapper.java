package org.miracum.streams.ume.obdstofhir.mapper.mii;

import com.google.common.hash.Hashing;
import de.basisdatensatz.obds.v3.SYSTTyp;
import de.basisdatensatz.obds.v3.SYSTTyp.Meldeanlass;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SystemischeTherapieMedicationStatementMapper extends ObdsToFhirMapper {

  private static final Logger LOG =
      LoggerFactory.getLogger(SystemischeTherapieMedicationStatementMapper.class);

  public SystemischeTherapieMedicationStatementMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<MedicationStatement> map(SYSTTyp syst, Reference patient, Reference procedure) {
    Objects.requireNonNull(syst, "Systemtherapie must not be null");

    Validate.notBlank(syst.getSYSTID(), "Required SYST_ID is unset");
    verifyReference(patient, ResourceType.Patient);
    verifyReference(procedure, ResourceType.Procedure);

    var result = new ArrayList<MedicationStatement>();

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
          substanzId = createSubstanzIdFromPlain(substanz.getBezeichnung());
          systMedicationStatement.setMedication(
              new CodeableConcept().setText(substanz.getBezeichnung()));
        }

        // TODO: can we be sure that this SYST-ID is globally unqiue across all SYSTs?
        // if not we may instead need to construct the ID from the patient-id + others.
        var identifier =
            new Identifier()
                .setSystem(
                    fhirProperties.getSystems().getSystemischeTherapieMedicationStatementId())
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

        result.add(systMedicationStatement);
      }
    }

    return result;
  }

  private String createSubstanzIdFromPlain(String plainName) {
    Validate.notBlank(plainName, "Required substance name is unset");
    return String.format(
        "%s_%s",
        plainName.replaceAll("[^A-Za-z0-9]+", ""),
        Hashing.sha256().hashString(plainName, StandardCharsets.UTF_8).toString().substring(0, 4));
  }
}
