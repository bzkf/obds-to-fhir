package io.github.bzkf.obdstofhir.processor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleUtil;
import de.basisdatensatz.obds.v3.*;
import de.basisdatensatz.obds.v3.OBDS.MengePatient;
import de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung;
import io.github.bzkf.obds2toobds3.ObdsMapper;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.config.WriteGroupedObdsToKafkaConfig;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.bzkf.obdstofhir.mapper.mii.BundleMapperResult;
import io.github.bzkf.obdstofhir.mapper.mii.ObdsToFhirBundleMapper;
import io.github.bzkf.obdstofhir.model.MeldungExportListV3;
import io.github.bzkf.obdstofhir.model.MeldungExportV3;
import io.github.bzkf.obdstofhir.model.ObdsOrAdt;
import io.github.bzkf.obdstofhir.serde.Obdsv3Deserializer;
import io.github.bzkf.obdstofhir.serde.Obdsv3Serializer;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.datatype.XMLGregorianCalendar;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.miracum.kafka.serializers.KafkaFhirSerde;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(
    value = "obds.process-from-directory.enabled",
    havingValue = "false",
    matchIfMissing = true)
@Configuration
public class Obdsv3Processor extends ObdsToFhirMapper {

  private static final FhirContext ctx = FhirContext.forR4();

  /**
   * Oldest first, by Meldedatum. That's only precise to the day, so the export row ID breaks ties:
   * it's an incrementing number, and comparing the length first keeps "9" before "10".
   */
  private static final Comparator<MeldungExportV3> REPORTING_ORDER =
      Comparator.comparing(
              (MeldungExportV3 meldung) -> meldung.getObds().getMeldedatum(),
              Comparator.nullsFirst(
                  Comparator.comparing(XMLGregorianCalendar::toGregorianCalendar)))
          .thenComparing(
              MeldungExportV3::getId,
              Comparator.nullsFirst(
                  Comparator.<String>comparingInt(String::length)
                      .thenComparing(Comparator.naturalOrder())));

  private final ObdsToFhirBundleMapper obdsToFhirBundleMapper;

  private final ObdsMapper obdsMapper;

  private final WriteGroupedObdsToKafkaConfig writeGroupedObdsToKafkaConfig;

  @Value("${fhir.mappings.create-provenance-resources.topic:fhir.obds.provenance}")
  private String provenanceTopic;

  protected Obdsv3Processor(
      FhirProperties fhirProperties,
      ObdsToFhirBundleMapper obdsToFhirBundleMapper,
      ObdsMapper obdsMapper,
      WriteGroupedObdsToKafkaConfig writeGroupedObdsToKafkaConfig) {
    super(fhirProperties);
    this.obdsToFhirBundleMapper = obdsToFhirBundleMapper;
    this.obdsMapper = obdsMapper;
    this.writeGroupedObdsToKafkaConfig = writeGroupedObdsToKafkaConfig;
  }

  @Bean
  public Function<KTable<String, MeldungExportV3>, KStream<String, Bundle>>
      getMeldungExportObdsV3Processor() {

    return stringOnkoMeldungExpTable -> {
      var mapped =
          stringOnkoMeldungExpTable.mapValues(
              meldung -> {
                var obdsOrAdt = meldung.getObdsOrAdt();
                if (obdsOrAdt.hasADT() && !obdsOrAdt.hasOBDS()) {
                  var obds = obdsMapper.map(obdsOrAdt.getAdt());
                  meldung.setObdsOrAdt(ObdsOrAdt.from(obds));
                }
                return meldung;
              });

      var stream =
          mapped
              .groupBy(
                  (key, meldung) -> KeyValue.pair(getPatIdFromMeldung(meldung), meldung),
                  Grouped.with(Serdes.String(), new JacksonJsonSerde<>(MeldungExportV3.class)))
              .aggregate(
                  MeldungExportListV3::new,
                  (key, meldung, aggregate) -> {
                    aggregate.addElement(meldung);
                    return retainLatestVersionOnly(aggregate);
                  },
                  (key, meldung, aggregate) -> {
                    aggregate.removeElement(meldung);
                    return retainLatestVersionOnly(aggregate);
                  },
                  Materialized.with(
                      Serdes.String(), new JacksonJsonSerde<>(MeldungExportListV3.class)))
              .toStream()
              .mapValues(this::groupByTumorId);

      if (writeGroupedObdsToKafkaConfig.enabled()) {
        stream
            .flatMapValues(this.getMeldungExportListToObdsListMapper())
            .selectKey(
                (key, obds) ->
                    String.format(
                        "%s-%s",
                        obds.getMengePatient().getPatient().getFirst().getPatientID(),
                        obds.getMengePatient()
                            .getPatient()
                            .getFirst()
                            .getMengeMeldung()
                            .getMeldung()
                            .getFirst()
                            .getTumorzuordnung()
                            .getTumorID()))
            .to(
                writeGroupedObdsToKafkaConfig.topic(),
                Produced.with(
                    Serdes.String(),
                    Serdes.serdeFrom(new Obdsv3Serializer(), new Obdsv3Deserializer())));
      }

      var resultStream = stream.flatMapValues(this.getMeldungExportListToBundleListMapper());

      if (StringUtils.hasText(provenanceTopic)) {
        resultStream
            .<IBaseResource>flatMapValues(result -> result.provenances())
            .to(provenanceTopic, Produced.with(Serdes.String(), new KafkaFhirSerde()));
      }

      return resultStream
          .mapValues(BundleMapperResult::bundle)
          .filter((key, bundle) -> bundle != null)
          .selectKey((key, bundle) -> patientBundleKeySelector(bundle));
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
        // generally only for debugging, can be removed later
        .map(
            meldung -> {
              if (meldung.getObds().getMengePatient().getPatient().size() > 1) {
                throw new IllegalStateException(
                    String.format(
                        "Meldung %s contains more than one patient, actually %d",
                        getReportingIdFromObds(meldung),
                        meldung.getObds().getMengePatient().getPatient().size()));
              }
              return meldung;
            })
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

  public ValueMapper<List<MeldungExportListV3>, List<BundleMapperResult>>
      getMeldungExportListToBundleListMapper() {
    return meldungGroupedByPatientIdAndTumorId -> {
      List<OBDS> tumorObds =
          meldungGroupedByPatientIdAndTumorId.stream().map(this::processMeldungGroup).toList();

      return obdsToFhirBundleMapper.map(tumorObds);
    };
  }

  private ValueMapper<List<MeldungExportListV3>, List<OBDS>>
      getMeldungExportListToObdsListMapper() {
    return meldungGroupedByPatientIdAndTumorId ->
        meldungGroupedByPatientIdAndTumorId.stream().map(this::processMeldungGroup).toList();
  }

  private OBDS processMeldungGroup(MeldungExportListV3 meldungsOfPatientAndTumor) {
    final OBDS obds = getObdsWithPatientMengeAndMeldungInitialized();

    var meldungenOldestFirst = meldungsOfPatientAndTumor.stream().sorted(REPORTING_ORDER).toList();
    var latestReportingByReportingDate = meldungenOldestFirst.getLast().getObds();

    // Meldedatum
    obds.setMeldedatum(latestReportingByReportingDate.getMeldedatum());

    // Patstammdaten
    setLatestReportedPatientData(latestReportingByReportingDate, obds);

    addAllMeldungen(meldungenOldestFirst, obds);

    return obds;
  }

  /**
   * Adds every Meldung of the tumor, regardless of its type (Diagnose, Pathologie, OP, ST, SYST,
   * Verlauf, Tod, Tumorkonferenz), to the given oBDS. At this point only the latest version of each
   * Meldung is left (see {@link #retainLatestVersionOnly}).
   *
   * <p>Meldungen describing the same therapy, Verlauf or Tumorkonferenz (e.g. the behandlungsbeginn
   * and -ende of a SYST) are all passed on: the {@link ObdsToFhirBundleMapper} processes the later
   * one last, so its resources override the ones with the same ID created from the earlier one.
   * This is the same input the bundle mapper gets when processing from a directory.
   *
   * @param meldungenOldestFirst the Meldungen in {@link #REPORTING_ORDER}. The bundle mapper's
   *     sorting is stable, so Meldungen it treats as equal still override each other in this order.
   */
  protected static void addAllMeldungen(List<MeldungExportV3> meldungenOldestFirst, OBDS obds) {
    meldungenOldestFirst.stream()
        .map(MeldungExportV3::getObds)
        .map(OBDS::getMengePatient)
        .map(OBDS.MengePatient::getPatient)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .map(OBDS.MengePatient.Patient::getMengeMeldung)
        .filter(Objects::nonNull)
        .map(OBDS.MengePatient.Patient.MengeMeldung::getMeldung)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .forEach(meldung -> addMeldung(meldung, obds));
  }

  /**
   * Patient -- set latest known patient master data by reporting date
   *
   * @param latestReportingByReportingDate input data source
   * @param obds target data opbject
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

  private static void addMeldung(Meldung meldung, OBDS obds) {
    if (meldung != null) {
      obds.getMengePatient().getPatient().getFirst().getMengeMeldung().getMeldung().add(meldung);
    }
  }

  private static String patientBundleKeySelector(Bundle bundle) {
    var conditions = BundleUtil.toListOfResourcesOfType(ctx, bundle, Condition.class);

    // bundle id equals patient id
    if (bundle.getId().isEmpty() || bundle.getId().isBlank()) {
      throw new RuntimeException("A patient bundle has no id");
    }
    var patientRef = String.format("%s/%s", "Patient", bundle.getId());

    if (conditions.isEmpty()) {
      /*
      in rare cases we could encounter a data slice without a condition.
      then we create a bundle without a condition ID.
      NOTE: if we should encounter more than one tumor for this patient, we could override a fhir bundle due same kafka key, since we miss condition discriminante.
      */
      return patientRef;
    }

    var condition = conditions.getFirst();
    return String.format("%s - %s/%s", patientRef, condition.getResourceType(), condition.getId());
  }
}
