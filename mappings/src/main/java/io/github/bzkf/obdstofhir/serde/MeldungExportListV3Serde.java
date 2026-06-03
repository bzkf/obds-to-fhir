package io.github.bzkf.obdstofhir.serde;

import io.github.bzkf.obdstofhir.model.MeldungExportListV3;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;

public class MeldungExportListV3Serde extends JacksonJsonSerde<MeldungExportListV3> {

  public MeldungExportListV3Serde() {
    super(MeldungExportListV3.class);
  }
}
