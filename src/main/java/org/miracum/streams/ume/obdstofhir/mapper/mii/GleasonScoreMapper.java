package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.AnlassGleasonScoreTyp;
import de.basisdatensatz.obds.v3.ModulProstataTyp;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.xml.datatype.XMLGregorianCalendar;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.util.Strings;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GleasonScoreMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(GleasonScoreMapper.class);

  private static final Pattern GLEASON_SCORE_PATTERN = Pattern.compile("^(\\d{1,2})[ab]?$");
  private static final Pattern GLEASON_SCORE_7_SUFFIX_PATTERN =
      Pattern.compile("^7(?<suffix>[ab])$");
  private static final Map<String, String> GLEASON_SCORE_TO_SNOMED =
      Map.ofEntries(
          Map.entry("2", "49878003"),
          Map.entry("3", "46677009"),
          Map.entry("4", "18430005"),
          Map.entry("5", "74013009"),
          Map.entry("6", "84556003"),
          Map.entry("7", "57403001"),
          Map.entry("7a", "57403001"),
          Map.entry("7b", "57403001"),
          Map.entry("8", "33013007"),
          Map.entry("9", "58925000"),
          Map.entry("10", "24514009"));

  protected GleasonScoreMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(
      ModulProstataTyp modulProstata,
      String meldungId,
      Reference patient,
      Reference condition,
      XMLGregorianCalendar opDate) {
    Validate.isTrue(
        modulProstata.getAnlassGleasonScore() == AnlassGleasonScoreTyp.O,
        "Attempt to map with an OP date, but the Anlass Gleason Score is not O.");

    var observation = map(modulProstata, meldungId, patient, condition);
    if (observation.hasEffective()) {
      LOG.warn("Overwriting already present effective date with OP date.");
    }

    convertObdsDatumToDateTimeType(opDate).ifPresent(observation::setEffective);

    return observation;
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

    convertObdsDatumToDateTimeType(modulProstata.getDatumStanzen())
        .ifPresent(observation::setEffective);

    observation.setCode(
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getSnomed())
                .setCode("385377005")
                .setDisplay("Gleason grade finding for prostatic cancer (finding)")));

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
    if (Strings.isBlank(scoreErgebnis)) {
      LOG.warn(
          "Gleason score ergebnis is missing. "
              + "Attempting to reconstruct from the primary and secondary patterns.");

      var gradPrimaer = modulProstata.getGleasonScore().getGradPrimaer();
      var gradSekundaer = modulProstata.getGleasonScore().getGradSekundaer();
      Validate.notBlank(gradPrimaer);
      Validate.notBlank(gradSekundaer);

      var totalGrade = Integer.parseInt(gradPrimaer) + Integer.parseInt(gradSekundaer);

      scoreErgebnis = String.valueOf(totalGrade);

      if (gradPrimaer.equals("3") && gradSekundaer.equals("4")) {
        scoreErgebnis += "a";
      } else if (gradPrimaer.equals("4") && gradSekundaer.equals("3")) {
        scoreErgebnis += "b";
      }

      LOG.debug(
          "Reconstructed Gleason score ergebnis: {} + {} = {}",
          gradPrimaer,
          gradSekundaer,
          scoreErgebnis);
    }

    String gradPrimaerDerived = null;
    String gradSekundaerDerived = null;

    var score7Matcher = GLEASON_SCORE_7_SUFFIX_PATTERN.matcher(scoreErgebnis);
    if (score7Matcher.matches()) {
      var suffix = score7Matcher.group("suffix");
      if (suffix.equals("a")) {
        gradPrimaerDerived = "3";
        gradSekundaerDerived = "4";
      } else if (suffix.equals("b")) {
        gradPrimaerDerived = "4";
        gradSekundaerDerived = "3";
      }
    }

    var matcher = GLEASON_SCORE_PATTERN.matcher(scoreErgebnis);
    if (!matcher.find()) {
      throw new IllegalArgumentException(
          String.format(
              "Gleason score %s doesn't match the pattern %s",
              scoreErgebnis, GLEASON_SCORE_PATTERN.pattern()));
    }

    var scoreSnomed = GLEASON_SCORE_TO_SNOMED.get(scoreErgebnis);
    var scoreCoding =
        new Coding().setSystem(fhirProperties.getSystems().getSnomed()).setCode(scoreSnomed);

    var gleasonScoreNumeric = matcher.group(1);
    scoreCoding.addExtension(
        fhirProperties.getExtensions().getOrdinalValue(), new IntegerType(gleasonScoreNumeric));

    var concept = new CodeableConcept(scoreCoding).setText(scoreErgebnis);
    observation.setValue(concept);

    if (modulProstata.getGleasonScore().getGradPrimaer() != null || gradPrimaerDerived != null) {
      var value =
          modulProstata.getGleasonScore().getGradPrimaer() != null
              ? modulProstata.getGleasonScore().getGradPrimaer()
              : gradPrimaerDerived;

      var component = createGleasonScoreComponent(value, "384994009", "Primary Gleason pattern");
      observation.addComponent(component);
    }

    if (modulProstata.getGleasonScore().getGradSekundaer() != null
        || gradSekundaerDerived != null) {
      var value =
          modulProstata.getGleasonScore().getGradSekundaer() != null
              ? modulProstata.getGleasonScore().getGradSekundaer()
              : gradSekundaerDerived;

      var component = createGleasonScoreComponent(value, "384995005", "Secondary Gleason pattern");
      observation.addComponent(component);
    }

    return observation;
  }

  private ObservationComponentComponent createGleasonScoreComponent(
      String value, String code, String display) {
    var coding =
        new Coding()
            .setSystem(fhirProperties.getSystems().getSnomed())
            .setCode(code)
            .setDisplay(display);
    return new ObservationComponentComponent()
        .setCode(new CodeableConcept(coding))
        .setValue(new IntegerType(value));
  }
}
