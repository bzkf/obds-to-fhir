package org.miracum.streams.ume.onkoadttofhir.processor;

import java.util.function.Function;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoObservationProcessor extends OnkoProcessor {

  @Value("${app.version}")
  private String appVersion;

  public OnkoObservationProcessor(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  @Bean
  public Function<KTable<String, MeldungExport>, KStream<String, Bundle>>
      getMeldungExportObservationProcessor() {
    return stringOnkoMeldungExpTable ->
        stringOnkoMeldungExpTable.mapValues(getOnkoToObservationBundleMapper()).toStream();
  }

  public ValueMapper<MeldungExport, Bundle> getOnkoToObservationBundleMapper() {
    return meldungExport -> {
      var observation = new Observation();

      // Generate an identifier based on MeldungExport Referenz_nummber (Pat. Id),  Meldung_ID and
      // Histologie_ID
      // from ADT XML
      var observationIdentifier =
          meldungExport.getReferenz_nummer()
              + meldungExport
                  .getXml_daten()
                  .getMenge_Patient()
                  .get(0)
                  .getPatient()
                  .getMenge_Meldung()
                  .getMeldung()
                  .getMeldung_ID()
              + meldungExport
                  .getXml_daten()
                  .getMenge_Patient()
                  .get(0)
                  .getPatient()
                  .getMenge_Meldung()
                  .getMeldung()
                  .getDiagnose()
                  .getMenge_Histologie()
                  .getHistologie()
                  .getHistologie_ID();

      observation.setId(this.getHash("Observation", observationIdentifier));

      observation
          .getMeta()
          .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkostar-to-fhir:" + appVersion);

      observation.setStatus(ObservationStatus.FINAL); // TODO abklaeren (bei Korrektur "amended" )

      observation.addCategory(
          new CodeableConcept()
              .addCoding(
                  new Coding(
                      fhirProperties.getSystems().getObservationCategorySystem(),
                      "laboratory",
                      "Laboratory")));

      observation.setCode(
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getLoinc())
                  .setCode("59847-4")
                  .setDisplay(fhirProperties.getDisplay().getHistologyLoinc())));

      var patId = meldungExport.getReferenz_nummer();
      var pid = convertId(patId); // TODO: testen ob das passt
      observation.setSubject(
          new Reference()
              .setReference("Patient/" + this.getHash("Patient", patId))
              .setIdentifier(
                  new Identifier()
                      .setSystem(fhirProperties.getSystems().getPatientId())
                      .setType(
                          new CodeableConcept(
                              new Coding(
                                  fhirProperties.getSystems().getIdentifierType(), "MR", null)))
                      .setValue(patId)));

      // TODO WIP: fehlende Eintraege

      var bundle = new Bundle();
      bundle
          .setType(Bundle.BundleType.TRANSACTION)
          .addEntry()
          .setFullUrl(new Reference("Observation/" + observation.getId()).getReference())
          .setResource(observation)
          .setRequest(
              new Bundle.BundleEntryRequestComponent()
                  .setMethod(Bundle.HTTPVerb.PUT)
                  .setUrl(
                      String.format(
                          "%s/%s", observation.getResourceType().name(), observation.getId())));
      return bundle;
    };
  }
}
