package io.github.bzkf.obdstofhir.serde;

import io.github.bzkf.obdstofhir.model.OnkoPatient;
import org.springframework.kafka.support.serializer.JsonSerde;

public class OnkoPatientSerde extends JsonSerde<OnkoPatient> {}
