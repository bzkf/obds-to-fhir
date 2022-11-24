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
import org.miracum.streams.ume.onkoadttofhir.lookup.TnmCpuPraefixTvsLookup;
import org.miracum.streams.ume.onkoadttofhir.model.ADT_GEKID;
import org.miracum.streams.ume.onkoadttofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExportList;
import org.miracum.streams.ume.onkoadttofhir.model.Tupel;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportListSerde;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportSerde;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoObservationProcessor extends OnkoProcessor {

  private final GradingLookup gradingLookup = new GradingLookup();

  private final TnmCpuPraefixTvsLookup tnmPraefixLookup = new TnmCpuPraefixTvsLookup();

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
            /*!value
            .getXml_daten()
            .getMenge_Patient()
            .getPatient()
            .getMenge_Meldung()
            .getMeldung()
            .getMeldung_ID()
            .startsWith("9999"))*/
            .groupBy(
                (key, value) -> KeyValue.pair(String.valueOf(value.getReferenz_nummer()), value),
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

      List<String> definedOrder = Arrays.asList("diagnose", "statusaenderung", "behandlungsende");

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

      return extractOnkoResourcesFromReportingReason(meldungExportList);
    };
  }

  public Bundle extractOnkoResourcesFromReportingReason(List<MeldungExport> meldungExportList) {

    if (meldungExportList.isEmpty()) {
      return null;
    }

    HashMap<String, ADT_GEKID.HistologieAbs> histMap = new HashMap<>();
    HashMap<String, Tupel<ADT_GEKID.CTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation>>
        cTnmMap = new HashMap<>();
    HashMap<String, Tupel<ADT_GEKID.PTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation>>
        pTnmMap = new HashMap<>();

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

      List<ADT_GEKID.HistologieAbs> histList = new ArrayList<>();
      Tupel<ADT_GEKID.CTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation> cTnm = null;
      Tupel<ADT_GEKID.PTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation> pTnm = null;

      if (Objects.equals(meldeanlass, "diagnose")) {
        // aus Diagnose: histologie, grading, c-tnm und p-tnm
        histList =
            new ArrayList<>(
                getValidHistologies(meldung.getDiagnose().getMenge_Histologie().getHistologie()));
        cTnm =
            new Tupel<>(
                meldung.getDiagnose().getCTNM(),
                meldung.getDiagnose().getMenge_Weitere_Klassifikation());
        pTnm =
            new Tupel<>(
                meldung.getDiagnose().getPTNM(),
                meldung.getDiagnose().getMenge_Weitere_Klassifikation());
      } else if (Objects.equals(meldeanlass, "statusaenderung")) {
        // aus Verlauf: histologie, grading und p-tnm
        // TODO Menge Verlauf berueksichtigen ggf. abfangen (in Erlangen immer nur ein Verlauf in
        // Menge_Verlauf), Jasmin klaert das noch
        var hist = meldung.getMenge_Verlauf().getVerlauf().getHistologie();
        if (hist != null) {
          histList = Arrays.asList(hist);
        }
        // TODO tnm Präfixe ob ptnm oder ctnm
        var statusTnm = meldung.getMenge_Verlauf().getVerlauf().getTNM();
        if (Objects.equals(statusTnm.getTNM_c_p_u_Praefix_T(), "p")
            || Objects.equals(statusTnm.getTNM_c_p_u_Praefix_N(), "p")
            || Objects.equals(statusTnm.getTNM_c_p_u_Praefix_M(), "p")) {
          pTnm = new Tupel<>(meldung.getMenge_Verlauf().getVerlauf().getTNM(), null);
        }
        // else {
        //  cTnm = new Tupel<>(meldung.getMenge_Verlauf().getVerlauf().getTNM(), null);
        // }
      } else if (Objects.equals(meldeanlass, "behandlungsende")) {
        // aus Operation: histologie, grading und p-tnm
        // TODO Menge OP berueksichtigen
        var hist = meldung.getMenge_OP().getOP().getHistologie();
        if (hist != null) {
          histList = Arrays.asList(hist);
        }
        pTnm = new Tupel<>(meldung.getMenge_OP().getOP().getTNM(), null);
      }

      if (!histList.isEmpty()) {
        for (var hist : histList) {
          histMap.put(hist.getHistologie_ID(), hist);
        }
      }

      if (cTnm != null && cTnm.getFirst() != null) {
        cTnmMap.put(cTnm.getFirst().getTNM_ID(), cTnm);
      }

      if (pTnm != null && pTnm.getFirst() != null) {
        pTnmMap.put(pTnm.getFirst().getTNM_ID(), pTnm);
      }
    }

    return mapOnkoResourcesToObservationsBundle(
        new ArrayList<>(histMap.values()),
        new ArrayList<>(cTnmMap.values()),
        new ArrayList<>(pTnmMap.values()),
        patId);
  }

  // public ValueMapper<MeldungExport, Bundle> getOnkoToObservationBundleMapper() {
  // return meldungExport -> {
  public Bundle mapOnkoResourcesToObservationsBundle(
      List<ADT_GEKID.HistologieAbs> histologieList,
      List<Tupel<ADT_GEKID.CTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation>> cTnmList,
      List<Tupel<ADT_GEKID.PTnmAbs, Meldung.Diagnose.Menge_Weitere_Klassifikation>> pTnmList,
      String patId) {

    var bundle = new Bundle();

    if (histologieList.isEmpty() && cTnmList.isEmpty() && pTnmList.isEmpty()) {
      // Meldeanlaesse: behandlungsbeginn, tod
      return null;
    }

    for (var hist : histologieList) {
      bundle = createHistologieAndGradingObservation(bundle, patId, hist);
    }

    for (var cTnm : cTnmList) {
      bundle = createCTnmObservation(bundle, patId, cTnm.getFirst(), cTnm.getSecond());
    }

    for (var pTnm : pTnmList) {
      bundle = createPTnmObservation(bundle, patId, pTnm.getFirst(), pTnm.getSecond());
    }

    if (bundle.getEntry().isEmpty()) {
      return null;
    }
    bundle.setType(Bundle.BundleType.TRANSACTION);
    return bundle;
  }

  public Bundle createHistologieAndGradingObservation(
      Bundle bundle, String patId, ADT_GEKID.HistologieAbs histologie) {

    var pid = convertId(patId);

    // Create a Grading Observation as in
    // https://simplifier.net/oncology/grading
    var gradingObs = new Observation();

    var histId = histologie.getHistologie_ID();

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

    // Generate an identifier based on Referenz_nummer (Pat. Id) and Histologie_ID
    var gradingObsIdentifier = patId + "grading" + histId;

    // grading may be undefined / null
    if (grading != null) {

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

    // TODO ID muss vorhanden sein ist aber ggf. kein Pflichtfeld, im Batch Job ggf. konfigurierbar
    // machen
    // Generate an identifier based on Referenz_nummer (Pat. Id) and Histologie_ID
    var observationIdentifier = patId + "histologie" + histId;

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

    bundle = addResourceAsEntryInBundle(bundle, histObs);

    if (grading != null) {
      bundle = addResourceAsEntryInBundle(bundle, gradingObs);
    }

    return bundle;
  }

  public Bundle createCTnmObservation(
      Bundle bundle,
      String patId,
      ADT_GEKID.CTnmAbs cTnm,
      Meldung.Diagnose.Menge_Weitere_Klassifikation classification) {

    var pid = convertId(patId);
    // TNM Observation
    // Create a TNM-c Observation as in
    // https://simplifier.net/oncology/tnmc
    var tnmcObs = new Observation();
    // Generate an identifier based on Referenz_nummer (Pat. Id) and c-tnm Id
    var tnmcObsIdentifier = patId + "ctnm" + cTnm.getTNM_ID();

    tnmcObs.setId(this.getHash("Observation", tnmcObsIdentifier));

    tnmcObs
        .getMeta()
        .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkostar-to-fhir:" + appVersion);

    tnmcObs
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getTnmC())));

    tnmcObs.setStatus(ObservationStatus.FINAL);

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
    Date tnmcDate = null;

    if (tnmcDateString != null) {
      SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN);
      try {
        tnmcDate = formatter.parse(tnmcDateString);
      } catch (ParseException e) {
        throw new RuntimeException(e);
      }
    }

    if (tnmcDate != null) {
      tnmcObs.setEffective(new DateTimeType(tnmcDate));
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
      Meldung.Diagnose.Menge_Weitere_Klassifikation classification) {
    // Create a TNM-p Observation as in
    // https://simplifier.net/oncology/tnmp
    var tnmpObs = new Observation();

    var pid = convertId(patId);

    // Generate an identifier based on Referenz_nummer (Pat. Id) and p-tnm Id
    var tnmpObsIdentifier = patId + "ptnm" + pTnm.getTNM_ID();

    tnmpObs.setId(this.getHash("Observation", tnmpObsIdentifier));

    tnmpObs
        .getMeta()
        .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkostar-to-fhir:" + appVersion);

    tnmpObs
        .getMeta()
        .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getTnmP())));

    tnmpObs.setStatus(ObservationStatus.FINAL);

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
    Date tnmpDate = null;

    if (tnmpDateString != null) {
      SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN);
      try {
        tnmpDate = formatter.parse(tnmpDateString);
      } catch (ParseException e) {
        throw new RuntimeException(e);
      }
    }

    if (tnmpDate != null) {
      tnmpObs.setEffective(new DateTimeType(tnmpDate));
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

    if (tnmPraefixCode == null || tnmPraefixDisplay == null) {
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

  // TODO k-Regel checken ob das gebraucht wird, da laut Jasmin keine doppelten HistIds in einer
  // Diagnose
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
