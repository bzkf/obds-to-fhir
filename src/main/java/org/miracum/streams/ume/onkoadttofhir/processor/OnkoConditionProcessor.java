package org.miracum.streams.ume.onkoadttofhir.processor;

import java.util.*;
import java.util.function.BiFunction;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.mapper.OnkoConditionMapper;
import org.miracum.streams.ume.onkoadttofhir.mapper.OnkoToFhirMapper;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExportList;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportListSerde;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportSerde;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoConditionProcessor extends OnkoToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(OnkoConditionProcessor.class);

  private final OnkoConditionMapper onkoConditionMapper;

  @Autowired
  protected OnkoConditionProcessor(
      FhirProperties fhirProperties, OnkoConditionMapper onkoConditionMapper) {
    super(fhirProperties);
    this.onkoConditionMapper = onkoConditionMapper;
  }

  @Bean
  public BiFunction<KTable<String, MeldungExport>, KTable<String, Bundle>, KStream<String, Bundle>>
      getMeldungExportConditionProcessor() {
    return (stringOnkoMeldungExpTable, stringOnkoObsBundles) ->
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
                            + getPatIdFromAdt(value)
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
            .leftJoin(stringOnkoObsBundles, Pair::of)
            .mapValues(this.getOnkoToConditionBundleMapper())
            .filter((key, value) -> value != null)
            .toStream();
  }

  public ValueMapper<Pair<MeldungExportList, Bundle>, Bundle> getOnkoToConditionBundleMapper() {
    return meldungPair -> {
      List<MeldungExport> meldungExportList =
          prioritiseLatestMeldungExports(
              meldungPair.getLeft(),
              Arrays.asList("diagnose", "behandlungsende", "statusaenderung"));

      return onkoConditionMapper.mapOnkoResourcesToCondition(
          meldungExportList, meldungPair.getRight());
    };
  }
}
