package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.AllgemeinerLeistungszustand;
import de.basisdatensatz.obds.v3.DatumTagOderMonatOderJahrOderNichtGenauTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.dizuker.tofhir.IdUtils;
import io.github.dizuker.tofhir.ReferenceUtils;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

@Service
public class LeistungszustandMapper extends ObdsToFhirMapper {

  protected LeistungszustandMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      AllgemeinerLeistungszustand allgemeinerLeistungszustand,
      String meldungsId,
      XMLGregorianCalendar datum,
      Reference patient,
      Reference condition) {
    var date = convertObdsDatumToDateTimeType(datum);
    return map(allgemeinerLeistungszustand, meldungsId, date.orElse(null), patient, condition);
  }

  public List<Observation> map(
      AllgemeinerLeistungszustand allgemeinerLeistungszustand,
      String meldungsId,
      DatumTagOderMonatOderJahrOderNichtGenauTyp datum,
      Reference patient,
      Reference condition) {
    var date = convertObdsDatumToDateTimeType(datum);
    return map(allgemeinerLeistungszustand, meldungsId, date.orElse(null), patient, condition);
  }

  /**
   * Maps a Karnofsky performance status to its own Observation. oBDS records ECOG grades and
   * Karnofsky percentages in the same element, so a Karnofsky input yields this Observation in
   * addition to the ECOG one; see {@link #map}.
   *
   * @return empty if the source recorded an ECOG grade rather than a Karnofsky percentage
   */
  private Optional<Observation> mapKarnofsky(
      AllgemeinerLeistungszustand allgemeinerLeistungszustand,
      String meldungsId,
      DateTimeType effective,
      Reference patient,
      Reference condition) {

    Objects.requireNonNull(allgemeinerLeistungszustand);
    Objects.requireNonNull(meldungsId);
    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    var karnofskyValue =
        Onkologie.CodeSystems.MiiCsOnkoAllgemeinerLeistungszustandKarnofsky.fromValue(
            allgemeinerLeistungszustand.value());
    if (karnofskyValue.isEmpty()) {
      return Optional.empty();
    }

    var observation = new Observation();

    observation
        .getMeta()
        .addProfile(Onkologie.Profiles.miiPrOnkoAllgemeinerLeistungszustandKarnofsky());

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties
                    .getSystems()
                    .getIdentifiers()
                    .getAllgemeinerLeistungszustandKarnofskyObservationId())
            .setValue(slugifier.slugify("KARNOFSKY-" + meldungsId));
    observation.addIdentifier(identifier);
    observation.setId(IdUtils.fromIdentifier(identifier));

    observation.setSubject(patient);

    observation.setStatus(Observation.ObservationStatus.FINAL);

    var codeConcept = new CodeableConcept();
    codeConcept.addCoding(
        fhirProperties
            .getCodings()
            .snomed()
            .setCode("761869008")
            .setDisplay("Karnofsky Performance Status score (observable entity)"));
    codeConcept.addCoding(
        fhirProperties
            .getCodings()
            .loinc()
            .setCode("89243-0")
            .setDisplay("Karnofsky Performance Status score"));

    observation.setCode(codeConcept);

    observation.setEffective(effective);

    observation.setFocus(Collections.singletonList(condition));

    observation.setValue(new CodeableConcept().addCoding(karnofskyValue.get().coding()));

    return Optional.of(observation);
  }

  /**
   * Maps an oBDS performance status to its FHIR Observations.
   *
   * @return the ECOG Observation alone if the source recorded an ECOG grade; the ECOG Observation
   *     and the Karnofsky Observation it derives from if the source recorded a Karnofsky percentage
   */
  public List<Observation> map(
      AllgemeinerLeistungszustand allgemeinerLeistungszustand,
      String meldungsId,
      DateTimeType effective,
      Reference patient,
      Reference condition) {

    Objects.requireNonNull(allgemeinerLeistungszustand);
    Objects.requireNonNull(meldungsId);
    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    var observation = new Observation();

    observation.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoAllgemeinerLeistungszustandEcog());

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties
                    .getSystems()
                    .getIdentifiers()
                    .getAllgemeinerLeistungszustandEcogObservationId())
            .setValue(slugifier.slugify("ECOG-" + meldungsId));
    observation.addIdentifier(identifier);
    observation.setId(IdUtils.fromIdentifier(identifier));

    observation.setSubject(patient);

    observation.setStatus(Observation.ObservationStatus.FINAL);

    var codeConcept = new CodeableConcept();
    codeConcept.addCoding(
        fhirProperties
            .getCodings()
            .snomed()
            .setCode("423740007")
            .setDisplay(
                "Eastern Cooperative Oncology Group performance status (observable entity)"));
    codeConcept.addCoding(
        fhirProperties
            .getCodings()
            .loinc()
            .setCode("89262-0")
            .setDisplay("ECOG Performance Status [Interpretation]"));

    observation.setCode(codeConcept);

    observation.setEffective(effective);

    observation.setFocus(Collections.singletonList(condition));

    var miiValue = new Coding();
    var loincValue = fhirProperties.getCodings().loinc();
    switch (allgemeinerLeistungszustand) {
      case ECOG_0, KARNOFSKY_90, KARNOFSKY_100:
        miiValue = Onkologie.CodeSystems.MiiCsOnkoAllgemeinerLeistungszustandEcog._0.coding();
        loincValue
            .setCode("LA9622-7")
            .setDisplay(
                "Fully active, able to carry on all pre-disease performance without restriction");
        break;
      case ECOG_1, KARNOFSKY_70, KARNOFSKY_80:
        miiValue = Onkologie.CodeSystems.MiiCsOnkoAllgemeinerLeistungszustandEcog._1.coding();
        loincValue
            .setCode("LA9623-5")
            .setDisplay(
                "Restricted in physically strenuous activity but ambulatory and able to carry out work of a light or sedentary nature, e.g., light house work, office work");
        break;
      case ECOG_2, KARNOFSKY_50, KARNOFSKY_60:
        miiValue = Onkologie.CodeSystems.MiiCsOnkoAllgemeinerLeistungszustandEcog._2.coding();
        loincValue
            .setCode("LA9624-3")
            .setDisplay(
                "Ambulatory and capable of all selfcare but unable to carry out any work activities. Up and about more than 50% of waking hours");
        break;
      case ECOG_3, KARNOFSKY_30, KARNOFSKY_40:
        miiValue = Onkologie.CodeSystems.MiiCsOnkoAllgemeinerLeistungszustandEcog._3.coding();
        loincValue
            .setCode("LA9625-0")
            .setDisplay(
                "Capable of only limited selfcare, confined to bed or chair more than 50% of waking hours");
        break;
      case ECOG_4, KARNOFSKY_10, KARNOFSKY_20:
        miiValue = Onkologie.CodeSystems.MiiCsOnkoAllgemeinerLeistungszustandEcog._4.coding();
        loincValue
            .setCode("LA9626-8")
            .setDisplay(
                "Completely disabled. Cannot carry on any selfcare. Totally confined to bed or chair");
        break;
      case U:
      default:
        miiValue = Onkologie.CodeSystems.MiiCsOnkoAllgemeinerLeistungszustandEcog.U.coding();
        break;
    }

    var valueConcept = new CodeableConcept().addCoding(miiValue);
    if (loincValue.hasCode()) {
      valueConcept.addCoding(loincValue);
    }

    observation.setValue(valueConcept);

    var karnofsky =
        mapKarnofsky(allgemeinerLeistungszustand, meldungsId, effective, patient, condition);
    if (karnofsky.isEmpty()) {
      return List.of(observation);
    }

    observation.setDerivedFrom(List.of(ReferenceUtils.createReferenceTo(karnofsky.get())));
    return List.of(observation, karnofsky.get());
  }
}
