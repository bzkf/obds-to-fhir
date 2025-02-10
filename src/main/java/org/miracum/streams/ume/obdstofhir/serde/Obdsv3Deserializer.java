package org.miracum.streams.ume.obdstofhir.serde;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;

public class Obdsv3Deserializer extends JsonDeserializer<OBDS> {

  private final XmlMapper mapper;

  public Obdsv3Deserializer() {
    this.mapper =
        XmlMapper.builder()
            .defaultUseWrapper(false)
            .addModule(new JakartaXmlBindAnnotationModule())
            .addModule(new Jdk8Module())
            .build();
  }

  @Override
  public OBDS deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException {

    var value = jsonParser.getValueAsString();
    return mapper.readValue(value, OBDS.class);
  }
}
