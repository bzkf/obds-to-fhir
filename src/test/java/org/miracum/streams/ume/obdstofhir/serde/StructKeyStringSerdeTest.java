package org.miracum.streams.ume.obdstofhir.serde;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.miracum.streams.ume.obdstofhir.model.StructKey;

public class StructKeyStringSerdeTest {

  private StructKeyStringSerde serde;

  @BeforeEach
  public void setup() {
    this.serde = new StructKeyStringSerde();
  }

  @Test
  void shouldSerializeStructKey() {
    var actual = serde.serializer().serialize("any", new StructKey("01234", "1"));
    assertThat(actual)
        .isEqualTo(
            "Struct{REFERENZ_NUMMER=\"01234\",TUMOR_ID=\"1\"}".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void shouldSerializePartialStructKeyWithRefNumOnly() {
    var actual = serde.serializer().serialize("any", new StructKey("01234", null));
    assertThat(actual)
        .isEqualTo("Struct{REFERENZ_NUMMER=\"01234\"}".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void shouldSerializeStructKeyWithTumIdOnly() {
    var actual = serde.serializer().serialize("any", new StructKey(null, "1"));
    assertThat(actual).isEqualTo("Struct{TUMOR_ID=\"1\"}".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void shouldDeserializeStructKey() {
    var actual =
        serde
            .deserializer()
            .deserialize(
                "any",
                "Struct{REFERENZ_NUMMER=\"01234\",TUMOR_ID=\"1\"}"
                    .getBytes(StandardCharsets.UTF_8));
    assertThat(actual).isEqualTo(new StructKey("01234", "1"));
  }

  @Test
  void shouldDeserializePartialStructKeyWithRefNumOnly() {
    var actual =
        serde
            .deserializer()
            .deserialize(
                "any", "Struct{REFERENZ_NUMMER=\"01234\"}".getBytes(StandardCharsets.UTF_8));
    assertThat(actual).isEqualTo(new StructKey("01234", null));
  }

  @Test
  void shouldDeserializePartialStructKeyWithTumIdOnly() {
    var actual =
        serde
            .deserializer()
            .deserialize("any", "Struct{TUMOR_ID=\"1\"}".getBytes(StandardCharsets.UTF_8));
    assertThat(actual).isEqualTo(new StructKey(null, "1"));
  }

  @Test
  void shouldUseLombokBuilderForStructKeyRecord() {
    var expected = new StructKey("01234", "1");
    var actual = StructKey.builder().referenzNummer("01234").tumorId("1").build();

    assertThat(actual).isEqualTo(expected);
  }
}
