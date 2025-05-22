package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.AllgemeinerLeistungszustand;
import de.basisdatensatz.obds.v3.DatumTagOderMonatOderJahrOderNichtGenauTyp;
import java.util.Collections;
import java.util.Objects;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
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
            .setSystem(fhirProperties.getSystems().getAllgemeinerLeistungszustandEcogId())
            .setValue("ECOG-" + meldungsId);
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    observation.setSubject(patient);

    observation.setStatus(Observation.ObservationStatus.FINAL);

    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("423740007")
                .setDisplay(
                    "Eastern Cooperative Oncology Group performance status (observable entity)")));

    observation.setEffective(effective);

    observation.setFocus(Collections.singletonList(condition));

    Coding value =
        new Coding()
            .setSystem(fhirProperties.getSystems().getMiiCsOnkoAllgemeinerLeistungszustandEcog());
    switch (allgemeinerLeistungszustand) {
      case ECOG_0, KARNOFSKY_90, KARNOFSKY_100:
        value.setCode("0");
        value.setDisplay(
            "Normale, uneingeschränkte Aktivität wie vor der Erkrankung (90 - 100 % "
                + "nach Karnofsky)");
        break;
      case ECOG_1, KARNOFSKY_70, KARNOFSKY_80:
        value.setCode("1");
        value.setDisplay(
            "Einschränkung bei körperlicher Anstrengung, aber gehfähig; leichte "
                + "körperliche Arbeit bzw. Arbeit im Sitzen (z. B. leichte Hausarbeit oder Büroarbeit) "
                + "möglich (70 - 80 % nach Karnofsky)");
        break;
      case ECOG_2, KARNOFSKY_50, KARNOFSKY_60:
        value.setCode("2");
        value.setDisplay(
            "Gehfähig, Selbstversorgung möglich, aber nicht arbeitsfähig; kann mehr "
                + "als 50 % der Wachzeit aufstehen (50 - 60 % nach Karnofsky)");
        break;
      case ECOG_3, KARNOFSKY_30, KARNOFSKY_40:
        value.setCode("3");
        value.setDisplay(
            "Nur begrenzte Selbstversorgung möglich; ist 50 % oder mehr der Wachzeit"
                + " an Bett oder Stuhl gebunden (30 40 % nach Karnofsky)");
        break;
      case ECOG_4, KARNOFSKY_10, KARNOFSKY_20:
        value.setCode("4");
        value.setDisplay(
            "Völlig pflegebedürftig, keinerlei Selbstversorgung möglich; völlig an "
                + "Bett oder Stuhl gebunden (10 - 20 % nach Karnofsky)");
        break;
      case U:
      default:
        value.setCode("U");
        value.setDisplay("Unbekannt");
        break;
    }
    observation.setValue(new CodeableConcept(value));

    return observation;
  }
}
