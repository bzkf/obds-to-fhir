package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.JNU;
import de.basisdatensatz.obds.v3.JNUTyp;
import de.basisdatensatz.obds.v3.TumorkonferenzTyp;
import io.micrometer.common.lang.NonNull;

import java.util.ArrayList;

import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TumorkonferenzMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(TumorkonferenzMapper.class);

  public TumorkonferenzMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public CarePlan map(
    @NonNull TumorkonferenzTyp tk, @NonNull Reference patient, Reference primaerDiagnose) {

    CarePlan carePlan = new CarePlan();
    // Meta
    carePlan.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoTumorkonferenz());
    // Identifier + Id
    final Identifier identifier =
      new Identifier()
        .setSystem(fhirProperties.getSystems().getTumorkonferenzId())
        .setValue("Tumorkonferenz_" + tk.getTumorkonferenzID());
    carePlan.addIdentifier(identifier);
    carePlan.setId(computeResourceIdFromIdentifier(identifier));
    // Status
    if (tk.getMeldeanlass().equals(TumorkonferenzTyp.Meldeanlass.BEHANDLUNGSENDE)) {
      carePlan.setStatus(CarePlan.CarePlanStatus.COMPLETED);
    } else {
      carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
    }
    // intent
    carePlan.setIntent(CarePlan.CarePlanIntent.PLAN);
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
    carePlan.setCreatedElement(convertObdsDatumToDateTimeType(tk.getDatum()));
    // addresses
    ArrayList<Reference> adresse = new ArrayList<>();
    adresse.add(primaerDiagnose);
    carePlan.setAddresses(adresse);

    // activity - detail
    //  detail.code
    if (tk.getTherapieempfehlung() != null) {
      for (String typ :
        tk.getTherapieempfehlung().getMengeTypTherapieempfehlung().getTypTherapieempfehlung()) {
        CodeableConcept therapieEmpfehlungen =
          new CodeableConcept(
            new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoTherapieTyp())
              .setCode(typ));

        CarePlan.CarePlanActivityDetailComponent cpadc = new CarePlan.CarePlanActivityDetailComponent();
        cpadc.setCode(therapieEmpfehlungen);
        // detail.Status
        CodeableConcept statusReason = new CodeableConcept(new Coding().setSystem(fhirProperties.getSystems().getMiiCsOnkoTherapieabweichung()).setCode(tk.getTherapieempfehlung().getAbweichungPatientenwunsch().value()));
        switch (tk.getTherapieempfehlung().getAbweichungPatientenwunsch()) {
          case JNU.J -> cpadc.setStatus(CarePlan.CarePlanActivityStatus.CANCELLED);
          case JNU.N -> {
            if (carePlan.getStatus() == CarePlan.CarePlanStatus.COMPLETED) {
              cpadc.setStatus(CarePlan.CarePlanActivityStatus.COMPLETED);
            } else if (carePlan.getStatus() == CarePlan.CarePlanStatus.ACTIVE) {
              cpadc.setStatus(CarePlan.CarePlanActivityStatus.INPROGRESS);
            }
          }
          case JNU.U -> cpadc.setStatus(CarePlan.CarePlanActivityStatus.UNKNOWN);
        }
        // detail.StatusReason
        cpadc.setStatusReason(statusReason);
        // add Activity.Detail
        carePlan.addActivity().setDetail(cpadc);
      }
    }
    return carePlan;
  }
}
