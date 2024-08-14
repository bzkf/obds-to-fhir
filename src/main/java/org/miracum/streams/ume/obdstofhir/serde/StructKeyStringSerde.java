package org.miracum.streams.ume.obdstofhir.serde;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.miracum.streams.ume.obdstofhir.model.StructKey;

public class StructKeyStringSerde implements Serde<StructKey> {

  private static final Pattern structPattern =
      Pattern.compile(
          "Struct\\{REFERENZ_NUMMER=\"(?<referenznummer>.*)\",\\s*TUMOR_ID=\"(?<tumorid>.*)\"\\}");

  @Override
  public Serializer<StructKey> serializer() {
    return (s, structKey) -> {
      if (null == structKey) {
        return null;
      }
      return String.format(
              "Struct{REFERENZ_NUMMER=\"%s\",TUMOR_ID=\"%s\"}",
              null == structKey.getReferenzNummer() ? "" : structKey.getReferenzNummer(),
              null == structKey.getTumorId() ? "" : structKey.getTumorId())
          .getBytes(StandardCharsets.UTF_8);
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
