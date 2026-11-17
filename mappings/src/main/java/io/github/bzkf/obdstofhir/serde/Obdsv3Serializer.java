package io.github.bzkf.obdstofhir.serde;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRootName;
import de.basisdatensatz.obds.v3.DiagnoseTyp.MengeFruehereTumorerkrankung.FruehereTumorerkrankung;
import de.basisdatensatz.obds.v3.OBDS;
import io.github.bzkf.obdstofhir.model.ObdsOrAdt;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlValue;
import java.time.ZoneOffset;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.NopAnnotationIntrospector;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.dataformat.xml.XmlAnnotationIntrospector;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.XmlWriteFeature;
import tools.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;

public class Obdsv3Serializer extends ValueSerializer<ObdsOrAdt>
    implements org.apache.kafka.common.serialization.Serializer<OBDS> {

  private static final String OBDS_NAMESPACE = "http://www.basisdatensatz.de/oBDS/XML";

  private static final XmlMapper MAPPER =
      XmlMapper.builder()
          .defaultUseWrapper(false)
          .addModule(new JakartaXmlBindAnnotationModule())
          .addModule(
              new SimpleModule() {
                @Override
                public void setupModule(SetupContext context) {
                  super.setupModule(context);
                  context.insertAnnotationIntrospector(new ObdsElementIntrospector());
                }
              }.addSerializer(XMLGregorianCalendar.class, new XsDateSerializer()))
          .addMixIn(OBDS.class, ObdsMixIn.class)
          // the XSD prescribes the order of the elements. The model's propOrder has it, except for
          // subclasses like PatientenAdresseMelderTyp, which only list their own fields. For those,
          // Jackson would sort the inherited fields alphabetically instead of keeping their order.
          .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          // the oBDS elements aren't nillable, so write the mandatory ones that are null as
          // <Adresse/>
          .disable(XmlWriteFeature.WRITE_NULLS_AS_XSI_NIL)
          .addMixIn(FruehereTumorerkrankung.class, FruehereTumorerkrankungMixIn.class)
          .changeDefaultPropertyInclusion(
              incl -> incl.withValueInclusion(JsonInclude.Include.NON_EMPTY))
          .build();

  @Override
  public void serialize(
      ObdsOrAdt value,
      tools.jackson.core.JsonGenerator gen,
      tools.jackson.databind.SerializationContext ctxt) {

    if (value.hasOBDS()) {
      gen.writeString(MAPPER.writeValueAsString(value.getObds()));
    } else if (value.hasADT()) {
      gen.writeString(MAPPER.writeValueAsString(value.getAdt()));
    } else {
      throw new RuntimeException("Unknown XML root element in serialization");
    }
  }

  @Override
  public byte[] serialize(String topic, OBDS data) {
    return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
  }

  @JsonRootName(value = "oBDS", namespace = OBDS_NAMESPACE)
  abstract static class ObdsMixIn {}

  /** Applies what the XSD says about the elements of the model, which Jackson doesn't know. */
  static class ObdsElementIntrospector extends NopAnnotationIntrospector
      implements XmlAnnotationIntrospector {

    /**
     * The model declares the oBDS namespace only in its {@code package-info}, which Jackson
     * ignores. The XSD qualifies all elements but no attributes.
     */
    @Override
    public String findNamespace(MapperConfig<?> config, Annotated annotated) {
      return isObdsElement(annotated) ? OBDS_NAMESPACE : null;
    }

    /**
     * The deserializer reads empty elements like {@code <Adresse/>} as null. Mandatory ones have to
     * be written back as empty elements instead of being left out like other empty values.
     */
    @Override
    public JsonInclude.Value findPropertyInclusion(MapperConfig<?> config, Annotated annotated) {
      if (isObdsElement(annotated)) {
        var element = annotated.getAnnotation(XmlElement.class);
        if (element != null && element.required()) {
          return JsonInclude.Value.construct(
              JsonInclude.Include.ALWAYS, JsonInclude.Include.ALWAYS);
        }
      }
      return JsonInclude.Value.empty();
    }

    private static boolean isObdsElement(Annotated annotated) {
      return annotated instanceof AnnotatedMember member
          && member.getDeclaringClass().getPackage() == OBDS.class.getPackage()
          && !member.hasAnnotation(XmlAttribute.class)
          && !member.hasAnnotation(XmlValue.class);
    }

    @Override
    public Boolean isOutputAsAttribute(MapperConfig<?> config, Annotated annotated) {
      return null;
    }

    @Override
    public Boolean isOutputAsText(MapperConfig<?> config, Annotated annotated) {
      return null;
    }

    @Override
    public Boolean isOutputAsCData(MapperConfig<?> config, Annotated annotated) {
      return null;
    }
  }

  /**
   * Writes the oBDS dates as {@code xs:date}, which is the type of all of them except the {@code
   * xs:gYear} that {@link FruehereTumorerkrankungMixIn} handles.
   *
   * <p>Jackson reads a date without a time zone, e.g. {@code 2024-02-16}, as midnight UTC, and
   * would write it back as {@code 2024-02-16T00:00:00.000Z}, which isn't valid oBDS. So these
   * instants are written as the date, with the time zone in which they are midnight, to keep them
   * unchanged when they are read again.
   */
  static class XsDateSerializer extends ValueSerializer<XMLGregorianCalendar> {
    private static final int SECONDS_PER_DAY = 24 * 60 * 60;

    @Override
    public void serialize(
        XMLGregorianCalendar value, JsonGenerator generator, SerializationContext ctxt) {
      generator.writeString(toXsDate(value));
    }

    static String toXsDate(XMLGregorianCalendar value) {
      if (value.getXMLSchemaType() != DatatypeConstants.DATETIME) {
        return value.toXMLFormat();
      }

      var utc =
          value
              .toGregorianCalendar()
              .toZonedDateTime()
              .toOffsetDateTime()
              .withOffsetSameInstant(ZoneOffset.UTC);
      var secondOfDay = utc.toLocalTime().toSecondOfDay();
      if (utc.getNano() != 0 || secondOfDay % 60 != 0) {
        // not a date in any time zone, so keep what we have
        return value.toXMLFormat();
      }
      if (secondOfDay == 0) {
        return utc.toLocalDate().toString();
      }

      // xs:date allows offsets up to 14 hours, so use the nearer of the two midnights
      var offset =
          ZoneOffset.ofTotalSeconds(
              secondOfDay >= SECONDS_PER_DAY / 2 ? SECONDS_PER_DAY - secondOfDay : -secondOfDay);
      return utc.withOffsetSameInstant(offset).toLocalDate() + offset.getId();
    }
  }
}
