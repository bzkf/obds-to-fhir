package org.miracum.streams.ume.obdstofhir.mapper.mii;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import de.basisdatensatz.obds.v3.HistologieTyp;
import de.basisdatensatz.obds.v3.OBDS;
import java.util.*;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpecimenMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(SpecimenMapper.class);

  public SpecimenMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Specimen> map(OBDS.MengePatient.Patient.MengeMeldung meldungen, Reference patient) {
    Objects.requireNonNull(meldungen, "Meldungen must not be null");
    Objects.requireNonNull(patient, "Reference to Patient must not be null");
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");

    var result = new ArrayList<Specimen>();

    // Collect histologieTyp from Diagnose, Verlauf and OP
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
    }

    for (var histo : histologie) {
      if (histo == null) {
        continue;
      }
      var specimen = new Specimen();

      // Identifier = HistologieId
      var identifier = new Identifier();
      identifier
          .setSystem(fhirProperties.getSystems().getSpecimenId())
          .setValue(histo.getHistologieID());
      specimen.addIdentifier(identifier);

      // Meta
      specimen.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoSpecimen());

      // accessionIdentifier=Histologie-Einsendenummer
      var accessionIdentifier = new Identifier();
      accessionIdentifier.setValue(histo.getHistologieEinsendeNr());
      specimen.setAccessionIdentifier(accessionIdentifier);
      // Subject
      specimen.setSubject(patient);

      // Specimen.collection.collected[x] = Tumor Histologiedatum
      var date =
          new DateTimeType(
              histo.getTumorHistologiedatum().getValue().toGregorianCalendar().getTime());
      date.setPrecision(TemporalPrecisionEnum.DAY);
      specimen.setCollection(new Specimen.SpecimenCollectionComponent().setCollected(date));
      result.add(specimen);
    }
    return result;
  }
}
