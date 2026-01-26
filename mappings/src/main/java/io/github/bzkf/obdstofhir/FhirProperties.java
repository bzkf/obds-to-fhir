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
  private FhirDisplay display;
  private FhirProfiles profiles;
  private FhirUrl url;
  private Codings codings;

  public static record Codings(Coding loinc, Coding snomed, Coding ops) {
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
  }

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
    private String miiExOnkoStrahlentherapieStellungzurop;
    private String miiExOnkoStrahlentherapieBestrahlung;
    private String miiExOnkoHistologyMorphologyBehaviorIcdo3;
    private String miiExOnkoSystemischeTherapieIntention;
    private String miiExOnkoStrahlentherapieBestrahlungEinzeldosis;
    private String miiExOnkoStrahlentherapieBestrahlungGesamtdosis;
    private String miiExOnkoStrahlentherapieBestrahlungBoost;
    private String miiExOnkoStrahlentherapieBestrahlungSeitenlokalisation;
    private String miiExOnkoTnmItcSuffix;
    private String miiExOnkoTnmSnSuffix;
    private String miiExOnkoTnmCpPraefix;
    private String conditionAssertedDate;
    private String ordinalValue;
    private String conditionOccurredFollowing;
    private String procedureMethod;
  }

  @Data
  public static class FhirIdentifierSystems {
    // local systems
    private String patientId;
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
    private String histologiebefundDiagnosticReportId;
    private String gradingObservationId;
    private String verlaufObservationId;
    private String todObservationId;
    private String nebenwirkungAdverseEventId;
    private String fruehereTumorerkrankungConditionId;
    private String prostataPsaObservationId;
    private String prostataAnzahlStanzenObservationId;
    private String prostataAnzahlPositiveStanzenObservationId;
    private String prostataCaBefallStanzeObservationId;
    private String prostataClavienDindoObservationId;
    private String prostataGleasonPatternsObservationId;
    private String prostataGleasonScoreObservationId;
  }

  @Data
  public static class FhirSystems {
    private FhirIdentifierSystems identifiers;

    private String observationId;
    private String procedureId;
    private String operationProcedureId;
    private String medicationStatementId;
    private String psaObservationId;

    private String diagnosticServiceSectionId;

    private String identifierType;
    private String observationCategorySystem;
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
    private String ucum;
    private String miiCsOnkoIntention;
    private String miiCsOnkoPrimaertumorDiagnosesicherung;
    private String miiCsOnkoStrahlentherapieApplikationsart;
    private String miiCsOnkoStrahlentherapieStrahlenart;
    private String miiCsOnkoStrahlentherapieZielgebiet;
    private String miiCsOnkoTherapieStellungzurop;
    private String miiCsOnkoStrahlentherapieBoost;
    private String miiCsOnkoOperationResidualstatus;
    private String miiCsOnkoSystemischeTherapieArt;
    private String miiCsOnkoSeitenlokalisation;
    private String miiCsOnkoResidualstatus;
    private String miiCsOnkoTherapieEndeGrund;
    private String miiCsOnkoTodInterpretation;
    private String conditionVerStatus;
    private String icdo3MorphologieOid;
    private String atcBfarm;
    private String atcWho;
    private String observationCategory;
    private String miiCsOnkoStudienteilnahme;
    private String miiCsOnkoGrading;
    private String miiCsOnkoTherapieplanungTyp;
    private String miiCsOnkoVerlaufPrimaertumor;
    private String miiCsOnkoVerlaufLymphknoten;
    private String miiCsOnkoVerlaufFernmetastasen;
    private String miiCsOnkoVerlaufGesamtbeurteilung;
    private String miiCsOnkoFernmetastasen;
    private String miiCsOnkoAllgemeinerLeistungszustandEcog;
    private String miiCsOnkoGenetischeVarianteAuspraegung;
    private String miiCsOnkoNebenwirkungCtcaeGrad;
    private String meddra;
    private String miiCsOnkoTherapieTyp;
    private String miiCsOnkoTherapieabweichung;
    private String miiCsOnkoTnmVersion;
    private String tnmUicc;
    private String miiCsOnkoProstataPostsurgicalComplications;
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
    private String miiPrOnkoSystemischeTherapieMedikation;
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
    private String miiPrOnkoStudienteilnahme;
    private String miiPrOnkoGenetischeVariante;
    private String miiPrOnkoNebenwirkungAdverseEvent;
    private String miiPrOnkoVerlauf;
    private String miiPrOnkoTumorkonferenz;
    private String miiPrOnkoWeitereKlassifikationen;
    private String miiPrOnkoTnmKlassifikation;
    private String miiPrOnkoTnmTKategorie;
    private String miiPrOnkoTnmNKategorie;
    private String miiPrOnkoTnmMKategorie;
    private String miiPrOnkoTnmASymbol;
    private String miiPrOnkoTnmMSymbol;
    private String miiPrOnkoTnmLKategorie;
    private String miiPrOnkoTnmPnKategorie;
    private String miiPrOnkoTnmRSymbol;
    private String miiPrOnkoTnmSKategorie;
    private String miiPrOnkoTnmVKategorie;
    private String miiPrOnkoTnmYSymbol;
    private String miiPrOnkoListeEvidenzErstdiagnose;
    private String miiPrOnkoStrahlentherapieBestrahlungStrahlentherapie;
    private String miiPrOnkoStrahlentherapieBestrahlungNuklearmedizin;
    private String miiPrOnkoFruehereTumorerkrankung;
    private String miiPrOnkoProstatePsa;
    private String miiPrOnkoProstateAnzahlStanzen;
    private String miiPrOnkoProstateAnzahlPositiveStanzen;
    private String miiPrOnkoProstateCaBefallStanze;
    private String miiPrOnkoProstateClavienDindo;
    private String miiPrOnkoProstateGleasonPatterns;
    private String miiPrOnkoProstateGleasonGradeGroup;
    private String miiPrOnkoResidualstatus;
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
