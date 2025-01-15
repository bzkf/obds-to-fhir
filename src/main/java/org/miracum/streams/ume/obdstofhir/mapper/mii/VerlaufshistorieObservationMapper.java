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
public class VerlaufshistorieObservationMapper extends ObdsToFhirMapper {

  private static final Logger LOG =
      LoggerFactory.getLogger(VerlaufshistorieObservationMapper.class);

  public VerlaufshistorieObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      OBDS.MengePatient.Patient.MengeMeldung meldungen,
      Reference patient,
      Reference specimen,
      Reference diagnose) {
    Objects.requireNonNull(meldungen, "Meldungen must not be null");
    Objects.requireNonNull(patient, "Reference to Patient must not be null");
    Objects.requireNonNull(specimen, "Reference to Specimen must not be null");
    Objects.requireNonNull(specimen, "Reference to Condition must not be null");
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");
    Validate.isTrue(
        Objects.equals(
            specimen.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.SPECIMEN.toCode()),
        "The specimen reference should point to a Specimen resource");
    Validate.isTrue(
        Objects.equals(
            diagnose.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.CONDITION.toCode()),
        "The condition reference should point to a Condition resource");

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
      if (histo == null) {
        continue;
      }
      var observation = new Observation();

      // Meta
      observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoHistologieIcdo3());

      // Identifer
      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getObservationHistologieId())
              .setValue(histo.getHistologieID() + "_ICDO3");
      observation.addIdentifier(identifier);
      // Id
      observation.setId(computeResourceIdFromIdentifier(identifier));

      // Status
      observation.setStatus(Observation.ObservationStatus.FINAL);

      // Code
      var code =
          new CodeableConcept(new Coding(fhirProperties.getSystems().getLoinc(), "59847-4", ""));
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
      var morph = histo.getMorphologieICDO().getFirst();
      var value =
          new CodeableConcept(
              new Coding(
                      fhirProperties.getSystems().getIcdo3Morphologie(),
                      morph.getCode(),
                      histo.getMorphologieFreitext())
                  .setVersion(morph.getVersion()));
      observation.setValue(value);

      // specimen
      observation.setSpecimen(specimen);

      result.add(observation);
    }
    return result;
  }
}
