package org.miracum.streams.ume.obdstofhir.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.lookup.FMLokalisationVsLookup;
import org.miracum.streams.ume.obdstofhir.lookup.GradingLookup;
import org.miracum.streams.ume.obdstofhir.lookup.JnuVsLookup;
import org.miracum.streams.ume.obdstofhir.lookup.TnmCpuPraefixTvsLookup;
import org.miracum.streams.ume.obdstofhir.model.*;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung.Menge_Verlauf.Verlauf.Tod;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID.Modul_Prostata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObdsObservationMapper extends ObdsToFhirMapper {

  public static record ModulProstataMappingParams(
      Meldeanlass meldeanlass,
      Modul_Prostata modulProstata,
      String patientId,
      String baseId,
      DateTimeType baseDatum,
      DateTimeType meldedatum) {}

  private static final Logger LOG = LoggerFactory.getLogger(ObdsObservationMapper.class);

  @Value("${app.version}")
  private String appVersion;

  @Value("${app.enableCheckDigitConv}")
  private boolean checkDigitConversion;

  @Value("${fhir.mappings.modul.prostata.enabled}")
  private boolean isModulProstataMappingEnabled;

  private final GleasonScoreToObservationMapper gleasonScoreMapper;

  private final PsaToObservationMapper psaMapper;

  public ObdsObservationMapper(
      FhirProperties fhirProperties,
      GleasonScoreToObservationMapper gleasonScoreMapper,
      PsaToObservationMapper psaMapper) {
    super(fhirProperties);

    this.gleasonScoreMapper = gleasonScoreMapper;
    this.psaMapper = psaMapper;
  }

  public Bundle mapOnkoResourcesToObservation(List<MeldungExport> meldungExportList) {

    if (meldungExportList.isEmpty()) {
      return null;
    }

    HashMap<String, Tupel<ADT_GEKID.HistologieAbs, Meldeanlass>> histMap = new HashMap<>();
    HashMap<
            String,
            Triple<ADT_GEKID.CTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation, Meldeanlass>>
        cTnmMap = new HashMap<>();
    HashMap<
            String,
            Triple<ADT_GEKID.PTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation, Meldeanlass>>
        pTnmMap = new HashMap<>();
    HashMap<String, Tupel<ADT_GEKID.FernMetastaseAbs, Meldeanlass>> fernMetaMap = new HashMap<>();
    HashMap<String, ModulProstataMappingParams> modulProstataMappingMap = new HashMap<>();

    Tod death = null;

    var patId = "";
    var verlaufId = "";
    var metaSource = "";

    // Process prioritized reports at the end of the for-loop
    for (int i = meldungExportList.size() - 1; i >= 0; i--) {
      var meldungExport = meldungExportList.get(i);

      LOG.debug(
          "Mapping Meldung {} to {}",
          getReportingIdFromAdt(meldungExport),
          ResourceType.Observation);

      var meldung =
          meldungExport
              .getXml_daten()
              .getMenge_Patient()
              .getPatient()
              .getMenge_Meldung()
              .getMeldung();

      // reporting reason
      var meldeanlass = meldung.getMeldeanlass();

      patId = getConvertedPatIdFromMeldung(meldungExport);

      var senderId = meldungExport.getXml_daten().getAbsender().getAbsender_ID();
      var softwareId = meldungExport.getXml_daten().getAbsender().getSoftware_ID();

      metaSource = generateProfileMetaSource(senderId, softwareId, appVersion);

      List<Tupel<ADT_GEKID.HistologieAbs, Meldeanlass>> histList = new ArrayList<>();
      Triple<ADT_GEKID.CTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation, Meldeanlass> cTnm =
          null;
      Triple<ADT_GEKID.PTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation, Meldeanlass> pTnm =
          null;
      List<Tupel<ADT_GEKID.FernMetastaseAbs, Meldeanlass>> fernMetaList = new ArrayList<>();

      ModulProstataMappingParams prostataMappingParams = null;

      if (meldeanlass == Meldeanlass.DIAGNOSE) {
        // aus Diagnose: histologie, grading, c-tnm und p-tnm
        histList = new ArrayList<>();
        var mengeHistologie = meldung.getDiagnose().getMenge_Histologie();
        if (mengeHistologie != null) {
          for (var hist : mengeHistologie.getHistologie()) {
            histList.add(new Tupel<>(hist, meldeanlass));
          }
        }

        cTnm =
            new Triple<>(
                meldung.getDiagnose().getCTNM(),
                meldung.getDiagnose().getMenge_Weitere_Klassifikation(),
                meldeanlass);
        pTnm =
            new Triple<>(
                meldung.getDiagnose().getPTNM(),
                meldung.getDiagnose().getMenge_Weitere_Klassifikation(),
                meldeanlass);

        fernMetaList = new ArrayList<>();
        if (meldung.getDiagnose().getMenge_FM() != null) {
          for (var fernMeta : meldung.getDiagnose().getMenge_FM().getFernmetastase()) {
            fernMetaList.add(new Tupel<>(fernMeta, meldeanlass));
          }
        }

        if (meldung.getDiagnose().getModul_Prostata().isPresent()) {
          var baseDatum =
              convertObdsDateToDateTimeType(meldung.getDiagnose().getDiagnosedatum().orElse(null));
          var meldedatum = convertObdsDateToDateTimeType(meldung.getMeldedatum());

          prostataMappingParams =
              new ModulProstataMappingParams(
                  meldeanlass,
                  meldung.getDiagnose().getModul_Prostata().get(),
                  patId,
                  meldung.getDiagnose().getTumor_ID(),
                  baseDatum,
                  meldedatum);
        }
      } else if (meldeanlass == Meldeanlass.STATUSAENDERUNG) {
        // aus Verlauf: histologie, grading und p-tnm
        // TODO Menge Verlauf berueksichtigen ggf. abfangen (in Erlangen immer nur ein
        // Verlauf in
        // Menge_Verlauf), Jasmin klaert das noch
        var verlauf = meldung.getMenge_Verlauf().getVerlauf();
        var hist = verlauf.getHistologie();
        if (hist != null) {
          histList = List.of(new Tupel<>(hist, meldeanlass));
        }

        var statusTnm = verlauf.getTNM();
        if (statusTnm != null
            && (Objects.equals(statusTnm.getTNM_c_p_u_Praefix_T(), "p")
                || Objects.equals(statusTnm.getTNM_c_p_u_Praefix_N(), "p")
                || Objects.equals(statusTnm.getTNM_c_p_u_Praefix_M(), "p"))) {
          pTnm = new Triple<>(statusTnm, null, meldeanlass);
        }

        fernMetaList = new ArrayList<>();
        if (verlauf.getMenge_FM() != null) {
          for (var fernMeta : verlauf.getMenge_FM().getFernmetastase()) {
            fernMetaList.add(new Tupel<>(fernMeta, meldeanlass));
          }
        }

        if (verlauf.getModul_Prostata().isPresent()) {
          var baseDatum =
              convertObdsDateToDateTimeType(verlauf.getUntersuchungsdatum_Verlauf().orElse(null));
          var meldedatum = convertObdsDateToDateTimeType(meldung.getMeldedatum());

          prostataMappingParams =
              new ModulProstataMappingParams(
                  meldeanlass,
                  verlauf.getModul_Prostata().get(),
                  patId,
                  meldung.getTumorzuordnung().getTumor_ID() + " - " + verlauf.getVerlauf_ID(),
                  baseDatum,
                  meldedatum);
        }
      } else if (meldeanlass == Meldeanlass.BEHANDLUNGSENDE) {
        // aus Operation: histologie, grading und p-tnm
        // TODO Menge OP berueksichtigen, in Erlangen aber immer neue Meldung
        if (meldung.getMenge_OP() != null) {
          var op = meldung.getMenge_OP().getOP();
          var hist = op.getHistologie();
          if (hist != null) {
            histList = List.of(new Tupel<>(hist, meldeanlass));
          }
          pTnm = new Triple<>(op.getTNM(), null, meldeanlass);

          if (op.getModul_Prostata().isPresent()) {
            var baseDatum = convertObdsDateToDateTimeType(op.getOP_Datum().orElse(null));
            var meldedatum = convertObdsDateToDateTimeType(meldung.getMeldedatum());

            prostataMappingParams =
                new ModulProstataMappingParams(
                    meldeanlass,
                    op.getModul_Prostata().get(),
                    patId,
                    meldung.getTumorzuordnung().getTumor_ID() + " - " + op.getOP_ID(),
                    baseDatum,
                    meldedatum);
          }
        }
      } else if (meldeanlass == Meldeanlass.TOD) {

        var mengeVerlauf =
            meldungExport
                .getXml_daten()
                .getMenge_Patient()
                .getPatient()
                .getMenge_Meldung()
                .getMeldung()
                .getMenge_Verlauf();

        if (mengeVerlauf != null && mengeVerlauf.getVerlauf() != null) {
          verlaufId = mengeVerlauf.getVerlauf().getVerlauf_ID();
          death = mengeVerlauf.getVerlauf().getTod();
        }
      }

      if (!histList.isEmpty()) {
        for (var hist : histList) {
          histMap.put(hist.getFirst().getHistologie_ID(), hist);
        }
      }

      if (cTnm != null && cTnm.getFirst() != null) {
        cTnmMap.put(cTnm.getFirst().getTNM_ID(), cTnm);
      }

      if (pTnm != null && pTnm.getFirst() != null) {
        pTnmMap.put(pTnm.getFirst().getTNM_ID(), pTnm);
      }

      if (prostataMappingParams != null) {
        modulProstataMappingMap.put(prostataMappingParams.baseId(), prostataMappingParams);
      }

      if (!fernMetaList.isEmpty()) {
        var index = 0;
        for (var fernMetaTupel : fernMetaList) {
          var fernMetaId =
              fernMetaTupel.getFirst().getFM_Diagnosedatum()
                  + fernMetaTupel.getFirst().getFM_Lokalisation();
          // Sonderfall OTH, hier brauchen wir alle (Datum und Lokalisation kann mehrfach
          // relevant
          // vorhanden sein)
          if (Objects.equals(fernMetaTupel.getFirst().getFM_Lokalisation(), "OTH")) {
            // TODO was wenn neues Datum mit OTH dann kann Reihenfolge nicht mehr stimmen
            // und führt
            // hier zum Bug
            fernMetaId += index;
            index++;
          }
          fernMetaMap.put(fernMetaId, fernMetaTupel);
        }
      }
    }

    return mapOnkoResourcesToObservationsBundle(
        new ArrayList<>(histMap.values()),
        new ArrayList<>(cTnmMap.values()),
        new ArrayList<>(pTnmMap.values()),
        new ArrayList<>(modulProstataMappingMap.values()),
        fernMetaMap,
        death,
        patId,
        metaSource,
        verlaufId);
  }

  // public ValueMapper<MeldungExport, Bundle> getOnkoToObservationBundleMapper()
  // {
  // return meldungExport -> {
  public Bundle mapOnkoResourcesToObservationsBundle(
      List<Tupel<ADT_GEKID.HistologieAbs, Meldeanlass>> histologieList,
      List<Triple<ADT_GEKID.CTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation, Meldeanlass>>
          cTnmList,
      List<Triple<ADT_GEKID.PTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation, Meldeanlass>>
          pTnmList,
      List<ModulProstataMappingParams> modulProstataList,
      Map<String, Tupel<ADT_GEKID.FernMetastaseAbs, Meldeanlass>> fernMetaMap,
      Tod death,
      String patId,
      String metaSource,
      String verlaufId) {

    var patientReference =
        new Reference()
            .setReference(ResourceType.Patient + "/" + this.getHash(ResourceType.Patient, patId))
            .setIdentifier(
                new Identifier()
                    .setSystem(fhirProperties.getSystems().getPatientId())
                    .setType(
                        new CodeableConcept(
                            new Coding(
                                fhirProperties.getSystems().getIdentifierType(), "MR", null)))
                    .setValue(patId));

    var bundle = new Bundle();

    if (histologieList.isEmpty()
        && cTnmList.isEmpty()
        && pTnmList.isEmpty()
        && fernMetaMap.isEmpty()) {
      // Meldeanlaesse: behandlungsbeginn, tod
      return null;
    }

    for (var hist : histologieList) {
      bundle =
          createHistologieAndGradingObservation(
              bundle, patId, metaSource, hist.getFirst(), hist.getSecond(), patientReference);
    }

    for (var cTnm : cTnmList) {
      bundle =
          createCTnmObservation(
              bundle,
              patId,
              metaSource,
              cTnm.getFirst(),
              cTnm.getSecond(),
              cTnm.getThird(),
              patientReference);
    }

    for (var pTnm : pTnmList) {
      bundle =
          createPTnmObservation(
              bundle,
              patId,
              metaSource,
              pTnm.getFirst(),
              pTnm.getSecond(),
              pTnm.getThird(),
              patientReference);
    }

    for (var fernMetaTupel : fernMetaMap.entrySet()) {
      bundle =
          createFernMetaObservation(
              bundle,
              metaSource,
              fernMetaTupel.getKey(),
              fernMetaTupel.getValue().getFirst(),
              fernMetaTupel.getValue().getSecond(),
              patientReference);
    }

    if (isModulProstataMappingEnabled) {
      for (var modulProstataParams : modulProstataList) {
        if (modulProstataParams.modulProstata.getGleasonScore().isPresent()) {
          bundle =
              createGleasonScoreObservation(
                  bundle, metaSource, modulProstataParams, patientReference);
        }

        if (modulProstataParams.modulProstata.getPSA().isPresent()) {
          bundle = createPsaObservation(bundle, metaSource, modulProstataParams, patientReference);
        }
      }
    }

    if (death != null) {
      bundle =
          createDeathObservation(bundle, patId, metaSource, verlaufId, death, patientReference);
    }

    if (bundle.getEntry().isEmpty()) {
      return null;
    }
    bundle.setType(Bundle.BundleType.TRANSACTION);
    return bundle;
  }

  private Bundle createGleasonScoreObservation(
      Bundle bundle,
      String metaSource,
      ModulProstataMappingParams modulProstataParams,
      Reference patientReference) {
    var observation = gleasonScoreMapper.map(modulProstataParams, patientReference, metaSource);

    return addResourceAsEntryInBundle(bundle, observation);
  }

  private Bundle createPsaObservation(
      Bundle bundle,
      String metaSource,
      ModulProstataMappingParams modulProstataParams,
      Reference patientReference) {
    var observation = psaMapper.map(modulProstataParams, patientReference, metaSource);

    return addResourceAsEntryInBundle(bundle, observation);
  }

  public Bundle createHistologieAndGradingObservation(
      Bundle bundle,
      String patId,
      String metaSource,
      ADT_GEKID.HistologieAbs histologie,
      Meldeanlass meldeanlass,
      Reference patientReference) {

    // Create a Grading Observation as in
    // https://simplifier.net/oncology/grading
    var gradingObs = new Observation();

    var histId = histologie.getHistologie_ID();

    // Histologiedatum
    var histDateString = histologie.getTumor_Histologiedatum();

    if (histDateString != null) {
      var histDateTimeType = convertObdsDateToDateTimeType(histDateString);
      if (histDateTimeType != null) {
        gradingObs.setEffective(histDateTimeType);
      }
    }

    var grading = histologie.getGrading();

    // Generate an identifier based on Referenz_nummer (Pat. Id) and Histologie_ID
    var gradingObsIdentifier = patId + "grading" + histId;

    // grading may be undefined / null
    if (grading != null) {

      gradingObs.setId(this.getHash(ResourceType.Observation, gradingObsIdentifier));

      gradingObs.getMeta().setSource(metaSource);

      gradingObs
          .getMeta()
          .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getGrading())));

      if (meldeanlass == Meldeanlass.STATUSAENDERUNG) {
        gradingObs.setStatus(ObservationStatus.AMENDED);
      } else {
        gradingObs.setStatus(ObservationStatus.FINAL);
      }

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

      gradingObs.setSubject(patientReference);

      var gradingValueCodeableCon =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getGradingDktk())
                  .setCode(grading)
                  .setDisplay(GradingLookup.lookupDisplay(grading)));

      gradingObs.setValue(gradingValueCodeableCon);
    }

    // Create an Histologie Observation as in
    // https://simplifier.net/oncology/histologie
    var histObs = new Observation();

    // Generate an identifier based on Referenz_nummer (Pat. Id) and Histologie_ID
    var observationIdentifier = patId + "histologie" + histId;

    histObs.setId(this.getHash(ResourceType.Observation, observationIdentifier));

    histObs.getMeta().setSource(metaSource);

    histObs
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getHistologie())));

    if (meldeanlass == Meldeanlass.STATUSAENDERUNG) {
      histObs.setStatus(ObservationStatus.AMENDED);
    } else {
      histObs.setStatus(ObservationStatus.FINAL);
    }

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

    histObs.setSubject(patientReference);

    // Histologiedatum
    if (histDateString != null) {
      histObs.setEffective(convertObdsDateToDateTimeType(histDateString));
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
              .setReference(
                  ResourceType.Observation
                      + "/"
                      + this.getHash(ResourceType.Observation, gradingObsIdentifier)));
    }

    bundle = addResourceAsEntryInBundle(bundle, histObs);

    if (grading != null) {
      bundle = addResourceAsEntryInBundle(bundle, gradingObs);
    }

    return bundle;
  }

  public Bundle createFernMetaObservation(
      Bundle bundle,
      String metaSource,
      String fernMetaId,
      ADT_GEKID.FernMetastaseAbs fernMeta,
      Meldeanlass meldeanlass,
      Reference patientReference) {

    // Create a Fernmetastasen Observation as in
    // https://simplifier.net/oncology/fernmetastasen-duplicate-2
    var fernMetaObs = new Observation();

    var fernMetaDateString = fernMeta.getFM_Diagnosedatum();
    var fernMetaLokal = fernMeta.getFM_Lokalisation();

    // TODO: this builds the indentifier slightly different than the other
    // Observations.
    // worth investigating if we should standardize instead?
    fernMetaObs.setId(this.getHash(ResourceType.Observation, fernMetaId));

    fernMetaObs.getMeta().setSource(metaSource);

    fernMetaObs
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getFernMeta())));

    if (meldeanlass == Meldeanlass.STATUSAENDERUNG) {
      fernMetaObs.setStatus(ObservationStatus.AMENDED);
    } else {
      fernMetaObs.setStatus(ObservationStatus.FINAL);
    }

    fernMetaObs.addCategory(
        new CodeableConcept()
            .addCoding(
                new Coding(
                    fhirProperties.getSystems().getObservationCategorySystem(),
                    "laboratory",
                    "Laboratory")));

    fernMetaObs.setCode(
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getLoinc())
                .setCode("21907-1")
                .setDisplay(fhirProperties.getDisplay().getFernMetaLoinc())));

    fernMetaObs.setSubject(patientReference);

    // Fernmetastasendatum
    if (fernMetaDateString != null) {
      fernMetaObs.setEffective(convertObdsDateToDateTimeType(fernMetaDateString));
    }

    fernMetaObs.setValue(
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getJnuCs())
                .setCode("J")
                .setDisplay("Ja")));

    fernMetaObs.setBodySite(
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getFMLokalisationCS())
                .setCode(fernMetaLokal)
                .setDisplay(FMLokalisationVsLookup.lookupDisplay(fernMetaLokal))));

    bundle = addResourceAsEntryInBundle(bundle, fernMetaObs);

    return bundle;
  }

  public Bundle createCTnmObservation(
      Bundle bundle,
      String patId,
      String metaSource,
      ADT_GEKID.CTnmAbs cTnm,
      Meldung.Diagnose.Menge_Weitere_Klassifikation classification,
      Meldeanlass meldeanlass,
      Reference patientReference) {

    // TNM Observation
    // Create a TNM-c Observation as in
    // https://simplifier.net/oncology/tnmc
    var tnmcObs = new Observation();
    // Generate an identifier based on Referenz_nummer (Pat. Id) and c-tnm Id
    var tnmcObsIdentifier = patId + "ctnm" + cTnm.getTNM_ID();

    tnmcObs.setId(this.getHash(ResourceType.Observation, tnmcObsIdentifier));

    tnmcObs.getMeta().setSource(metaSource);

    tnmcObs
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getTnmC())));

    if (meldeanlass == Meldeanlass.STATUSAENDERUNG) {
      tnmcObs.setStatus(ObservationStatus.AMENDED);
    } else {
      tnmcObs.setStatus(ObservationStatus.FINAL);
    }

    tnmcObs.addCategory(
        new CodeableConcept()
            .addCoding(
                new Coding(
                    fhirProperties.getSystems().getObservationCategorySystem(),
                    "laboratory",
                    "Laboratory")));

    tnmcObs.setCode(
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getLoinc())
                .setCode("21908-9")
                .setDisplay(fhirProperties.getDisplay().getTnmcLoinc())));

    tnmcObs.setSubject(patientReference);

    // tnm c Date
    var tnmcDateString = cTnm.getTNM_Datum();
    if (tnmcDateString != null) {
      var tnmcDateTimeType = convertObdsDateToDateTimeType(tnmcDateString);
      if (tnmcDateTimeType != null) {
        tnmcObs.setEffective(tnmcDateTimeType);
      }
    }

    if (classification != null
        && (classification.getWeitere_Klassifikation().getName().equals("UICC"))) {
      var valueCodeableCon =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getUicc())
                  .setCode(classification.getWeitere_Klassifikation().getStadium())
                  .setVersion(cTnm.getTNM_Version()));

      tnmcObs.setValue(valueCodeableCon);
    }

    var backBoneComponentListC = new ArrayList<Observation.ObservationComponentComponent>();

    var cTnmCpuPraefixT = cTnm.getTNM_c_p_u_Praefix_T();
    var cTnmCpuPraefixN = cTnm.getTNM_c_p_u_Praefix_N();
    var cTnmCpuPraefixM = cTnm.getTNM_c_p_u_Praefix_M();
    var cTnmT = cTnm.getTNM_T();
    var cTnmN = cTnm.getTNM_N();
    var cTnmM = cTnm.getTNM_M();
    var cTnmYSymbol = cTnm.getTNM_y_Symbol();
    var cTnmRSymbol = cTnm.getTNM_r_Symbol();
    var cTnmMSymbol = cTnm.getTNM_m_Symbol();

    // cTNM-T
    if (cTnmT != null) {
      backBoneComponentListC.add(
          createTNMComponentElement(
              cTnmCpuPraefixT,
              TnmCpuPraefixTvsLookup.lookupDisplay(cTnmCpuPraefixT),
              "21905-5",
              "Primary tumor.clinical Cancer",
              fhirProperties.getSystems().getTnmTCs(),
              cTnmT,
              cTnmT));
    }

    // cTNM-N
    if (cTnmN != null) {
      backBoneComponentListC.add(
          createTNMComponentElement(
              cTnmCpuPraefixN,
              TnmCpuPraefixTvsLookup.lookupDisplay(cTnmCpuPraefixN),
              "21906-3",
              "Regional lymph nodes.clinical",
              fhirProperties.getSystems().getTnmNCs(),
              cTnmN,
              cTnmN));
    }

    // cTNM-M
    if (cTnmM != null) {
      backBoneComponentListC.add(
          createTNMComponentElement(
              cTnmCpuPraefixM,
              TnmCpuPraefixTvsLookup.lookupDisplay(cTnmCpuPraefixM),
              "21907-1",
              "Distant metastases.clinical [Class] Cancer",
              fhirProperties.getSystems().getTnmMCs(),
              cTnmM,
              cTnmM));
    }
    // TNM-y Symbol
    if (cTnmYSymbol != null) {
      backBoneComponentListC.add(
          createTNMComponentElement(
              null,
              null,
              "59479-6",
              "Collaborative staging post treatment extension Cancer",
              fhirProperties.getSystems().getTnmYSymbolCs(),
              cTnmYSymbol,
              cTnmYSymbol));
    }

    // TNM-r Symbol
    if (cTnmRSymbol != null) {
      backBoneComponentListC.add(
          createTNMComponentElement(
              null,
              null,
              "21983-2",
              "Recurrence type first episode Cancer",
              fhirProperties.getSystems().getTnmRSymbolCs(),
              cTnmRSymbol,
              cTnmRSymbol));
    }

    // TNM-m Symbol
    if (cTnmMSymbol != null) {
      backBoneComponentListC.add(
          createTNMComponentElement(
              null,
              null,
              "42030-7",
              "Multiple tumors reported as single primary Cancer",
              fhirProperties.getSystems().getTnmMSymbolCs(),
              cTnmMSymbol,
              cTnmMSymbol));
    }

    tnmcObs.setComponent(backBoneComponentListC);

    bundle = addResourceAsEntryInBundle(bundle, tnmcObs);
    return bundle;
  }

  public Bundle createPTnmObservation(
      Bundle bundle,
      String patId,
      String metaSource,
      ADT_GEKID.PTnmAbs pTnm,
      Meldung.Diagnose.Menge_Weitere_Klassifikation classification,
      Meldeanlass meldeanlass,
      Reference patientReference) {
    // Create a TNM-p Observation as in
    // https://simplifier.net/oncology/tnmp
    var tnmpObs = new Observation();

    // Generate an identifier based on Referenz_nummer (Pat. Id) and p-tnm Id
    var tnmpObsIdentifier = patId + "ptnm" + pTnm.getTNM_ID();

    tnmpObs.setId(this.getHash(ResourceType.Observation, tnmpObsIdentifier));

    tnmpObs.getMeta().setSource(metaSource);

    tnmpObs
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getTnmP())));

    if (meldeanlass == Meldeanlass.STATUSAENDERUNG) {
      tnmpObs.setStatus(ObservationStatus.AMENDED);
    } else {
      tnmpObs.setStatus(ObservationStatus.FINAL);
    }

    tnmpObs.addCategory(
        new CodeableConcept()
            .addCoding(
                new Coding(
                    fhirProperties.getSystems().getObservationCategorySystem(),
                    "laboratory",
                    "Laboratory")));
    tnmpObs.setCode(
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getLoinc())
                .setCode("21902-2")
                .setDisplay(fhirProperties.getDisplay().getTnmpLoinc())));

    tnmpObs.setSubject(patientReference);

    // tnm p Date
    var tnmpDateString = pTnm.getTNM_Datum();
    if (tnmpDateString != null) {
      var tnmpDateTimeType = convertObdsDateToDateTimeType(tnmpDateString);
      if (tnmpDateTimeType != null) {
        tnmpObs.setEffective(tnmpDateTimeType);
      }
    }

    // only defined in diagnosis
    if (classification != null
        && (classification.getWeitere_Klassifikation().getName().equals("UICC"))) {
      var valueCodeableCon =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getUicc())
                  .setCode(classification.getWeitere_Klassifikation().getStadium())
                  .setVersion(pTnm.getTNM_Version()));

      tnmpObs.setValue(valueCodeableCon);
    }

    var backBoneComponentListP = new ArrayList<Observation.ObservationComponentComponent>();

    var pTnmCpuPraefixT = pTnm.getTNM_c_p_u_Praefix_T();
    var pTnmCpuPraefixN = pTnm.getTNM_c_p_u_Praefix_N();
    var pTnmCpuPraefixM = pTnm.getTNM_c_p_u_Praefix_M();
    var pTnmT = pTnm.getTNM_T();
    var pTnmN = pTnm.getTNM_N();
    var pTnmM = pTnm.getTNM_M();
    var pTnmYSymbol = pTnm.getTNM_y_Symbol();
    var pTnmRSymbol = pTnm.getTNM_r_Symbol();
    var pTnmMSymbol = pTnm.getTNM_m_Symbol();

    // pTNM-T
    if (pTnmT != null) {
      backBoneComponentListP.add(
          createTNMComponentElement(
              pTnmCpuPraefixT,
              TnmCpuPraefixTvsLookup.lookupDisplay(pTnmCpuPraefixT),
              "21899-0",
              "Primary tumor.pathology Cancer",
              fhirProperties.getSystems().getTnmTCs(),
              pTnmT,
              pTnmT));
    }

    // pTNM-N
    if (pTnmN != null) {
      backBoneComponentListP.add(
          createTNMComponentElement(
              pTnmCpuPraefixN,
              TnmCpuPraefixTvsLookup.lookupDisplay(pTnmCpuPraefixN),
              "21900-6",
              "Regional lymph nodes.pathology",
              fhirProperties.getSystems().getTnmNCs(),
              pTnmN,
              pTnmN));
    }

    // pTNM-M
    if (pTnmM != null) {
      backBoneComponentListP.add(
          createTNMComponentElement(
              pTnmCpuPraefixM,
              TnmCpuPraefixTvsLookup.lookupDisplay(pTnmCpuPraefixM),
              "21901-4",
              "Distant metastases.pathology [Class] Cancer",
              fhirProperties.getSystems().getTnmMCs(),
              pTnmM,
              pTnmM));
    }

    // pTNM-y Symbol
    if (pTnmYSymbol != null) {
      backBoneComponentListP.add(
          createTNMComponentElement(
              null,
              null,
              "59479-6",
              "Collaborative staging post treatment extension Cancer",
              fhirProperties.getSystems().getTnmYSymbolCs(),
              pTnmYSymbol,
              pTnmYSymbol));
    }

    // TNM-r Symbol
    if (pTnmRSymbol != null) {
      backBoneComponentListP.add(
          createTNMComponentElement(
              null,
              null,
              "21983-2",
              "Recurrence type first episode Cancer",
              fhirProperties.getSystems().getTnmRSymbolCs(),
              pTnmRSymbol,
              pTnmRSymbol));
    }

    // TNM-m Symbol
    if (pTnmMSymbol != null) {
      backBoneComponentListP.add(
          createTNMComponentElement(
              null,
              null,
              "42030-7",
              "Multiple tumors reported as single primary Cancer",
              fhirProperties.getSystems().getTnmMSymbolCs(),
              pTnmMSymbol,
              pTnmMSymbol));
    }

    tnmpObs.setComponent(backBoneComponentListP);

    bundle = addResourceAsEntryInBundle(bundle, tnmpObs);
    return bundle;
  }

  public Observation.ObservationComponentComponent createTNMComponentElement(
      String tnmPraefixCode,
      String tnmPraefixDisplay,
      String tnmCodeCode,
      String tnmCodeDisplay,
      String tnmValueSystem,
      String tnmValueCode,
      String tnmValueDisplay) {
    var tnmBackBone = new Observation.ObservationComponentComponent();

    if (tnmPraefixCode != null) {
      var tnmPraefixExtens =
          new Extension()
              .setUrl(fhirProperties.getUrl().getTnmPraefix())
              .setValue(
                  new CodeableConcept()
                      .addCoding(
                          new Coding(
                              fhirProperties.getSystems().getTnmPraefix(),
                              tnmPraefixCode,
                              tnmPraefixDisplay)));

      tnmBackBone.addExtension(tnmPraefixExtens);
    }

    tnmBackBone.setCode(
        new CodeableConcept(
            new Coding(fhirProperties.getSystems().getLoinc(), tnmCodeCode, tnmCodeDisplay)));

    tnmBackBone.setValue(
        new CodeableConcept(new Coding(tnmValueSystem, tnmValueCode, tnmValueDisplay)));

    return tnmBackBone;
  }

  public Bundle createDeathObservation(
      Bundle bundle,
      String patId,
      String metaSource,
      String verlaufId,
      Tod death,
      Reference patientReference) {

    // Create a Death Observation as in
    // https://simplifier.net/oncology/todursache
    var deathObs = new Observation();

    var deathId = patId + "death" + verlaufId;

    deathObs.setId(this.getHash(ResourceType.Observation, deathId));

    deathObs.getMeta().setSource(metaSource);

    deathObs
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getDeathObservation())));

    deathObs.setStatus(ObservationStatus.FINAL);

    deathObs.setCode(
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getLoinc())
                .setCode("68343-3")
                .setDisplay(fhirProperties.getDisplay().getDeathLoinc())));

    deathObs.setSubject(patientReference);

    // Sterbedatum
    var deathDateString = death.getSterbedatum();
    if (deathDateString != null) {
      deathObs.setEffective(convertObdsDateToDateTimeType(deathDateString));
    }

    var deathValueCodeConcept = new CodeableConcept();

    if (death.getMenge_Todesursache() != null
        && death.getMenge_Todesursache().getTodesursache_ICD() != null) {
      var icdCoding =
          new Coding()
              .setSystem(fhirProperties.getSystems().getIcd10gm())
              .setCode(death.getMenge_Todesursache().getTodesursache_ICD());
      deathValueCodeConcept.addCoding(icdCoding);
    }

    if (death.getTod_tumorbedingt() != null) {
      var deathByTumorCoding =
          new Coding()
              .setSystem(fhirProperties.getSystems().getJnuCs())
              .setCode(death.getTod_tumorbedingt())
              .setDisplay(JnuVsLookup.lookupDisplay(death.getTod_tumorbedingt()));
      deathValueCodeConcept.addCoding(deathByTumorCoding);
    }

    if (!deathValueCodeConcept.isEmpty()) {
      deathObs.setValue(deathValueCodeConcept);
      bundle = addResourceAsEntryInBundle(bundle, deathObs);
    }

    return bundle;
  }
}
