package io.github.bzkf.obdstofhir.serde;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * {@code Fruehere_Tumorerkrankung/Diagnosedatum} is the only oBDS date that contains just a year.
 * Jackson reads an all-digit date as an epoch timestamp, turning {@code 2010} into {@code
 * 1970-01-01T00:00:02.010Z}, so this field is read and written in its XML form instead.
 */
abstract class FruehereTumorerkrankungMixIn {

  @JsonDeserialize(using = XmlFormatDeserializer.class)
  @JsonSerialize(using = XmlFormatSerializer.class)
  protected XMLGregorianCalendar diagnosedatum;

  static class XmlFormatDeserializer extends ValueDeserializer<XMLGregorianCalendar> {
    @Override
    public XMLGregorianCalendar deserialize(JsonParser parser, DeserializationContext ctxt) {
      try {
        return DatatypeFactory.newInstance().newXMLGregorianCalendar(parser.getValueAsString());
      } catch (DatatypeConfigurationException e) {
        throw new IllegalStateException("Unable to create the DatatypeFactory", e);
      }
    }
  }

  static class XmlFormatSerializer extends ValueSerializer<XMLGregorianCalendar> {
    @Override
    public void serialize(
        XMLGregorianCalendar value, JsonGenerator generator, SerializationContext ctxt) {
      generator.writeString(value.toXMLFormat());
    }
  }
}
