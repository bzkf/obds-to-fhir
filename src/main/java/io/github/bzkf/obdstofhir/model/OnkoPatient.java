package io.github.bzkf.obdstofhir.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.LocalDate;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class OnkoPatient implements Serializable {

  @EqualsAndHashCode.Include
  @JsonProperty("ID")
  String id;

  @JsonProperty("PATIENTEN_ID")
  String patientId;

  @JsonProperty("LETZTEINFORMATION")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  LocalDate letzteInformation;

  @JsonProperty("STERBEDATUM")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  LocalDate sterbeDatum;
}
