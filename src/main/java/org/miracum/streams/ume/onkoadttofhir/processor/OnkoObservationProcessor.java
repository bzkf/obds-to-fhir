package org.miracum.streams.ume.onkoadttofhir.processor;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.lookup.GradingLookup;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoObservationProcessor extends OnkoProcessor {

  private final GradingLookup gradingLookup = new GradingLookup();

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

      // Create a Grading Observation as in
      // https://simplifier.net/oncology/histologie
      var gradingObs = new Observation();

      var histologie =
          meldungExport
              .getXml_daten()
              .getMenge_Patient()
              .get(0)
              .getPatient()
              .getMenge_Meldung()
              .getMeldung()
              .getDiagnose()
              .getMenge_Histologie()
              .getHistologie();

      // TODO anpassen
      var gradingObsIdentifier =
          meldungExport.getReferenz_nummer()
              + histologie.getHistologie_ID()
              + histologie.getGrading();

      gradingObs.setId(this.getHash("Observation", gradingObsIdentifier));

      gradingObs
          .getMeta()
          .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkostar-to-fhir:" + appVersion);

      gradingObs
          .getMeta()
          .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getGrading())));

      gradingObs.setStatus(ObservationStatus.FINAL); // bei Korrektur "amended"

      gradingObs.addCategory(
          new CodeableConcept()
              .addCoding(
                  new Coding(
                      fhirProperties.getSystems().getObservationCategorySystem(),
                      "laboratory",
                      "Laboratory")));

      gradingObs.setCode(
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getLoinc())
                  .setCode("59542-1")
                  .setDisplay(fhirProperties.getDisplay().getGradingLoinc())));

      var patId = meldungExport.getReferenz_nummer();
      var pid = convertId(patId);
      gradingObs.setSubject(
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

      // Histologiedatum
      var histDateString = histologie.getTumor_Histologiedatum();
      Date histDate = null;

      if (histDateString != null) {
        SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN);
        try {
          histDate = formatter.parse(histDateString);
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
      }

      if (histDate != null) {
        gradingObs.setEffective(new DateTimeType(histDate));
      }

      var grading = histologie.getGrading();

      var gradingValueCodeableCon =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getGradingDktk())
                  .setCode(grading)
                  .setVersion(gradingLookup.lookupGradingDisplay(grading)));

      gradingObs.setValue(gradingValueCodeableCon);

      // Create an Histologie Observation as in
      // https://simplifier.net/oncology/histologie
      var histObs = new Observation();

      // TODO reicht das und bleibt Histologie_ID wirklich immer identisch
      // Generate an identifier based on MeldungExport Referenz_nummer (Pat. Id) and Histologie_ID
      // from ADT XML
      var observationIdentifier =
          meldungExport.getReferenz_nummer() + histologie.getHistologie_ID();

      histObs.setId(this.getHash("Observation", observationIdentifier));

      histObs
          .getMeta()
          .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkostar-to-fhir:" + appVersion);

      histObs
          .getMeta()
          .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getHistologie())));

      histObs.setStatus(ObservationStatus.FINAL); // (bei Korrektur "amended" )

      histObs.addCategory(
          new CodeableConcept()
              .addCoding(
                  new Coding(
                      fhirProperties.getSystems().getObservationCategorySystem(),
                      "laboratory",
                      "Laboratory")));

      histObs.setCode(
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getLoinc())
                  .setCode("59847-4")
                  .setDisplay(fhirProperties.getDisplay().getHistologyLoinc())));

      histObs.setSubject(
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

      // Histologiedatum
      if (histDate != null) {
        histObs.setEffective(new DateTimeType(histDate));
      }

      var valueCodeableCon =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getIdco3Morphologie())
                  .setCode(histologie.getMorphologie_Code())
                  .setVersion(histologie.getMorphologie_ICD_O_Version()));

      var morphFreitext = histologie.getMorphologie_Freitext();

      if (morphFreitext != null) {
        valueCodeableCon.setText(morphFreitext);
      }

      histObs.setValue(valueCodeableCon);

      histObs.addHasMember(
          new Reference()
              .setReference("Observation/" + this.getHash("Observation", gradingObsIdentifier)));

      var bundle = new Bundle();
      bundle
          .setType(Bundle.BundleType.TRANSACTION)
          .addEntry()
          .setFullUrl(new Reference("Observation/" + gradingObs.getId()).getReference())
          .setResource(gradingObs)
          .setRequest(
              new Bundle.BundleEntryRequestComponent()
                  .setMethod(Bundle.HTTPVerb.PUT)
                  .setUrl(
                      String.format(
                          "%s/%s", gradingObs.getResourceType().name(), gradingObs.getId())));
      bundle
          .addEntry()
          .setFullUrl(new Reference("Observation/" + histObs.getId()).getReference())
          .setResource(histObs)
          .setRequest(
              new Bundle.BundleEntryRequestComponent()
                  .setMethod(Bundle.HTTPVerb.PUT)
                  .setUrl(
                      String.format("%s/%s", histObs.getResourceType().name(), histObs.getId())));

      return bundle;
    };
  }
}
