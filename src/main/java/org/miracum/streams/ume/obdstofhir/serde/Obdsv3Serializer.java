package org.miracum.streams.ume.obdstofhir.serde;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import java.io.IOException;
import org.miracum.streams.ume.obdstofhir.model.ObdsOrAdt;

public class Obdsv3Serializer extends JsonSerializer<ObdsOrAdt> {

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
  public void serialize(ObdsOrAdt value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {

    String xml = mapper.writeValueAsString(value);

    if (xml.toLowerCase().contains("<obds") && xml.toLowerCase().contains("schema_version=\"3.")) {
      gen.writeString(mapper.writeValueAsString(value.getObds()));
    } else if (xml.toLowerCase().contains("<adt")
        && xml.toLowerCase().contains("schema_version=\"2.")) {
      gen.writeString(mapper.writeValueAsString(value.getAdt()));
    } else {
      throw new IOException("Unknown XML root element in serialization");
    }
  }
}
