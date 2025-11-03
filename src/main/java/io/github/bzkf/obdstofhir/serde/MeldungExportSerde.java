package io.github.bzkf.obdstofhir.serde;

import io.github.bzkf.obdstofhir.model.MeldungExport;
import org.springframework.kafka.support.serializer.JsonSerde;

public class MeldungExportSerde extends JsonSerde<MeldungExport> {}
