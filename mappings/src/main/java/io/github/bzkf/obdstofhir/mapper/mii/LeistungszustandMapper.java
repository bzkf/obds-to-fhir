package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.AllgemeinerLeistungszustand;
import de.basisdatensatz.obds.v3.DatumTagOderMonatOderJahrOderNichtGenauTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.Collections;
import java.util.Objects;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

@Service
public class LeistungszustandMapper extends ObdsToFhirMapper {

  protected LeistungszustandMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(
      AllgemeinerLeistungszustand allgemeinerLeistungszustand,
      String meldungsId,
      XMLGregorianCalendar datum,
      Reference patient,
      Reference condition) {
    var date = convertObdsDatumToDateTimeType(datum);
    return map(allgemeinerLeistungszustand, meldungsId, date.orElse(null), patient, condition);
  }

  public Observation map(
      AllgemeinerLeistungszustand allgemeinerLeistungszustand,
      String meldungsId,
      DatumTagOderMonatOderJahrOderNichtGenauTyp datum,
      Reference patient,
      Reference condition) {
    var date = convertObdsDatumToDateTimeType(datum);
    return map(allgemeinerLeistungszustand, meldungsId, date.orElse(null), patient, condition);
  }

  public Observation map(
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

    observation.setMeta(
        new Meta()
            .addProfile(
                fhirProperties.getProfiles().getMiiPrOnkoAllgemeinerLeistungszustandEcog()));

    var identifier =
        new Identifier()
            .setSystem(
                fhirProperties
                    .getSystems()
                    .getIdentifiers()
                    .getAllgemeinerLeistungszustandEcogObservationId())
            .setValue(slugifier.slugify("ECOG-" + meldungsId));
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

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

    var miiValue =
        new Coding()
            .setSystem(fhirProperties.getSystems().getMiiCsOnkoAllgemeinerLeistungszustandEcog());
    var loincValue = fhirProperties.getCodings().loinc();
    switch (allgemeinerLeistungszustand) {
      case ECOG_0, KARNOFSKY_90, KARNOFSKY_100:
        miiValue.setCode("0");
        miiValue.setDisplay(
            "Normale, uneingeschränkte Aktivität wie vor der Erkrankung (90 - 100 % "
                + "nach Karnofsky)");
        loincValue
            .setCode("LA9622-7")
            .setDisplay(
                "Fully active, able to carry on all pre-disease performance without restriction");
        break;
      case ECOG_1, KARNOFSKY_70, KARNOFSKY_80:
        miiValue.setCode("1");
        miiValue.setDisplay(
            "Einschränkung bei körperlicher Anstrengung, aber gehfähig; leichte "
                + "körperliche Arbeit bzw. Arbeit im Sitzen (z. B. leichte Hausarbeit oder Büroarbeit) "
                + "möglich (70 - 80 % nach Karnofsky)");
        loincValue
            .setCode("LA9623-5")
            .setDisplay(
                "Restricted in physically strenuous activity but ambulatory and able to carry out work of a light or sedentary nature, e.g., light house work, office work");
        break;
      case ECOG_2, KARNOFSKY_50, KARNOFSKY_60:
        miiValue.setCode("2");
        miiValue.setDisplay(
            "Gehfähig, Selbstversorgung möglich, aber nicht arbeitsfähig; kann mehr "
                + "als 50 % der Wachzeit aufstehen (50 - 60 % nach Karnofsky)");
        loincValue
            .setCode("LA9624-3")
            .setDisplay(
                "Ambulatory and capable of all selfcare but unable to carry out any work activities. Up and about more than 50% of waking hours");
        break;
      case ECOG_3, KARNOFSKY_30, KARNOFSKY_40:
        miiValue.setCode("3");
        miiValue.setDisplay(
            "Nur begrenzte Selbstversorgung möglich; ist 50 % oder mehr der Wachzeit"
                + " an Bett oder Stuhl gebunden (30 40 % nach Karnofsky)");
        loincValue
            .setCode("LA9625-0")
            .setDisplay(
                "Capable of only limited selfcare, confined to bed or chair more than 50% of waking hours");
        break;
      case ECOG_4, KARNOFSKY_10, KARNOFSKY_20:
        miiValue.setCode("4");
        miiValue.setDisplay(
            "Völlig pflegebedürftig, keinerlei Selbstversorgung möglich; völlig an "
                + "Bett oder Stuhl gebunden (10 - 20 % nach Karnofsky)");
        loincValue
            .setCode("LA9626-8")
            .setDisplay(
                "Completely disabled. Cannot carry on any selfcare. Totally confined to bed or chair");
        break;
      case U:
      default:
        miiValue.setCode("U");
        miiValue.setDisplay("Unbekannt");
        break;
    }

    var valueConcept = new CodeableConcept();
    valueConcept.addCoding(miiValue);
    valueConcept.addCoding(loincValue);
    observation.setValue(valueConcept);

    return observation;
  }
}
