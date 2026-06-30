package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.JNU;
import de.basisdatensatz.obds.v3.TumorkonferenzTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.dizuker.tofhir.IdUtils;
import java.util.ArrayList;
import org.hl7.fhir.r4.model.*;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TumorkonferenzMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(TumorkonferenzMapper.class);

  public TumorkonferenzMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public CarePlan map(
      @NonNull TumorkonferenzTyp tk, @NonNull Reference patient, Reference primaerDiagnose) {

    verifyReference(patient, ResourceType.Patient);
    verifyReference(primaerDiagnose, ResourceType.Condition);

    CarePlan carePlan = new CarePlan();
    // Meta
    carePlan.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoTumorkonferenz());
    // Identifier + Id
    final Identifier identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getIdentifiers().getTumorkonferenzCarePlanId())
            .setValue(slugifier.slugify("Tumorkonferenz-" + tk.getTumorkonferenzID()));
    carePlan.addIdentifier(identifier);
    carePlan.setId(IdUtils.fromIdentifier(identifier));
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
            Onkologie.CodeSystems.MiiCsOnkoTherapieplanungTyp.fromValue(tk.getTyp()).coding());
    carePlan.addCategory(codeableConcept);
    // subject
    carePlan.setSubject(patient);
    // created
    convertObdsDatumToDateTimeType(tk.getDatum()).ifPresent(carePlan::setCreatedElement);
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
            new CodeableConcept(Onkologie.CodeSystems.MiiCsOnkoTherapieTyp.fromValue(typ).coding());

        CarePlan.CarePlanActivityDetailComponent cpadc =
            new CarePlan.CarePlanActivityDetailComponent();
        cpadc.setCode(therapieEmpfehlungen);
        // detail.Status
        CodeableConcept statusReason =
            new CodeableConcept(
                Onkologie.CodeSystems.MiiCsOnkoTherapieabweichung.fromValue(
                        tk.getTherapieempfehlung().getAbweichungPatientenwunsch().value())
                    .coding());
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
