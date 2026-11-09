package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.AllgemeinICDTyp;
import de.basisdatensatz.obds.v3.TodTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.*;
import org.hl7.fhir.r4.model.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TodMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(TodMapper.class);

  public TodMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  private Observation createTodObservation(
      String identifierSystem,
      String identifierValue,
      Reference patient,
      @Nullable Reference condition,
      @Nullable DateTimeType todesZeitpunkt,
      TodTyp tod) {

    var observation =
        createBaseObservation(
            Onkologie.Profiles.miiPrOnkoTod(),
            identifierSystem,
            identifierValue,
            patient,
            condition,
            todesZeitpunkt);

    // Code | 184305005 | Cause of death (observable entity)
    var snomedCode =
        fhirProperties
            .getCodings()
            .snomed()
            .setCode("184305005")
            .setDisplay("Cause of death (observable entity)");
    observation.setCode(new CodeableConcept(snomedCode));

    // Interpretation | Tod Tumorbedingt
    if (tod.getTodTumorbedingt() != null) {
      var interpretation =
          new CodeableConcept()
              .addCoding(
                  Onkologie.CodeSystems.MiiCsOnkoTod.fromValueOrThrow(
                          tod.getTodTumorbedingt().value())
                      .coding());
      observation.setInterpretation(Arrays.asList(interpretation));
    }

    return observation;
  }

  public List<Observation> map(
      @NonNull TodTyp tod,
      @NonNull Reference patient,
      @Nullable Reference condition,
      String tumorId,
      boolean fromAdditionalOnkoPatientInfo) {
    // Validation
    verifyReference(patient, ResourceType.Patient);
    if (condition != null) {
      // fine to be null if called from the extra patient table processor
      verifyReference(condition, ResourceType.Condition);
    }

    String identifierValue;
    String patientIdentifier =
        Objects.requireNonNull(
            patient.getIdentifier().getValue(), "Patient identifier must not be null");
    if (fromAdditionalOnkoPatientInfo) {
      identifierValue = String.format("%s-%s", patientIdentifier, "fromAdditionalOnkoPatientInfo");
    } else {
      identifierValue = String.format("%s-%s", patient.getIdentifier().getValue(), tumorId);
    }

    var observationList = new ArrayList<Observation>();

    var todesZeitpunkt = convertObdsDatumToDateTimeType(tod.getSterbedatum()).orElse(null);

    var todObsIdentifierSytstem =
        fhirProperties.getSystems().getIdentifiers().getTodObservationId();
    if (fromAdditionalOnkoPatientInfo) {
      todObsIdentifierSytstem =
          fhirProperties.getSystems().getIdentifiers().getTodObservationOnkostarPatientTableId();
    }

    if (tod.getMengeTodesursachen() != null) {
      for (AllgemeinICDTyp todesursache : tod.getMengeTodesursachen().getTodesursacheICD()) {

        // set identifier per todesursache
        var observation =
            createTodObservation(
                todObsIdentifierSytstem,
                identifierValue + "-" + todesursache.getCode(),
                patient,
                condition,
                todesZeitpunkt,
                tod);

        // Value | Todesursache(n) ICD10GM
        var gm =
            mapIcdToCodings(
                    todesursache.getCode(),
                    todesursache.getVersion(),
                    "Todesursachen_ICD_Version",
                    LOG)
                .getFirst();
        var value = new CodeableConcept(gm);
        // the profile fixes the system of every coding to ICD-10-GM, so a code from another
        // catalog can only be kept as text
        if (gm.getCode() == null) {
          value.setText(todesursache.getCode());
        }
        observation.setValue(value);

        observationList.add(observation);
      }

    } else {
      // if the ICD cause of death is unset, use the plain identifier without icd code
      // suffix
      observationList.add(
          createTodObservation(
              todObsIdentifierSytstem, identifierValue, patient, condition, todesZeitpunkt, tod));
    }

    return observationList;
  }
}
