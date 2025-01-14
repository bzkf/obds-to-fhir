package org.miracum.streams.ume.obdstofhir.mapper.mii;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import de.basisdatensatz.obds.v3.MengeFMTyp;
import de.basisdatensatz.obds.v3.OBDS;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FernmetastasenMapper extends ObdsToFhirMapper {
  private static final Logger Log = LoggerFactory.getLogger(FernmetastasenMapper.class);

  public FernmetastasenMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      OBDS.MengePatient.Patient.MengeMeldung meldungen,
      Reference patient,
      List<Reference> diagnosen) {

    Objects.requireNonNull(meldungen);
    Objects.requireNonNull(meldungen.getMeldung());
    Objects.requireNonNull(patient);
    Objects.requireNonNull(diagnosen);
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(), ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");
    Validate.isTrue(
        Objects.equals(
            diagnosen.getFirst().getReferenceElement().getResourceType(),
            ResourceType.CONDITION.toCode()),
        "The diagnose reference should point to a Condition resource");

    var result = new ArrayList<Observation>();
    // alle Meldungen
    for (var meldung : meldungen.getMeldung()) {
      var mengeFMsDiagnose =
          new MengeFMTyp[] {
            meldung.getDiagnose() != null ? meldung.getDiagnose().getMengeFM() : null
          };
      var mengeFMsVerlauf =
          new MengeFMTyp[] {
            meldung.getVerlauf() != null ? meldung.getVerlauf().getMengeFM() : null
          };

      var combinedFMs = Map.of("Diagnose", mengeFMsDiagnose, "Verlauf", mengeFMsVerlauf);

      for (var entry : combinedFMs.entrySet()) {
        String typ = entry.getKey();
        MengeFMTyp[] mengeFMs = entry.getValue();
        for (var mengeFM : mengeFMs) {
          if (mengeFM == null) continue;
          int id = 0;
          for (var fernmetastase : mengeFM.getFernmetastase()) {
            var observation = new Observation();

            // Identifier
            var identifier = new Identifier();
            identifier
                .setSystem(fhirProperties.getSystems().getFernmetastasenId())
                .setValue(typ + "_" + id);
            observation.addIdentifier(identifier);
            id++;
            observation.setId(computeResourceIdFromIdentifier(identifier));
            // Meta-Daten
            observation
                .getMeta()
                .addProfile(fhirProperties.getProfiles().getMiiPrOnkoFernmetastasen());
            observation.setStatus(Observation.ObservationStatus.FINAL);
            // Code
            observation.setCode(
                new CodeableConcept(
                    new Coding(fhirProperties.getSystems().getSnomed(), "385421009", "")));
            // Subject
            observation.setSubject(patient);
            // Fokus
            observation.setFocus(diagnosen);
            // Datum
            var effective =
                new DateTimeType(
                    fernmetastase.getDiagnosedatum().getValue().toGregorianCalendar().getTime());
            effective.setPrecision(TemporalPrecisionEnum.DAY);
            observation.setEffective(effective);
            // Lokalisation
            var lokalisation = new CodeableConcept();
            lokalisation
                .addCoding()
                .setSystem(fhirProperties.getSystems().getMiiCsOnkoFernmetastasen())
                .setCode(fernmetastase.getLokalisation());
            observation.setValue(lokalisation);

            // Füge Observation zur Liste hinzu
            result.add(observation);
          }
        }
      }
    }
    return result;
  }
}
