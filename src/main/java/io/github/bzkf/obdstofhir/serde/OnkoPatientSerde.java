package io.github.bzkf.obdstofhir.serde;

import io.github.bzkf.obdstofhir.model.OnkoPatient;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;

public class OnkoPatientSerde extends JacksonJsonSerde<OnkoPatient> {

  public OnkoPatientSerde() {
    super(OnkoPatient.class);
  }
}
