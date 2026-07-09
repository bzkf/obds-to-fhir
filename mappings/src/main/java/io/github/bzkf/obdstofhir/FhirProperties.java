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
  private FhirProfiles profiles;
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
    private String miiExOnkoOPIntention;
    private String miiExOnkoStrahlentherapieIntention;
    private String miiExOnkoStrahlentherapieStellungzurop;
    private String miiExOnkoStrahlentherapieBestrahlung;
    private String miiExOnkoHistologyMorphologyBehaviorIcdo3;
    private String miiExOnkoSystemischeTherapieIntention;
    private String miiExOnkoSystemischeTherapieStellungzurop;
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
    private String miiCsVitalStatus;
  }

  @Data
  public static class FhirProfiles {
    private String miiPatientPseudonymisiert;
    private String miiVitalStatus;

    private String miiPrMedicationStatement;
    private String miiPrMedication;

    private String miiPrOnkoDiagnosePrimaertumor;
    private String miiPrOnkoOperation;
    private String miiPrOnkoStrahlentherapie;
    private String miiPrOnkoSystemischeTherapie;
    private String miiPrOnkoSystemischeTherapieMedikation;
    private String miiPrOnkoFernmetastasen;
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
}
