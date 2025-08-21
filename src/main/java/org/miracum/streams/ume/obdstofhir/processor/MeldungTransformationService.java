package org.miracum.streams.ume.obdstofhir.processor;

import de.basisdatensatz.obds.v3.*;
import dev.pcvolkmer.onko.obds2to3.ObdsMapper;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.miracum.streams.ume.obdstofhir.mapper.mii.ObdsToFhirBundleMapper;
import org.miracum.streams.ume.obdstofhir.model.Meldeanlass;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportListV3;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportV3;
import org.miracum.streams.ume.obdstofhir.model.ObdsOrAdt;
import org.springframework.stereotype.Component;

@Component
public class MeldungTransformationService {

  private final ObdsMapper obdsMapper;
  private final ObdsToFhirBundleMapper obdsToFhirBundleMapper;

  public MeldungTransformationService(
      ObdsMapper obdsMapper, ObdsToFhirBundleMapper obdsToFhirBundleMapper) {
    this.obdsMapper = obdsMapper;
    this.obdsToFhirBundleMapper = obdsToFhirBundleMapper;
  }

  public MeldungExportV3 mapObdsOrAdt(MeldungExportV3 meldung) {
    var obdsOrAdt = meldung.getObdsOrAdt();
    if (obdsOrAdt.hasADT() && !obdsOrAdt.hasOBDS()) {
      var obds = obdsMapper.map(obdsOrAdt.getAdt());
      meldung.setObdsOrAdt(ObdsOrAdt.from(obds));
    }
    return meldung;
  }

  public MeldungExportListV3 aggregate(MeldungExportV3 meldung, MeldungExportListV3 aggregate) {
    aggregate.addElement(meldung);
    return retainLatestVersionOnly(aggregate);
  }

  public MeldungExportListV3 remove(MeldungExportV3 meldung, MeldungExportListV3 aggregate) {
    aggregate.removeElement(meldung);
    return retainLatestVersionOnly(aggregate);
  }

  public List<Bundle> toBundles(MeldungExportListV3 meldungList) {
    var grouped = groupByTumorId(meldungList);
    return getMeldungExportListToBundleListMapper().apply(grouped);
  }

  private ValueMapper<List<MeldungExportListV3>, List<Bundle>>
      getMeldungExportListToBundleListMapper() {
    return meldungGroupedByPatientIdAndTumorId -> {
      List<OBDS> tumorObds =
          meldungGroupedByPatientIdAndTumorId.stream()
              .map(this::processMeldungGroup)
              .collect(Collectors.toList());
      return obdsToFhirBundleMapper.map(tumorObds);
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
                MeldungTransformationService::getReportingIdFromObds,
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
                    MeldungTransformationService::getTumorIdFromMeldung,
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

  public List<Bundle> processDirMeldungen(MeldungExportListV3 meldungen) {
    // map ADT to OBDS if needed
    var mapped = meldungen.stream().map(this::mapObdsOrAdt).toList();

    // group by patientId
    var groupedByPatient =
        mapped.stream().collect(Collectors.groupingBy(this::getPatIdFromMeldung));

    // aggregate per patient
    var aggregated =
        groupedByPatient.values().stream()
            .map(
                list -> {
                  var agg = new MeldungExportListV3();
                  list.forEach(agg::addElement);
                  return retainLatestVersionOnly(agg);
                })
            .toList();

    return aggregated.stream()
        .flatMap(meldungList -> this.toBundles(meldungList).stream())
        .filter(Objects::nonNull)
        .toList();
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
    obds.setMengePatient(new OBDS.MengePatient());
    obds.getMengePatient().getPatient().add(new OBDS.MengePatient.Patient());
    obds.getMengePatient()
        .getPatient()
        .getFirst()
        .setMengeMeldung(new OBDS.MengePatient.Patient.MengeMeldung());
    return obds;
  }

  protected List<OBDS.MengePatient.Patient.MengeMeldung.Meldung> getSystemtherapieMeldungen(
      MeldungExportListV3 meldungExportList) {
    return selectMultipleByMeldeanlass(
        meldungExportList,
        Meldeanlass.BEHANDLUNGSENDE,
        Meldeanlass.BEHANDLUNGSBEGINN,
        OBDS.MengePatient.Patient.MengeMeldung.Meldung::getSYST,
        syst -> ((SYSTTyp) syst).getSYSTID(), // Identify unique SYST instances
        syst ->
            ((SYSTTyp) syst).getMeldeanlass() == null
                ? null
                : ((SYSTTyp) syst).getMeldeanlass().toString());
  }

  protected List<OBDS.MengePatient.Patient.MengeMeldung.Meldung> getStrahlentherapieMeldungen(
      MeldungExportListV3 meldungExportList) {
    return selectMultipleByMeldeanlass(
        meldungExportList,
        Meldeanlass.BEHANDLUNGSENDE,
        Meldeanlass.BEHANDLUNGSBEGINN,
        OBDS.MengePatient.Patient.MengeMeldung.Meldung::getST,
        st -> ((STTyp) st).getSTID(), // Identify unique ST instances
        st ->
            ((STTyp) st).getMeldeanlass() == null
                ? null
                : ((STTyp) st).getMeldeanlass().toString());
  }

  protected List<OBDS.MengePatient.Patient.MengeMeldung.Meldung> getVerlaufMeldungen(
      MeldungExportListV3 meldungExportList) {
    return selectMultipleByMeldeanlass(
        meldungExportList,
        Meldeanlass.STATUSAENDERUNG,
        Meldeanlass.STATUSMELDUNG,
        OBDS.MengePatient.Patient.MengeMeldung.Meldung::getVerlauf,
        verlauf -> ((VerlaufTyp) verlauf).getVerlaufID(), // Identify unique Verlauf instances
        verlauf ->
            ((VerlaufTyp) verlauf).getMeldeanlass() == null
                ? null
                : ((VerlaufTyp) verlauf).getMeldeanlass());
  }

  protected List<OBDS.MengePatient.Patient.MengeMeldung.Meldung> getTumorKonferenzmeldungen(
      MeldungExportListV3 meldungExportList) {
    return selectMultipleByMeldeanlass(
        meldungExportList,
        Meldeanlass.BEHANDLUNGSENDE,
        Meldeanlass.BEHANDLUNGSBEGINN,
        OBDS.MengePatient.Patient.MengeMeldung.Meldung::getTumorkonferenz,
        tumorkonferenz -> ((TumorkonferenzTyp) tumorkonferenz).getTumorkonferenzID(),
        tumorkonferenz ->
            ((TumorkonferenzTyp) tumorkonferenz).getMeldeanlass() == null
                ? null
                : ((TumorkonferenzTyp) tumorkonferenz).getMeldeanlass().toString());
  }

  private static void addMeldung(
      OBDS.MengePatient.Patient.MengeMeldung.Meldung meldung, OBDS obds) {
    if (meldung != null) {
      obds.getMengePatient().getPatient().getFirst().getMengeMeldung().getMeldung().add(meldung);
    }
  }

  private List<OBDS.MengePatient.Patient.MengeMeldung.Meldung> selectMultipleByMeldeanlass(
      MeldungExportListV3 meldungList,
      Meldeanlass primary,
      Meldeanlass secondary,
      Function<OBDS.MengePatient.Patient.MengeMeldung.Meldung, ?>
          fieldExtractor, // Extracts SYST, ST, or Verlauf
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
                              if (Objects.equals(meldeanlass, primary.name()))
                                return 0; // Highest priority
                              if (Objects.equals(meldeanlass, secondary.name()))
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

  public String getPatIdFromMeldung(MeldungExportV3 meldung) {
    return meldung.getObds().getMengePatient().getPatient().getFirst().getPatientID();
  }
}
