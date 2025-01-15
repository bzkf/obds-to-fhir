package org.miracum.streams.ume.obdstofhir.mapper.mii;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import de.basisdatensatz.obds.v3.HistologieTyp;
import de.basisdatensatz.obds.v3.OBDS;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GradingObservationMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(GradingObservationMapper.class);

  public GradingObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      OBDS.MengePatient.Patient.MengeMeldung meldungen, Reference patient, Reference diagnose) {
    Objects.requireNonNull(meldungen, "Meldungen must not be null");
    Objects.requireNonNull(diagnose, "Reference to Condition must not be null");
    Objects.requireNonNull(patient, "Reference to Patient must not be null");
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");

    var result = new ArrayList<Observation>();

    // Collect histologieTyp from Diagnose, Verlauf, OP, Pathologie
    var histologie = new ArrayList<HistologieTyp>();
    for (var meldung : meldungen.getMeldung()) {
      if (meldung.getDiagnose() != null && meldung.getDiagnose().getHistologie() != null) {
        histologie.add(meldung.getDiagnose().getHistologie());
      }
      if (meldung.getVerlauf() != null && meldung.getVerlauf().getHistologie() != null) {
        histologie.add(meldung.getVerlauf().getHistologie());
      }
      if (meldung.getOP() != null && meldung.getOP().getHistologie() != null) {
        histologie.add(meldung.getOP().getHistologie());
      }
      if (meldung.getPathologie() != null && meldung.getPathologie().getHistologie() != null) {
        histologie.add(meldung.getPathologie().getHistologie());
      }
    }

    for (var histo : histologie) {
      if (histo == null || histo.getGrading() == null) {
        continue;
      }
      var observation = new Observation();
      // Meta
      observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoGrading());

      // Identifer
      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getObservationHistologieId())
              .setValue(histo.getHistologieID() + "_Grading");
      observation.addIdentifier(identifier);

      // Id
      observation.setId(computeResourceIdFromIdentifier(identifier));

      // Status
      observation.setStatus(Observation.ObservationStatus.FINAL);

      // category
      var laboratory =
          new CodeableConcept(
              new Coding(fhirProperties.getSystems().getObservationCategory(), "laboratory", ""));
      observation.addCategory(laboratory);

      // code
      var code =
          new CodeableConcept(new Coding(fhirProperties.getSystems().getLoinc(), "33732-9", ""));
      var coding = new Coding(fhirProperties.getSystems().getSnomed(), "371469007", "");
      code.addCoding(coding);
      observation.setCode(code);

      // subject
      observation.setSubject(patient);

      // focus
      observation.addFocus(diagnose);

      // effective
      var date =
          new DateTimeType(
              histo.getTumorHistologiedatum().getValue().toGregorianCalendar().getTime());
      date.setPrecision(TemporalPrecisionEnum.DAY);
      observation.setEffective(date);

      // value
      var value =
          new CodeableConcept(
              new Coding(
                  fhirProperties.getSystems().getMiiCsOnkoGrading(), histo.getGrading(), ""));
      observation.setValue(value);

      result.add(observation);
    }
    return result;
  }
}
