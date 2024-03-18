package org.miracum.streams.ume.obdstofhir.mapper;

import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Reference;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung.Menge_OP.OP.Modul_Prostata;
import org.miracum.streams.ume.obdstofhir.model.Meldeanlass;
import org.springframework.util.StringUtils;

public class GleasonScoreToObservationMapper extends ObdsToFhirMapper {

  private static final Pattern gleasonErgebnisPattern = Pattern.compile("^(\\d{1,2})[a-z]?$");

  public GleasonScoreToObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  // TODO: we might need more than just the opId, since the module also exists
  // in Diagnose, Verlauf, Pathologie
  public Observation map(
      Modul_Prostata modulProstata,
      String patientId,
      String opId,
      DateTimeType effective,
      Meldeanlass meldeanlass,
      Reference patientReference) {
    var gleasonScoreErgebnis = modulProstata.getGleasonScore().getGleasonScoreErgebnis();

    if (!StringUtils.hasLength(gleasonScoreErgebnis)) {
      throw new IllegalArgumentException("gleasonScoreErgebnis is null or empty");
    }

    var gleasonScoreObservation = new Observation();

    if (meldeanlass == Meldeanlass.STATUSAENDERUNG) {
      gleasonScoreObservation.setStatus(ObservationStatus.AMENDED);
    } else {
      gleasonScoreObservation.setStatus(ObservationStatus.FINAL);
    }

    var identifierValue = patientId + "-op-gleason-score-" + opId;

    // TODO: if we also set Observation.identifier, we need to make sure to cryptohash/pseudonymize
    // it since it contains
    // the patient id in plaintext
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getGleasonScoreObservationId())
            .setValue(identifierValue);

    gleasonScoreObservation.setId(computeResourceIdFromIdentifier(identifier));

    gleasonScoreObservation.setSubject(patientReference);

    var gleasonConcept = new CodeableConcept();
    gleasonConcept
        .addCoding()
        .setSystem(fhirProperties.getSystems().getLoinc())
        .setCode("35266-6")
        .setVersion("2.77")
        .setDisplay(fhirProperties.getDisplay().getGleasonScoreLoinc());
    gleasonScoreObservation.setCode(gleasonConcept);

    gleasonScoreObservation.setEffective(effective);

    // Offen ob diese auch gemappt werden sollten als primär/sekundär Component
    // s.a. https://build.fhir.org/ig/davidhay25/actnow/Observation-ExObservationGleason.json.html
    // var gleasonGradPrimaer = modulProstata.getGleasonScore().getGleasonGradPrimaer();
    // var gleasonGradSekundär = modulProstata.getGleasonScore().getGleasonGradSekundaer();

    var matcher = gleasonErgebnisPattern.matcher(gleasonScoreErgebnis);

    if (!matcher.find()) {
      throw new IllegalArgumentException(
          String.format(
              "Gleason score %s doesn't match the pattern %s",
              gleasonScoreErgebnis, gleasonErgebnisPattern.pattern()));
    }
    var gleasonScoreDigits = matcher.group(1);

    gleasonScoreObservation.setValue(new IntegerType(gleasonScoreDigits));

    return gleasonScoreObservation;
  }
}
