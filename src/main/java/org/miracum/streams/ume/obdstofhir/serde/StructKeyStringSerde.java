package org.miracum.streams.ume.obdstofhir.serde;

import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.miracum.streams.ume.obdstofhir.model.StructKey;

public class StructKeyStringSerde implements Serde<StructKey> {

  private static final Pattern structPattern =
      Pattern.compile(
          "Struct\\{((REFERENZ_NUMMER=\"(?<referenznummer>[^\"]*)\")|(TUMOR_ID=\"(?<tumorid>[^\"]*))\"|,\\s*)+\\}");

  @Override
  public Serializer<StructKey> serializer() {
    return (s, structKey) -> {
      if (null == structKey) {
        return null;
      }

      var values = new StringJoiner(",");

      if (null != structKey.referenzNummer()) {
        values.add(String.format("REFERENZ_NUMMER=\"%s\"", structKey.referenzNummer()));
      }

      if (null != structKey.tumorId()) {
        values.add(String.format("TUMOR_ID=\"%s\"", structKey.tumorId()));
      }

      return String.format("Struct{%s}", values).getBytes(StandardCharsets.UTF_8);
    };
  }

  @Override
  public Deserializer<StructKey> deserializer() {
    return (s, structKey) -> {
      if (null == structKey) {
        return null;
      }
      var matcher = structPattern.matcher(new String(structKey, StandardCharsets.UTF_8));
      if (matcher.find()) {
        return new StructKey(matcher.group("referenznummer"), matcher.group("tumorid"));
      }
      return null;
    };
  }
}
