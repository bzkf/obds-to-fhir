package org.miracum.streams.ume.obdstofhir.processor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleUtil;
import de.basisdatensatz.obds.v3.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Patient;
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
                Obdsv3Processor::getReportingIdFromAdt,
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

  public static String getReportingIdFromAdt(MeldungExportV3 meldung) {
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
    return meldungExportListList -> {
      List<OBDS> tumorObds = new ArrayList<>();

      for (MeldungExportListV3 meldungExportList : meldungExportListList) {
        OBDS obds = new OBDS();

        // init obds
        obds.setMengePatient(new OBDS.MengePatient());
        obds.getMengePatient().getPatient().add(new OBDS.MengePatient.Patient());
        obds.getMengePatient()
            .getPatient()
            .getFirst()
            .setMengeMeldung(new OBDS.MengePatient.Patient.MengeMeldung());

        // Meldedatum
        var latestReportingByReportingDate =
            meldungExportList.stream()
                .max(Comparator.comparing(v -> v.getObds().getMeldedatum().getMillisecond()))
                .get()
                .getObds();
        obds.setMeldedatum(latestReportingByReportingDate.getMeldedatum());

        // Patient -- set latest known patient master data by reporting date
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

        // Diagnose, OP, Tod
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
            .findFirst() // Assumption always only one Meldung in MengeMeldung
            .ifPresent(
                meldung -> {
                  if (meldung.getDiagnose() != null
                      || meldung.getOP() != null
                      || meldung.getTod() != null) {
                    obds.getMengePatient()
                        .getPatient()
                        .get(0)
                        .getMengeMeldung()
                        .getMeldung()
                        .add(meldung);
                  }
                });

        // Systemtherapie
        var systMeldung =
            selectByMeldeanlass(
                meldungExportList,
                Meldeanlass.BEHANDLUNGSENDE,
                Meldeanlass.BEHANDLUNGSBEGINN,
                OBDS.MengePatient.Patient.MengeMeldung.Meldung::getSYST,
                syst ->
                    ((SYSTTyp) syst).getMeldeanlass() == null
                        ? null
                        : ((SYSTTyp) syst).getMeldeanlass().toString());
        if (systMeldung != null) {
          obds.getMengePatient()
              .getPatient()
              .getFirst()
              .getMengeMeldung()
              .getMeldung()
              .add(systMeldung);
        }

        // Strahlentherapie
        var stMeldung =
            selectByMeldeanlass(
                meldungExportList,
                Meldeanlass.BEHANDLUNGSENDE,
                Meldeanlass.BEHANDLUNGSBEGINN,
                OBDS.MengePatient.Patient.MengeMeldung.Meldung::getST,
                st ->
                    ((STTyp) st).getMeldeanlass() == null
                        ? null
                        : ((STTyp) st).getMeldeanlass().toString());
        if (stMeldung != null) {
          obds.getMengePatient()
              .getPatient()
              .getFirst()
              .getMengeMeldung()
              .getMeldung()
              .add(stMeldung);
        }

        // Verlauf
        var verlaufMeldung =
            selectByMeldeanlass(
                meldungExportList,
                Meldeanlass.STATUSAENDERUNG,
                Meldeanlass.STATUSMELDUNG,
                OBDS.MengePatient.Patient.MengeMeldung.Meldung::getVerlauf,
                verlauf ->
                    ((VerlaufTyp) verlauf).getMeldeanlass() == null
                        ? null
                        : ((VerlaufTyp) verlauf).getMeldeanlass());
        if (verlaufMeldung != null) {
          obds.getMengePatient()
              .getPatient()
              .getFirst()
              .getMengeMeldung()
              .getMeldung()
              .add(verlaufMeldung);
        }

        tumorObds.add(obds);
      }

      return obdsToFhirBundleMapper.map(tumorObds);
    };
  }

  private OBDS.MengePatient.Patient.MengeMeldung.Meldung selectByMeldeanlass(
      List<MeldungExportV3> meldungList,
      Meldeanlass primary,
      Meldeanlass secondary,
      Function<OBDS.MengePatient.Patient.MengeMeldung.Meldung, ?>
          fieldExtractor, // Extracts SYST, ST, or Verlauf
      Function<Object, String> meldeanlassExtractor // Extracts Meldeanlass from the extracted type
      ) {
    return meldungList.stream()
        .map(this::extractMeldung)
        .filter(Objects::nonNull)
        .filter(
            meldung -> {
              Object field = fieldExtractor.apply(meldung);
              return field != null
                  && Objects.equals(meldeanlassExtractor.apply(field), primary.toString());
            })
        .findFirst()
        .or(
            () ->
                meldungList.stream()
                    .map(this::extractMeldung)
                    .filter(Objects::nonNull)
                    .filter(
                        meldung -> {
                          Object field = fieldExtractor.apply(meldung);
                          return field != null
                              && Objects.equals(
                                  meldeanlassExtractor.apply(field), secondary.toString());
                        })
                    .findFirst())
        .orElse(null);
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
    if (conditions.size() != 1) {
      throw new RuntimeException(
          String.format(
              "A patient bundle contains %d condition resources instead of 1", conditions.size()));
    }
    var patient = patients.getFirst();
    var condition = conditions.getFirst();
    return String.format(
        "%s/%s - %s/%s",
        patient.getResourceType(), patient.getId(), condition.getResourceType(), condition.getId());
  }
}
