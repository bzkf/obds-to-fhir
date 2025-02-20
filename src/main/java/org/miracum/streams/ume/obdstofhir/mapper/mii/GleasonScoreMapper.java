package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.ModulProstataTyp;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.springframework.stereotype.Service;

@Service
public class GleasonScoreMapper extends ObdsToFhirMapper {

  private static final Pattern GLEASON_SCORE_PATTERN = Pattern.compile("^(\\d{1,2})[ab]?$");
  private static final String GLEASON_SCORE_7_SNOMED = "57403001";
  private static final Map<String, String> GLEASON_SCORE_TO_SNOMED =
      Map.ofEntries(
          Map.entry("2", "49878003"),
          Map.entry("3", "46677009"),
          Map.entry("4", "18430005"),
          Map.entry("5", "74013009"),
          Map.entry("6", "84556003"),
          Map.entry("7", GLEASON_SCORE_7_SNOMED),
          Map.entry("7a", GLEASON_SCORE_7_SNOMED),
          Map.entry("7b", GLEASON_SCORE_7_SNOMED),
          Map.entry("8", "33013007"),
          Map.entry("9", "58925000"),
          Map.entry("10", "24514009"));

  protected GleasonScoreMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(
      ModulProstataTyp modulProstata, String meldungId, Reference patient, Reference condition) {
    Objects.requireNonNull(modulProstata);
    Objects.requireNonNull(modulProstata.getGleasonScore());
    Objects.requireNonNull(meldungId);
    Objects.requireNonNull(patient);
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");
    Objects.requireNonNull(condition);
    Validate.isTrue(
        Objects.equals(
            condition.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.CONDITION.toCode()),
        "The condition reference should point to a Condition resource");
    Validate.notBlank(modulProstata.getGleasonScore().getScoreErgebnis());

    var observation = new Observation();

    // TODO: define and add a profile URL
    observation.getMeta().addProfile("");

    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getGleasonScoreObservationId())
            .setValue("gleason-score-" + meldungId);
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    observation.setSubject(patient);

    observation.addFocus(condition);

    observation.setStatus(Observation.ObservationStatus.FINAL);

    observation.setCode(
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getSnomed())
                .setCode("385377005")
                .setDisplay("Gleason grade finding for prostatic cancer")));

    if (modulProstata.getAnlassGleasonScore() != null) {
      var coding =
          switch (modulProstata.getAnlassGleasonScore()) {
            case O ->
                new Coding(
                    fhirProperties.getSystems().getSnomed(), "65801008", "Excision (procedure)");
            case S ->
                new Coding(
                    fhirProperties.getSystems().getSnomed(), "86273004", "Biopsy (procedure)");
            case U ->
                new Coding(
                    fhirProperties.getSystems().getSnomed(),
                    "261665006",
                    "Unknown (qualifier value)");
          };

      observation.setMethod(new CodeableConcept(coding));
    }

    var scoreErgebnis = modulProstata.getGleasonScore().getScoreErgebnis();

    var scoreSnomed = GLEASON_SCORE_TO_SNOMED.get(scoreErgebnis);
    var scoreCoding =
        new Coding().setSystem(fhirProperties.getSystems().getSnomed()).setCode(scoreSnomed);

    var matcher = GLEASON_SCORE_PATTERN.matcher(scoreErgebnis);
    if (!matcher.find()) {
      throw new IllegalArgumentException(
          String.format(
              "Gleason score %s doesn't match the pattern %s",
              scoreErgebnis, GLEASON_SCORE_PATTERN.pattern()));
    }
    var gleasonScoreNumeric = matcher.group(1);
    scoreCoding.addExtension(
        fhirProperties.getExtensions().getOrdinalValue(), new IntegerType(gleasonScoreNumeric));

    observation.setValue(new CodeableConcept(scoreCoding));

    // TODO: component for primary/secondary score if set

    return observation;
  }
}
