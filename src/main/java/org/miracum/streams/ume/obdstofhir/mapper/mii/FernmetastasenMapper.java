package org.miracum.streams.ume.obdstofhir.mapper.mii;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import de.basisdatensatz.obds.v3.MengeFMTyp;
import de.basisdatensatz.obds.v3.OBDS;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;

public class FernmetastasenMapper extends ObdsToFhirMapper {
  private static final Logger Log = LoggerFactory.getLogger(FernmetastasenMapper.class);

  public FernmetastasenMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Bundle map(OBDS.MengePatient.Patient.MengeMeldung meldungen, Reference patient) {

    Objects.requireNonNull(meldungen);
    Objects.requireNonNull(meldungen.getMeldung());
    Objects.requireNonNull(patient);
    Validate.isTrue(
      Objects.equals(
        patient.getReferenceElement().getResourceType(), ResourceType.PATIENT.toCode()),
      "The subject reference should point to a Patient resource");

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

          //Meta-Daten
          observation
              .getMeta()
              .addProfile(fhirProperties.getProfiles().getMiiPrOnkoFernmetastasen());
          observation.setStatus(Observation.ObservationStatus.FINAL);
          //Code
          observation.setCode(
              new CodeableConcept(
                  new Coding(fhirProperties.getSystems().getSnomed(), "385421009", "")));
          //Subject
          observation.setSubject(patient);
          // Datum
          var effective = new DateTimeType(fernmetastase.getDiagnosedatum().getValue().toGregorianCalendar().getTime());
          effective.setPrecision(TemporalPrecisionEnum.DAY);
          observation.setEffective(effective);
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
