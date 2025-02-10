package org.miracum.streams.ume.obdstofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Function;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serdes.StringSerde;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.hl7.fhir.r4.model.Bundle;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.miracum.kafka.serializers.KafkaFhirDeserializer;
import org.miracum.kafka.serializers.KafkaFhirSerde;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.mii.ConditionMapper;
import org.miracum.streams.ume.obdstofhir.mapper.mii.LeistungszustandMapper;
import org.miracum.streams.ume.obdstofhir.mapper.mii.ObdsToFhirBundleMapper;
import org.miracum.streams.ume.obdstofhir.mapper.mii.OperationMapper;
import org.miracum.streams.ume.obdstofhir.mapper.mii.PatientMapper;
import org.miracum.streams.ume.obdstofhir.mapper.mii.ResidualstatusMapper;
import org.miracum.streams.ume.obdstofhir.mapper.mii.StrahlentherapieMapper;
import org.miracum.streams.ume.obdstofhir.mapper.mii.SystemischeTherapieMedicationStatementMapper;
import org.miracum.streams.ume.obdstofhir.mapper.mii.SystemischeTherapieProcedureMapper;
import org.miracum.streams.ume.obdstofhir.mapper.mii.TodMapper;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportV3;
import org.miracum.streams.ume.obdstofhir.serde.MeldungExportV3Serde;
import org.miracum.streams.ume.obdstofhir.serde.Obdsv3Deserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.kafka.support.serializer.JsonSerializer;

@SpringBootTest(
    classes = {
      Obdsv3Processor.class,
      FhirProperties.class,
      ObdsToFhirBundleMapper.class,
      PatientMapper.class,
      ConditionMapper.class,
      SystemischeTherapieProcedureMapper.class,
      SystemischeTherapieMedicationStatementMapper.class,
      StrahlentherapieMapper.class,
      TodMapper.class,
      LeistungszustandMapper.class,
      OperationMapper.class,
      ResidualstatusMapper.class,
      Obdsv3Deserializer.class,
      JsonSerializer.class
    })
public class Obdsv3ProcessorTest {

  @Autowired private Obdsv3Processor processor;

  @Autowired private Obdsv3Deserializer obdsDeserializer;

  @Value("classpath:obds3/test1.xml")
  Resource testObds;

  @Test
  void getMeldungExportObdsV3Processor_mapsToBundle() throws IOException {

    var inputTopicName = "meldung-obds";
    var outputTopicName = "onko-fhir";

    try (var driver =
        buildStream(processor.getMeldungExportObdsV3Processor(), inputTopicName, outputTopicName)) {

      var inputTopic =
          driver.createInputTopic(
              inputTopicName, new StringSerializer(), new JsonSerializer<MeldungExportV3>());
      var outputTopic =
          driver.createOutputTopic(
              outputTopicName, new StringDeserializer(), new KafkaFhirDeserializer());

      // get test data
      var meldung = getTestObds(testObds);

      // create input record
      inputTopic.pipeInput("test", meldung);

      // get records from output topic
      var outputRecords = outputTopic.readRecordsToList();

      // assert coding exists
      assertThat(outputRecords).isNotEmpty();
    }
  }

  TopologyTestDriver buildStream(
      Function<KTable<String, MeldungExportV3>, KStream<String, Bundle>> processor,
      String inputTopic,
      String outputTopic) {

    // StreamConfig props
    var props = new Properties();
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, StringSerde.class);
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, KafkaFhirSerde.class);

    var builder = new StreamsBuilder();
    final KTable<String, MeldungExportV3> stream =
        builder.table(inputTopic, Consumed.with(Serdes.String(), new MeldungExportV3Serde()));

    processor.apply(stream).to(outputTopic);

    return new TopologyTestDriver(builder.build(), props);
  }

  private MeldungExportV3 getTestObds(Resource testObds) throws IOException {

    var meldung =
        new JSONObject()
            .put("ID", "42")
            .put("XML_DATEN", testObds.getContentAsString(StandardCharsets.UTF_8))
            .toString();

    var mapper = new ObjectMapper();
    return mapper.readValue(meldung, MeldungExportV3.class);
  }
}
