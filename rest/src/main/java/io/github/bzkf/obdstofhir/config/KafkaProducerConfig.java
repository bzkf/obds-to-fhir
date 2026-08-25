package io.github.bzkf.obdstofhir.config;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaProducerConfig {

  @Bean
  public ProducerFactory<String, IBaseResource> producerFactory(KafkaProperties kafkaProperties) {
    return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
  }

  @Bean
  public KafkaTemplate<String, IBaseResource> kafkaTemplate(
      ProducerFactory<String, IBaseResource> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }
}
