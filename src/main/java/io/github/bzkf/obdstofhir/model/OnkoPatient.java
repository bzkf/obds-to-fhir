package io.github.bzkf.obdstofhir.model;

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
  Integer patid;

  @JsonProperty("LETZTE_INFORMATION")
  LocalDate letzteInformation;

  @JsonProperty("STERBEDATUM")
  LocalDate sterbeDatum;

  @JsonProperty("STERBEDATUM_ACC")
  String sterbedatumAcc;

  @JsonProperty("PATIENTEN_ID")
  String patientId;

  @JsonProperty("ANGELEGT_AM")
  LocalDateTime angelegtAm;

  @JsonProperty("ZU_LOESCHEN")
  Integer zuLoeschen;

  @JsonProperty("PATIENTEN_IDS_VORHER")
  Integer patientenIdsVorher;

  @JsonProperty("BEARBEITET_AM")
  LocalDateTime bearbeitetAm;
}
