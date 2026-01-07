# Configuration

<!-- Update this file by running helm-docs --chart-search-root src/main/resources/ -f application.yml -f ../../../mappings/src/main/resources/application-mappings.yml -t config.md.gotmpl -o config.md && mv src/main/resources/config.md docs/config.md -->

## FHIR systems

See [application-mappings.yml](../mappings/src/main/resources/application-mappings.yml) for FHIR systems configuration settings.

## Environment Variables

The config values below can be set as environment variables by replacing all `.` and `-` with `_`
and optionally upper-casing the value. E.g. to change the default system for the Specimen.id, change:

`fhir.systems.identifiers.histologie-specimen-id`

to

`FHIR_SYSTEMS_IDENTIFIERS_HISTOLOGIE_SPECIMEN_ID`

and pass it as an environment variable to the container.

## Configuration file

You can also load extra configuration files. See the [official Spring Boot docs](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files) for details.

## Patient Reference Generation

By default, the job creates FHIR Patient resources which are referenced by each resource.
If you already create Patient resources from a different source, you may want to set
`FHIR_MAPPINGS_CREATE_PATIENT_RESOURCES_ENABLED=false` and specify a Reference generation
strategy that allows for creating matching logical IDs to your existing Patient resources:

Set `FHIR_MAPPINGS_PATIENT_REFERENCE_GENERATION_STRATEGY` to one of the values below:

- `SHA256_HASHED_PATIENT_IDENTIFIER_SYSTEM_AND_PATIENT_ID` (default)

    The reference is computed as the SHA-256 hash of `fhir.systems.identifiers.patient-id` + "|" + Patient_ID.
- `MD5_HASHED_PATIENT_ID`

    The reference is computed as the MD5 hash of Patient_ID.
- `PATIENT_ID_UNDERSCORES_REPLACED_WITH_DASHES`

    Patient_ID and any occurrence of `_` is replaced by `-`.
- `PATIENT_ID`

    Patient_ID is taken as-is.
- `FHIR_SERVER_LOOKUP`

    Query a FHIR server to get the logical ID of the Patient resource from the identifier value.
    This sends a `GET /Patient?identifier=<fhir.systems.identifiers.patient-id>|<Patient_ID>` query
    to the server specified in `patient-reference-generation.fhir-server.base-url` and returns the
    `Patient.id` in the response. If the result is empty or more than one Patient resource is found,
    the job exits with an error.

- `RECORD_ID_DATABASE_LOOKUP`

    Query a database table to lookup an internal ID from the given Patient_ID. The database connection
    and SQL query to run can be configured in the `patient-reference-generation.record-id-database` settings.

## All Available Settings

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| fhir.codings.loinc.system | string | `"${fhir.systems.loinc}"` |  |
| fhir.codings.loinc.version | string | `"2.80"` |  |
| fhir.codings.ops.system | string | `"${fhir.systems.ops}"` |  |
| fhir.codings.ops.version | string | `"2025"` |  |
| fhir.codings.snomed.system | string | `"${fhir.systems.snomed}"` |  |
| fhir.codings.snomed.version | string | `"http://snomed.info/sct/900000000000207008/version/20250501"` |  |
| fhir.display.deathLoinc | string | `"Primary cause of death"` |  |
| fhir.display.fernMetaLoinc | string | `"Distant metastases.clinical"` |  |
| fhir.display.gleasonScoreLoinc | string | `"Gleason score in Specimen Qualitative"` |  |
| fhir.display.gleasonScoreSct | string | `"Gleason score (observable entity)"` |  |
| fhir.display.gradingLoinc | string | `"Grade pathology value Cancer"` |  |
| fhir.display.histologyLoinc | string | `"Histology and Behavior ICD-O-3 Cancer"` |  |
| fhir.display.psaLoinc | string | `"Prostate specific Ag [Mass/volume] in Serum or Plasma"` |  |
| fhir.display.tnmcLoinc | string | `"Stage group.clinical Cancer"` |  |
| fhir.display.tnmpLoinc | string | `"Stage group.pathology Cancer"` |  |
| fhir.extensions.condition-asserted-date | string | `"http://hl7.org/fhir/StructureDefinition/condition-assertedDate"` |  |
| fhir.extensions.condition-occurred-following | string | `"http://hl7.org/fhir/StructureDefinition/condition-occurredFollowing"` |  |
| fhir.extensions.dataAbsentReason | string | `"http://hl7.org/fhir/StructureDefinition/data-absent-reason"` |  |
| fhir.extensions.fernMetaExt | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Extension-Fernmetastasen"` |  |
| fhir.extensions.genderAmtlich | string | `"http://fhir.de/StructureDefinition/gender-amtlich-de"` |  |
| fhir.extensions.mii-ex-onko-histology-morphology-behavior-icdo3 | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-ex-onko-histology-morphology-behavior-icdo3"` |  |
| fhir.extensions.mii-ex-onko-op-intention | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-ex-onko-operation-intention"` |  |
| fhir.extensions.mii-ex-onko-strahlentherapie-bestrahlung | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-ex-onko-strahlentherapie-bestrahlung"` |  |
| fhir.extensions.mii-ex-onko-strahlentherapie-bestrahlung-boost | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-ex-onko-strahlentherapie-bestrahlung-boost"` |  |
| fhir.extensions.mii-ex-onko-strahlentherapie-bestrahlung-einzeldosis | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-ex-onko-strahlentherapie-bestrahlung-einzeldosis"` |  |
| fhir.extensions.mii-ex-onko-strahlentherapie-bestrahlung-gesamtdosis | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-ex-onko-strahlentherapie-bestrahlung-gesamtdosis"` |  |
| fhir.extensions.mii-ex-onko-strahlentherapie-bestrahlung-seitenlokalisation | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-ex-onko-strahlentherapie-bestrahlung-seitenlokalisation"` |  |
| fhir.extensions.mii-ex-onko-strahlentherapie-intention | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-ex-onko-strahlentherapie-intention"` |  |
| fhir.extensions.mii-ex-onko-strahlentherapie-stellungzurop | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-ex-onko-strahlentherapie-stellungzurop"` |  |
| fhir.extensions.mii-ex-onko-systemische-therapie-intention | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-ex-onko-systemische-therapie-intention"` |  |
| fhir.extensions.mii-ex-onko-tnm-cp-praefix | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-ex-onko-tnm-cp-praefix${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.extensions.mii-ex-onko-tnm-itc-suffix | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-ex-onko-tnm-itc-suffix${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.extensions.mii-ex-onko-tnm-sn-suffix | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-ex-onko-tnm-sn-suffix${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.extensions.opIntention | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Extension-OPIntention"` |  |
| fhir.extensions.ordinal-value | string | `"http://hl7.org/fhir/StructureDefinition/ordinalValue"` |  |
| fhir.extensions.stellungOP | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Extension-StellungZurOp"` |  |
| fhir.extensions.sysTheraProto | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Extension-SystemischeTherapieProtokoll"` |  |
| fhir.extensions.systIntention | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Extension-SYSTIntention"` |  |
| fhir.mappings.create-patient-resources.enabled | bool | `true` | Whether Patient resources should be created. Useful to disable if you already create FHIR resources from a different source. |
| fhir.mappings.meta.source | string | `""` | Value to set for the meta.source field in all generated resources |
| fhir.mappings.substanz-to-atc.extra-mappings-file-path | string | `""` | path to a CSV file containing additional Substanz -> ATC code mappings. The CSV file needs to have two headings: `Substanzbezeichnung;ATC-Code`, all columns must be seperated by `;`. The job always ships with the mappings from <https://plattform65c.atlassian.net/wiki/spaces/UMK/pages/15532506/Substanzen>, if the extra file contains duplicate mappings to the default ones, the ones from this file take precedence. |
| fhir.profiles.condition | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Condition-Primaerdiagnose"` |  |
| fhir.profiles.deathObservation | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Observation-TodUrsache"` |  |
| fhir.profiles.fernMeta | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Observation-Fernmetastasen"` |  |
| fhir.profiles.genVariante | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Observation-GenetischeVariante"` |  |
| fhir.profiles.grading | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Observation-Grading"` |  |
| fhir.profiles.histologie | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Observation-Histologie"` |  |
| fhir.profiles.mii-patient-pseudonymisiert | string | `"https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert${fhir.profiles.versions.mii-person}"` |  |
| fhir.profiles.mii-pr-medication-statement | string | `"https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/MedicationStatement${fhir.profiles.versions.mii-medikation}"` |  |
| fhir.profiles.mii-pr-onko-allgemeiner-leistungszustand-ecog | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-allgemeiner-leistungszustand-ecog${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-anzahl-befallene-lymphknoten | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-anzahl-befallene-lymphknoten${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-anzahl-befallene-sentinel-lymphknoten | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-anzahl-befallene-sentinel-lymphknoten${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-anzahl-untersuchte-lymphknoten | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-anzahl-untersuchte-lymphknoten${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-anzahl-untersuchte-sentinel-lymphknoten | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-anzahl-untersuchte-sentinel-lymphknoten${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-befund | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-befund${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-diagnose-primaertumor | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-diagnose-primaertumor${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-fernmetastasen | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-fernmetastasen${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-fruehere-tumorerkrankung | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-fruehere-tumorerkrankung${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-genetische-variante | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-genetische-variante${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-grading | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-grading${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-histologie-icdo3 | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-histologie-icdo3${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-liste-evidenz-erstdiagnose | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-liste-evidenz-erstdiagnose${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-nebenwirkung-adverse-event | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-nebenwirkung-adverse-event${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-operation | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-operation${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-prostate-anzahl-positive-stanzen | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-prostate-anzahl-positive-stanzen"` |  |
| fhir.profiles.mii-pr-onko-prostate-anzahl-stanzen | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-prostate-anzahl-stanzen"` |  |
| fhir.profiles.mii-pr-onko-prostate-ca-befall-stanze | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-prostate-ca-befall-stanze"` |  |
| fhir.profiles.mii-pr-onko-prostate-clavien-dindo | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-prostate-clavien-dindo"` |  |
| fhir.profiles.mii-pr-onko-prostate-gleason-grade-group | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-prostate-gleason-grade-group"` |  |
| fhir.profiles.mii-pr-onko-prostate-gleason-patterns | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-prostate-gleason-patterns"` |  |
| fhir.profiles.mii-pr-onko-prostate-psa | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-prostate-psa"` |  |
| fhir.profiles.mii-pr-onko-specimen | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-specimen${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-strahlentherapie | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-strahlentherapie${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-strahlentherapie-bestrahlung-nuklearmedizin | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-strahlentherapie-bestrahlung-nuklearmedizin${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-strahlentherapie-bestrahlung-strahlentherapie | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-strahlentherapie-bestrahlung-strahlentherapie${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-studienteilnahme | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-studienteilnahme${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-systemische-therapie | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-systemische-therapie${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-systemische-therapie-medikation | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-systemische-therapie-medikation${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-tnm-a-symbol | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tnm-a-symbol${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-tnm-klassifikation | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tnm-klassifikation${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-tnm-l-kategorie | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tnm-l-kategorie${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-tnm-m-kategorie | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tnm-m-kategorie${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-tnm-m-symbol | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tnm-m-symbol${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-tnm-n-kategorie | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tnm-n-kategorie${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-tnm-pn-kategorie | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tnm-pn-kategorie${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-tnm-r-symbol | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tnm-r-symbol${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-tnm-s-kategorie | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tnm-s-kategorie${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-tnm-t-kategorie | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tnm-t-kategorie${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-tnm-v-kategorie | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tnm-v-kategorie${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-tnm-y-symbol | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tnm-y-symbol${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-tod | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tod${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-tumorkonferenz | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tumorkonferenz${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-verlauf | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-verlauf${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.mii-pr-onko-weitere-klassifikationen | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-weitere-klassifikationen${fhir.profiles.versions.mii-onkologie}"` |  |
| fhir.profiles.opProcedure | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Procedure-Operation"` |  |
| fhir.profiles.stProcedure | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Procedure-Strahlentherapie"` |  |
| fhir.profiles.systMedStatement | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-MedicationStatement-Systemtherapie"` |  |
| fhir.profiles.tnmC | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Observation-TNMc"` |  |
| fhir.profiles.tnmP | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Observation-TNMp"` |  |
| fhir.systems.adtSeitenlokalisation | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/SeitenlokalisationCS"` |  |
| fhir.systems.atcBfarm | string | `"http://fhir.de/CodeSystem/bfarm/atc"` |  |
| fhir.systems.atcWho | string | `"http://www.whocc.no/atc"` |  |
| fhir.systems.condition-ver-status | string | `"http://terminology.hl7.org/CodeSystem/condition-ver-status"` |  |
| fhir.systems.ctcaeGrading | string | `"http://hl7.org/fhir/us/ctcae/CodeSystem/ctcae-grade-code-system"` |  |
| fhir.systems.diagnostic-service-section-id | string | `"http://terminology.hl7.org/CodeSystem/v2-0074"` |  |
| fhir.systems.fMLokalisationCS | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/FMLokalisationCS"` |  |
| fhir.systems.genderAmtlichDe | string | `"http://fhir.de/CodeSystem/gender-amtlich-de"` |  |
| fhir.systems.gesamtBeurtResidualCS | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/GesamtbeurteilungResidualstatusCS"` |  |
| fhir.systems.gradingDktk | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/GradingCS"` |  |
| fhir.systems.icd10gm | string | `"http://fhir.de/CodeSystem/bfarm/icd-10-gm"` |  |
| fhir.systems.icdo3Morphologie | string | `"http://terminology.hl7.org/CodeSystem/icd-o-3"` |  |
| fhir.systems.icdo3MorphologieOid | string | `"urn:oid:2.16.840.1.113883.6.43.1"` |  |
| fhir.systems.identifier-type | string | `"http://terminology.hl7.org/CodeSystem/v2-0203"` |  |
| fhir.systems.identifiers.allgemeiner-leistungszustand-ecog-observation-id | string | `"${fhir.systems.identifiers.base-url}/allgemeiner-leistungszustand-ecog-id"` |  |
| fhir.systems.identifiers.base-url | string | `"https://bzkf.github.io/obds-to-fhir/identifiers"` |  |
| fhir.systems.identifiers.erstdiagnose-evidenz-list-id | string | `"${fhir.systems.identifiers.base-url}/erstdiagnose-evidenz-list-id"` |  |
| fhir.systems.identifiers.fernmetastasen-observation-id | string | `"${fhir.systems.identifiers.base-url}/fernmetastasen-id"` |  |
| fhir.systems.identifiers.fruehere-tumorerkrankung-condition-id | string | `"${fhir.systems.identifiers.base-url}/fruehere-tumorerkrankung-condition-id"` |  |
| fhir.systems.identifiers.genetische-variante-observation-id | string | `"${fhir.systems.identifiers.base-url}/genetische-variante-observation-id"` |  |
| fhir.systems.identifiers.grading-observation-id | string | `"${fhir.systems.identifiers.base-url}/grading-observation-id"` |  |
| fhir.systems.identifiers.histologie-specimen-id | string | `"${fhir.systems.identifiers.base-url}/histologie-specimen-id"` |  |
| fhir.systems.identifiers.histologiebefund-diagnostic-report-id | string | `"${fhir.systems.identifiers.base-url}/histologiebefund-diagnostic-report-id"` |  |
| fhir.systems.identifiers.lymphknotenuntersuchung-observation-id | string | `"${fhir.systems.identifiers.base-url}/lymphknotenuntersuchung-observation-id"` |  |
| fhir.systems.identifiers.nebenwirkung-adverse-event-id | string | `"${fhir.systems.identifiers.base-url}/nebenwirkung-adverse-event-id"` |  |
| fhir.systems.identifiers.patient-id | string | `"${fhir.systems.identifiers.base-url}/patient-id"` |  |
| fhir.systems.identifiers.primaerdiagnose-condition-id | string | `"${fhir.systems.identifiers.base-url}/primaerdiagnose-condition-id"` |  |
| fhir.systems.identifiers.prostata-anzahl-positive-stanzen-observation-id | string | `"${fhir.systems.identifiers.base-url}/prostata-anzahl-positive-stanzen-observation-id"` |  |
| fhir.systems.identifiers.prostata-anzahl-stanzen-observation-id | string | `"${fhir.systems.identifiers.base-url}/prostata-anzahl-stanzen-observation-id"` |  |
| fhir.systems.identifiers.prostata-ca-befall-stanze-observation-id | string | `"${fhir.systems.identifiers.base-url}/prostata-ca-befall-stanze-observation-id"` |  |
| fhir.systems.identifiers.prostata-clavien-dindo-observation-id | string | `"${fhir.systems.identifiers.base-url}/prostata-clavien-dindo-observation-id"` |  |
| fhir.systems.identifiers.prostata-gleason-patterns-observation-id | string | `"${fhir.systems.identifiers.base-url}/prostata-gleason-patterns-observation-id"` |  |
| fhir.systems.identifiers.prostata-gleason-score-observation-id | string | `"${fhir.systems.identifiers.base-url}/prostata-gleason-score-observation-id"` |  |
| fhir.systems.identifiers.prostata-psa-observation-id | string | `"${fhir.systems.identifiers.base-url}/prostata-psa-observation-id"` |  |
| fhir.systems.identifiers.residualstatus-observation-id | string | `"${fhir.systems.identifiers.base-url}/residualstatus-observation-id"` |  |
| fhir.systems.identifiers.strahlentherapie-bestrahlung-procedure-id | string | `"${fhir.systems.identifiers.base-url}/strahlentherapie-bestrahlung-procedure-id"` |  |
| fhir.systems.identifiers.strahlentherapie-procedure-id | string | `"${fhir.systems.identifiers.base-url}/strahlentherapie-procedure-id"` |  |
| fhir.systems.identifiers.studienteilnahme-observation-id | string | `"${fhir.systems.identifiers.base-url}/studienteilnahme-observation-id"` |  |
| fhir.systems.identifiers.systemische-therapie-medication-statement-id | string | `"${fhir.systems.identifiers.base-url}/systemische-therapie-medication-statement-id"` |  |
| fhir.systems.identifiers.systemische-therapie-procedure-id | string | `"${fhir.systems.identifiers.base-url}/systemische-therapie-procedure-id"` |  |
| fhir.systems.identifiers.tnm-a-symbol-observation-id | string | `"${fhir.systems.identifiers.base-url}/tnm-a-symbol-observation-id"` |  |
| fhir.systems.identifiers.tnm-grouping-observation-id | string | `"${fhir.systems.identifiers.base-url}/tnm-grouping-observation-id"` |  |
| fhir.systems.identifiers.tnm-l-kategorie-observation-id | string | `"${fhir.systems.identifiers.base-url}/tnm-l-kategorie-observation-id"` |  |
| fhir.systems.identifiers.tnm-m-kategorie-observation-id | string | `"${fhir.systems.identifiers.base-url}/tnm-m-kategorie-observation-id"` |  |
| fhir.systems.identifiers.tnm-m-symbol-observation-id | string | `"${fhir.systems.identifiers.base-url}/tnm-m-symbol-observation-id"` |  |
| fhir.systems.identifiers.tnm-n-kategorie-observation-id | string | `"${fhir.systems.identifiers.base-url}/tnm-n-kategorie-observation-id"` |  |
| fhir.systems.identifiers.tnm-pn-kategorie-observation-id | string | `"${fhir.systems.identifiers.base-url}/tnm-pn-kategorie-observation-id"` |  |
| fhir.systems.identifiers.tnm-r-symbol-observation-id | string | `"${fhir.systems.identifiers.base-url}/tnm-r-symbol-observation-id"` |  |
| fhir.systems.identifiers.tnm-s-kategorie-observation-id | string | `"${fhir.systems.identifiers.base-url}/tnm-s-kategorie-observation-id"` |  |
| fhir.systems.identifiers.tnm-t-kategorie-observation-id | string | `"${fhir.systems.identifiers.base-url}/tnm-t-kategorie-observation-id"` |  |
| fhir.systems.identifiers.tnm-v-kategorie-observation-id | string | `"${fhir.systems.identifiers.base-url}/tnm-v-kategorie-observation-id"` |  |
| fhir.systems.identifiers.tnm-y-symbol-observation-id | string | `"${fhir.systems.identifiers.base-url}/tnm-y-symbol-observation-id"` |  |
| fhir.systems.identifiers.tod-observation-id | string | `"${fhir.systems.identifiers.base-url}/tod-observation-id"` |  |
| fhir.systems.identifiers.tumorkonferenz-care-plan-id | string | `"${fhir.systems.identifiers.base-url}/tumorkonferenz-care-plan-id"` |  |
| fhir.systems.identifiers.verlauf-observation-id | string | `"${fhir.systems.identifiers.base-url}/verlauf-observation-id"` |  |
| fhir.systems.identifiers.verlaufshistologie-observation-id | string | `"${fhir.systems.identifiers.base-url}/verlaufshistologie-observation-id"` |  |
| fhir.systems.identifiers.weitere-klassifikation-observation-id | string | `"${fhir.systems.identifiers.base-url}/weitere-klassifikation-observation-id"` |  |
| fhir.systems.jnuCs | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/JNUCS"` |  |
| fhir.systems.loinc | string | `"http://loinc.org"` |  |
| fhir.systems.lokalBeurtResidualCS | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/LokaleBeurteilungResidualstatusCS"` |  |
| fhir.systems.meddra | string | `"https://www.meddra.org"` |  |
| fhir.systems.medicationStatementId | string | `"${fhir.systems.identifiers.base-url}/medication-statement-id"` |  |
| fhir.systems.mii-cs-onko-allgemeiner-leistungszustand-ecog | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-allgemeiner-leistungszustand-ecog"` |  |
| fhir.systems.mii-cs-onko-fernmetastasen | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-fernmetastasen"` |  |
| fhir.systems.mii-cs-onko-genetische-variante-auspraegung | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-genetische-variante-auspraegung"` |  |
| fhir.systems.mii-cs-onko-grading | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-grading"` |  |
| fhir.systems.mii-cs-onko-intention | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-intention"` |  |
| fhir.systems.mii-cs-onko-nebenwirkung-ctcae-grad | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-nebenwirkung-ctcae-grad"` |  |
| fhir.systems.mii-cs-onko-operation-residualstatus | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-residualstatus"` |  |
| fhir.systems.mii-cs-onko-primaertumor-diagnosesicherung | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-primaertumor-diagnosesicherung"` |  |
| fhir.systems.mii-cs-onko-prostata-postsurgical-complications | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-prostata-postsurgical-complications"` |  |
| fhir.systems.mii-cs-onko-residualstatus | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-residualstatus"` |  |
| fhir.systems.mii-cs-onko-seitenlokalisation | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-seitenlokalisation"` |  |
| fhir.systems.mii-cs-onko-strahlentherapie-applikationsart | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-strahlentherapie-applikationsart"` |  |
| fhir.systems.mii-cs-onko-strahlentherapie-boost | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-strahlentherapie-boost"` |  |
| fhir.systems.mii-cs-onko-strahlentherapie-strahlenart | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-strahlentherapie-strahlenart"` |  |
| fhir.systems.mii-cs-onko-strahlentherapie-zielgebiet | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-strahlentherapie-zielgebiet"` |  |
| fhir.systems.mii-cs-onko-studienteilnahme | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-studienteilnahme"` |  |
| fhir.systems.mii-cs-onko-systemische-therapie-art | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-therapie-typ"` |  |
| fhir.systems.mii-cs-onko-therapie-ende-grund | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-therapie-ende-grund"` |  |
| fhir.systems.mii-cs-onko-therapie-stellungzurop | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-therapie-stellungzurop"` |  |
| fhir.systems.mii-cs-onko-therapie-typ | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-therapie-typ"` |  |
| fhir.systems.mii-cs-onko-therapieabweichung | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-therapieabweichung"` |  |
| fhir.systems.mii-cs-onko-therapieplanung-typ | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-therapieplanung-typ"` |  |
| fhir.systems.mii-cs-onko-tnm-version | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-tnm-version"` |  |
| fhir.systems.mii-cs-onko-tod-interpretation | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-tod"` |  |
| fhir.systems.mii-cs-onko-verlauf-fernmetastasen | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-verlauf-fernmetastasen"` |  |
| fhir.systems.mii-cs-onko-verlauf-gesamtbeurteilung | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-verlauf-gesamtbeurteilung"` |  |
| fhir.systems.mii-cs-onko-verlauf-lymphknoten | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-verlauf-lymphknoten"` |  |
| fhir.systems.mii-cs-onko-verlauf-primaertumor | string | `"https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-verlauf-primaertumor"` |  |
| fhir.systems.observation-value | string | `"http://terminology.hl7.org/CodeSystem/v3-ObservationValue"` |  |
| fhir.systems.observationCategory | string | `"http://terminology.hl7.org/CodeSystem/observation-category"` |  |
| fhir.systems.observationCategorySystem | string | `"http://hl7.org/fhir/observation-category"` |  |
| fhir.systems.observationId | string | `"${fhir.systems.identifiers.base-url}/observation-id"` |  |
| fhir.systems.opComplication | string | `"urn:oid:1.2.276.0.76.3.1.131.1.5.122"` |  |
| fhir.systems.opIntention | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/OPIntentionCS"` |  |
| fhir.systems.operationProcedureId | string | `"${fhir.systems.identifiers.base-url}/operation-procedure-id"` |  |
| fhir.systems.ops | string | `"http://fhir.de/CodeSystem/bfarm/ops"` |  |
| fhir.systems.procedureId | string | `"${fhir.systems.identifiers.base-url}/procedure-id"` |  |
| fhir.systems.psaObservationId | string | `"${fhir.systems.identifiers.base-url}/psa-observation-id"` |  |
| fhir.systems.sideEffectTypeOid | string | `"urn:oid:1.2.276.0.76.3.1.131.1.5.20"` |  |
| fhir.systems.snomed | string | `"http://snomed.info/sct"` |  |
| fhir.systems.systIntention | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/SYSTIntentionCS"` |  |
| fhir.systems.systStellungOP | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/SYSTStellungOPCS"` |  |
| fhir.systems.systTherapieart | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/SYSTTherapieartCS"` |  |
| fhir.systems.tnm-uicc | string | `"https://www.uicc.org/resources/tnm"` |  |
| fhir.systems.tnmMCs | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/TNMMCS"` |  |
| fhir.systems.tnmMSymbolCs | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/TNMmSymbolCS"` |  |
| fhir.systems.tnmNCs | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/TNMNCS"` |  |
| fhir.systems.tnmPraefix | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/TNMcpuPraefixTCS"` |  |
| fhir.systems.tnmRSymbolCs | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/TNMrSymbolCS"` |  |
| fhir.systems.tnmTCs | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/TNMTCS"` |  |
| fhir.systems.tnmYSymbolCs | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/TNMySymbolCS"` |  |
| fhir.systems.ucum | string | `"http://unitsofmeasure.org"` |  |
| fhir.systems.uicc | string | `"http://dktk.dkfz.de/fhir/onco/core/CodeSystem/UiccstadiumCS"` |  |
| fhir.url.tnmPraefix | string | `"http://dktk.dkfz.de/fhir/StructureDefinition/onco-core-Extension-TNMcpuPraefix"` |  |
