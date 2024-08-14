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
}
