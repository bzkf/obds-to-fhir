package io.github.bzkf.obdstofhir.serde;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.basisdatensatz.obds.v2.ADTGEKID;
import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.model.ObdsOrAdt;
import java.util.Locale;
import org.springframework.stereotype.Service;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.XmlReadFeature;
import tools.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;

@Service
public class Obdsv3Deserializer extends ValueDeserializer<ObdsOrAdt>
    implements org.apache.kafka.common.serialization.Deserializer<OBDS> {

  private static final XmlMapper MAPPER =
      XmlMapper.builder()
          .defaultUseWrapper(false)
          .addModule(new JakartaXmlBindAnnotationModule())
          .addHandler(new SchemaLocationPropertyHandler())
          .enable(XmlReadFeature.EMPTY_ELEMENT_AS_NULL)
          .changeDefaultPropertyInclusion(
              incl -> incl.withValueInclusion(JsonInclude.Include.NON_EMPTY))
          .build();

  private static class SchemaLocationPropertyHandler
      extends tools.jackson.databind.deser.DeserializationProblemHandler {

    @Override
    public boolean handleUnknownProperty(
        DeserializationContext ctxt,
        JsonParser p,
        ValueDeserializer<?> deserializer,
        Object beanOrClass,
        String propertyName) {

      return propertyName.equals("schemaLocation");
    }
  }

  @Override
  public ObdsOrAdt deserialize(
      tools.jackson.core.JsonParser jsonParser,
      tools.jackson.databind.DeserializationContext ctxt) {

    return deserializeAsObdsOrAdt(jsonParser.getValueAsString());
  }

  public ObdsOrAdt deserializeAsObdsOrAdt(String xml) {
    if (xml == null || xml.trim().isEmpty()) {
      return new ObdsOrAdt(null, null);
    }

    String lower = xml.toLowerCase(Locale.ENGLISH);

    if (lower.contains("<obds") && lower.contains("schema_version=\"3.")) {

      OBDS obds = MAPPER.readValue(xml, OBDS.class);
      return ObdsOrAdt.from(obds);

    } else if (lower.contains("<adt") && lower.contains("schema_version=\"2.")) {

      ADTGEKID adt = MAPPER.readValue(xml, ADTGEKID.class);
      return ObdsOrAdt.from(adt);

    } else {
      throw new IllegalArgumentException("Unknown XML root element in deserialization");
    }
  }

  @Override
  public OBDS deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }

    return MAPPER.readValue(data, OBDS.class);
  }
}
