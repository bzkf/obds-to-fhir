package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.MengeGenetikTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.ObservationCategory;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GenetischeVarianteMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(GenetischeVarianteMapper.class);

  public GenetischeVarianteMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  /**
   * @param section the section of the Meldung containing the Genetische_Variante elements, e.g.
   *     {@code Verlauf}. It's part of the identifier.
   */
  public List<Observation> map(
      @NonNull MengeGenetikTyp mengeGenetik,
      @NonNull String section,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @NonNull String meldungsID) {
    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    var genetischeVarianteList = mengeGenetik.getGenetischeVariante();

    // create a list to hold all single Observation resources, 1 observation per
    // <Genetische_Variante> in Meldung
    var observationList = new ArrayList<Observation>();

    for (int i = 0; i < genetischeVarianteList.size(); i++) {
      var genetischeVariante = genetischeVarianteList.get(i);
      var observation = new Observation();

      // identifier, meta
      String value =
          genetischeVariante.getAuspraegung() != null
              ? String.format(
                  "GenetischeAusprägung-%s-%s-%s-%d",
                  section, meldungsID, genetischeVariante.getAuspraegung().value(), i)
              : String.format("GenetischeAusprägung-%s-%s-%d", section, meldungsID, i);

      setIdentifierAndId(
          observation,
          fhirProperties.getSystems().getIdentifiers().getGenetischeVarianteObservationId(),
          value);

      // meta
      observation.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoGenetischeVariante());

      // status
      observation.setStatus(Observation.ObservationStatus.FINAL);

      // code
      CodeableConcept codeCodeableConcept =
          new CodeableConcept()
              .addCoding(
                  fhirProperties
                      .getCodings()
                      .loinc()
                      .setCode("69548-6")
                      .setDisplay("Genetic variant assessment"));

      observation.setCode(codeCodeableConcept);

      // category
      observation.addCategory(observationCategory(ObservationCategory.LABORATORY));
      observation.addCategory(
          new CodeableConcept()
              .addCoding(
                  new Coding()
                      .setSystem(fhirProperties.getSystems().getDiagnosticServiceSection())
                      .setCode("GE")
                      .setDisplay("Genetics")));

      // subject reference
      observation.setSubject(patient);

      // Focus
      observation.addFocus(condition);

      // effective
      var dateTime = convertObdsDatumToDateTimeType(genetischeVariante.getDatum());
      if (dateTime.isPresent()) {
        observation.setEffective(dateTime.get());
      }

      // Genetische Variante Ausprägung = interpretation

      var interpretationCodeableConcept = new CodeableConcept();
      if (genetischeVariante.getAuspraegung() != null) {
        interpretationCodeableConcept.addCoding(
            Onkologie.CodeSystems.MiiCsOnkoGenetischeVarianteAuspraegung.fromValueOrThrow(
                    genetischeVariante.getAuspraegung().value())
                .coding());
      } else if (genetischeVariante.getSonstigeAuspraegung() != null) {
        interpretationCodeableConcept.addCoding(
            Onkologie.CodeSystems.MiiCsOnkoGenetischeVarianteAuspraegung.S.coding());
        interpretationCodeableConcept.setText(genetischeVariante.getSonstigeAuspraegung());
      } else {
        LOG.warn("Neither Auspraegung nor Sonstige_Auspraegung are set.");
      }

      observation.addInterpretation(interpretationCodeableConcept);

      // Genetische Variante Name = note
      observation.addNote().setText(genetischeVariante.getBezeichnung());

      // add observation to observationList here
      observationList.add(observation);
    }

    return observationList;
  }
}
