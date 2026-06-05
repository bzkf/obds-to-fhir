package io.github.bzkf.obdstofhir.config;

import io.github.bzkf.obdstofhir.model.OnkoPatient;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class KafkaProducerConfig {

  /** Default Template for ProcessFromDirectory */
  @Bean
  public ProducerFactory<String, IBaseResource> producerFactory(KafkaProperties kafkaProperties) {
    return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
  }

  @Bean
  public KafkaTemplate<String, IBaseResource> kafkaTemplate(
      ProducerFactory<String, IBaseResource> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  /** Edits default Kafka producer config Json serialization for OnkoPatient for csv-input option */
  @Bean
  public ProducerFactory<String, OnkoPatient> onkoPatientProducerFactory(
      KafkaProperties kafkaProperties) {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());

    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, OnkoPatient> onkoPatientKafkaTemplate(
      ProducerFactory<String, OnkoPatient> onkoPatientProducerFactory) {
    return new KafkaTemplate<>(onkoPatientProducerFactory);
  }
}
