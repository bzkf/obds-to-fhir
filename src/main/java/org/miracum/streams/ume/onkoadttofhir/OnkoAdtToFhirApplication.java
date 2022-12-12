package org.miracum.streams.ume.onkoadttofhir;

import org.apache.kafka.common.serialization.Serde;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.miracum.kafka.serializers.KafkaFhirSerde;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class OnkoAdtToFhirApplication {

  public static void main(String[] args) {
    SpringApplication.run(OnkoAdtToFhirApplication.class, args);
  }

  @Bean
  public Serde<IBaseResource> fhirSerde() {
    return new KafkaFhirSerde();
  }
}
