package io.github.bzkf.obdstofhir.serde;

import io.github.bzkf.obdstofhir.model.MeldungExportV3;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;

public class MeldungExportV3Serde extends JacksonJsonSerde<MeldungExportV3> {

  public MeldungExportV3Serde() {
    super(MeldungExportV3.class);
  }
}
