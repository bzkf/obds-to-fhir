package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.VerlaufTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

@Service
public class VerlaufObservationMapper extends ObdsToFhirMapper {

  public VerlaufObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(VerlaufTyp verlauf, Reference patient, Reference condition) {

    // Validate input
    Objects.requireNonNull(verlauf, "VerlaufTyp must not be null");
    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    // Create Observation
    var observation = new Observation();

    // Meta
    observation.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoVerlauf());

    setIdentifierAndId(
        observation,
        fhirProperties.getSystems().getIdentifiers().getVerlaufObservationId(),
        verlauf.getVerlaufID());

    // Status
    observation.setStatus(Observation.ObservationStatus.FINAL);

    // Code
    observation.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("396432002")
                .setDisplay("Status of regression of tumor (observable entity)")));

    // Subject
    observation.setSubject(patient);

    // Focus
    observation.addFocus(condition);

    // Effective Date
    convertObdsDatumToDateTimeType(verlauf.getUntersuchungsdatumVerlauf())
        .ifPresent(observation::setEffective);

    // Components
    // Tumorstatus Primärtumor
    var verlaufLokalerTumorstatus = verlauf.getVerlaufLokalerTumorstatus();
    if (verlaufLokalerTumorstatus != null) {
      var tumorstatusPrimaertumor =
          Onkologie.CodeSystems.MiiCsOnkoVerlaufPrimaertumor.fromValueOrThrow(
              verlaufLokalerTumorstatus);
      observation.addComponent(
          new Observation.ObservationComponentComponent()
              .setCode(
                  new CodeableConcept(
                      fhirProperties
                          .getCodings()
                          .snomed()
                          .setCode("445200009")
                          .setDisplay("Status of residual neoplasm (observable entity)")))
              .setValue(new CodeableConcept(tumorstatusPrimaertumor.coding())));
    }

    // Tumorstatus Lymphknoten
    var verlaufTumorstatusLymphknoten = verlauf.getVerlaufTumorstatusLymphknoten();
    if (verlaufTumorstatusLymphknoten != null) {
      var tumorstatusLymphknoten =
          Onkologie.CodeSystems.MiiCsOnkoVerlaufLymphknoten.fromValueOrThrow(
              verlaufTumorstatusLymphknoten);
      observation.addComponent(
          new Observation.ObservationComponentComponent()
              .setCode(
                  new CodeableConcept(
                      fhirProperties
                          .getCodings()
                          .snomed()
                          .setCode("399656008")
                          .setDisplay(
                              "Presence of metastatic neoplasm in regional lymph node (observable entity)")))
              .setValue(new CodeableConcept(tumorstatusLymphknoten.coding())));
    }

    // Tumorstatus Fernmetastasen
    var verlaufTumorstatusFernmetastasen = verlauf.getVerlaufTumorstatusFernmetastasen();
    if (verlaufTumorstatusFernmetastasen != null) {
      var tumorstatusFernmetastasen =
          Onkologie.CodeSystems.MiiCsOnkoVerlaufFernmetastasen.fromValueOrThrow(
              verlaufTumorstatusFernmetastasen);
      observation.addComponent(
          new Observation.ObservationComponentComponent()
              .setCode(
                  new CodeableConcept(
                      fhirProperties
                          .getCodings()
                          .snomed()
                          .setCode("399608002")
                          .setDisplay("Status of distant metastasis (observable entity)")))
              .setValue(new CodeableConcept(tumorstatusFernmetastasen.coding())));
    }

    // Value
    var gesamtbeurteilung = verlauf.getGesamtbeurteilungTumorstatus();
    if (gesamtbeurteilung != null) {
      var value =
          Onkologie.CodeSystems.MiiCsOnkoVerlaufGesamtbeurteilung.fromValueOrThrow(
              gesamtbeurteilung);
      observation.setValue(new CodeableConcept(value.coding()));
    }

    return observation;
  }
}
