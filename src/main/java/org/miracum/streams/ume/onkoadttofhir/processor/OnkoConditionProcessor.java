package org.miracum.streams.ume.onkoadttofhir.processor;

import java.util.*;
import java.util.function.BiFunction;
import org.apache.commons.lang3.tuple.Pair;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoConditionProcessor extends OnkoProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(OnkoConditionProcessor.class);

  private final SnomedCtSeitenlokalisationLookup snomedCtSeitenlokalisationLookup =
      new SnomedCtSeitenlokalisationLookup();

  private final DisplayAdtSeitenlokalisationLookup displayAdtSeitenlokalisationLookup =
      new DisplayAdtSeitenlokalisationLookup();

  @Value("${app.version}")
  private String appVersion;

  @Value("#{new Boolean('${app.enableCheckDigitConversion}')}")
  private boolean checkDigitConversion;

  protected OnkoConditionProcessor(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  @Bean
  public BiFunction<KTable<String, MeldungExport>, KTable<String, Bundle>, KStream<String, Bundle>>
      getMeldungExportConditionProcessor() {
    return (stringOnkoMeldungExpTable, stringOnkoObsBundles) ->
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
                (key, value) ->
                    KeyValue.pair(
                        "Struct{REFERENZ_NUMMER="
                            + value.getReferenz_nummer()
                            + ",TUMOR_ID="
                            + getTumorIdFromAdt(value)
                            + "}",
                        value),
                Grouped.with(Serdes.String(), new MeldungExportSerde()))
            .aggregate(
                MeldungExportList::new,
                (key, value, aggregate) -> aggregate.addElement(value),
                (key, value, aggregate) -> aggregate.removeElement(value),
                Materialized.with(Serdes.String(), new MeldungExportListSerde()))
            .leftJoin(stringOnkoObsBundles, Pair::of)
            .mapValues(this.getOnkoToConditionBundleMapper())
            .filter((key, value) -> value != null)
            .toStream();
  }

  public ValueMapper<Pair<MeldungExportList, Bundle>, Bundle> getOnkoToConditionBundleMapper() {
    return meldungPair -> {
      List<MeldungExport> meldungExportList =
          prioritiseLatestMeldungExports(
              meldungPair.getLeft(),
              Arrays.asList("diagnose", "behandlungsende", "statusaenderung"));

      return mapOnkoResourcesToCondition(meldungExportList, meldungPair.getRight());
    };
  }

  public Bundle mapOnkoResourcesToCondition(
      List<MeldungExport> meldungExportList, Bundle observationBundle) {

    if (meldungExportList.isEmpty()) {
      return null;
    }

    // get last element of meldungExportList
    // TODO ueberpruefen ob letzte Meldung reicht
    var meldungExport = meldungExportList.get(meldungExportList.size() - 1);

    LOG.debug("Mapping Meldung {} to {}", getReportingIdFromAdt(meldungExport), "condition");

    var onkoCondition = new Condition();

    var meldung =
        meldungExport
            .getXml_daten()
            .getMenge_Patient()
            .getPatient()
            .getMenge_Meldung()
            .getMeldung();

    var meldungsId = meldung.getMeldung_ID();

    LOG.debug("Processing Meldung: {}", meldungsId);

    ADT_GEKID.PrimaryConditionAbs primDia = meldung.getDiagnose();

    // diagnose Tag ist only specified in meldeanlass 'diagnose', otherwise use tag 'Tumorzuordung'
    if (primDia == null) {
      primDia = meldung.getTumorzuordnung();

      if (primDia == null) {
        return null;
      }
    }

    var patId = meldungExport.getReferenz_nummer();
    var pid = patId;
    if (checkDigitConversion) {
      pid = convertId(patId) + "X";
    }

    var conIdentifier = pid + "condition" + primDia.getTumor_ID();

    onkoCondition.setId(this.getHash("Condition", conIdentifier));

    var senderInfo = meldungExport.getXml_daten().getAbsender();
    onkoCondition
        .getMeta()
        .setSource(
            generateProfileMetaSource(
                senderInfo.getAbsender_ID(), senderInfo.getSoftware_ID(), appVersion));

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
                    .setValue(pid)));

    var conditionDateString = primDia.getDiagnosedatum();

    if (conditionDateString != null) {
      onkoCondition.setOnset(extractDateTimeFromADTDate(conditionDateString));
    }

    var stageBackBoneComponentList = new ArrayList<Condition.ConditionStageComponent>();
    var evidenceBackBoneComponentList = new ArrayList<Condition.ConditionEvidenceComponent>();

    if (observationBundle != null && observationBundle.getEntry() != null) {
      for (var obsEntry : observationBundle.getEntry()) {
        var profile = obsEntry.getResource().getMeta().getProfile().get(0).getValue();
        if (profile.equals(fhirProperties.getProfiles().getHistologie())) {
          // || profile.equals(fhirProperties.getProfiles().getGenVariante())) { Genetische Variante
          // erst ab ADTv3
          var conditionEvidenceComponent = new Condition.ConditionEvidenceComponent();
          conditionEvidenceComponent.addDetail(new Reference(obsEntry.getFullUrl()));
          evidenceBackBoneComponentList.add(conditionEvidenceComponent);
        } else if (profile.equals(fhirProperties.getProfiles().getTnmC())
            || profile.equals(fhirProperties.getProfiles().getTnmP())) {
          var conditionStageComponent = new Condition.ConditionStageComponent();
          conditionStageComponent.addAssessment(new Reference(obsEntry.getFullUrl()));
          stageBackBoneComponentList.add(conditionStageComponent);
        } else if (profile.equals(fhirProperties.getProfiles().getFernMeta())) {
          onkoCondition
              .addExtension()
              .setUrl(fhirProperties.getExtensions().getFernMetaExt())
              .setValue(new Reference(obsEntry.getFullUrl()));
        }
      }
    }

    if (!stageBackBoneComponentList.isEmpty()) {
      onkoCondition.setStage(stageBackBoneComponentList);
    }

    if (!evidenceBackBoneComponentList.isEmpty()) {
      onkoCondition.setEvidence(evidenceBackBoneComponentList);
    }

    var bundle = new Bundle();
    bundle.setType(Bundle.BundleType.TRANSACTION);
    bundle = addResourceAsEntryInBundle(bundle, onkoCondition);

    return bundle;
  }
}
