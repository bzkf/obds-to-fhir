package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.MengeFMTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.dizuker.tofhir.FhirExtensions.DataAbsentReason;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FernmetastasenMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(FernmetastasenMapper.class);

  public FernmetastasenMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  /**
   * @param section the section of the Meldung containing the Fernmetastasen, e.g. {@code Verlauf}.
   *     It's part of the identifier.
   */
  public List<Observation> map(
      MengeFMTyp mengeFM, String section, String meldungId, Reference patient, Reference diagnose) {
    verifyReference(patient, ResourceType.Patient);
    verifyReference(diagnose, ResourceType.Condition);

    var fernmetastasen = mengeFM.getFernmetastase();
    var result = new ArrayList<Observation>();
    for (int i = 0; i < fernmetastasen.size(); i++) {
      var fernmetastase = fernmetastasen.get(i);
      var observation = new Observation();

      setIdentifierAndId(
          observation,
          fhirProperties.getSystems().getIdentifiers().getFernmetastasenObservationId(),
          meldungId + "-" + section + "-" + i);

      // Meta-Daten
      observation.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoFernmetastasen());
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
      convertObdsDatumToDateTimeType(fernmetastase.getDiagnosedatum())
          .ifPresentOrElse(
              observation::setEffective,
              () -> {
                LOG.warn("Fernmetastase Diagnosedatum is unset. Setting data absent extension.");
                observation.setEffective(absentDateTime());
              });

      // Lokalisation
      var lokalisation = new CodeableConcept();
      if (StringUtils.hasText(fernmetastase.getLokalisation())) {
        lokalisation.addCoding(
            Onkologie.CodeSystems.MiiCsOnkoFernmetastasen.fromValueOrThrow(
                    fernmetastase.getLokalisation())
                .coding());
      } else {
        LOG.warn("Fernmetastase Lokalisation is unset. Setting data absent extension.");
        lokalisation.addExtension(DataAbsentReason.unknown());
      }
      observation.setValue(lokalisation);

      // Füge Observation zur Liste hinzu
      result.add(observation);
    }
    return result;
  }
}
