package io.github.bzkf.obdstofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.PatientReferenceGenerator;
import io.github.bzkf.obdstofhir.mapper.DeviceMapper;
import io.github.bzkf.obdstofhir.mapper.mii.VitalStatusMapper;
import io.github.bzkf.obdstofhir.model.OnkoPatient;
import io.github.bzkf.obdstofhir.serde.OnkoPatientSerde;
import java.io.IOException;
import java.util.Properties;
import java.util.function.Function;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.miracum.kafka.serializers.KafkaFhirDeserializer;
import org.miracum.kafka.serializers.KafkaFhirSerde;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonSerializer;

@SpringBootTest(
    classes = {
      PatientVitalStatusProcessor.class,
      FhirProperties.class,
      VitalStatusMapper.class,
      DeviceMapper.class,
      PatientReferenceGenerator.class
    })
@EnableConfigurationProperties(value = {FhirProperties.class})
class PatientVitalStatusProcessorTest extends io.github.bzkf.obdstofhir.MapperTest {

  private static final String INPUT_TOPIC_NAME = "patient-table-vs";
  private static final String OUTPUT_TOPIC_NAME = "onko-fhir-vs";

  @Autowired private PatientVitalStatusProcessor processor;

  @Test
  void testPatientVitalStatusProcessor_MapsPatientToObsBundle() throws IOException {
    try (var driver =
        buildStream(
            processor.getPatientVitalStatusProcessor(), INPUT_TOPIC_NAME, OUTPUT_TOPIC_NAME)) {

      var inputTopic =
          driver.createInputTopic(INPUT_TOPIC_NAME, new StringSerializer(), new JsonSerializer<>());
      var outputTopic =
          driver.createOutputTopic(
              OUTPUT_TOPIC_NAME, new StringDeserializer(), new KafkaFhirDeserializer());

      // pipe test data
      inputTopic.pipeInput("key1", buildOnkoPatient("1", "12356789", null, "2010-01-01"));
      inputTopic.pipeInput("key1", buildOnkoPatient("2", "22356789", "2023-01-01", null));

      var outputRecords = outputTopic.readKeyValuesToList();
      assertThat(outputRecords).hasSize(2);

      var bundles = outputRecords.stream().map(kv -> (Bundle) kv.value).toList();

      assertThat(bundles.get(0).getEntry()).hasSize(1);
      var obs1 = (Observation) bundles.get(0).getEntry().get(0).getResource();
      assertThat(obs1.getValueCodeableConcept().getCodingFirstRep().getCode()).isEqualTo("L");
      assertThat(obs1.getEffectiveDateTimeType().getValueAsString()).isEqualTo("2010-01-01");

      assertThat(bundles.get(1).getEntry()).hasSize(1);
      var obs2 = (Observation) bundles.get(1).getEntry().get(0).getResource();
      assertThat(obs2.getValueCodeableConcept().getCodingFirstRep().getCode()).isEqualTo("T");
      assertThat(obs2.getEffectiveDateTimeType().getValueAsString()).isEqualTo("2023-01-01");
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  private OnkoPatient buildOnkoPatient(String id, String patId, String sterbedatum, String lastInfo)
      throws IOException, JSONException {

    var onkoPatient =
        new JSONObject()
            .put("ID", id)
            .put("LETZTEINFORMATION", lastInfo)
            .put("STERBEDATUM", sterbedatum)
            .put("PATIENTEN_ID", patId)
            .put("BEARBEITET_AM", null)
            .toString();

    var mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper.readValue(onkoPatient, OnkoPatient.class);
  }

  private TopologyTestDriver buildStream(
      Function<KTable<String, OnkoPatient>, KStream<String, Bundle>> processor,
      String inputTopic,
      String outputTopic) {

    // StreamConfig props
    var props = new Properties();
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, KafkaFhirSerde.class);

    var builder = new StreamsBuilder();
    final KTable<String, OnkoPatient> stream =
        builder.table(inputTopic, Consumed.with(Serdes.String(), new OnkoPatientSerde()));

    processor.apply(stream).to(outputTopic);

    return new TopologyTestDriver(builder.build(), props);
  }
}
