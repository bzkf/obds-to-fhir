package io.github.bzkf.obdstofhir;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "fhir")
@Data
@EqualsAndHashCode(callSuper = false)
public class FhirProperties extends io.github.dizuker.tofhir.config.FhirProperties {
  private FhirExtensions extensions;
  private FhirSystems systems;

  // the codings and their versions default to the ones from to-fhir
  public Codings getCodings() {
    return codings();
  }

  @Data
  public static class FhirExtensions {
    private String conditionAssertedDate;
    private String itemWeight;
    private String conditionOccurredFollowing;
    private String procedureMethod;
  }

  @Data
  public static class FhirIdentifierSystems {
    // local systems
    private String patientId;
    private String vitalStatusId;
    private String vitalStatusOnkostarPatientTableId;
    private String primaerdiagnoseConditionId;
    private String fernmetastasenObservationId;
    private String residualstatusObservationId;
    private String weitereKlassifikationObservationId;
    private String histologieSpecimenId;
    private String studienteilnahmeObservationId;
    private String lymphknotenuntersuchungObservationId;
    private String allgemeinerLeistungszustandEcogObservationId;
    private String allgemeinerLeistungszustandKarnofskyObservationId;
    private String genetischeVarianteObservationId;
    private String tumorkonferenzCarePlanId;
    private String tnmGroupingObservationId;
    private String tnmTKategorieObservationId;
    private String tnmNKategorieObservationId;
    private String tnmMKategorieObservationId;
    private String tnmLKategorieObservationId;
    private String tnmPnKategorieObservationId;
    private String tnmSKategorieObservationId;
    private String tnmVKategorieObservationId;
    private String erstdiagnoseEvidenzListId;
    private String verlaufshistologieObservationId;
    private String strahlentherapieProcedureId;
    private String strahlentherapieBestrahlungProcedureId;
    private String systemischeTherapieProcedureId;
    private String systemischeTherapieMedicationStatementId;
    private String systemischeTherapieMedicationId;
    private String histologiebefundDiagnosticReportId;
    private String gradingObservationId;
    private String verlaufObservationId;
    private String todObservationId;
    private String todObservationOnkostarPatientTableId;
    private String nebenwirkungAdverseEventId;
    private String fruehereTumorerkrankungConditionId;
    private String prostataPsaObservationId;
    private String prostataAnzahlStanzenObservationId;
    private String prostataAnzahlPositiveStanzenObservationId;
    private String prostataCaBefallStanzeObservationId;
    private String prostataClavienDindoObservationId;
    private String prostataGleasonPatternsObservationId;
    private String prostataGleasonScoreObservationId;
    private String obdsMeldungId;
    private String provenanceId;
    private String obdsToFhirDeviceId;
    private String operationProcedureId;
  }

  @Data
  public static class FhirSystems {
    private FhirIdentifierSystems identifiers;

    private String diagnosticServiceSection;
    private String v3DataOperation;
    private String v3ParticipationType;
    private String provenanceParticipantType;
    private String identifierType;
    private String v3ObservationValue;
    private String loinc;
    private String icdo3Morphologie;
    private String icd10gm;
    private String icd10who;
    private String snomed;
    private String ops;
    private String ucum;
    private String conditionVerStatus;
    private String atcBfarm;
    private String observationCategory;
    private String meddra;
  }
}
