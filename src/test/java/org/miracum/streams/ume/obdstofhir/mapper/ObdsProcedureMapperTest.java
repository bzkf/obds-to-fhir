package org.miracum.streams.ume.obdstofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung.Menge_ST.ST.Menge_Bestrahlung.Bestrahlung;
import org.miracum.streams.ume.obdstofhir.model.Tupel;

public class ObdsProcedureMapperTest {

  private ObdsProcedureMapper mapper;

  @BeforeEach
  void setup() {
    this.mapper = new ObdsProcedureMapper(new FhirProperties());
  }

  @Test
  void shouldGetWholeTimeSpanFromPartialRadiations() {
    final var partialRadiations =
        List.of(
            getTestPartialRadiationForTimespand("01.01.2024", "31.01.2024"),
            getTestPartialRadiationForTimespand("01.03.2024", "30.04.2024"),
            getTestPartialRadiationForTimespand("10.05.2024", "31.05.2024"));

    final var actual = this.mapper.getTimeSpanFromPartialRadiations(partialRadiations);

    assertThat(actual)
        .isEqualTo(
            Tupel.builder()
                .first(Date.from(Instant.parse("2024-01-01T00:00:00Z")))
                .second(Date.from(Instant.parse("2024-05-31T00:00:00Z")))
                .build());
  }

  private static Bestrahlung getTestPartialRadiationForTimespand(String start, String end) {
    Bestrahlung bestrahlung = new Bestrahlung();
    bestrahlung.setST_Beginn_Datum(start);
    bestrahlung.setST_Ende_Datum(end);
    return bestrahlung;
  }
}
