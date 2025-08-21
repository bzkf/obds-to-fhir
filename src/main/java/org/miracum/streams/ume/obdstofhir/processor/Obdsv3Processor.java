package org.miracum.streams.ume.obdstofhir.processor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleUtil;
import java.util.function.Function;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Patient;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.WriteGroupedObdsToKafkaConfig;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportListV3;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportV3;
import org.miracum.streams.ume.obdstofhir.serde.MeldungExportListV3Serde;
import org.miracum.streams.ume.obdstofhir.serde.MeldungExportV3Serde;
import org.miracum.streams.ume.obdstofhir.serde.Obdsv3Deserializer;
import org.miracum.streams.ume.obdstofhir.serde.Obdsv3Serializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    value = "obds.process-from-directory.enabled",
    havingValue = "false",
    matchIfMissing = true)
@Configuration
public class Obdsv3Processor extends ObdsToFhirMapper {

  private static final FhirContext ctx = FhirContext.forR4();

  private final MeldungTransformationService meldungTransformationService;

  private final WriteGroupedObdsToKafkaConfig writeGroupedObdsToKafkaConfig;

  protected Obdsv3Processor(
      FhirProperties fhirProperties,
      MeldungTransformationService meldungTransformationService,
      WriteGroupedObdsToKafkaConfig writeGroupedObdsToKafkaConfig) {
    super(fhirProperties);
    this.meldungTransformationService = meldungTransformationService;
    this.writeGroupedObdsToKafkaConfig = writeGroupedObdsToKafkaConfig;
  }

  @Bean
  public Function<KTable<String, MeldungExportV3>, KStream<String, Bundle>>
      getMeldungExportObdsV3Processor() {

    return stringOnkoMeldungExpTable -> {
      var mapped = stringOnkoMeldungExpTable.mapValues(meldungTransformationService::mapObdsOrAdt);

      var stream =
          mapped
              .groupBy(
                  (key, meldung) -> KeyValue.pair(getPatIdFromMeldung(meldung), meldung),
                  Grouped.with(Serdes.String(), new MeldungExportV3Serde()))
              .aggregate(
                  MeldungExportListV3::new,
                  (key, meldung, aggregate) ->
                      meldungTransformationService.aggregate(meldung, aggregate),
                  (key, meldung, aggregate) ->
                      meldungTransformationService.remove(meldung, aggregate),
                  Materialized.with(Serdes.String(), new MeldungExportListV3Serde()))
              .toStream()
              .mapValues(meldungTransformationService::groupByTumorId);

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

      return stream
          .flatMapValues(meldungTransformationService::toBundles)
          .filter((key, bundle) -> bundle != null)
          .selectKey((key, bundle) -> patientBundleKeySelector(bundle));
    };
  }

  public static String getPatIdFromMeldung(MeldungExportV3 meldung) {
    return meldung.getObds().getMengePatient().getPatient().getFirst().getPatientID();
  }

  private ValueMapper<List<MeldungExportListV3>, List<OBDS>>
      getMeldungExportListToObdsListMapper() {
    return meldungGroupedByPatientIdAndTumorId ->
        meldungGroupedByPatientIdAndTumorId.stream().map(meldungTransformationService::processMeldungGroup).toList();
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
