package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.AllgemeinerLeistungszustand;
import de.basisdatensatz.obds.v3.OBDS;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
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
      OBDS.MengePatient.Patient.MengeMeldung.Meldung meldung, Reference patient) {

    Objects.requireNonNull(meldung);
    Objects.requireNonNull(meldung.getDiagnose());
    Objects.requireNonNull(meldung.getDiagnose().getAllgemeinerLeistungszustand());
    Objects.requireNonNull(meldung.getMeldungID());
    Objects.requireNonNull(patient);
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");

    var observation = new Observation();

    // TODO: is this prefix suitable? e.g. ECOG-10_1_DI_1
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getConditionId())
            .setValue("ECOG-" + meldung.getMeldungID());
    observation.addIdentifier(identifier);
    observation.setId(computeResourceIdFromIdentifier(identifier));

    observation.setSubject(patient);

    observation.setStatus(Observation.ObservationStatus.FINAL);

    observation.setCode(
        new CodeableConcept(
            new Coding().setSystem(fhirProperties.getSystems().getSnomed()).setCode("423740007")));

    Coding value =
        new Coding()
            .setSystem(fhirProperties.getSystems().getMiiCsOnkoAllgemeinerLeistungszustandEcog());
    AllgemeinerLeistungszustand allgemeinerLeistungszustand =
        meldung.getDiagnose().getAllgemeinerLeistungszustand();
    switch (allgemeinerLeistungszustandToEcog(allgemeinerLeistungszustand)) {
      case ECOG_0:
        value.setCode("0");
        value.setDisplay(
            "Normale, uneingeschränkte Aktivität wie vor der Erkrankung (90 - 100 % "
                + "nach Karnofsky)");
        break;
      case ECOG_1:
        value.setCode("1");
        value.setDisplay(
            "Einschränkung bei körperlicher Anstrengung, aber gehfähig; leichte "
                + "körperliche Arbeit bzw. Arbeit im Sitzen (z. B. leichte Hausarbeit oder Büroarbeit) "
                + "möglich (70 - 80 % nach Karnofsky)");
        break;
      case ECOG_2:
        value.setCode("2");
        value.setDisplay(
            "Gehfähig, Selbstversorgung möglich, aber nicht arbeitsfähig; kann mehr "
                + "als 50 % der Wachzeit aufstehen (50 - 60 % nach Karnofsky)");
        break;
      case ECOG_3:
        value.setCode("3");
        value.setDisplay(
            "Nur begrenzte Selbstversorgung möglich; ist 50 % oder mehr der Wachzeit"
                + " an Bett oder Stuhl gebunden (30 40 % nach Karnofsky)");
        break;
      case ECOG_4:
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
