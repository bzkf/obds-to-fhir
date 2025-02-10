package org.miracum.streams.ume.obdstofhir.mapper.mii;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import de.basisdatensatz.obds.v3.DiagnoseTyp;
import de.basisdatensatz.obds.v3.MengeFMTyp.Fernmetastase;
import de.basisdatensatz.obds.v3.VerlaufTyp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FernmetastasenMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(FernmetastasenMapper.class);

  public FernmetastasenMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(VerlaufTyp verlaufTyp, Reference patient, Reference diagnose) {
    return createObservations(
        verlaufTyp.getMengeFM().getFernmetastase(), "Verlauf", patient, diagnose);
  }

  public List<Observation> map(DiagnoseTyp diagnoseTyp, Reference patient, Reference diagnose) {
    return createObservations(
        diagnoseTyp.getMengeFM().getFernmetastase(), "Diagnose", patient, diagnose);
  }

  private List<Observation> createObservations(
      List<Fernmetastase> fernmetastasen, String source, Reference patient, Reference diagnose) {
    Objects.requireNonNull(patient);
    Objects.requireNonNull(diagnose);
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(), ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");
    Validate.isTrue(
        Objects.equals(
            diagnose.getReferenceElement().getResourceType(), ResourceType.CONDITION.toCode()),
        "The diagnose reference should point to a Condition resource");

    var result = new ArrayList<Observation>();
    for (int i = 0; i < fernmetastasen.size(); i++) {
      var fernmetastase = fernmetastasen.get(i);
      var observation = new Observation();

      // Identifier
      var identifier = new Identifier();
      identifier
          .setSystem(fhirProperties.getSystems().getFernmetastasenId())
          .setValue(source + "_" + i);
      observation.addIdentifier(identifier);
      observation.setId(computeResourceIdFromIdentifier(identifier));
      // Meta-Daten
      observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoFernmetastasen());
      observation.setStatus(Observation.ObservationStatus.FINAL);
      // Code
      observation.setCode(
          new CodeableConcept(
              new Coding(fhirProperties.getSystems().getSnomed(), "385421009", "")));
      // Subject
      observation.setSubject(patient);
      // Fokus
      observation.addFocus(diagnose);
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
    return result;
  }
}
