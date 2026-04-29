package io.github.bzkf.obdstofhir.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class OnkoPatient implements Serializable {

  @EqualsAndHashCode.Include
  @JsonProperty("ID")
  String id;

  @JsonProperty("PATIENTEN_ID")
  String patientId;

  @JsonProperty("LETZTEINFORMATION")
  LocalDate letzteInformation;

  @JsonProperty("STERBEDATUM")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  LocalDate sterbeDatum;

  @JsonProperty("BEARBEITET_AM")
  LocalDateTime bearbeitetAm;
}
