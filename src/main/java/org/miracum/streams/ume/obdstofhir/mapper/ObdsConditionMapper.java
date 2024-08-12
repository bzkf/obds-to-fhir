package org.miracum.streams.ume.obdstofhir.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
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
import org.springframework.util.StringUtils;

@Configuration
public class ObdsConditionMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ObdsConditionMapper.class);

  private static final Pattern icdVersionPattern =
      Pattern.compile("^(10 (?<versionYear>20\\d{2}) ((GM)|(WHO))|Sonstige)$");

  @Value("${app.version}")
  private String appVersion;

  @Value("${app.enableCheckDigitConv}")
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

    // Diagnose Element is only fully specified in meldeanlass 'diagnose', otherwise use element
    // 'Tumorzuordung'
    // It's possible that 'Meldung.Diagnose' is set but 'Meldung.Diagnose.Primaertumor_*' is not,
    // in that case also use the TumorZuordnung to construct the Condition.
    if (primDia == null || primDia.getPrimaertumor_ICD_Code() == null) {
      primDia = meldung.getTumorzuordnung();

      if (primDia == null) {
        return null;
      }
    }

    var patId = getPatIdFromMeldung(meldungExport);
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

    var coding =
        new Coding()
            .setCode(primDia.getPrimaertumor_ICD_Code())
            .setSystem(fhirProperties.getSystems().getIcd10gm());

    // Aufbau: "10 2021 GM"
    var icd10Version = primDia.getPrimaertumor_ICD_Version();
    if (StringUtils.hasLength(icd10Version)) {
      var matcher = icdVersionPattern.matcher(icd10Version);
      if (matcher.matches()) {
        coding.setVersion(matcher.group("versionYear"));
      } else {
        LOG.warn(
            "Primaertumor_ICD_Version doesn't match expected format. Expected: '{}', actual: '{}'",
            icdVersionPattern.pattern(),
            icd10Version);
      }
    } else {
      LOG.warn("Primaertumor_ICD_Version is unset or contains only whitespaces");
    }

    var conditionCode = new CodeableConcept().addCoding(coding);
    onkoCondition.setCode(conditionCode);

    var bodySiteADTCoding = new Coding();
    var bodySiteSNOMEDCoding = new Coding();

    var adtBodySite = primDia.getSeitenlokalisation();

    if (adtBodySite != null) {
      var adtSeitenlokalisationDisplay =
          displayAdtSeitenlokalisationLookup.lookupDisplay(adtBodySite);
      var snomedCtSeitenlokalisationCode = snomedCtSeitenlokalisationLookup.lookupCode(adtBodySite);
      var snomedCtSeitenlokalisationDisplay =
          snomedCtSeitenlokalisationLookup.lookupDisplay(adtBodySite);

      if (adtSeitenlokalisationDisplay != null) {
        bodySiteADTCoding
            .setSystem(fhirProperties.getSystems().getAdtSeitenlokalisation())
            .setCode(adtBodySite)
            .setDisplay(adtSeitenlokalisationDisplay);
      } else {
        LOG.warn("Unmappable body site in oBDS data: {}", adtBodySite);
      }

      if (snomedCtSeitenlokalisationDisplay != null) {
        bodySiteSNOMEDCoding
            .setSystem(fhirProperties.getSystems().getSnomed())
            .setCode(snomedCtSeitenlokalisationCode)
            .setDisplay(snomedCtSeitenlokalisationDisplay);
      } else {
        LOG.warn("Unmappable snomed body site in oBDS data: {}", adtBodySite);
      }

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
    if (conditionDateString.isPresent()) {
      onkoCondition.setOnset(convertObdsDateToDateTimeType(conditionDateString.get()));
    }

    var stageBackBoneComponentList = new ArrayList<Condition.ConditionStageComponent>();
    var evidenceBackBoneComponentList = new ArrayList<Condition.ConditionEvidenceComponent>();

    if (observationBundle != null && observationBundle.getEntry() != null) {
      for (var obsEntry : observationBundle.getEntry()) {
        if (obsEntry.getResource().getMeta().getProfile().isEmpty()) {
          // For now, the custom Gleason score observation doesn't set a profile.
          var observation = (Observation) obsEntry.getResource();
          if (observation.getCode().getCodingFirstRep().getCode().equals("35266-6")) {
            var conditionStageComponent =
                new Condition.ConditionStageComponent()
                    .setType(
                        new CodeableConcept(
                            new Coding(
                                fhirProperties.getSystems().getSnomed(),
                                "106241006",
                                "Gleason grading system for prostatic cancer (staging scale)")))
                    .addAssessment(new Reference(obsEntry.getFullUrl()));
            stageBackBoneComponentList.add(conditionStageComponent);
          }
        } else {
          var profile = obsEntry.getResource().getMeta().getProfile().get(0).getValue();
          if (profile.equals(fhirProperties.getProfiles().getHistologie())) {
            // || profile.equals(fhirProperties.getProfiles().getGenVariante())) { Genetische
            // Variante
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
