package org.miracum.streams.ume.onkoadttofhir.processor;

import java.util.function.Function;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MeldungExportProcessor extends OnkoProcessor {

  @Value("${app.version}")
  private String appVersion;

  protected MeldungExportProcessor(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  @Bean
  public Function<KTable<String, MeldungExport>, KStream<String, Bundle>>
      getMeldungExportProcessor() {
    return stringOnkoMeldungExpTable ->
        stringOnkoMeldungExpTable.mapValues(getOnkoBundleMapper()).toStream();
  }

  public ValueMapper<MeldungExport, Bundle> getOnkoBundleMapper() {
    return meldungExport -> {
      var testMeldung = meldungExport.getXml_daten();

      System.out.println(
          testMeldung
              .getMenge_Patient()
              .get(0)
              .getPatient()
              .getPatienten_Stammdaten()
              .getKrankenversichertenNr());

      var bundle = new Bundle();
      return bundle;
    };
  }
}
