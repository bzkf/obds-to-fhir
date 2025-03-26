package org.miracum.streams.ume.obdstofhir.serde;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;

public class Obdsv3Serializer extends JsonSerializer<OBDS> {

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
  public void serialize(OBDS value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    gen.writeString(mapper.writeValueAsString(value));
  }
}
