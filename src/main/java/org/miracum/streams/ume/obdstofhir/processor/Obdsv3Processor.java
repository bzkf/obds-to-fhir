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
        .get(0)
        .getMengeMeldung()
        .getMeldung()
        .get(0)
        .getMeldungID();
  }

  public static String getTumorIdFromMeldung(MeldungExportV3 meldung) {
    return meldung
        .getObds()
        .getMengePatient()
        .getPatient()
        .get(0)
        .getMengeMeldung()
        .getMeldung()
        .get(0)
        .getTumorzuordnung()
        .getTumorID();
  }

  public static String getPatIdFromMeldung(MeldungExportV3 meldung) {
    return meldung.getObds().getMengePatient().getPatient().get(0).getPatientID();
  }

  public ValueMapper<List<MeldungExportListV3>, List<Bundle>>
      getMeldungExportListToBundleListMapper() {
    return meldungExportListList -> {
      List<OBDS> tumorObds = new ArrayList<>();

      for (MeldungExportListV3 meldungExportList : meldungExportListList) {
        OBDS obds = new OBDS();

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
            .findFirst()
            .ifPresent(
                meldung -> {
                  obds.getMengePatient()
                      .getPatient()
                      .get(0)
                      .getMengeMeldung()
                      .getMeldung()
                      .get(0)
                      .setDiagnose(meldung.getDiagnose());
                  obds.getMengePatient()
                      .getPatient()
                      .get(0)
                      .getMengeMeldung()
                      .getMeldung()
                      .get(0)
                      .setOP(meldung.getOP());
                  obds.getMengePatient()
                      .getPatient()
                      .get(0)
                      .getMengeMeldung()
                      .getMeldung()
                      .get(0)
                      .setTod(meldung.getTod());
                });

        // Systemtherapie
        obds.getMengePatient()
            .getPatient()
            .get(0)
            .getMengeMeldung()
            .getMeldung()
            .get(0)
            .setSYST(
                selectByMeldeanlass(
                    meldungExportList,
                    Meldeanlass.BEHANDLUNGSENDE,
                    Meldeanlass.BEHANDLUNGSBEGINN,
                    this::extractSYST,
                    (SYSTTyp s) -> s.getMeldeanlass().toString()));

        // Strahlentherapie
        obds.getMengePatient()
            .getPatient()
            .get(0)
            .getMengeMeldung()
            .getMeldung()
            .get(0)
            .setST(
                selectByMeldeanlass(
                    meldungExportList,
                    Meldeanlass.BEHANDLUNGSENDE,
                    Meldeanlass.BEHANDLUNGSBEGINN,
                    this::extractST,
                    (STTyp s) -> s.getMeldeanlass().toString()));

        // Verlauf
        obds.getMengePatient()
            .getPatient()
            .get(0)
            .getMengeMeldung()
            .getMeldung()
            .get(0)
            .setVerlauf(
                selectByMeldeanlass(
                    meldungExportList,
                    Meldeanlass.STATUSAENDERUNG,
                    Meldeanlass.STATUSMELDUNG,
                    this::extractVerlauf,
                    VerlaufTyp::getMeldeanlass));

        tumorObds.add(obds);
      }

      return obdsToFhirBundleMapper.map(tumorObds);
    };
  }

  private <T> T selectByMeldeanlass(
      List<MeldungExportV3> meldungList,
      Meldeanlass primary,
      Meldeanlass secondary,
      Function<MeldungExportV3, T> extractor,
      Function<T, String> meldeanlassExtractor) {
    return meldungList.stream()
        .map(extractor)
        .filter(Objects::nonNull)
        .filter(meldung -> meldeanlassExtractor.apply(meldung).equals(primary.toString()))
        .findFirst()
        .or(
            () ->
                meldungList.stream()
                    .map(extractor)
                    .filter(Objects::nonNull)
                    .filter(
                        meldung -> meldeanlassExtractor.apply(meldung).equals(secondary.toString()))
                    .findFirst())
        .orElse(null);
  }

  private SYSTTyp extractSYST(MeldungExportV3 meldungExport) {
    return extractField(meldungExport, OBDS.MengePatient.Patient.MengeMeldung.Meldung::getSYST);
  }

  private STTyp extractST(MeldungExportV3 meldungExport) {
    return extractField(meldungExport, OBDS.MengePatient.Patient.MengeMeldung.Meldung::getST);
  }

  private VerlaufTyp extractVerlauf(MeldungExportV3 meldungExport) {
    return extractField(meldungExport, OBDS.MengePatient.Patient.MengeMeldung.Meldung::getVerlauf);
  }

  private <T> T extractField(
      MeldungExportV3 meldungExport,
      Function<OBDS.MengePatient.Patient.MengeMeldung.Meldung, T> fieldExtractor) {
    return Optional.ofNullable(meldungExport.getObds())
        .map(OBDS::getMengePatient)
        .map(OBDS.MengePatient::getPatient)
        .flatMap(patients -> patients.stream().findFirst())
        .map(OBDS.MengePatient.Patient::getMengeMeldung)
        .map(OBDS.MengePatient.Patient.MengeMeldung::getMeldung)
        .flatMap(meldungen -> meldungen.stream().findFirst())
        .map(fieldExtractor)
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
