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
            .filter((key, value) -> value != null)
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

    var patId = meldungExport.getReferenz_nummer();
    var pid = convertId(patId);

    var meldung =
        meldungExport
            .getXml_daten()
            .getMenge_Patient()
            .getPatient()
            .getMenge_Meldung()
            .getMeldung();

    // reporting reason
    var meldeanlass = meldung.getMeldeanlass();

    var refNum = meldungExport.getReferenz_nummer();

    // histologie from operation
    var mengeOp = meldung.getMenge_OP();

    // histologie list from diagnosis
    var diagnosis = meldung.getDiagnose();

    // meldeanlass bleibt in LKR Meldung immer gleich
    // kein Überschreiben von FHIR-Resourcen über verschiedene Meldeanlässe hinweg
    if (Objects.equals(meldeanlass, "diagnose")) {
      // aus Diagnose: histologie, grading and c-tnm
      bundle = createHistologieAndGradingObservation(bundle, mengeOp, diagnosis, pid, refNum);
      bundle = createCTnmObservation(bundle, mengeOp, diagnosis, pid, refNum);
    } else if (Objects.equals(meldeanlass, "behandlungsende")) {
      // aus Operation: histologie, grading und p-tnm
      bundle = createHistologieAndGradingObservation(bundle, mengeOp, diagnosis, pid, refNum);
      bundle = createPTnmObservation(bundle, mengeOp, diagnosis, pid, refNum);
    } else if (Objects.equals(meldeanlass, "statusaenderung")) {
      // aus Verlauf: histologie, grading und p-tnm
      bundle = createHistologieAndGradingObservation(bundle, mengeOp, diagnosis, pid, refNum);
      bundle = createPTnmObservation(bundle, mengeOp, diagnosis, pid, refNum);
    } else {
      // Meldeanlaesse: behandlungsbeginn, tod
      return null;
    }

    bundle.setType(Bundle.BundleType.TRANSACTION);
    return bundle;
  }

  public Bundle createHistologieAndGradingObservation(
      Bundle bundle,
      ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung.Menge_OP mengeOp,
      ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung.Diagnose diagnosis,
      String pid,
      String refNum) {

    // Create a Grading Observation as in
    // https://simplifier.net/oncology/histologie
    var gradingObs = new Observation();

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
      var gradingObsIdentifier = refNum + histId + grading;

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

      // TODO reicht das und bleibt Histologie_ID wirklich immer identisch
      // Generate an identifier based on MeldungExport Referenz_nummer (Pat. Id) and Histologie_ID
      // from ADT XML
      var observationIdentifier = refNum + histId;

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
    }
    return bundle;
  }

  public Bundle createCTnmObservation(
      Bundle bundle,
      ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung.Menge_OP mengeOp,
      ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung.Diagnose diagnosis,
      String pid,
      String refNum) {
    // TNM Observation
    // Create a TNM-c Observation as in
    // https://simplifier.net/oncology/tnmc
    var tnmcObs = new Observation();

    // TODO checken
    var tnmcObsIdentifier = refNum + diagnosis.getCTNM().getTNM_ID();

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
    var tnmcDateString = diagnosis.getCTNM().getTNM_Datum();
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

    if (diagnosis.getMenge_Weitere_Klassifikation().getWeitere_Klassifikation().equals("UICC")) {
      var valueCodeableCon =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getUicc())
                  .setCode(
                      diagnosis
                          .getMenge_Weitere_Klassifikation()
                          .getWeitere_Klassifikation()
                          .getStadium())
                  .setVersion(diagnosis.getCTNM().getTNM_Version()));

      tnmcObs.setValue(valueCodeableCon);
    }

    var backBoneComponentListC = new ArrayList<Observation.ObservationComponentComponent>();

    // TODO add NULL checks
    var cTnmCpuPraefixT = diagnosis.getCTNM().getTNM_c_p_u_Praefix_T();
    var cTnmCpuPraefixN = diagnosis.getCTNM().getTNM_c_p_u_Praefix_N();
    var cTnmCpuPraefixM = diagnosis.getCTNM().getTNM_c_p_u_Praefix_M();
    var cTnmT = diagnosis.getCTNM().getTNM_T();
    var cTnmN = diagnosis.getCTNM().getTNM_N();
    var cTnmM = diagnosis.getCTNM().getTNM_M();
    var cTnmYSymbol = diagnosis.getCTNM().getTNM_y_Symbol();
    var cTnmRSymbol = diagnosis.getCTNM().getTNM_r_Symbol();
    var cTnmMSymbol = diagnosis.getCTNM().getTNM_m_Symbol();

    // cTNM-T
    backBoneComponentListC.add(
        createTNMComponentElement(
            cTnmCpuPraefixT,
            tnmPraefixLookup.lookupTnmCpuPraefixDisplay(cTnmCpuPraefixT),
            "21905-5",
            "Primary tumor.clinical Cancer",
            fhirProperties.getSystems().getTnmTCs(),
            cTnmT,
            cTnmT));

    // cTNM-N
    backBoneComponentListC.add(
        createTNMComponentElement(
            cTnmCpuPraefixN,
            tnmPraefixLookup.lookupTnmCpuPraefixDisplay(cTnmCpuPraefixN),
            "21906-3",
            "Regional lymph nodes.clinical",
            fhirProperties.getSystems().getTnmNCs(),
            cTnmN,
            cTnmN));

    // cTNM-M
    backBoneComponentListC.add(
        createTNMComponentElement(
            cTnmCpuPraefixM,
            tnmPraefixLookup.lookupTnmCpuPraefixDisplay(cTnmCpuPraefixM),
            "21907-1",
            "Distant metastases.clinical [Class] Cancer",
            fhirProperties.getSystems().getTnmMCs(),
            cTnmM,
            cTnmM));

    // TNM-y Symbol
    backBoneComponentListC.add(
        createTNMComponentElement(
            null,
            null,
            "59479-6",
            "Collaborative staging post treatment extension Cancer",
            fhirProperties.getSystems().getTnmYSymbolCs(),
            cTnmYSymbol,
            cTnmYSymbol));

    // TNM-r Symbol
    backBoneComponentListC.add(
        createTNMComponentElement(
            null,
            null,
            "21983-2",
            "Recurrence type first episode Cancer",
            fhirProperties.getSystems().getTnmRSymbolCs(),
            cTnmRSymbol,
            cTnmRSymbol));

    // TNM-m Symbol
    backBoneComponentListC.add(
        createTNMComponentElement(
            null,
            null,
            "42030-7",
            "Multiple tumors reported as single primary Cancer",
            fhirProperties.getSystems().getTnmMSymbolCs(),
            cTnmMSymbol,
            cTnmMSymbol));

    tnmcObs.setComponent(backBoneComponentListC);

    bundle = addResourceAsEntryInBundle(bundle, tnmcObs);

    return bundle;
  }

  public Bundle createPTnmObservation(
      Bundle bundle,
      ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung.Menge_OP mengeOp,
      ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung.Diagnose diagnosis,
      String pid,
      String refNum) {
    // Create a TNM-p Observation as in
    // https://simplifier.net/oncology/tnmp
    var tnmpObs = new Observation();
    // TODO checken
    var tnmpObsIdentifier = refNum + mengeOp.getOP().getTNM().getTNM_ID();

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
    var tnmpDateString = mengeOp.getOP().getTNM().getTNM_Datum();
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

    // TODO UICC gibt es scheinbar nur in Diagnose :( mit Noemi beim adt2fhir Logik Termin klären
    /*if (diagnosis.getMenge_Weitere_Klassifikation().getWeitere_Klassifikation().equals("UICC")) {
      var valueCodeableCon =
              new CodeableConcept(
                      new Coding()
                              .setSystem(fhirProperties.getSystems().getUicc())
                              .setCode(
                                      diagnosis
                                              .getMenge_Weitere_Klassifikation()
                                              .getWeitere_Klassifikation()
                                              .getStadium())
                              .setVersion(diagnosis.getCTNM().getTNM_Version()));

      tnmcObs.setValue(valueCodeableCon);
    } */

    var backBoneComponentListP = new ArrayList<Observation.ObservationComponentComponent>();

    // TODO add NULL checks
    var pTnmCpuPraefixT = mengeOp.getOP().getTNM().getTNM_c_p_u_Praefix_T();
    var pTnmCpuPraefixN = mengeOp.getOP().getTNM().getTNM_c_p_u_Praefix_N();
    var pTnmCpuPraefixM = mengeOp.getOP().getTNM().getTNM_c_p_u_Praefix_M();
    var pTnmT = mengeOp.getOP().getTNM().getTNM_T();
    var pTnmN = mengeOp.getOP().getTNM().getTNM_N();
    var pTnmM = mengeOp.getOP().getTNM().getTNM_M();
    var pTnmYSymbol = mengeOp.getOP().getTNM().getTNM_y_Symbol();
    var pTnmRSymbol = mengeOp.getOP().getTNM().getTNM_r_Symbol();
    var pTnmMSymbol = mengeOp.getOP().getTNM().getTNM_m_Symbol();

    // pTNM-T
    backBoneComponentListP.add(
        createTNMComponentElement(
            pTnmCpuPraefixT,
            tnmPraefixLookup.lookupTnmCpuPraefixDisplay(pTnmCpuPraefixT),
            "21899-0",
            "Primary tumor.pathology Cancer",
            fhirProperties.getSystems().getTnmTCs(),
            pTnmT,
            pTnmT));

    // pTNM-N
    backBoneComponentListP.add(
        createTNMComponentElement(
            pTnmCpuPraefixN,
            tnmPraefixLookup.lookupTnmCpuPraefixDisplay(pTnmCpuPraefixN),
            "21900-6",
            "Regional lymph nodes.pathology",
            fhirProperties.getSystems().getTnmNCs(),
            pTnmN,
            pTnmN));

    // pTNM-M
    backBoneComponentListP.add(
        createTNMComponentElement(
            pTnmCpuPraefixM,
            tnmPraefixLookup.lookupTnmCpuPraefixDisplay(pTnmCpuPraefixM),
            "21901-4",
            "Distant metastases.pathology [Class] Cancer",
            fhirProperties.getSystems().getTnmMCs(),
            pTnmM,
            pTnmM));

    // pTNM-y Symbol
    backBoneComponentListP.add(
        createTNMComponentElement(
            null,
            null,
            "59479-6",
            "Collaborative staging post treatment extension Cancer",
            fhirProperties.getSystems().getTnmYSymbolCs(),
            pTnmYSymbol,
            pTnmYSymbol));

    // TNM-r Symbol
    backBoneComponentListP.add(
        createTNMComponentElement(
            null,
            null,
            "21983-2",
            "Recurrence type first episode Cancer",
            fhirProperties.getSystems().getTnmRSymbolCs(),
            pTnmRSymbol,
            pTnmRSymbol));

    // TNM-m Symbol
    backBoneComponentListP.add(
        createTNMComponentElement(
            null,
            null,
            "42030-7",
            "Multiple tumors reported as single primary Cancer",
            fhirProperties.getSystems().getTnmMSymbolCs(),
            pTnmMSymbol,
            pTnmMSymbol));

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
