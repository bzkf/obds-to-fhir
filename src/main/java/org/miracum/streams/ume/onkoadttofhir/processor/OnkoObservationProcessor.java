package org.miracum.streams.ume.onkoadttofhir.processor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.lookup.FMLokalisationVsLookup;
import org.miracum.streams.ume.onkoadttofhir.lookup.GradingLookup;
import org.miracum.streams.ume.onkoadttofhir.lookup.TnmCpuPraefixTvsLookup;
import org.miracum.streams.ume.onkoadttofhir.model.*;
import org.miracum.streams.ume.onkoadttofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportListSerde;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportSerde;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoObservationProcessor extends OnkoProcessor {

  private final GradingLookup gradingLookup = new GradingLookup();

  private final TnmCpuPraefixTvsLookup tnmPraefixLookup = new TnmCpuPraefixTvsLookup();

  private final FMLokalisationVsLookup fmLokalisationVSLookup = new FMLokalisationVsLookup();

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
            .mapValues(this.getOnkoToObservationBundleMapper())
            .filter((key, value) -> value != null)
            .toStream();
  }

  public ValueMapper<MeldungExportList, Bundle> getOnkoToObservationBundleMapper() {
    return meldungExporte -> {
      List<MeldungExport> meldungExportList =
          prioritiseLatestMeldungExports(
              meldungExporte, Arrays.asList("behandlungsende", "statusaenderung", "diagnose"));

      return extractOnkoResourcesFromReportingReason(meldungExportList);
    };
  }

  public Bundle extractOnkoResourcesFromReportingReason(List<MeldungExport> meldungExportList) {

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

    var patId = "";

    for (var meldungExport : meldungExportList) {
      var meldung =
          meldungExport
              .getXml_daten()
              .getMenge_Patient()
              .getPatient()
              .getMenge_Meldung()
              .getMeldung();

      // reporting reason
      var meldeanlass = meldung.getMeldeanlass();

      patId = meldungExport.getReferenz_nummer();

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
        var hist = meldung.getMenge_OP().getOP().getHistologie();
        if (hist != null) {
          histList = Arrays.asList(new Tupel<>(hist, meldeanlass));
        }
        pTnm = new Triple<>(meldung.getMenge_OP().getOP().getTNM(), null, meldeanlass);
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
        patId);
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
      String patId) {

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
          createHistologieAndGradingObservation(bundle, patId, hist.getFirst(), hist.getSecond());
    }

    for (var cTnm : cTnmList) {
      bundle =
          createCTnmObservation(bundle, patId, cTnm.getFirst(), cTnm.getSecond(), cTnm.getThird());
    }

    for (var pTnm : pTnmList) {
      bundle =
          createPTnmObservation(bundle, patId, pTnm.getFirst(), pTnm.getSecond(), pTnm.getThird());
    }

    for (var fernMetaTupel : fernMetaMap.entrySet()) {
      bundle =
          createFernMetaObservation(
              bundle,
              patId,
              fernMetaTupel.getKey(),
              fernMetaTupel.getValue().getFirst(),
              fernMetaTupel.getValue().getSecond());
    }

    if (bundle.getEntry().isEmpty()) {
      return null;
    }
    bundle.setType(Bundle.BundleType.TRANSACTION);
    return bundle;
  }

  public Bundle createHistologieAndGradingObservation(
      Bundle bundle, String patId, ADT_GEKID.HistologieAbs histologie, String meldeanlass) {

    var pid = convertId(patId);

    // Create a Grading Observation as in
    // https://simplifier.net/oncology/grading
    var gradingObs = new Observation();

    var histId = histologie.getHistologie_ID();

    // Histologiedatum
    var histDateString = histologie.getTumor_Histologiedatum();

    if (histDateString != null) {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
      LocalDate histDate = LocalDate.parse(histDateString, formatter);
      LocalDateTime histDateTime = histDate.atStartOfDay();
      gradingObs.setEffective(
          new DateTimeType(Date.from(histDateTime.atZone(ZoneId.of("Europe/Berlin")).toInstant())));
    }

    var grading = histologie.getGrading();

    // Generate an identifier based on Referenz_nummer (Pat. Id) and Histologie_ID
    var gradingObsIdentifier = pid + "grading" + histId;

    // grading may be undefined / null
    if (grading != null) {

      gradingObs.setId(this.getHash("Observation", gradingObsIdentifier));

      gradingObs
          .getMeta()
          .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkoadt-to-fhir:" + appVersion);

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

    // TODO ID muss vorhanden sein ist aber ggf. kein Pflichtfeld, im Batch Job ggf. konfigurierbar
    // machen
    // Generate an identifier based on Referenz_nummer (Pat. Id) and Histologie_ID
    var observationIdentifier = pid + "histologie" + histId;

    histObs.setId(this.getHash("Observation", observationIdentifier));

    histObs
        .getMeta()
        .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkoadt-to-fhir:" + appVersion);

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
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
      LocalDate histDate = LocalDate.parse(histDateString, formatter);
      LocalDateTime histDateTime = histDate.atStartOfDay();
      histObs.setEffective(
          new DateTimeType(Date.from(histDateTime.atZone(ZoneId.of("Europe/Berlin")).toInstant())));
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
      String fernMetaId,
      ADT_GEKID.FernMetastaseAbs fernMeta,
      String meldeanlass) {

    var pid = convertId(patId);

    // Create a Fernmetastasen Observation as in
    // https://simplifier.net/oncology/fernmetastasen-duplicate-2
    var fernMetaObs = new Observation();

    var fernMetaDateString = fernMeta.getFM_Diagnosedatum();
    var fernMetaLokal = fernMeta.getFM_Lokalisation();

    fernMetaObs.setId(this.getHash("Observation", fernMetaId));

    fernMetaObs
        .getMeta()
        .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkoadt-to-fhir:" + appVersion);

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
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
      LocalDate fernMetaDate = LocalDate.parse(fernMetaDateString, formatter);
      LocalDateTime fernMetaDateTime = fernMetaDate.atStartOfDay();
      fernMetaObs.setEffective(
          new DateTimeType(
              Date.from(fernMetaDateTime.atZone(ZoneId.of("Europe/Berlin")).toInstant())));
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
      ADT_GEKID.CTnmAbs cTnm,
      Meldung.Diagnose.Menge_Weitere_Klassifikation classification,
      String meldeanlass) {

    var pid = convertId(patId);
    // TNM Observation
    // Create a TNM-c Observation as in
    // https://simplifier.net/oncology/tnmc
    var tnmcObs = new Observation();
    // Generate an identifier based on Referenz_nummer (Pat. Id) and c-tnm Id
    var tnmcObsIdentifier = pid + "ctnm" + cTnm.getTNM_ID();

    tnmcObs.setId(this.getHash("Observation", tnmcObsIdentifier));

    tnmcObs
        .getMeta()
        .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkoadt-to-fhir:" + appVersion);

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

    // TODO setFocus, add later after diagnosis processor is implemented

    // tnm c Date
    var tnmcDateString = cTnm.getTNM_Datum();

    if (tnmcDateString != null) {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
      LocalDate tnmcDate = LocalDate.parse(tnmcDateString, formatter);
      LocalDateTime tnmcDateTime = tnmcDate.atStartOfDay();
      tnmcObs.setEffective(
          new DateTimeType(Date.from(tnmcDateTime.atZone(ZoneId.of("Europe/Berlin")).toInstant())));
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
      ADT_GEKID.PTnmAbs pTnm,
      Meldung.Diagnose.Menge_Weitere_Klassifikation classification,
      String meldeanlass) {
    // Create a TNM-p Observation as in
    // https://simplifier.net/oncology/tnmp
    var tnmpObs = new Observation();

    var pid = convertId(patId);

    // Generate an identifier based on Referenz_nummer (Pat. Id) and p-tnm Id
    var tnmpObsIdentifier = pid + "ptnm" + pTnm.getTNM_ID();

    tnmpObs.setId(this.getHash("Observation", tnmpObsIdentifier));

    tnmpObs
        .getMeta()
        .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkoadt-to-fhir:" + appVersion);

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

    // TODO setFocus, add later after diagnosis processor is implemented

    // tnm p Date
    var tnmpDateString = pTnm.getTNM_Datum();

    if (tnmpDateString != null) {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
      LocalDate tnmpDate = LocalDate.parse(tnmpDateString, formatter);
      LocalDateTime tnmpDateTime = tnmpDate.atStartOfDay();
      tnmpObs.setEffective(
          new DateTimeType(Date.from(tnmpDateTime.atZone(ZoneId.of("Europe/Berlin")).toInstant())));
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

  /*
  // k-Regel
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
  */
}
