package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.DiagnoseTyp;
import de.basisdatensatz.obds.v3.MengeGenetikTyp;
import de.basisdatensatz.obds.v3.OPTyp;
import de.basisdatensatz.obds.v3.PathologieTyp;
import de.basisdatensatz.obds.v3.VerlaufTyp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GenetischeVarianteMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(OperationMapper.class);

  @Autowired
  public GenetischeVarianteMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Observation> map(DiagnoseTyp diagnoseTyp, Reference patient, Reference diagnose) {
    return createObservations(
        diagnoseTyp.getMengeGenetik().getGenetischeVariante(), "Diagnose", patient, diagnose);
  }

  public List<Observation> map(OPTyp opTyp, Reference patient, Reference diagnose) {
    return createObservations(
        opTyp.getMengeGenetik().getGenetischeVariante(), "OP", patient, diagnose);
  }

  public List<Observation> map(VerlaufTyp verlaufTyp, Reference patient, Reference diagnose) {
    return createObservations(
        verlaufTyp.getMengeGenetik().getGenetischeVariante(), "Verlauf", patient, diagnose);
  }

  public List<Observation> map(PathologieTyp pathologieTyp, Reference patient, Reference diagnose) {
    return createObservations(
        pathologieTyp.getMengeGenetik().getGenetischeVariante(), "Pathologie", patient, diagnose);
  }

  private List<Observation> createObservations(
      List<MengeGenetikTyp.GenetischeVariante> genetischeVarianteList,
      String source,
      Reference patient,
      Reference condition) {

    Objects.requireNonNull(genetischeVarianteList, "genetischeVarianteList must not be null");
    Objects.requireNonNull(patient, "Reference must not be null");
    Objects.requireNonNull(condition, "Reference must not be null");

    // create a list to hold all single Observation resources, 1 observation per
    // <Genetische_Variante> in Meldung
    var observationList = new ArrayList<Observation>();

    for (int i = 0; i < genetischeVarianteList.size(); i++) {
      var genetischeVariante = genetischeVarianteList.get(i);
      var observation = new Observation();

      // identifier, meta
      // to do: identifier anpassen, es gibt kein GenetischeVariante_ID - was ist hier sinnvoll?
      String value;
      if (genetischeVariante.getAuspraegung() != null) {
        value = source + genetischeVariante.getAuspraegung().value() + i;
      } else {
        value = source + i;
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
      observation.setFocus(Collections.singletonList(condition));

      // effective
      var dataAbsentExtension =
          new Extension(
              fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
      var dataAbsentCode = new CodeType();
      dataAbsentCode.addExtension(dataAbsentExtension);

      var dateTime = convertObdsDatumToDateTimeType(genetischeVariante.getDatum());
      if (dateTime.isPresent()) {
        observation.setEffective(dateTime.get());
      } else {
        var performed = new DateTimeType();
        performed.addExtension(dataAbsentExtension);
        observation.setEffective(performed);
      }

      // Genetische Variante Ausprägung = interpretation
      // to do: was ist hier mit genetischeVariante.getSonstigeAuspraegung())
      CodeableConcept interpretationCodeableConcept = new CodeableConcept();
      Coding coding =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoGenetischeVarianteAuspraegung());

      if (genetischeVariante.getAuspraegung() != null) {
        coding.setCode(genetischeVariante.getAuspraegung().value());
      }

      interpretationCodeableConcept.addCoding(coding);

      observation.setInterpretation(Collections.singletonList(interpretationCodeableConcept));

      // Genetische Variante Name = note
      Annotation codedAnnotation = new Annotation().setText(genetischeVariante.getBezeichnung());

      observation.setNote(Collections.singletonList(codedAnnotation));

      // add observation to observationList here
      observationList.add(observation);
    }

    return observationList;
  }
}
