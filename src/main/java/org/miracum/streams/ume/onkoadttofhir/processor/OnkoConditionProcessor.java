package org.miracum.streams.ume.onkoadttofhir.processor;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.lookup.DisplayAdtSeitenlokalisationLookup;
import org.miracum.streams.ume.onkoadttofhir.lookup.SnomedCtSeitenlokalisationLookup;
import org.miracum.streams.ume.onkoadttofhir.model.ADT_GEKID;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExportList;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportListSerde;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportSerde;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoConditionProcessor extends OnkoProcessor {

  private final SnomedCtSeitenlokalisationLookup snomedCtSeitenlokalisationLookup =
      new SnomedCtSeitenlokalisationLookup();

  private final DisplayAdtSeitenlokalisationLookup displayAdtSeitenlokalisationLookup =
      new DisplayAdtSeitenlokalisationLookup();

  @Value("${app.version}")
  private String appVersion;

  protected OnkoConditionProcessor(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  @Bean
  public Function<KTable<String, MeldungExport>, KStream<String, Bundle>>
      getMeldungExportConditionProcessor() {
    return stringOnkoMeldungExpTable ->
        stringOnkoMeldungExpTable
            .filter(
                (key, value) ->
                    value
                            .getXml_daten()
                            .getMenge_Patient()
                            .getPatient()
                            .getMenge_Meldung()
                            .getMeldung()
                            .getMenge_Tumorkonferenz()
                        == null) // ignore tumor conferences
            .groupBy(
                (key, value) -> KeyValue.pair(String.valueOf(value.getReferenz_nummer()), value),
                Grouped.with(Serdes.String(), new MeldungExportSerde()))
            .aggregate(
                MeldungExportList::new,
                (key, value, aggregate) -> aggregate.addElement(value),
                (key, value, aggregate) -> aggregate.removeElement(value),
                Materialized.with(Serdes.String(), new MeldungExportListSerde()))
            .mapValues(this.getOnkoToConditionBundleMapper())
            .filter((key, value) -> value != null)
            .toStream();
  }

  public ValueMapper<MeldungExportList, Bundle> getOnkoToConditionBundleMapper() {
    return meldungExporte -> {
      var meldungen = meldungExporte.getElements();

      var meldungExportMap = new HashMap<Integer, MeldungExport>();
      // meldeanlass bleibt in LKR Meldung immer gleich
      for (var meldung : meldungen) {
        var lkrId = meldung.getLkr_meldung();
        var currentMeldungVersion = meldungExportMap.get(lkrId);
        if (currentMeldungVersion == null
            || meldung.getVersionsnummer() > currentMeldungVersion.getVersionsnummer()) {
          meldungExportMap.put(lkrId, meldung);
        }
      }

      List<String> definedOrder = Arrays.asList("statusaenderung", "behandlungsende", "diagnose");

      Comparator<MeldungExport> meldungComparator =
          Comparator.comparing(
              m ->
                  definedOrder.indexOf(
                      m.getXml_daten()
                          .getMenge_Patient()
                          .getPatient()
                          .getMenge_Meldung()
                          .getMeldung()
                          .getMeldeanlass()));

      List<MeldungExport> meldungExportList = new ArrayList<>(meldungExportMap.values());
      meldungExportList.sort(meldungComparator);

      return mapOnkoResourcesToCondition(meldungExportList);
    };
  }

  public Bundle mapOnkoResourcesToCondition(List<MeldungExport> meldungExportList) {

    if (meldungExportList.isEmpty()) {
      return null;
    }

    // get last element of meldungExportList
    var meldungExport = meldungExportList.get(meldungExportList.size() - 1);

    var onkoCondition = new Condition();

    var meldung =
        meldungExport
            .getXml_daten()
            .getMenge_Patient()
            .getPatient()
            .getMenge_Meldung()
            .getMeldung();

    ADT_GEKID.PrimaryConditionAbs primDia = meldung.getDiagnose();

    // diagnose Tag ist only specified in meldeanlass 'diagnose', otherwise use tag 'Tumorzuordung'
    if (primDia == null) {
      primDia = meldung.getTumorzuordnung();

      if (primDia == null) {
        return null;
      }
    }

    var patId = meldungExport.getReferenz_nummer();
    var pid = convertId(patId);

    var conIdentifier = pid + "condition" + primDia.getTumor_ID();

    onkoCondition.setId(this.getHash("Condition", conIdentifier));

    onkoCondition
        .getMeta()
        .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkostar-to-fhir:" + appVersion);
    onkoCondition
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getCondition())));

    var coding = new Coding();
    var icd10Version = primDia.getPrimaertumor_ICD_Version();
    // Aufbau: "10 2021 GM"
    String[] icdVersionArray = icd10Version.split(" ");

    if (icdVersionArray.length == 3 && icdVersionArray[1].matches("^20\\d{2}$")) {
      coding.setVersion(icdVersionArray[1]);
    } // FIXME: else throw exception?

    coding
        .setCode(primDia.getPrimaertumor_ICD_Code())
        .setSystem(fhirProperties.getSystems().getIcd10gm());

    var conditionCode = new CodeableConcept().addCoding(coding);
    onkoCondition.setCode(conditionCode);

    var bodySiteADTCoding = new Coding();
    var bodySiteSNOMEDCoding = new Coding();

    var adtBodySite = primDia.getSeitenlokalisation();

    if (adtBodySite != null) {
      bodySiteADTCoding
          .setSystem(fhirProperties.getSystems().getAdtSeitenlokalisation())
          .setCode(adtBodySite)
          .setDisplay(
              displayAdtSeitenlokalisationLookup.lookupAdtSeitenlokalisationDisplay(adtBodySite));
      bodySiteSNOMEDCoding
          .setSystem(fhirProperties.getSystems().getSnomed())
          .setCode(snomedCtSeitenlokalisationLookup.lookupSnomedCode(adtBodySite))
          .setDisplay(snomedCtSeitenlokalisationLookup.lookupSnomedDisplay(adtBodySite));

      var bodySiteConcept = new CodeableConcept();
      bodySiteConcept.addCoding(bodySiteADTCoding).addCoding(bodySiteSNOMEDCoding);
      onkoCondition.addBodySite(bodySiteConcept);
    }

    onkoCondition.setSubject(
        new Reference()
            .setReference("Patient/" + this.getHash("Patient", pid))
            .setIdentifier(
                new Identifier()
                    .setSystem(fhirProperties.getSystems().getPatientId())
                    .setType(
                        new CodeableConcept(
                            new Coding(
                                fhirProperties.getSystems().getIdentifierType(), "MR", null)))
                    .setValue(pid)));

    Date conditionDate = null;
    var conditionDateString = primDia.getDiagnosedatum();

    if (conditionDateString != null) {
      SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN);
      try {
        conditionDate = formatter.parse(conditionDateString);
      } catch (ParseException e) {
        throw new RuntimeException(e);
      }
    }

    if (conditionDate != null) {
      onkoCondition.setOnset(new DateTimeType(conditionDate));
    }

    var bundle = new Bundle();
    bundle.setType(Bundle.BundleType.TRANSACTION);
    bundle = addResourceAsEntryInBundle(bundle, onkoCondition);

    return bundle;
  }
}
