package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.SYSTTyp;
import de.basisdatensatz.obds.v3.SYSTTyp.Meldeanlass;
import de.basisdatensatz.obds.v3.SYSTTyp.MengeSubstanz;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.SubstanzToAtcMapper;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.ArrayList;
import java.util.Collection;
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
public class SystemischeTherapieMedicationStatementMapper extends ObdsToFhirMapper {

  private static final Logger LOG =
      LoggerFactory.getLogger(SystemischeTherapieMedicationStatementMapper.class);

  private final SubstanzToAtcMapper substanzToAtcMapper;

  public SystemischeTherapieMedicationStatementMapper(
      FhirProperties fhirProperties, SubstanzToAtcMapper substanzToAtcMapper) {
    super(fhirProperties);

    this.substanzToAtcMapper = substanzToAtcMapper;
  }

  public List<MedicationStatement> map(
      @NonNull SYSTTyp syst,
      @NonNull Reference patient,
      @NonNull Reference procedure,
      @NonNull Reference primaryConditionReference) {

    Validate.notBlank(syst.getSYSTID(), "Required SYST_ID is unset");
    verifyReference(patient, ResourceType.Patient);
    verifyReference(procedure, ResourceType.Procedure);
    verifyReference(primaryConditionReference, ResourceType.Condition);

    MDC.put("SYST_ID", syst.getSYSTID());

    // the condition reference is made from the patient_id + tumor_id so we can be
    // sure that the created resource is now unique per patient, tumor, systid
    var identifierBase =
        String.format(
            "%s-%s", primaryConditionReference.getReferenceElement().getIdPart(), syst.getSYSTID());

    var result = new ArrayList<MedicationStatement>();

    var distinctSubstanzen = getDistinctSubstanzen(syst.getMengeSubstanz().getSubstanz());

    for (var substanz : distinctSubstanzen) {
      var systMedicationStatement = new MedicationStatement();
      systMedicationStatement
          .getMeta()
          .addProfile(fhirProperties.getProfiles().getMiiPrOnkoSystemischeTherapieMedikation());

      if ((substanz.getATC() == null || !StringUtils.hasText(substanz.getATC().getCode()))
          && !StringUtils.hasText(substanz.getBezeichnung())) {
        LOG.warn("Substanz in Systemische Therapie is missing ATC code or Bezeichnung.");
      }

      var substanzId = "";
      if (null != substanz.getATC() && StringUtils.hasText(substanz.getATC().getCode())) {
        substanzId = substanz.getATC().getCode();
        var atcCoding =
            new Coding(fhirProperties.getSystems().getAtcBfarm(), substanz.getATC().getCode(), "");
        systMedicationStatement.setMedication(new CodeableConcept(atcCoding));
      } else {
        // previously, we overwrote substanzId with the ATC code if it was present in the mapping,
        // however this will also end up changing the resource ID, in particular if we update
        // the mapping table, then multiple MedicationStatements would be created for the same
        // Substanz (a new one with the ATC code as its identifier, the old one with just the
        // Substanz).
        substanzId = substanz.getBezeichnung();

        var mappedCode = substanzToAtcMapper.getCode(substanz.getBezeichnung());
        if (mappedCode.isPresent()) {
          var atcCode = mappedCode.get();
          var atcCoding = new Coding(fhirProperties.getSystems().getAtcBfarm(), atcCode, "");
          systMedicationStatement.setMedication(
              new CodeableConcept(atcCoding).setText(substanz.getBezeichnung()));
        } else {
          LOG.warn(
              "Substanz in Systemische Therapie with Bezeichnung '{}' could not be mapped to an ATC code.",
              substanz.getBezeichnung());
          var absentCodeableConcept = new CodeableConcept();
          absentCodeableConcept.setText(substanz.getBezeichnung());
          var absentCode = new CodeType();
          absentCode.addExtension(
              fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("as-text"));
          absentCodeableConcept
              .addCoding()
              .setSystem(fhirProperties.getSystems().getAtcBfarm())
              .setCodeElement(absentCode);

          systMedicationStatement.setMedication(absentCodeableConcept);
        }
      }

      var identifier =
          new Identifier()
              .setSystem(
                  fhirProperties
                      .getSystems()
                      .getIdentifiers()
                      .getSystemischeTherapieMedicationStatementId())
              .setValue(slugifier.slugify(identifierBase + "-" + substanzId));
      systMedicationStatement.addIdentifier(identifier);
      systMedicationStatement.setId(computeResourceIdFromIdentifier(identifier));

      // Status
      var meldeanlass = syst.getMeldeanlass();
      var period = new Period();
      if (meldeanlass == Meldeanlass.BEHANDLUNGSENDE) {
        systMedicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.COMPLETED);
      } else {
        systMedicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.ACTIVE);
      }

      convertObdsDatumToDateTimeType(syst.getBeginn())
          .ifPresent(start -> period.setStart(start.getValue(), start.getPrecision()));
      convertObdsDatumToDateTimeType(syst.getEnde())
          .ifPresent(end -> period.setEnd(end.getValue(), end.getPrecision()));

      systMedicationStatement.setEffective(period);

      // Subject
      systMedicationStatement.setSubject(patient);

      // Part of
      systMedicationStatement.setPartOf(List.of(procedure));

      systMedicationStatement.addReasonReference(primaryConditionReference);

      result.add(systMedicationStatement);
    }

    MDC.remove("SYST_ID");

    return result;
  }

  private static Collection<MengeSubstanz.Substanz> getDistinctSubstanzen(
      List<MengeSubstanz.Substanz> substanzen) {
    var substanzMap = new HashMap<String, MengeSubstanz.Substanz>();

    for (var substanz : substanzen) {
      String key;
      if (substanz.getATC() != null && StringUtils.hasText(substanz.getATC().getCode())) {
        key = substanz.getATC().getCode();
      } else {
        key = substanz.getBezeichnung();
      }

      if (!substanzMap.containsKey(key)) {
        substanzMap.put(key, substanz);
      } else {
        LOG.debug("Duplicate Substanz found with key: {}", key);

        if (substanz.getATC() != null && StringUtils.hasText(substanz.getATC().getCode())) {
          // note that version is a mandatory field in oBDS
          var version = substanz.getATC().getVersion();

          var existing = substanzMap.get(key);
          // getATC() should always return a non-null value, if not, then someone placed
          // an ATC-code as a Bezeichnung.
          var existingVersion = existing.getATC().getVersion();

          if (version == null || existingVersion == null) {
            LOG.error(
                "ATC version is missing when comparing duplicates. Defaulting to keeping the current one.");
            continue;
          }

          if (version.compareTo(existingVersion) > 0) {
            LOG.debug(
                "Duplicate Substanzen with ATC code {} found. Updating version {} over version {}.",
                key,
                version,
                existingVersion);
            substanzMap.put(key, substanz);
          }
        }
      }
    }

    return substanzMap.values();
  }
}
