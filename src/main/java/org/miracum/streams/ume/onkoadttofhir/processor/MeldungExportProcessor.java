package org.miracum.streams.ume.onkoadttofhir.processor;

import java.io.StringReader;
import java.util.function.Function;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.model.ADT_GEKID;
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

      // System.out.println(meldungExport.getXml_daten());

      StringReader sr = new StringReader(meldungExport.getXml_daten());

      try {
        JAXBContext jaxbContext = JAXBContext.newInstance(ADT_GEKID.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        ADT_GEKID test = (ADT_GEKID) unmarshaller.unmarshal(sr);

        System.out.println(test.getSchema_version());

        var kvn =
            test.getMenge_patientList()
                .get(0)
                .getPatient()
                .getPatienten_stammdaten()
                .getKrankenversichertenNr();
      } catch (JAXBException e) {
        throw new RuntimeException(e);
      }

      var bundle = new Bundle();
      return bundle;
    };
  }
}
