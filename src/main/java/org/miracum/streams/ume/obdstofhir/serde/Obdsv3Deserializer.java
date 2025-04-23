package org.miracum.streams.ume.obdstofhir.serde;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import de.basisdatensatz.obds.v2.ADTGEKID;
import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import org.miracum.streams.ume.obdstofhir.model.ObdsOrAdt;

public class Obdsv3Deserializer extends JsonDeserializer<ObdsOrAdt> {

  private final XmlMapper mapper;

  public Obdsv3Deserializer() {
    this.mapper =
        XmlMapper.builder()
            .defaultUseWrapper(false)
            .addModule(new JakartaXmlBindAnnotationModule())
            .addModule(new Jdk8Module())
            .enable(FromXmlParser.Feature.EMPTY_ELEMENT_AS_NULL)
            .build();
  }

  @Override
  public ObdsOrAdt deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException {

    String xml = jsonParser.getValueAsString();

    if (xml == null || xml.trim().isEmpty()) {
      return new ObdsOrAdt(null, null);
    }

    if (xml.toLowerCase().contains("<obds") && xml.toLowerCase().contains("schema_version=\"3.")) {
      OBDS obds = mapper.readValue(xml, OBDS.class);
      return new ObdsOrAdt(obds, null);
    } else if (xml.toLowerCase().contains("<adt")
        && xml.toLowerCase().contains("schema_version=\"2.")) {
      ADTGEKID adt = mapper.readValue(xml, ADTGEKID.class);
      return new ObdsOrAdt(null, adt);
    } else {
      throw new IOException("Unknown XML root element in deserialization");
    }
  }
}
