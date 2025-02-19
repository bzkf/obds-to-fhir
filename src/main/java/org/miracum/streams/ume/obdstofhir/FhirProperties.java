package org.miracum.streams.ume.obdstofhir;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "fhir")
@Data
public class FhirProperties {
  private FhirExtensions extensions;
  private FhirSystems systems;
  private FhirDisplay display;
  private FhirProfiles profiles;
  private FhirUrl url;

  @Data
  public static class FhirExtensions {
    private String fernMetaExt;
    private String opIntention;
    private String stellungOP;
    private String systIntention;
    private String sysTheraProto;
    private String dataAbsentReason;
    private String genderAmtlich;
    private String miiExOnkoOPIntention;
    private String miiExOnkoStrahlentherapieIntention;
    private String miiExOnkoStrahlentherapieBestrahlung;
    private String miiExOnkoHistologyMorphologyBehaviorIcdo3;
    private String miiExOnkoSystemischeTherapieIntention;
    private String conditionAssertedDate;
  }

  @Data
  public static class FhirSystems {
    private String patientId;
    private String identifierType;
    private String conditionId;
    private String observationId;
    private String procedureId;
    private String operationProcedureId;
    private String medicationStatementId;
    private String fernmetastasenId;
    private String residualstatusObservationId;
    private String specimenId;
    private String observationCategorySystem;
    private String allgemeinerLeistungszustandEcogId;
    private String loinc;
    private String icdo3Morphologie;
    private String gradingDktk;
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
    private String ctcaeGrading;
    private String sideEffectTypeOid;
    private String opComplication;
    private String observationValue;
    private String genderAmtlichDe;
    private String gleasonScoreObservationId;
    private String psaObservationId;
    private String ucum;
    private String miiCsOnkoIntention;
    private String miiCsOnkoPrimaertumorDiagnosesicherung;
    private String miiCsOnkoStrahlentherapieApplikationsart;
    private String miiCsOnkoStrahlentherapieStrahlenart;
    private String miiCsOnkoStrahlentherapieZielgebiet;
    private String miiCsOnkoOperationResidualstatus;
    private String strahlentherapieProcedureId;
    private String systemischeTherapieProcedureId;
    private String systemischeTherapieMedicationStatementId;
    private String miiCsOnkoSystemischeTherapieArt;
    private String miiCsOnkoSeitenlokalisation;
    private String miiCsOnkoResidualstatus;
    private String miiCsTherapieGrundEnde;
    private String miiCsOnkoTodInterpretation;
    private String miiCsOnkoTodObservationId;
    private String conditionVerStatus;
    private String icdo3MorphologieOid;
    private String atcBfarm;
    private String atcWho;
    private String observationHistologieId;
    private String observationCategory;
    private String miiCsOnkoGrading;

    private String miiCsOnkoFernmetastasen;
    private String miiCsOnkoAllgemeinerLeistungszustandEcog;
    private String histologiebefundDiagnosticReportId;
    private String nebenwirkungAdverseEventId;
    private String miiVsOnkoNebenwirkungCtcaeGrad;
    private String meddra;
  }

  @Data
  public static class FhirProfiles {
    private String histologie;
    private String grading;
    private String tnmC;
    private String tnmP;
    private String fernMeta;
    private String condition;
    private String genVariante;
    private String opProcedure;
    private String stProcedure;
    private String systMedStatement;
    private String miiPatientPseudonymisiert;
    private String deathObservation;
    private String miiPrOnkoDiagnosePrimaertumor;
    private String miiPrOnkoOperation;
    private String miiPrOnkoStrahlentherapie;
    private String miiPrOnkoSystemischeTherapie;
    private String miiPrOnkoFernmetastasen;
    private String miiPrMedicationStatement;
    private String miiPrOnkoBefund;
    private String miiPrOnkoGrading;
    private String miiPrOnkoAnzahlBefalleneLymphknoten;
    private String miiPrOnkoAnzahlBefalleneSentinelLymphknoten;
    private String miiPrOnkoAnzahlUntersuchteLymphknoten;
    private String miiPrOnkoAnzahlUntersuchteSentinelLymphknoten;
    private String miiPrOnkoAllgemeinerLeistungszustandEcog;
    private String miiPrOnkoTod;
    private String miiPrOnkoSpecimen;
    private String miiPrOnkoHistologieIcdo3;
    private String miiPrOnkoNebenwirkungAdverseEvent;
  }

  @Data
  public static class FhirDisplay {
    private String histologyLoinc;
    private String gradingLoinc;
    private String tnmcLoinc;
    private String tnmpLoinc;
    private String fernMetaLoinc;
    private String deathLoinc;
    private String gleasonScoreSct;
    private String gleasonScoreLoinc;
    private String psaLoinc;
  }

  @Data
  public static class FhirUrl {
    private String tnmPraefix;
  }
}
