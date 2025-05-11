package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.DiagnoseTyp;
import de.basisdatensatz.obds.v3.MengeGenetikTyp;
import de.basisdatensatz.obds.v3.OPTyp;
import de.basisdatensatz.obds.v3.PathologieTyp;
import de.basisdatensatz.obds.v3.VerlaufTyp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class GenetischeVarianteMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(GenetischeVarianteMapper.class);

  public GenetischeVarianteMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(
      DiagnoseTyp diagnoseTyp, Reference patient, Reference diagnose, String meldungsID) {
    return createObservations(
        diagnoseTyp.getMengeGenetik().getGenetischeVariante(),
        "Diagnose",
        patient,
        diagnose,
        meldungsID);
  }

  public List<Observation> map(
      OPTyp opTyp, Reference patient, Reference diagnose, String meldungsID) {
    return createObservations(
        opTyp.getMengeGenetik().getGenetischeVariante(), "OP", patient, diagnose, meldungsID);
  }

  public List<Observation> map(
      VerlaufTyp verlaufTyp, Reference patient, Reference diagnose, String meldungsID) {
    return createObservations(
        verlaufTyp.getMengeGenetik().getGenetischeVariante(),
        "Verlauf",
        patient,
        diagnose,
        meldungsID);
  }

  public List<Observation> map(
      PathologieTyp pathologieTyp, Reference patient, Reference diagnose, String meldungsID) {
    return createObservations(
        pathologieTyp.getMengeGenetik().getGenetischeVariante(),
        "Pathologie",
        patient,
        diagnose,
        meldungsID);
  }

  private List<Observation> createObservations(
      @NonNull List<MengeGenetikTyp.GenetischeVariante> genetischeVarianteList,
      @NonNull String source,
      @NonNull Reference patient,
      @NonNull Reference condition,
      @NonNull String meldungsID) {
    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    // create a list to hold all single Observation resources, 1 observation per
    // <Genetische_Variante> in Meldung
    var observationList = new ArrayList<Observation>();

    for (int i = 0; i < genetischeVarianteList.size(); i++) {
      var genetischeVariante = genetischeVarianteList.get(i);
      var observation = new Observation();

      // identifier, meta
      String value;
      if (genetischeVariante.getAuspraegung() != null) {
        value =
            "GenetischeAusprägung-"
                + source
                + meldungsID
                + genetischeVariante.getAuspraegung().value()
                + i;
      } else {
        value = "GenetischeAusprägung-" + source + meldungsID + i;
      }

      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getGenetischeVarianteId())
              .setValue(value);

      observation.addIdentifier(identifier);
      observation.setId(computeResourceIdFromIdentifier(identifier));

      // meta
      observation
          .getMeta()
          .addProfile(fhirProperties.getProfiles().getMiiPrOnkoGenetischeVariante());

      // status
      observation.setStatus(Observation.ObservationStatus.FINAL);

      // code
      CodeableConcept codeCodeableConcept =
          new CodeableConcept()
              .addCoding(
                  new Coding()
                      .setSystem(fhirProperties.getSystems().getLoinc())
                      .setCode("69548-6"));

      observation.setCode(codeCodeableConcept);

      // category
      CodeableConcept categoryCodeableConcept =
          new CodeableConcept()
              .addCoding(
                  new Coding()
                      .setSystem(fhirProperties.getSystems().getObservationCategory())
                      .setCode("laboratory"));
      observation.setCategory(Collections.singletonList(categoryCodeableConcept));

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

      var coding =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoGenetischeVarianteAuspraegung());
      var interpretationCodeableConcept = new CodeableConcept(coding);
      if (genetischeVariante.getAuspraegung() != null) {
        coding.setCode(genetischeVariante.getAuspraegung().value());
      } else if (genetischeVariante.getSonstigeAuspraegung() != null) {
        coding.setCode("S");
        interpretationCodeableConcept.setText(genetischeVariante.getSonstigeAuspraegung());
      } else {
        // this resets the concept so no incomplete codings are shown.
        interpretationCodeableConcept = new CodeableConcept();
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
