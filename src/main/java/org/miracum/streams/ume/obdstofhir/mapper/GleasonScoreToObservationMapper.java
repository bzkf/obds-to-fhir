package org.miracum.streams.ume.obdstofhir.mapper;

import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Reference;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsObservationMapper.ModulProstataMappingParams;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID.AnlassGleasonScore;
import org.miracum.streams.ume.obdstofhir.model.Meldeanlass;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GleasonScoreToObservationMapper extends ObdsToFhirMapper {

  // we currently ignore the 7a/7b difference. We could map it when using
  // valueCodeableConcept with https://loinc.org/94734-1
  private static final Pattern gleasonErgebnisPattern = Pattern.compile("^(\\d{1,2})[a-z]?$");

  public GleasonScoreToObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(
      ModulProstataMappingParams modulProstataParams,
      Reference patientReference,
      String metaSource) {
    var modulProstata = modulProstataParams.modulProstata();
    if (modulProstata.getGleasonScore().isEmpty()) {
      throw new IllegalArgumentException("Modul_Prostata_GleasonScore is unset.");
    }

    var gleasonScoreErgebnis = modulProstata.getGleasonScore().get().getGleasonScoreErgebnis();

    if (!StringUtils.hasText(gleasonScoreErgebnis)) {
      throw new IllegalArgumentException("GleasonScoreErgebnis is null or empty");
    }

    var gleasonScoreObservation = new Observation();
    gleasonScoreObservation.getMeta().setSource(metaSource);

    if (modulProstataParams.meldeanlass() == Meldeanlass.STATUSAENDERUNG) {
      gleasonScoreObservation.setStatus(ObservationStatus.AMENDED);
    } else {
      gleasonScoreObservation.setStatus(ObservationStatus.FINAL);
    }

    var identifierValue =
        String.format(
            "%s-%s-%s-gleason-score",
            modulProstataParams.patientId(),
            modulProstata.getAnlassGleasonScore().orElse(AnlassGleasonScore.UNBEKANNT),
            modulProstataParams.baseId());

    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getGleasonScoreObservationId())
            .setValue(identifierValue);

    gleasonScoreObservation.setId(computeResourceIdFromIdentifier(identifier));

    gleasonScoreObservation.setSubject(patientReference);

    // TODO: if we can't assume that the AnlassGleasonScore is always set, we may have to
    // approximate it
    // based on whether the Score is from the OP, Diagnose, or Verlauf element.
    if (modulProstata.getAnlassGleasonScore().isPresent()) {
      var code =
          switch (modulProstata.getAnlassGleasonScore().get()) {
            case OP ->
                new Coding(
                    fhirProperties.getSystems().getSnomed(), "65801008", "Excision (procedure)");
            case STANZE ->
                new Coding(
                    fhirProperties.getSystems().getSnomed(), "86273004", "Biopsy (procedure)");
              // feels a bit ugly to set an empty coding as the "unset".
            case UNBEKANNT ->
            new Coding(
                fhirProperties.getSystems().getSnomed(), "261665006", "Unknown (qualifier value)");
          };

      gleasonScoreObservation.setMethod(new CodeableConcept(code));
    }

    var gleasonConcept = new CodeableConcept();
    gleasonConcept
        .addCoding()
        .setSystem(fhirProperties.getSystems().getLoinc())
        .setCode("35266-6")
        .setVersion("2.77")
        .setDisplay(fhirProperties.getDisplay().getGleasonScoreLoinc());
    gleasonScoreObservation.setCode(gleasonConcept);

    if (modulProstataParams.baseDatum() != null) {
      gleasonScoreObservation.setEffective(modulProstataParams.baseDatum());
    }

    // if it's a biopsy and a more accurate biopsy date is available, use that
    if (modulProstata.getAnlassGleasonScore().isPresent()
        && modulProstata.getAnlassGleasonScore().get() == AnlassGleasonScore.STANZE
        && modulProstata.getDatumStanzen().isPresent()) {
      gleasonScoreObservation.setEffective(
          ObdsToFhirMapper.convertObdsDateToDateTimeType(modulProstata.getDatumStanzen().get()));
    }

    // should we also map the "source" gradings?
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
