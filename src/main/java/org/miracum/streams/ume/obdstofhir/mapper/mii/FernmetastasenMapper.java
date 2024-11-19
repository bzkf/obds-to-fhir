package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.MengeFMTyp;
import de.basisdatensatz.obds.v3.OBDS;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FernmetastasenMapper extends ObdsToFhirMapper {
  private static final Logger Log = LoggerFactory.getLogger(FernmetastasenMapper.class);

  public FernmetastasenMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Bundle map(OBDS.MengePatient.Patient.MengeMeldung meldungen, Reference patient) {
    var obsBundle = new Bundle();
    obsBundle.setType(Bundle.BundleType.COLLECTION);
    // alle Meldungen
    for (var meldung : meldungen.getMeldung()) {

      var mengeFMs =
          new MengeFMTyp[] {
            meldung.getDiagnose() != null ? meldung.getDiagnose().getMengeFM() : null,
            meldung.getVerlauf() != null ? meldung.getVerlauf().getMengeFM() : null
          };
      for (var mengeFM : mengeFMs) {
        if (mengeFM == null) continue;

        for (var fernmetastase : mengeFM.getFernmetastase()) {
          var observation = new Observation();

          // Set Meta-Daten, Status, Code, Subject
          observation
              .getMeta()
              .addProfile(fhirProperties.getProfiles().getMiiPrOnkoFernmetastasen());
          observation.setStatus(Observation.ObservationStatus.FINAL);
          observation.setCode(
              new CodeableConcept(
                  new Coding(fhirProperties.getSystems().getIcd10gm(), "385421009", "")));
          observation.setSubject(patient);

          // Datum
          var date = fernmetastase.getDiagnosedatum().getValue().toGregorianCalendar().getTime();
          observation.setEffective(new DateTimeType(date));

          // Lokalisation
          var lokalisation = new CodeableConcept();
          lokalisation
              .addCoding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoFernmetastasen())
              .setCode(fernmetastase.getLokalisation());
          observation.setValue(lokalisation);

          // Füge Observation zur Bundle hinzu
          obsBundle.addEntry().setResource(observation);
        }
      }
    }
    return obsBundle;
  }
}
