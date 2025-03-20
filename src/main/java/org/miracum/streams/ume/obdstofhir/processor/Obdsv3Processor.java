package org.miracum.streams.ume.obdstofhir.processor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleUtil;
import de.basisdatensatz.obds.v3.*;
import de.basisdatensatz.obds.v3.OBDS.MengePatient;
import de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Patient;
import org.jetbrains.annotations.NotNull;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.miracum.streams.ume.obdstofhir.mapper.mii.ObdsToFhirBundleMapper;
import org.miracum.streams.ume.obdstofhir.model.Meldeanlass;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportListV3;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportV3;
import org.miracum.streams.ume.obdstofhir.serde.MeldungExportListV3Serde;
import org.miracum.streams.ume.obdstofhir.serde.MeldungExportV3Serde;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class Obdsv3Processor extends ObdsToFhirMapper {

  private static final FhirContext ctx = FhirContext.forR4();

  private final ObdsToFhirBundleMapper obdsToFhirBundleMapper;

  protected Obdsv3Processor(
      FhirProperties fhirProperties, ObdsToFhirBundleMapper obdsToFhirBundleMapper) {
    super(fhirProperties);
    this.obdsToFhirBundleMapper = obdsToFhirBundleMapper;
  }

  @Bean
  public Function<KTable<String, MeldungExportV3>, KStream<String, Bundle>>
      getMeldungExportObdsV3Processor() {

    return stringOnkoMeldungExpTable -> {
      // return (stringOnkoMeldungExpTable) ->
      var filtered =
          stringOnkoMeldungExpTable
              // only process adt v3.x.x
              .filter((key, value) -> value.getObds().getSchemaVersion().matches("^3\\..*"));

      return filtered
          .groupBy(
              (key, meldung) -> KeyValue.pair(getPatIdFromMeldung(meldung), meldung),
              Grouped.with(Serdes.String(), new MeldungExportV3Serde()))
          .aggregate(
              MeldungExportListV3::new,
              (key, value, aggregate) -> {
                aggregate.addElement(value);
                return retainLatestVersionOnly(aggregate);
              },
              (key, value, aggregate) -> {
                aggregate.removeElement(value);
                return retainLatestVersionOnly(aggregate);
              },
              Materialized.with(Serdes.String(), new MeldungExportListV3Serde()))
          .toStream()
          .mapValues(this::groupByTumorId)
          .flatMapValues(this.getMeldungExportListToBundleListMapper())
          .filter((key, value) -> value != null)
          .selectKey((key, value) -> patientBundleKeySelector(value));
    };
  }

  /**
   * Filters a list of MeldungExport objects to retain only the latest version of each unique
   * reporting ID. The latest version is determined by the highest versionsnummer.
   *
   * @param meldungExportList The list of MeldungExport objects to process.
   * @return A new MeldungExportList containing only the latest versions.
   */
  private MeldungExportListV3 retainLatestVersionOnly(MeldungExportListV3 meldungExportList) {
    return meldungExportList.stream()
        .collect(
            Collectors.groupingBy(
                Obdsv3Processor::getReportingIdFromObds,
                Collectors.collectingAndThen(
                    Collectors.maxBy(Comparator.comparingInt(MeldungExportV3::getVersionsnummer)),
                    optionalMeldung -> optionalMeldung.map(List::of).orElse(List.of()))))
        .values()
        .stream()
        .flatMap(List::stream)
        .collect(Collectors.toCollection(MeldungExportListV3::new));
  }

  /**
   * Groups MeldungExport objects by Tumor ID.
   *
   * @param meldungExportList The list of MeldungExport objects to group.
   * @return A list of MeldungExportList, each containing meldungen belonging to the same Tumor ID.
   */
  private List<MeldungExportListV3> groupByTumorId(MeldungExportListV3 meldungExportList) {
    return new ArrayList<>(
        meldungExportList.stream()
            .collect(
                Collectors.groupingBy(
                    Obdsv3Processor::getTumorIdFromMeldung,
                    Collectors.toCollection(MeldungExportListV3::new)))
            .values());
  }

  public static String getReportingIdFromObds(MeldungExportV3 meldung) {
    return meldung
        .getObds()
        .getMengePatient()
        .getPatient()
        .getFirst()
        .getMengeMeldung()
        .getMeldung()
        .getFirst()
        .getMeldungID();
  }

  public static String getTumorIdFromMeldung(MeldungExportV3 meldung) {
    return meldung
        .getObds()
        .getMengePatient()
        .getPatient()
        .getFirst()
        .getMengeMeldung()
        .getMeldung()
        .getFirst()
        .getTumorzuordnung()
        .getTumorID();
  }

  public static String getPatIdFromMeldung(MeldungExportV3 meldung) {
    return meldung.getObds().getMengePatient().getPatient().getFirst().getPatientID();
  }

  public ValueMapper<List<MeldungExportListV3>, List<Bundle>>
      getMeldungExportListToBundleListMapper() {
    return meldungGroupedByPatientIdAndTumorId -> {
      List<OBDS> tumorObds =
          meldungGroupedByPatientIdAndTumorId.stream()
              .map(this::processMeldungGroup)
              .collect(Collectors.toList());

      return obdsToFhirBundleMapper.map(tumorObds);
    };
  }

  private OBDS processMeldungGroup(MeldungExportListV3 meldungsOfPatientAndTumor) {
    final OBDS obds = getObdsWithPatientMengeAndMeldungInitialized();

    // Meldedatum
    var latestReportingByReportingDate =
        meldungsOfPatientAndTumor.stream()
            .max(Comparator.comparing(v -> v.getObds().getMeldedatum().getMillisecond()))
            .get()
            .getObds();
    obds.setMeldedatum(latestReportingByReportingDate.getMeldedatum());

    // Patstammdaten
    setLatestReportedPatientData(latestReportingByReportingDate, obds);

    // Diagnose, OP, Tod
    tryAddDiagnoseOpTodMeldung(meldungsOfPatientAndTumor, obds);

    // Systemtherapie
    getSystemtherapieMeldungen(meldungsOfPatientAndTumor).forEach(m -> addMeldung(m, obds));

    // Strahlentherapie
    getStrahlentherapieMeldungen(meldungsOfPatientAndTumor).forEach(m -> addMeldung(m, obds));

    // Verlauf
    getVerlaufMeldungen(meldungsOfPatientAndTumor).forEach(m -> addMeldung(m, obds));

    // Tumorkonferenz
    getTumorKonferenzmeldungen(meldungsOfPatientAndTumor).forEach(m -> addMeldung(m, obds));

    return obds;
  }

  protected static void tryAddDiagnoseOpTodMeldung(
      MeldungExportListV3 meldungExportList, OBDS obds) {
    meldungExportList.stream()
        .map(MeldungExportV3::getObds)
        .map(OBDS::getMengePatient)
        .map(OBDS.MengePatient::getPatient)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .map(OBDS.MengePatient.Patient::getMengeMeldung)
        .map(OBDS.MengePatient.Patient.MengeMeldung::getMeldung)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .forEach(
            meldung -> {
              if (meldung.getDiagnose() != null
                  || meldung.getOP() != null
                  || meldung.getTod() != null) {
                addMeldung(meldung, obds);
              }
            });
  }

  /**
   * Patient -- set latest known patient master data by reporting date
   *
   * @param latestReportingByReportingDate input data source
   * @param obds target data opbject
   * @return Patient last reported
   */
  protected static void setLatestReportedPatientData(
      OBDS latestReportingByReportingDate, OBDS obds) {
    var latestReportingStammdatenPatient =
        latestReportingByReportingDate.getMengePatient().getPatient().getFirst();
    obds.getMengePatient()
        .getPatient()
        .getFirst()
        .setPatientenStammdaten(latestReportingStammdatenPatient.getPatientenStammdaten());
    obds.getMengePatient()
        .getPatient()
        .getFirst()
        .setPatientID(latestReportingStammdatenPatient.getPatientID());
  }

  @NotNull
  protected static OBDS getObdsWithPatientMengeAndMeldungInitialized() {
    OBDS obds = new OBDS();

    // init obds
    obds.setMengePatient(new MengePatient());
    obds.getMengePatient().getPatient().add(new MengePatient.Patient());
    obds.getMengePatient()
        .getPatient()
        .getFirst()
        .setMengeMeldung(new MengePatient.Patient.MengeMeldung());
    return obds;
  }

  protected List<Meldung> getSystemtherapieMeldungen(MeldungExportListV3 meldungExportList) {
    return selectMultipleByMeldeanlass(
        meldungExportList,
        Meldeanlass.BEHANDLUNGSENDE,
        Meldeanlass.BEHANDLUNGSBEGINN,
        Meldung::getSYST,
        syst -> ((SYSTTyp) syst).getSYSTID(), // Identify unique SYST instances
        syst ->
            ((SYSTTyp) syst).getMeldeanlass() == null
                ? null
                : ((SYSTTyp) syst).getMeldeanlass().toString());
  }

  protected List<Meldung> getStrahlentherapieMeldungen(MeldungExportListV3 meldungExportList) {
    return selectMultipleByMeldeanlass(
        meldungExportList,
        Meldeanlass.BEHANDLUNGSENDE,
        Meldeanlass.BEHANDLUNGSBEGINN,
        Meldung::getST,
        st -> ((STTyp) st).getSTID(), // Identify unique ST instances
        st ->
            ((STTyp) st).getMeldeanlass() == null
                ? null
                : ((STTyp) st).getMeldeanlass().toString());
  }

  protected List<Meldung> getVerlaufMeldungen(MeldungExportListV3 meldungExportList) {
    return selectMultipleByMeldeanlass(
        meldungExportList,
        Meldeanlass.STATUSAENDERUNG,
        Meldeanlass.STATUSMELDUNG,
        Meldung::getVerlauf,
        verlauf -> ((VerlaufTyp) verlauf).getVerlaufID(), // Identify unique Verlauf instances
        verlauf ->
            ((VerlaufTyp) verlauf).getMeldeanlass() == null
                ? null
                : ((VerlaufTyp) verlauf).getMeldeanlass());
  }

  protected List<Meldung> getTumorKonferenzmeldungen(MeldungExportListV3 meldungExportList) {
    return selectMultipleByMeldeanlass(
        meldungExportList,
        Meldeanlass.BEHANDLUNGSENDE,
        Meldeanlass.BEHANDLUNGSBEGINN,
        Meldung::getTumorkonferenz,
        tumorkonferenz -> ((TumorkonferenzTyp) tumorkonferenz).getTumorkonferenzID(),
        tumorkonferenz ->
            ((TumorkonferenzTyp) tumorkonferenz).getMeldeanlass() == null
                ? null
                : ((TumorkonferenzTyp) tumorkonferenz).getMeldeanlass());
  }

  private static void addMeldung(Meldung meldung, OBDS obds) {
    if (meldung != null) {
      obds.getMengePatient().getPatient().getFirst().getMengeMeldung().getMeldung().add(meldung);
    }
  }

  private List<Meldung> selectMultipleByMeldeanlass(
      MeldungExportListV3 meldungList,
      Meldeanlass primary,
      Meldeanlass secondary,
      Function<Meldung, ?> fieldExtractor, // Extracts SYST, ST, or Verlauf
      Function<Object, String> idExtractor, // Extracts unique ID
      Function<Object, String> meldeanlassExtractor // Extracts Meldeanlass from the extracted type
      ) {
    return meldungList.stream()
        .map(this::extractMeldung)
        .filter(Objects::nonNull)
        .filter(meldung -> fieldExtractor.apply(meldung) != null) // Ensure the field exists
        .collect(
            Collectors.groupingBy(
                meldung -> {
                  Object field = fieldExtractor.apply(meldung);
                  return field != null ? idExtractor.apply(field) : null; // Extract unique ID
                },
                Collectors.toList()))
        .values()
        .stream()
        .map(
            meldungen ->
                meldungen.stream()
                    .sorted(
                        Comparator.comparing(
                            m -> {
                              String meldeanlass =
                                  meldeanlassExtractor.apply(fieldExtractor.apply(m));
                              if (Objects.equals(meldeanlass, primary.toString()))
                                return 0; // Highest priority
                              if (Objects.equals(meldeanlass, secondary.toString()))
                                return 1; // Fallback
                              return 2; // Otherwise ignore
                            }))
                    .findFirst() // Take only one per group (either primary or secondary)
                    .orElse(null) // If neither primary nor secondary exists, return null
            )
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private OBDS.MengePatient.Patient.MengeMeldung.Meldung extractMeldung(
      MeldungExportV3 meldungExport) {
    return Optional.ofNullable(meldungExport.getObds())
        .map(OBDS::getMengePatient)
        .map(OBDS.MengePatient::getPatient)
        .flatMap(patients -> patients.stream().findFirst())
        .map(OBDS.MengePatient.Patient::getMengeMeldung)
        .map(OBDS.MengePatient.Patient.MengeMeldung::getMeldung)
        .flatMap(meldungen -> meldungen.stream().findFirst())
        .orElse(null);
  }

  private static String patientBundleKeySelector(Bundle bundle) {
    var patients = BundleUtil.toListOfResourcesOfType(ctx, bundle, Patient.class);
    var conditions = BundleUtil.toListOfResourcesOfType(ctx, bundle, Condition.class);

    if (patients.size() != 1) {
      throw new RuntimeException(
          String.format(
              "A patient bundle contains %d patient resources instead of 1", patients.size()));
    }
    var patient = patients.getFirst();

    if (conditions.isEmpty()) {
      /*
      in rare cases we could encounter a data slice without a condition.
      then we create a bundle without a condition ID.
      NOTE: if we should encounter more than one tumor for this patient, we could override a fhir bundle due same kafka key, since we miss condition discriminante.
      */
      return String.format("%s/%s", patient.getResourceType(), patient.getId());
    }

    var condition = conditions.getFirst();
    return String.format(
        "%s/%s - %s/%s",
        patient.getResourceType(), patient.getId(), condition.getResourceType(), condition.getId());
  }
}
