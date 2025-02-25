package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.TumorkonferenzTyp;
import java.util.ArrayList;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TumorkonferenzMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(TumorkonferenzMapper.class);

  public TumorkonferenzMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public CarePlan map(TumorkonferenzTyp tk, Reference patient, Reference primaerDiagnose) {
    Objects.requireNonNull(tk);
    Objects.requireNonNull(patient);

    var carePlan = new CarePlan();
    // Meta
    carePlan.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoTumorkonferenz());
    // Identifier + Id
    Identifier identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getTumorkonferenzId())
            .setValue("Tumorkonferenz_" + tk.getTumorkonferenzID());
    carePlan.addIdentifier(identifier);
    carePlan.setId(computeResourceIdFromIdentifier(identifier));
    // Status-->Die CarePlan-Ressource sieht eine verpflichtende Angabe des status-Elements
    // einer activity vor. Diese Informationen sind in dieser Form nicht im oBDS enthalten.
    // Die tatsächlich erfolgten Therapien werden jedoch in den Krebsregisterdaten erfasst
    // und SOLLEN über Procedure.basedOn(Reference(CarePlan)) auf die
    // Tumorkonferenz-Ressource verweisen.
    carePlan.setStatus(CarePlan.CarePlanStatus.UNKNOWN);
    // intent--> bin hier auch unsicher, welcher Wert
    carePlan.setIntent(CarePlan.CarePlanIntent.ORDER);
    // category
    CodeableConcept codeableConcept =
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getMiiCsOnkoTherapieplanungTyp())
                .setCode(tk.getTyp()));
    carePlan.addCategory(codeableConcept);
    // subject
    carePlan.setSubject(patient);
    // created
    carePlan.setCreated(tk.getDatum().getValue().toGregorianCalendar().getTime());
    // addresses
    var adresse = new ArrayList<Reference>();
    adresse.add(primaerDiagnose);
    carePlan.setAddresses(adresse);
    // activity - detail
    //  detail.code
    var cpadc = new CarePlan.CarePlanActivityDetailComponent();
    var therapieEmpfehlungen = new CodeableConcept();
    for (int i = 0;
        i
            < tk.getTherapieempfehlung()
                .getMengeTypTherapieempfehlung()
                .getTypTherapieempfehlung()
                .size();
        i++) {
      Coding code =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoTherapieTyp())
              .setCode(
                  tk.getTherapieempfehlung()
                      .getMengeTypTherapieempfehlung()
                      .getTypTherapieempfehlung()
                      .get(i));
      therapieEmpfehlungen.addCoding(code);
    }
    cpadc.setCode(therapieEmpfehlungen);
    // detail.Status
    cpadc.setStatus(CarePlan.CarePlanActivityStatus.COMPLETED);
    // detail.StatusReason
    cpadc.setStatusReason(
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getMiiCsOnkoTherapieabweichung())
                .setCode(tk.getTherapieempfehlung().getAbweichungPatientenwunsch().value())));
    // add Activity.Detail
    carePlan.addActivity().setDetail(cpadc);
    return carePlan;
  }
}
