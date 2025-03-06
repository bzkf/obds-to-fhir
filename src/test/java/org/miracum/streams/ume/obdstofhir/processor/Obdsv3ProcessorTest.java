package org.miracum.streams.ume.obdstofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
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
import org.hl7.fhir.r4.model.Condition;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(value = {FhirProperties.class})
public class Obdsv3ProcessorTest {

  static final String INPUT_TOPIC_NAME = "meldung-obds";
  static final String OUTPUT_TOPIC_NAME = "onko-fhir";

  private static final FhirContext ctx = FhirContext.forR4();

  @Autowired private Obdsv3Processor processor;

  @Autowired private Obdsv3Deserializer obdsDeserializer;

  @Value("classpath:obds3/*.xml")
  private Resource[] obdsResources;

  public Resource getResourceByName(String filename) {
    return Arrays.stream(obdsResources)
        .filter(resource -> Objects.equals(resource.getFilename(), filename))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Test OBDS resource not found: " + filename));
  }

  @Test
  void getMeldungExportObdsV3Processor_mapsToBundle() throws IOException {
    try (var driver =
        buildStream(
            processor.getMeldungExportObdsV3Processor(), INPUT_TOPIC_NAME, OUTPUT_TOPIC_NAME)) {

      var inputTopic =
          driver.createInputTopic(INPUT_TOPIC_NAME, new StringSerializer(), new JsonSerializer<>());
      var outputTopic =
          driver.createOutputTopic(
              OUTPUT_TOPIC_NAME, new StringDeserializer(), new KafkaFhirDeserializer());

      // pipe test data
      inputTopic.pipeInput(
          "test", buildMeldungExport(getResourceByName("test1.xml"), "1", "12356789", "123", 1));

      assertThat(outputTopic.readRecordsToList()).isNotEmpty();
    }
  }

  @Test
  public void testVersionsnummerPrioritization() throws IOException {
    try (var driver =
        buildStream(
            processor.getMeldungExportObdsV3Processor(), INPUT_TOPIC_NAME, OUTPUT_TOPIC_NAME)) {

      var inputTopic =
          driver.createInputTopic(INPUT_TOPIC_NAME, new StringSerializer(), new JsonSerializer<>());
      var outputTopic =
          driver.createOutputTopic(
              OUTPUT_TOPIC_NAME, new StringDeserializer(), new KafkaFhirDeserializer());

      // pipe test data
      inputTopic.pipeInput(
          "key1",
          buildMeldungExport(getResourceByName("Priopatient_1.xml"), "1", "12356789", "123", 1));
      inputTopic.pipeInput(
          "key2",
          buildMeldungExport(getResourceByName("Priopatient_2.xml"), "2", "12356789", "123", 3));
      inputTopic.pipeInput(
          "key3",
          buildMeldungExport(getResourceByName("Priopatient_3.xml"), "3", "12356789", "123", 2));

      var outputRecords = outputTopic.readKeyValuesToList();
      assertThat(outputRecords).hasSize(3);

      var kafkaKeys = outputRecords.stream().map(record -> record.key).distinct().toList();
      assertThat(kafkaKeys).hasSize(1); // Ensure all records have the same key

      var conditions =
          outputRecords.stream()
              .map(
                  record ->
                      BundleUtil.toListOfResourcesOfType(
                              ctx, (Bundle) record.value, Condition.class)
                          .getFirst()
                          .getCode()
                          .getCoding()
                          .getFirst()
                          .getCode())
              .toList();

      // validate prioritization
      assertThat(conditions).containsExactly("C50.9", "C61", "C61");
    }
  }

  private TopologyTestDriver buildStream(
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

  private MeldungExportV3 buildMeldungExport(
      Resource xmlObds, String id, String refNummer, String lkrMeldung, int versionsnummer)
      throws IOException {
    var meldung =
        new JSONObject()
            .put("ID", id)
            .put("REFERENZ_NUMMER", refNummer)
            .put("LKR_MELDUNG", lkrMeldung)
            .put("VERSIONSNUMMER", versionsnummer)
            .put("XML_DATEN", xmlObds.getContentAsString(StandardCharsets.UTF_8))
            .toString();

    var mapper = new ObjectMapper();
    return mapper.readValue(meldung, MeldungExportV3.class);
  }
}
