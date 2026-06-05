package io.github.bzkf.obdstofhir.serde;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.model.ObdsOrAdt;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;

public class Obdsv3Serializer extends ValueSerializer<ObdsOrAdt>
    implements org.apache.kafka.common.serialization.Serializer<OBDS> {

  private static final XmlMapper MAPPER =
      XmlMapper.builder()
          .defaultUseWrapper(false)
          .addModule(new JakartaXmlBindAnnotationModule())
          .changeDefaultPropertyInclusion(
              incl -> incl.withValueInclusion(JsonInclude.Include.NON_EMPTY))
          .build();

  @Override
  public void serialize(
      ObdsOrAdt value,
      tools.jackson.core.JsonGenerator gen,
      tools.jackson.databind.SerializationContext ctxt) {

    String xml = MAPPER.writeValueAsString(value);

    if (xml.toLowerCase().contains("<obds") && xml.toLowerCase().contains("schema_version=\"3.")) {
      gen.writeString(MAPPER.writeValueAsString(value.getObds()));
    } else if (xml.toLowerCase().contains("<adt")
        && xml.toLowerCase().contains("schema_version=\"2.")) {
      gen.writeString(MAPPER.writeValueAsString(value.getAdt()));
    } else {
      throw new RuntimeException("Unknown XML root element in serialization");
    }
  }

  @Override
  public byte[] serialize(String topic, OBDS data) {
    return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
  }
}
