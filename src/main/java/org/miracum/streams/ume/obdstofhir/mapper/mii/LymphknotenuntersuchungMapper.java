package org.miracum.streams.ume.obdstofhir.mapper.mii;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import de.basisdatensatz.obds.v3.*;
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

public class LymphknotenuntersuchungMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(LymphknotenuntersuchungMapper.class);

  public LymphknotenuntersuchungMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      OBDS.MengePatient.Patient.MengeMeldung meldungen, Reference patient) {
    Objects.requireNonNull(meldungen, "Meldungen must not be null");
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
    // Create Obserations
    for (var histo : histologie) {
      if (histo == null
          || histo.getLKBefallen() == null
          || histo.getLKUntersucht() == null
          || histo.getSentinelLKBefallen() == null
          || histo.getSentinelLKUntersucht() == null) {
        continue;
      }
      var obs_befallen = new Observation();
      var obs_befallen_sentienel = new Observation();
      var obs_untersucht = new Observation();
      var obs_untersucht_sentinel = new Observation();
      // Identifer
      var identifier_befallen =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getObservationHistologieId())
              .setValue(histo.getHistologieID() + "_befallen");
      obs_befallen.addIdentifier(identifier_befallen);

      var identifier_untersucht =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getObservationHistologieId())
              .setValue(histo.getHistologieID() + "_untersucht");
      obs_untersucht.addIdentifier(identifier_untersucht);

      var identifier_befallen_sentinel =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getObservationHistologieId())
              .setValue(histo.getHistologieID() + "_befallen_sentinel");
      obs_befallen_sentienel.addIdentifier(identifier_befallen_sentinel);

      var identifier_untersucht_sentinel =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getObservationHistologieId())
              .setValue(histo.getHistologieID() + "_untersucht_sentinel");
      obs_untersucht_sentinel.addIdentifier(identifier_untersucht_sentinel);

      // Meta
      obs_befallen
          .getMeta()
          .addProfile(fhirProperties.getProfiles().getMiiPrOnkoAnzahlBefalleneLymphknoten());
      obs_untersucht
          .getMeta()
          .addProfile(fhirProperties.getProfiles().getMiiPrOnkoAnzahlUntersuchteLymphknoten());
      obs_befallen_sentienel
          .getMeta()
          .addProfile(
              fhirProperties.getProfiles().getMiiPrOnkoAnzahlBefalleneSentinelLymphknoten());
      obs_untersucht_sentinel
          .getMeta()
          .addProfile(
              fhirProperties.getProfiles().getMiiPrOnkoAnzahlUntersuchteSentinelLymphknoten());

      // Status
      obs_befallen.setStatus(Observation.ObservationStatus.FINAL);
      obs_untersucht.setStatus(Observation.ObservationStatus.FINAL);
      obs_befallen_sentienel.setStatus(Observation.ObservationStatus.FINAL);
      obs_untersucht_sentinel.setStatus(Observation.ObservationStatus.FINAL);

      // Category
      var laboratory =
          new CodeableConcept(
              new Coding(
                  fhirProperties.getSystems().getObservationCategorySystem(), "laboratory", ""));
      List<CodeableConcept> list = new ArrayList<>();
      list.add(laboratory);
      obs_befallen.setCategory(list);
      obs_untersucht.setCategory(list);
      obs_befallen_sentienel.setCategory(list);
      obs_untersucht_sentinel.setCategory(list);

      // Code Loinc
      var code_befallen =
          new CodeableConcept(new Coding(fhirProperties.getSystems().getLoinc(), "21893-3", ""));
      var code_untersucht =
          new CodeableConcept(new Coding(fhirProperties.getSystems().getLoinc(), "21894-1", ""));
      var code_befallen_sentinel =
          new CodeableConcept(new Coding(fhirProperties.getSystems().getLoinc(), "92832-5", ""));
      var code_untersucht_sentinel =
          new CodeableConcept(new Coding(fhirProperties.getSystems().getLoinc(), "85347-3", ""));

      // Snomed
      var coding_befallen = new Coding(fhirProperties.getSystems().getSnomed(), "443527007", "");
      var coding_untersucht = new Coding(fhirProperties.getSystems().getSnomed(), "444025001", "");
      var coding_befallen_sentinel =
          new Coding(fhirProperties.getSystems().getSnomed(), "1264491009", "");
      var coding_untersucht_sentinel =
          new Coding(fhirProperties.getSystems().getSnomed(), "444411008", "");

      code_befallen.addCoding(coding_befallen);
      code_untersucht.addCoding(coding_untersucht);
      code_befallen_sentinel.addCoding(coding_befallen_sentinel);
      code_untersucht_sentinel.addCoding(coding_untersucht_sentinel);

      obs_befallen.setCode(code_befallen);
      obs_untersucht.setCode(code_untersucht);
      obs_befallen_sentienel.setCode(code_befallen_sentinel);
      obs_untersucht_sentinel.setCode(code_untersucht_sentinel);

      // Subject
      obs_befallen.setSubject(patient);
      obs_untersucht.setSubject(patient);
      obs_befallen_sentienel.setSubject(patient);
      obs_untersucht_sentinel.setSubject(patient);

      // Effective
      var date =
          new DateTimeType(
              histo.getTumorHistologiedatum().getValue().toGregorianCalendar().getTime());
      date.setPrecision(TemporalPrecisionEnum.DAY);
      obs_befallen.setEffective(date);
      obs_untersucht.setEffective(date);
      obs_befallen_sentienel.setEffective(date);
      obs_untersucht_sentinel.setEffective(date);

      // Value
      var value_befallen =
          new Quantity()
              .setCode("1")
              .setSystem(fhirProperties.getSystems().getUcum())
              .setValue(histo.getLKBefallen().intValue());
      obs_befallen.setValue(value_befallen);
      var value_untersucht =
          new Quantity()
              .setCode("1")
              .setSystem(fhirProperties.getSystems().getUcum())
              .setValue(histo.getLKUntersucht().intValue());
      obs_untersucht.setValue(value_untersucht);
      var value_befallen_sentinel =
          new Quantity()
              .setCode("1")
              .setSystem(fhirProperties.getSystems().getUcum())
              .setValue(histo.getSentinelLKBefallen().intValue());
      obs_befallen_sentienel.setValue(value_befallen_sentinel);
      var value_untersucht_sentinel =
          new Quantity()
              .setCode("1")
              .setSystem(fhirProperties.getSystems().getUcum())
              .setValue(histo.getSentinelLKUntersucht().intValue());
      obs_untersucht_sentinel.setValue(value_untersucht_sentinel);

      result.add(obs_befallen);
      result.add(obs_untersucht);
      result.add(obs_befallen_sentienel);
      result.add(obs_untersucht_sentinel);
    }
    return result;
  }
}
