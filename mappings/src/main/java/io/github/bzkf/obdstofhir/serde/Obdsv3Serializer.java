package io.github.bzkf.obdstofhir.serde;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.model.ObdsOrAdt;
import java.io.IOException;
import tools.jackson.databind.ValueSerializer;

public class Obdsv3Serializer extends ValueSerializer<ObdsOrAdt>
    implements org.apache.kafka.common.serialization.Serializer<OBDS> {

  private final XmlMapper mapper;

  public Obdsv3Serializer() {
    this.mapper =
        XmlMapper.builder()
            .defaultUseWrapper(false)
            .addModule(new JakartaXmlBindAnnotationModule())
            .addModule(new Jdk8Module())
            .build();
  }

  @Override
  public void serialize(
      ObdsOrAdt value,
      tools.jackson.core.JsonGenerator gen,
      tools.jackson.databind.SerializationContext ctxt) {
    try {
      String xml = mapper.writeValueAsString(value);

      if (xml.toLowerCase().contains("<obds")
          && xml.toLowerCase().contains("schema_version=\"3.")) {
        gen.writeString(mapper.writeValueAsString(value.getObds()));
      } else if (xml.toLowerCase().contains("<adt")
          && xml.toLowerCase().contains("schema_version=\"2.")) {
        gen.writeString(mapper.writeValueAsString(value.getAdt()));
      } else {
        throw new IOException("Unknown XML root element in serialization");
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize XML_DATEN", e);
    }
  }

  @Override
  public byte[] serialize(String topic, OBDS data) {
    try {
      return mapper
          .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
          .writerWithDefaultPrettyPrinter()
          .writeValueAsBytes(data);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error serializing OBDS data", e);
    }
  }
}
