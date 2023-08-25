package org.miracum.streams.ume.onkoadttofhir.mapper;

import java.util.*;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.lookup.FMLokalisationVsLookup;
import org.miracum.streams.ume.onkoadttofhir.lookup.GradingLookup;
import org.miracum.streams.ume.onkoadttofhir.lookup.JnuVsLookup;
import org.miracum.streams.ume.onkoadttofhir.lookup.TnmCpuPraefixTvsLookup;
import org.miracum.streams.ume.onkoadttofhir.model.*;
import org.miracum.streams.ume.onkoadttofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung;
import org.miracum.streams.ume.onkoadttofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung.Menge_Verlauf.Verlauf.Tod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoObservationMapper extends OnkoToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(OnkoObservationMapper.class);

  private final GradingLookup gradingLookup = new GradingLookup();

  private final TnmCpuPraefixTvsLookup tnmPraefixLookup = new TnmCpuPraefixTvsLookup();

  private final FMLokalisationVsLookup fmLokalisationVSLookup = new FMLokalisationVsLookup();

  private final JnuVsLookup jnuVsLookup = new JnuVsLookup();

  @Value("${app.version}")
  private String appVersion;

  @Value("#{new Boolean('${app.enableCheckDigitConversion}')}")
  private boolean checkDigitConversion;

  public OnkoObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Bundle mapOnkoResourcesToObservation(List<MeldungExport> meldungExportList) {

    if (meldungExportList.isEmpty()) {
      return null;
    }

    HashMap<String, Tupel<ADT_GEKID.HistologieAbs, String>> histMap = new HashMap<>();
    HashMap<
            String,
            Triple<ADT_GEKID.CTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation, String>>
        cTnmMap = new HashMap<>();
    HashMap<
            String,
            Triple<ADT_GEKID.PTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation, String>>
        pTnmMap = new HashMap<>();
    HashMap<String, Tupel<ADT_GEKID.FernMetastaseAbs, String>> fernMetaMap = new HashMap<>();
    Tod death = null;

    var patId = "";
    var senderId = "";
    var softwareId = "";
    var verlaufId = "";

    for (var meldungExport : meldungExportList) {

      LOG.debug("Mapping Meldung {} to {}", getReportingIdFromAdt(meldungExport), "observation");

      var meldung =
          meldungExport
              .getXml_daten()
              .getMenge_Patient()
              .getPatient()
              .getMenge_Meldung()
              .getMeldung();

      // reporting reason
      var meldeanlass = meldung.getMeldeanlass();

      patId = getPatIdFromAdt(meldungExport);
      senderId = meldungExport.getXml_daten().getAbsender().getAbsender_ID();
      softwareId = meldungExport.getXml_daten().getAbsender().getSoftware_ID();

      // var tumorId = meldung.getTumorzuordnung();

      List<Tupel<ADT_GEKID.HistologieAbs, String>> histList = new ArrayList<>();
      Triple<ADT_GEKID.CTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation, String> cTnm = null;
      Triple<ADT_GEKID.PTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation, String> pTnm = null;
      List<Tupel<ADT_GEKID.FernMetastaseAbs, String>> fernMetaList = new ArrayList<>();

      if (Objects.equals(meldeanlass, "diagnose")) {
        // aus Diagnose: histologie, grading, c-tnm und p-tnm
        histList = new ArrayList<>();
        for (var hist : meldung.getDiagnose().getMenge_Histologie().getHistologie()) {
          histList.add(new Tupel<>(hist, meldeanlass));
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

      } else if (Objects.equals(meldeanlass, "statusaenderung")) {
        // aus Verlauf: histologie, grading und p-tnm
        // TODO Menge Verlauf berueksichtigen ggf. abfangen (in Erlangen immer nur ein Verlauf in
        // Menge_Verlauf), Jasmin klaert das noch
        var hist = meldung.getMenge_Verlauf().getVerlauf().getHistologie();
        if (hist != null) {
          histList = Arrays.asList(new Tupel<>(hist, meldeanlass));
        }

        var statusTnm = meldung.getMenge_Verlauf().getVerlauf().getTNM();
        if (statusTnm != null) {
          if (Objects.equals(statusTnm.getTNM_c_p_u_Praefix_T(), "p")
              || Objects.equals(statusTnm.getTNM_c_p_u_Praefix_N(), "p")
              || Objects.equals(statusTnm.getTNM_c_p_u_Praefix_M(), "p")) {
            pTnm = new Triple<>(statusTnm, null, meldeanlass);
          }
        }

        fernMetaList = new ArrayList<>();
        if (meldung.getMenge_Verlauf().getVerlauf().getMenge_FM() != null) {
          for (var fernMeta :
              meldung.getMenge_Verlauf().getVerlauf().getMenge_FM().getFernmetastase()) {
            fernMetaList.add(new Tupel<>(fernMeta, meldeanlass));
          }
        }
      } else if (Objects.equals(meldeanlass, "behandlungsende")) {
        // aus Operation: histologie, grading und p-tnm
        // TODO Menge OP berueksichtigen, in Erlangen aber immer neue Meldung
        if (meldung.getMenge_OP() != null) {
          var hist = meldung.getMenge_OP().getOP().getHistologie();
          if (hist != null) {
            histList = Arrays.asList(new Tupel<>(hist, meldeanlass));
          }
          pTnm = new Triple<>(meldung.getMenge_OP().getOP().getTNM(), null, meldeanlass);
        }
      } else if (Objects.equals(meldeanlass, "tod")) {

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

      if (!fernMetaList.isEmpty()) {
        var index = 0;
        for (var fernMetaTupel : fernMetaList) {
          var fernMetaId =
              fernMetaTupel.getFirst().getFM_Diagnosedatum()
                  + fernMetaTupel.getFirst().getFM_Lokalisation();
          // Sonderfall OTH, hier brauchen wir alle (Datum und Lokalisation kann mehrfach relevant
          // vorhanden sein)
          if (Objects.equals(fernMetaTupel.getFirst().getFM_Lokalisation(), "OTH")) {
            // TODO was wenn neues Datum mit OTH dann kann Reihenfolge nicht mehr stimmen und führt
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
        fernMetaMap,
        death,
        patId,
        senderId,
        softwareId,
        verlaufId);
  }

  // public ValueMapper<MeldungExport, Bundle> getOnkoToObservationBundleMapper() {
  // return meldungExport -> {
  public Bundle mapOnkoResourcesToObservationsBundle(
      List<Tupel<ADT_GEKID.HistologieAbs, String>> histologieList,
      List<Triple<ADT_GEKID.CTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation, String>>
          cTnmList,
      List<Triple<ADT_GEKID.PTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation, String>>
          pTnmList,
      HashMap<String, Tupel<ADT_GEKID.FernMetastaseAbs, String>> fernMetaMap,
      Tod death,
      String patId,
      String senderId,
      String softwareId,
      String verlaufId) {

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
              bundle, patId, senderId, softwareId, hist.getFirst(), hist.getSecond());
    }

    for (var cTnm : cTnmList) {
      bundle =
          createCTnmObservation(
              bundle,
              patId,
              senderId,
              softwareId,
              cTnm.getFirst(),
              cTnm.getSecond(),
              cTnm.getThird());
    }

    for (var pTnm : pTnmList) {
      bundle =
          createPTnmObservation(
              bundle,
              patId,
              senderId,
              softwareId,
              pTnm.getFirst(),
              pTnm.getSecond(),
              pTnm.getThird());
    }

    for (var fernMetaTupel : fernMetaMap.entrySet()) {
      bundle =
          createFernMetaObservation(
              bundle,
              patId,
              senderId,
              softwareId,
              fernMetaTupel.getKey(),
              fernMetaTupel.getValue().getFirst(),
              fernMetaTupel.getValue().getSecond());
    }

    if (death != null) {
      bundle = createDeathObservation(bundle, patId, senderId, softwareId, verlaufId, death);
    }

    if (bundle.getEntry().isEmpty()) {
      return null;
    }
    bundle.setType(Bundle.BundleType.TRANSACTION);
    return bundle;
  }

  public Bundle createHistologieAndGradingObservation(
      Bundle bundle,
      String patId,
      String senderId,
      String softwareId,
      ADT_GEKID.HistologieAbs histologie,
      String meldeanlass) {

    var pid = patId;
    if (checkDigitConversion) {
      pid = convertId(patId);
    }

    // Create a Grading Observation as in
    // https://simplifier.net/oncology/grading
    var gradingObs = new Observation();

    var histId = histologie.getHistologie_ID();

    // Histologiedatum
    var histDateString = histologie.getTumor_Histologiedatum();

    if (histDateString != null) {
      gradingObs.setEffective(extractDateTimeFromADTDate(histDateString));
    }

    var grading = histologie.getGrading();

    // Generate an identifier based on Referenz_nummer (Pat. Id) and Histologie_ID
    var gradingObsIdentifier = pid + "grading" + histId;

    // grading may be undefined / null
    if (grading != null) {

      gradingObs.setId(this.getHash("Observation", gradingObsIdentifier));

      gradingObs.getMeta().setSource(generateProfileMetaSource(senderId, softwareId, appVersion));

      gradingObs
          .getMeta()
          .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getGrading())));

      if (Objects.equals(meldeanlass, "statusaenderung")) {
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

      gradingObs.setSubject(
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

      var gradingValueCodeableCon =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getGradingDktk())
                  .setCode(grading)
                  .setDisplay(gradingLookup.lookupGradingDisplay(grading)));

      gradingObs.setValue(gradingValueCodeableCon);
    }

    // Create an Histologie Observation as in
    // https://simplifier.net/oncology/histologie
    var histObs = new Observation();

    // Generate an identifier based on Referenz_nummer (Pat. Id) and Histologie_ID
    var observationIdentifier = pid + "histologie" + histId;

    histObs.setId(this.getHash("Observation", observationIdentifier));

    histObs.getMeta().setSource(generateProfileMetaSource(senderId, softwareId, appVersion));

    histObs
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getHistologie())));

    if (Objects.equals(meldeanlass, "statusaenderung")) {
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

    histObs.setSubject(
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

    // Histologiedatum
    if (histDateString != null) {
      histObs.setEffective(extractDateTimeFromADTDate(histDateString));
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

    bundle = addResourceAsEntryInBundle(bundle, histObs);

    if (grading != null) {
      bundle = addResourceAsEntryInBundle(bundle, gradingObs);
    }

    return bundle;
  }

  public Bundle createFernMetaObservation(
      Bundle bundle,
      String patId,
      String senderId,
      String softwareId,
      String fernMetaId,
      ADT_GEKID.FernMetastaseAbs fernMeta,
      String meldeanlass) {

    var pid = patId;
    if (checkDigitConversion) {
      pid = convertId(patId);
    }

    // Create a Fernmetastasen Observation as in
    // https://simplifier.net/oncology/fernmetastasen-duplicate-2
    var fernMetaObs = new Observation();

    var fernMetaDateString = fernMeta.getFM_Diagnosedatum();
    var fernMetaLokal = fernMeta.getFM_Lokalisation();

    fernMetaObs.setId(this.getHash("Observation", fernMetaId));

    fernMetaObs.getMeta().setSource(generateProfileMetaSource(senderId, softwareId, appVersion));

    fernMetaObs
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getFernMeta())));

    if (Objects.equals(meldeanlass, "statusaenderung")) {
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

    fernMetaObs.setSubject(
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

    // Fernmetastasendatum
    if (fernMetaDateString != null) {
      fernMetaObs.setEffective(extractDateTimeFromADTDate(fernMetaDateString));
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
                .setDisplay(fmLokalisationVSLookup.lookupFMLokalisationVSDisplay(fernMetaLokal))));

    bundle = addResourceAsEntryInBundle(bundle, fernMetaObs);

    return bundle;
  }

  public Bundle createCTnmObservation(
      Bundle bundle,
      String patId,
      String senderId,
      String softwareId,
      ADT_GEKID.CTnmAbs cTnm,
      Meldung.Diagnose.Menge_Weitere_Klassifikation classification,
      String meldeanlass) {

    var pid = patId;
    if (checkDigitConversion) {
      pid = convertId(patId);
    }
    // TNM Observation
    // Create a TNM-c Observation as in
    // https://simplifier.net/oncology/tnmc
    var tnmcObs = new Observation();
    // Generate an identifier based on Referenz_nummer (Pat. Id) and c-tnm Id
    var tnmcObsIdentifier = pid + "ctnm" + cTnm.getTNM_ID();

    tnmcObs.setId(this.getHash("Observation", tnmcObsIdentifier));

    tnmcObs.getMeta().setSource(generateProfileMetaSource(senderId, softwareId, appVersion));

    tnmcObs
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getTnmC())));

    if (Objects.equals(meldeanlass, "statusaenderung")) {
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

    tnmcObs.setSubject(
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

    // tnm c Date
    var tnmcDateString = cTnm.getTNM_Datum();

    if (tnmcDateString != null) {
      tnmcObs.setEffective(extractDateTimeFromADTDate(tnmcDateString));
    }

    if (classification != null) {
      if (classification.getWeitere_Klassifikation().getName().equals("UICC")) {
        var valueCodeableCon =
            new CodeableConcept(
                new Coding()
                    .setSystem(fhirProperties.getSystems().getUicc())
                    .setCode(classification.getWeitere_Klassifikation().getStadium())
                    .setVersion(cTnm.getTNM_Version()));

        tnmcObs.setValue(valueCodeableCon);
      }
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
              tnmPraefixLookup.lookupTnmCpuPraefixDisplay(cTnmCpuPraefixT),
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
              tnmPraefixLookup.lookupTnmCpuPraefixDisplay(cTnmCpuPraefixN),
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
              tnmPraefixLookup.lookupTnmCpuPraefixDisplay(cTnmCpuPraefixM),
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
      String senderId,
      String softwareId,
      ADT_GEKID.PTnmAbs pTnm,
      Meldung.Diagnose.Menge_Weitere_Klassifikation classification,
      String meldeanlass) {
    // Create a TNM-p Observation as in
    // https://simplifier.net/oncology/tnmp
    var tnmpObs = new Observation();

    var pid = patId;
    if (checkDigitConversion) {
      pid = convertId(patId);
    }

    // Generate an identifier based on Referenz_nummer (Pat. Id) and p-tnm Id
    var tnmpObsIdentifier = pid + "ptnm" + pTnm.getTNM_ID();

    tnmpObs.setId(this.getHash("Observation", tnmpObsIdentifier));

    tnmpObs.getMeta().setSource(generateProfileMetaSource(senderId, softwareId, appVersion));

    tnmpObs
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getTnmP())));

    if (Objects.equals(meldeanlass, "statusaenderung")) {
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

    tnmpObs.setSubject(
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

    // tnm p Date
    var tnmpDateString = pTnm.getTNM_Datum();

    if (tnmpDateString != null) {
      tnmpObs.setEffective(extractDateTimeFromADTDate(tnmpDateString));
    }

    // only defined in diagnosis
    if (classification != null) {
      if (classification.getWeitere_Klassifikation().getName().equals("UICC")) {
        var valueCodeableCon =
            new CodeableConcept(
                new Coding()
                    .setSystem(fhirProperties.getSystems().getUicc())
                    .setCode(classification.getWeitere_Klassifikation().getStadium())
                    .setVersion(pTnm.getTNM_Version()));

        tnmpObs.setValue(valueCodeableCon);
      }
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
              tnmPraefixLookup.lookupTnmCpuPraefixDisplay(pTnmCpuPraefixT),
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
              tnmPraefixLookup.lookupTnmCpuPraefixDisplay(pTnmCpuPraefixN),
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
              tnmPraefixLookup.lookupTnmCpuPraefixDisplay(pTnmCpuPraefixM),
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
      String senderId,
      String softwareId,
      String verlaufId,
      Tod death) {

    var pid = patId;
    if (checkDigitConversion) {
      pid = convertId(patId);
    }

    // Create a Death Observation as in
    // https://simplifier.net/oncology/todursache
    var deathObs = new Observation();

    var deathId = pid + "death" + verlaufId;

    deathObs.setId(this.getHash("Observation", deathId));

    deathObs.getMeta().setSource(generateProfileMetaSource(senderId, softwareId, appVersion));

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

    deathObs.setSubject(
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

    // Sterbedatum
    var deathDateString = death.getSterbedatum();
    if (deathDateString != null) {
      deathObs.setEffective(extractDateTimeFromADTDate(deathDateString));
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
              .setDisplay(jnuVsLookup.lookupJnuDisplay(death.getTod_tumorbedingt()));
      deathValueCodeConcept.addCoding(deathByTumorCoding);
    }

    if (!deathValueCodeConcept.isEmpty()) {
      deathObs.setValue(deathValueCodeConcept);
      bundle = addResourceAsEntryInBundle(bundle, deathObs);
    }

    return bundle;
  }
}
