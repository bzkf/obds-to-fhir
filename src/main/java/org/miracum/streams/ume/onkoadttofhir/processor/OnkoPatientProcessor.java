package org.miracum.streams.ume.onkoadttofhir.processor;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.Bundle;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExportList;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportListSerde;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportSerde;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoPatientProcessor extends OnkoProcessor {

  @Value("${app.version}")
  private String appVersion;

  protected OnkoPatientProcessor(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  @Bean
  public Function<KTable<String, MeldungExport>, KStream<String, Bundle>>
      getMeldungExportPatientProcessor() {
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
            .mapValues(this.getOnkoToOPatientBundleMapper())
            .filter((key, value) -> value != null)
            .toStream();
  }

  public ValueMapper<MeldungExportList, Bundle> getOnkoToOPatientBundleMapper() {
    return meldungExporte -> {
      List<MeldungExport> meldungExportList =
          prioritiseLatestMeldungExports(
              meldungExporte, Arrays.asList("behandlungsende", "statusaenderung", "diagnose"));

      return extractOnkoResourcesFromReportingReason(meldungExportList);
    };
  }

  public Bundle extractOnkoResourcesFromReportingReason(List<MeldungExport> meldungExportList) {

    var meldungExport = meldungExportList.get(0);

    var bundle = new Bundle();
    return bundle;
  }
}
