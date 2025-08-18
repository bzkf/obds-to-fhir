import sys
import config
import great_expectations as gx
# import pandas as pd
from great_expectations.checkpoint import UpdateDataDocsAction
from great_expectations.core.result_format import ResultFormat
from loguru import logger
from pathling import Expression as exp
from pathling import PathlingContext
from pyspark.sql.functions import col, concat, lit
import expectations

pc = PathlingContext.create(enable_extensions=True, enable_delta=True)

date_columns = [
    "date_of_birth",
    "deceased_date_time",
    "asserted_date"
]

pc.spark.sparkContext.setCheckpointDir(f"file:///{config.checkpoint_path}")

data = pc.read.bundles(
    config.snapshots_dir, ["Patient", "Condition", "Observation", "Procedure", "MedicationStatement"]
)


data.read('Patient').cache()
patients = data.extract(
    "Patient",
    columns=[
        exp("id", "patient_id"),
        exp("gender", "gender"),
        exp("birthDate", "date_of_birth"),
        exp("deceasedDateTime", "deceased_date_time"),
    ],
).drop_duplicates()
patients = patients.checkpoint(eager=True)
patients.show(truncate=False)

data.read('Condition').cache()
conditions = data.extract(
    "Condition",
    columns=[
        exp("id", "condition_id"),
        exp(
            f"code.coding.where(system='{config.ICD_10_GM_SYSTEM}').code",
            "icd_code",
        ),
        exp(
            f"code.coding.where(system='{config.ICD_10_GM_SYSTEM}').version",
            "icd_version",
        ),
        exp("subject.reference", "subject_reference"),
        exp(
            f"extension('{config.ASSERTED_DATE_EXTENSION}').valueDateTime",
            "asserted_date",
        ),
        exp("recordedDate", "recorded_date"),
    ],
).drop_duplicates()
conditions = conditions.checkpoint(eager=True)
# (full) outer join to retain null values if either side is missing
patients_with_conditions = conditions.join(
    patients,
    conditions["subject_reference"] == concat(lit("Patient/"), col("patient_id")),
    how="outer",
)

patients_with_conditions.show(truncate=False)

data.read('Observation').cache()
observations = data.extract(
    "Observation",
    columns=[
        exp("id", "observation_id"),
        exp("subject.reference", "subject_reference"),
        exp("code.coding.system", "code_system"),
        exp("code.coding.code", "code_code"),
        exp("""Observation.where(
                    code.coding.exists(system='http://loinc.org' or
                        system='http://snomed.info/sct')).valueCodeableConcept.coding.code""",
            "value_codeable_concept_coding_code",
        ),
        exp("effectiveDateTime", "effective_date_time"),
        exp("meta.profile", "meta_profile"),
    ],
).drop_duplicates()
observations = observations.checkpoint(eager=True)
patients_with_observations = observations.join(
    patients,
    observations["subject_reference"] == concat(lit("Patient/"), col("patient_id")),
    how="outer",
)

patients_with_observations.show(truncate=False)

patients_with_observations.write.mode("overwrite").csv(
    "patients_with_observations.csv", header=True
)

data.read('Procedure').cache()
procedures = data.extract(
    "Procedure",
    columns=[
        exp("id", "procedure_id"),
        exp(f"code.coding.where(system='{config.OPS_SYSTEM}').system", "code_system,"),
        exp(f"code.coding.where(system='{config.OPS_SYSTEM}').version", "code_system_version"),
        exp(f"code.coding.where(system='{config.OPS_SYSTEM}').code", "code_code"),
        exp("subject.reference", "subject_reference"),
        exp("performedDateTime", "performed_date_time"),
        exp("meta.profile", "meta_profile")
    ]
).drop_duplicates()
print(procedures.columns)
procedures = procedures.checkpoint(eager=True)
patients_with_procedures = procedures.join(
    patients,
    procedures["subject_reference"] == concat(lit("Patient/"), col("patient_id")),
    how="inner",
)

patients_with_procedures.show(truncate=False)
patients_with_procedures.write.mode("overwrite").csv(
    "patients_with_procedures.csv", header=True
)

data.read('MedicationStatement').cache()
medicationStatements = data.extract(
     "MedicationStatement",
     columns=[
         exp("id", "MedicationStatement_id"),

         exp("subject.reference", "subject_reference"),
         exp("effectivePeriod.start", "effectivePeriod_start"),
         exp("effectivePeriod.end", "effectivePeriod_end"),
         exp( "meta.profile", "meta_profile")
     ]
 ).drop_duplicates()
medicationStatements = medicationStatements.checkpoint(eager=True)
print(medicationStatements.columns)

patients_with_medications = medicationStatements.join(
    patients,
    medicationStatements["subject_reference"] == concat(lit("Patient/"), col("patient_id")),
    how="inner",
    )

patients_with_medications.show(truncate=False)
patients_with_medications.write.mode("overwrite").csv(
    "patients_with_medications.csv", header=True
)

df_merged = patients_with_conditions.merge(patients_with_procedures, on="subject_reference", how="left")
df_merged.show(truncate=False)

gx_context = gx.get_context(mode="file")


def create_expectations_and_suite(expectations, suite_name, gx_context):
    suite = gx.ExpectationSuite(name=suite_name)
    for expectation in expectations:
        suite.add_expectation(expectation)
    suite = gx_context.suites.add_or_update(suite=suite)
    return suite


def create_validation_definition(gx_context, data_asset, suite, name, batch_name):
    return gx_context.validation_definitions.add_or_update(
        gx.ValidationDefinition(
            name=name,
            data=data_asset.add_batch_definition_whole_dataframe(batch_name),
            suite=suite,
        )
    )


def create_checkpoint(gx_context, name, validation_definitions, action_list):
    return gx_context.checkpoints.add_or_update(
        gx.Checkpoint(
            name=name,
            validation_definitions=validation_definitions,
            actions=action_list,
            result_format=ResultFormat.COMPLETE,
        )
    )


# Create expectation suites
suite_conditions = create_expectations_and_suite(
    expectations.expectations_conditions, "patients_with_conditions_suite", gx_context
)
suite_observations = create_expectations_and_suite(
    expectations.expectations_observations, "patients_with_observations_suite", gx_context
)

suite_procedure = create_expectations_and_suite(
    expectations.expectations_procedure, "patients_with_procedure_suite", gx_context
)

suite_medicationStatements = create_expectations_and_suite(
    expectations.expectations_medicationStatements, "patients_with_MedicationStatement_suite", gx_context
)

data_source_name = "snapshot_bundles"
data_source = gx_context.data_sources.add_or_update_spark(name=data_source_name)
data_asset = data_source.add_dataframe_asset(name="patients_and_conditions")
data_asset_observations = data_source.add_dataframe_asset(name="patients_and_observations")
data_asset_procedure = data_source.add_dataframe_asset(name="patients_and_procedures")
data_asset_medicationStatements = data_source.add_dataframe_asset(name="patients_and_MedicationStatements")

# Add the Batch Definition and Validation Definitions
validation_definition_conditions = create_validation_definition(
    gx_context,
    data_asset,
    suite_conditions,
    "validate_conditions",
    "patients_and_conditions_all_rows",
)
validation_definition_observations = create_validation_definition(
    gx_context,
    data_asset_observations,
    suite_observations,
    "validate_observations",
    "patients_and_observations_all_rows",
)
validation_definition_procedure = create_validation_definition(
    gx_context,
    data_asset_procedure,
    suite_procedure,
    "validate_procedure",
    "patients_and_procedure_all_rows",
)

validation_definition_medicationStatement = create_validation_definition(
    gx_context,
    data_asset_medicationStatements,
    suite_medicationStatements,
    "validate_medicationStatements",
    "patients_and_medicationStatements_all_rows",
)
# Actions for checkpoints
action_list = [
    UpdateDataDocsAction(
        name="update_all_data_docs",
    ),
]

# Create checkpoints
checkpoint_conditions = create_checkpoint(
    gx_context,
    "validate_conditions_checkpoint",
    [validation_definition_conditions],
    action_list,
)
checkpoint_observations = create_checkpoint(
    gx_context,
    "validate_observations_checkpoint",
    [validation_definition_observations],
    action_list,
)
checkpoint_procedure = create_checkpoint(
    gx_context,
    "validate_procedure_checkpoint",
    [validation_definition_procedure],
    action_list,
)

checkpoint_medicationStatement = create_checkpoint(
    gx_context,
    "validate_medicationStatements_checkpoint",
    [validation_definition_medicationStatement],
    action_list,
)
# Add Data Docs Site
site_name = "obds_to_fhir_data_docs_site"
base_directory = "uncommitted/data_docs/local_site/"
site_config = {
    "class_name": "SiteBuilder",
    "store_backend": {
        "class_name": "TupleFilesystemStoreBackend",
        "base_directory": base_directory,
    },
    "site_index_builder": {"class_name": "DefaultSiteIndexBuilder"},
}

# this only has to be done once, after that the site is persisted to the file system.
# and otherwise you also get:
# InvalidKeyError: Data Docs Site `obds_to_fhir_data_docs_site` already exists
# in the Data Context.
#gx_context.add_data_docs_site(site_name=site_name, site_config=site_config)

#gx_context.update_data_docs_site(
#    site_name=site_name,
#    site_config=site_config,
#)

# Build the Data Docs
gx_context.build_data_docs(site_names=[site_name])

# Run the Checkpoints
validation_results_conditions = checkpoint_conditions.run(
    batch_parameters={"dataframe": patients_with_conditions}
)

logger.info(validation_results_conditions.describe())

if not validation_results_conditions.success:
    logger.error("Validation run failed!")

validation_results_observations = checkpoint_observations.run(
    batch_parameters={"dataframe": patients_with_observations}
)

logger.info(validation_results_observations.describe())

if not validation_results_observations.success:
    logger.error("Validation run failed!")
    sys.exit(1)

validation_results_procedure = checkpoint_procedure.run(
    batch_parameters={"dataframe": patients_with_procedures}
)

logger.info(validation_results_procedure.describe())

if not validation_results_procedure.success:
    logger.error("Validation run failed!")
    sys.exit(1)

validation_results_medicationStatement = checkpoint_medicationStatement.run(
        batch_parameters={"dataframe": patients_with_medications}
    )
logger.info(validation_results_medicationStatement.describe())

if not validation_results_medicationStatement.success:
    logger.error("Validation run failed!")
    sys.exit(1)
