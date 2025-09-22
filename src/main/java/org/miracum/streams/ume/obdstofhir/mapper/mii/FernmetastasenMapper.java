package org.miracum.streams.ume.obdstofhir.mapper.mii;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import de.basisdatensatz.obds.v3.DiagnoseTyp;
import de.basisdatensatz.obds.v3.MengeFMTyp.Fernmetastase;
import de.basisdatensatz.obds.v3.PathologieTyp;
import de.basisdatensatz.obds.v3.VerlaufTyp;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FernmetastasenMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(FernmetastasenMapper.class);

  public FernmetastasenMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      VerlaufTyp verlaufTyp, String meldungId, Reference patient, Reference diagnose) {
    return createObservations(
        verlaufTyp.getMengeFM().getFernmetastase(), meldungId + "-Verlauf", patient, diagnose);
  }

  public List<Observation> map(
      DiagnoseTyp diagnoseTyp, String meldungId, Reference patient, Reference diagnose) {
    return createObservations(
        diagnoseTyp.getMengeFM().getFernmetastase(), meldungId + "-Diagnose", patient, diagnose);
  }

  public List<Observation> map(
      PathologieTyp pathologieTyp, String meldungId, Reference patient, Reference diagnose) {
    return createObservations(
        pathologieTyp.getMengeFM().getFernmetastase(),
        meldungId + "-Pathologie",
        patient,
        diagnose);
  }

  private List<Observation> createObservations(
      List<Fernmetastase> fernmetastasen, String source, Reference patient, Reference diagnose) {
    verifyReference(patient, ResourceType.Patient);
    verifyReference(diagnose, ResourceType.Condition);

    var result = new ArrayList<Observation>();
    for (int i = 0; i < fernmetastasen.size(); i++) {
      var fernmetastase = fernmetastasen.get(i);
      var observation = new Observation();

      // Identifier
      var identifier = new Identifier();
      identifier
          .setSystem(fhirProperties.getSystems().getIdentifiers().getFernmetastasenObservationId())
          .setValue(slugifier.slugify(source + "-" + i));
      observation.addIdentifier(identifier);
      observation.setId(computeResourceIdFromIdentifier(identifier));
      // Meta-Daten
      observation.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoFernmetastasen());
      observation.setStatus(Observation.ObservationStatus.FINAL);
      // Code
      observation.setCode(
          new CodeableConcept(
              fhirProperties
                  .getCodings()
                  .snomed()
                  .setCode("385421009")
                  .setDisplay(
                      "Anatomic location of metastatic spread of malignant neoplasm (observable entity)")));
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
