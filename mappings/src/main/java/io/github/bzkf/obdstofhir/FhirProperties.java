package io.github.bzkf.obdstofhir;

import lombok.Data;
import org.hl7.fhir.r4.model.Coding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "fhir")
@Data
public class FhirProperties {
  private FhirExtensions extensions;
  private FhirSystems systems;
  private Codings codings;

  public static record Codings(Coding loinc, Coding snomed, Coding ops, Coding atc) {
    @Override
    public Coding loinc() {
      // return a fresh copy, otherwise the original instance will be modified
      return loinc.copy();
    }

    @Override
    public Coding snomed() {
      return snomed.copy();
    }

    @Override
    public Coding ops() {
      return ops.copy();
    }

    @Override
    public Coding atc() {
      return atc.copy();
    }
  }

  @Data
  public static class FhirExtensions {
    private String dataAbsentReason;
    private String conditionAssertedDate;
    private String ordinalValue;
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
    private String tnmASymbolObservationId;
    private String tnmMSymbolObservationId;
    private String tnmRSymbolObservationId;
    private String tnmYSymbolObservationId;
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
    private String uicc;
    private String tnmPraefix;
    private String tnmTCs;
    private String tnmNCs;
    private String tnmMCs;
    private String tnmYSymbolCs;
    private String tnmRSymbolCs;
    private String tnmMSymbolCs;
    private String fMLokalisationCS;
    private String jnuCs;
    private String icd10gm;
    private String adtSeitenlokalisation;
    private String snomed;
    private String opIntention;
    private String systTherapieart;
    private String ops;
    private String lokalBeurtResidualCS;
    private String gesamtBeurtResidualCS;
    private String systIntention;
    private String systStellungOP;
    private String sideEffectTypeOid;
    private String opComplication;
    private String ucum;
    private String conditionVerStatus;
    private String icdo3MorphologieOid;
    private String atcBfarm;
    private String atcWho;
    private String observationCategory;
    private String meddra;
  }
}
