package org.miracum.streams.ume.obdstofhir.serde;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import de.basisdatensatz.obds.v2.ADTGEKID;
import de.basisdatensatz.obds.v3.OBDS;
import java.io.IOException;
import java.util.Locale;
import org.miracum.streams.ume.obdstofhir.model.ObdsOrAdt;
import org.springframework.stereotype.Service;

@Service
public class Obdsv3Deserializer extends JsonDeserializer<ObdsOrAdt> {

  private final XmlMapper mapper;

  public Obdsv3Deserializer() {
    this.mapper =
        XmlMapper.builder()
            .defaultUseWrapper(false)
            .addModule(new JakartaXmlBindAnnotationModule())
            .addModule(new Jdk8Module())
            .enable(FromXmlParser.Feature.EMPTY_ELEMENT_AS_NULL)
            .addHandler(new SchemaLocationPropertyHandler())
            .build();
  }

  private static class SchemaLocationPropertyHandler extends DeserializationProblemHandler {
    @Override
    public boolean handleUnknownProperty(
        DeserializationContext ctxt,
        JsonParser p,
        JsonDeserializer<?> deserializer,
        Object beanOrClass,
        String propertyName) {
      // returning true here means that we handled the unknown property
      // returning false let's the default handler take care of other unknown properties
      return propertyName.equals("schemaLocation");
    }
  }

  @Override
  public ObdsOrAdt deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException {

    String xml = jsonParser.getValueAsString();

    return deserializeAsObdsOrAdt(xml);
  }

  public ObdsOrAdt deserializeAsObdsOrAdt(String xml) throws IOException {
    if (xml == null || xml.trim().isEmpty()) {
      return new ObdsOrAdt(null, null);
    }

    if (xml.toLowerCase(Locale.ENGLISH).contains("<obds")
        && xml.toLowerCase(Locale.ENGLISH).contains("schema_version=\"3.")) {
      OBDS obds = mapper.readValue(xml, OBDS.class);
      return ObdsOrAdt.from(obds);
    } else if (xml.toLowerCase(Locale.ENGLISH).contains("<adt")
        && xml.toLowerCase(Locale.ENGLISH).contains("schema_version=\"2.")) {
      ADTGEKID adt = mapper.readValue(xml, ADTGEKID.class);
      return ObdsOrAdt.from(adt);
    } else {
      throw new IOException("Unknown XML root element in deserialization");
    }
  }
}
