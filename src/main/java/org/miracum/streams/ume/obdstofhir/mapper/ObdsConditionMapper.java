package org.miracum.streams.ume.obdstofhir.mapper;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.lookup.*;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID;
import org.miracum.streams.ume.obdstofhir.model.MeldungExport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObdsConditionMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ObdsConditionMapper.class);

  @Value("${app.version}")
  private String appVersion;

  @Value("${app.enableCheckDigitConversion}")
  private boolean checkDigitConversion;

  private final SnomedCtSeitenlokalisationLookup snomedCtSeitenlokalisationLookup =
      new SnomedCtSeitenlokalisationLookup();

  private final DisplayAdtSeitenlokalisationLookup displayAdtSeitenlokalisationLookup =
      new DisplayAdtSeitenlokalisationLookup();

  @Autowired
  public ObdsConditionMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Bundle mapOnkoResourcesToCondition(
      List<MeldungExport> meldungExportList, Bundle observationBundle) {

    if (meldungExportList.isEmpty()) {
      return null;
    }

    // get first element of meldungExportList
    var meldungExport = meldungExportList.get(0);

    LOG.debug(
        "Mapping Meldung {} to {}", getReportingIdFromAdt(meldungExport), ResourceType.Condition);

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

    var patId = getPatIdFromAdt(meldungExport);
    var pid = patId;
    if (checkDigitConversion) {
      pid = convertId(patId);
    }

    var conIdentifier = pid + "condition" + primDia.getTumor_ID();

    onkoCondition.setId(this.getHash(ResourceType.Condition, conIdentifier));

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
            .setReference(ResourceType.Patient + "/" + this.getHash(ResourceType.Patient, pid))
            .setIdentifier(
                new Identifier()
                    .setSystem(fhirProperties.getSystems().getPatientId())
                    .setType(
                        new CodeableConcept(
                            new Coding(
                                fhirProperties.getSystems().getIdentifierType(), "MR", null)))
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
