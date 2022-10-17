package org.miracum.streams.ume.onkoadttofhir.processor;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.lookup.GradingLookup;
import org.miracum.streams.ume.onkoadttofhir.model.ADT_GEKID;
import org.miracum.streams.ume.onkoadttofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExportList;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportListSerde;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportSerde;
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
        stringOnkoMeldungExpTable
            .filter(
                (key, value) ->
                    !value
                        .getXml_daten()
                        .getMenge_Patient()
                        .getPatient()
                        .getMenge_Meldung()
                        .getMeldung()
                        .getMeldung_ID()
                        .startsWith("9999")) // ignore tumor conferences
            // TODO group by LKR MeldungsID
            // .groupBy(this::selectPatientIdAsKey)
            .groupBy(
                (key, value) -> KeyValue.pair(String.valueOf(value.getLkr_meldung()), value),
                Grouped.with(Serdes.String(), new MeldungExportSerde()))
            .aggregate(
                MeldungExportList::new,
                (key, value, aggregate) -> aggregate.addElement(value),
                (key, value, aggregate) -> aggregate.removeElement(value),
                Materialized.with(Serdes.String(), new MeldungExportListSerde()))
            .mapValues(this.getOnkoToObservationBundleMapper())
            .toStream();
  }

  public ValueMapper<MeldungExportList, Bundle> getOnkoToObservationBundleMapper() {
    return meldungExporte -> {
      var meldungen = meldungExporte.getElements();
      meldungen.sort(Comparator.comparingInt(MeldungExport::getVersionsnummer));

      var latestMeldung = meldungen.get(meldungen.size() - 1);

      return mapOnkoToObservationBundle(latestMeldung);
    };
  }

  // public ValueMapper<MeldungExport, Bundle> getOnkoToObservationBundleMapper() {
  // return meldungExport -> {
  public Bundle mapOnkoToObservationBundle(MeldungExport meldungExport) {

    var bundle = new Bundle();

    // Create a Grading Observation as in
    // https://simplifier.net/oncology/histologie
    var gradingObs = new Observation();

    // histologie from operation
    var mengeOp =
        meldungExport
            .getXml_daten()
            .getMenge_Patient()
            .getPatient()
            .getMenge_Meldung()
            .getMeldung()
            .getMenge_OP();

    // histologie list from diagnosis
    var diagnosis =
        meldungExport
            .getXml_daten()
            .getMenge_Patient()
            .getPatient()
            .getMenge_Meldung()
            .getMeldung()
            .getDiagnose();

    // check if histologie is defined in operation or diagnosis
    List<ADT_GEKID.HistologieAbs> histologies = new ArrayList<>();
    if (mengeOp == null) {
      // TODO Meldegrund Statusaenderung hat weder OP noch Diagnose
      histologies.addAll(getValidHistologies(diagnosis.getMenge_Histologie().getHistologie()));
    } else {
      // TODO Menge OP berueksichtigen
      histologies.add(mengeOp.getOP().getHistologie());
    }

    for (var histologie : histologies) {

      var histId = histologie.getHistologie_ID();

      var patId = meldungExport.getReferenz_nummer();
      var pid = convertId(patId);

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

      var grading = histologie.getGrading();

      // TODO anpassen
      var gradingObsIdentifier = meldungExport.getReferenz_nummer() + histId + grading;
      // grading may be undefined / null
      if (grading != null) {

        gradingObs.setId(this.getHash("Observation", gradingObsIdentifier));

        gradingObs
            .getMeta()
            .setSource(
                "DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkostar-to-fhir:" + appVersion);

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

        if (histDate != null) {
          gradingObs.setEffective(new DateTimeType(histDate));
        }

        var gradingValueCodeableCon =
            new CodeableConcept(
                new Coding()
                    .setSystem(fhirProperties.getSystems().getGradingDktk())
                    .setCode(grading)
                    .setVersion(gradingLookup.lookupGradingDisplay(grading)));

        gradingObs.setValue(gradingValueCodeableCon);
      }

      // Create an Histologie Observation as in
      // https://simplifier.net/oncology/histologie
      var histObs = new Observation();

      // TODO reicht das und bleibt Histologie_ID wirklich immer identisch
      // Generate an identifier based on MeldungExport Referenz_nummer (Pat. Id) and Histologie_ID
      // from ADT XML
      var observationIdentifier = meldungExport.getReferenz_nummer() + histId;

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

      if (grading != null) {
        histObs.addHasMember(
            new Reference()
                .setReference("Observation/" + this.getHash("Observation", gradingObsIdentifier)));
      }

      bundle
          .setType(Bundle.BundleType.TRANSACTION)
          .addEntry()
          .setFullUrl(new Reference("Observation/" + histObs.getId()).getReference())
          .setResource(histObs)
          .setRequest(
              new Bundle.BundleEntryRequestComponent()
                  .setMethod(Bundle.HTTPVerb.PUT)
                  .setUrl(
                      String.format("%s/%s", histObs.getResourceType().name(), histObs.getId())));

      if (grading != null) {
        bundle
            .addEntry()
            .setFullUrl(new Reference("Observation/" + gradingObs.getId()).getReference())
            .setResource(gradingObs)
            .setRequest(
                new Bundle.BundleEntryRequestComponent()
                    .setMethod(Bundle.HTTPVerb.PUT)
                    .setUrl(
                        String.format(
                            "%s/%s", gradingObs.getResourceType().name(), gradingObs.getId())));
      }
    }
    return bundle;
    // };
  }

  public List<Meldung.Diagnose.Menge_Histologie.Histologie> getValidHistologies(
      List<Meldung.Diagnose.Menge_Histologie.Histologie> mengeHist) {
    // returns a list of unique histIds having the maximum defined morphology code

    Map<String, Meldung.Diagnose.Menge_Histologie.Histologie> histologieMap = new HashMap<>();

    for (var hist : mengeHist) {
      var histId = hist.getHistologie_ID();
      if (histologieMap.get(histId) == null) {
        histologieMap.put(histId, hist);
      } else {
        var current =
            Integer.parseInt(StringUtils.left(histologieMap.get(histId).getMorphologie_Code(), 4));
        var update = Integer.parseInt(StringUtils.left(hist.getMorphologie_Code(), 4));
        if (update > current) {
          histologieMap.put(histId, hist);
        }
      }
    }
    return new ArrayList<>(histologieMap.values());
  }
}
