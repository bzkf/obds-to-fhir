import os
from pathlib import Path
import sys

import great_expectations as gx
from great_expectations.checkpoint import UpdateDataDocsAction
from loguru import logger
import pandas as pd
from pathling import Expression as exp
from pathling import PathlingContext
from pyspark.sql.functions import concat, lit, col

pc = PathlingContext.create(enable_extensions=True)

HERE = Path(os.path.abspath(os.path.dirname(__file__)))

snapshots_dir = (
    HERE
    / "../../test/java/snapshots/org/miracum/streams/ume/obdstofhir/"
    / "mapper/mii/ObdsToFhirBundleMapperTest/"
).as_posix()

data = pc.read.bundles(
    snapshots_dir, ["Patient", "Condition", "Observation", "Procedure"]
)

patients = data.extract(
    "Patient",
    columns=[
        exp("id", "patient_id"),
        exp("gender", "gender"),
        exp("birthDate", "date_of_birth"),
        exp("deceasedDateTime", "deceased_date_time"),
    ],
)

patients.show(truncate=False)

conditions = data.extract(
    "Condition",
    columns=[
        exp("id", "condition_id"),
        exp(
            "code.coding.where(system='http://fhir.de/CodeSystem/bfarm/icd-10-gm').code",
            "icd_code",
        ),
        exp(
            "code.coding.where(system='http://fhir.de/CodeSystem/bfarm/icd-10-gm').version",
            "icd_version",
        ),
        exp("subject.reference", "subject_reference"),
        exp(
            "extension('http://hl7.org/fhir/StructureDefinition/condition-assertedDate').valueDateTime",
            "asserted_date",
        ),
        exp("recordedDate", "recorded_date"),
    ],
)

# (full) outer join to retain null values if either side is missing
patients_with_conditions = conditions.join(
    patients,
    conditions["subject_reference"] == concat(lit("Patient/"), patients["patient_id"]),
    how="outer",
)

patients_with_conditions.show(truncate=False)

# patients_with_conditions.coalesce(1).write.mode("overwrite").csv(
#     "patients_with_conditions.csv", header=True, sep=","
# )

gx_context = gx.get_context(mode="file")

suite = gx.ExpectationSuite(name="patients_with_conditions_suite")

min_date = pd.Timestamp("1900-01-01")
max_date = pd.Timestamp("2025-12-31")

expectations = [
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="patient_id",
        description="Check if the id is always set. "
        + "Could also indicate a Condition without a Patient.",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="condition_id",
        description="Check if the id is always set. "
        + "Could also indicate a Patient without a Condition.",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="icd_version", description="Check if ICD version is always provided"
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="icd_code",
        description="Check if ICD code is always provided.",
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="recorded_date",
        column_B="date_of_birth",
        or_equal=True,
        description="Check if diagnosis recorded date is on or after the date of birth",
    ),
    # Birth date validations
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="date_of_birth", min_value=min_date, max_value=max_date
    ),
    # Deceased date validations
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="deceased_date_time",
        column_B="date_of_birth",
        ignore_row_if="either_value_is_missing",
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="deceased_date_time", min_value=min_date, max_value=max_date
    ),
    # asserted datetime validations
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="asserted_date", min_value=min_date, max_value=max_date
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="asserted_date",
        column_B="date_of_birth",
        or_equal=True,
        description="Check if diagnosis asserted date is on or after the date of birth",
    ),
]

for expectation in expectations:
    suite.add_expectation(expectation)

suite = gx_context.suites.add_or_update(suite=suite)

data_source_name = "snapshot_bundles"
data_asset_name = "patients_and_conditions"
batch_definition_name = "all_rows"

data_source = gx_context.data_sources.add_or_update_spark(name=data_source_name)
data_asset = data_source.add_dataframe_asset(name=data_asset_name)

# Add the Batch Definition
batch_definition = data_asset.add_batch_definition_whole_dataframe(
    batch_definition_name
)

# Create runtime parameters for the DataFrame
batch_parameters = {"dataframe": patients_with_conditions}
batch = batch_definition.get_batch(batch_parameters=batch_parameters)

batch.head(10)

# Create a Validation Definition
definition_name = "validate_conditions"
validation_definition = gx.ValidationDefinition(
    data=batch_definition, suite=suite, name=definition_name
)

# Add the Validation Definition to the Data Context
validation_definition = gx_context.validation_definitions.add_or_update(
    validation_definition
)

validation_definitions = [gx_context.validation_definitions.get(definition_name)]

# Check points:  Checkpoint executes one or more Validation Definitions
# performs a set of Actions based on the Validation Results each
# Validation Definition returns.

action_list = [
    # This Action updates the Data Docs static website with the Validation
    #   Results after the Checkpoint is run.
    UpdateDataDocsAction(
        name="update_all_data_docs",
    ),
]

# Create the Checkpoint
checkpoint_name = "validation_checkpoint"
checkpoint = gx.Checkpoint(
    name=checkpoint_name,
    validation_definitions=validation_definitions,
    actions=action_list,
    result_format={"result_format": "SUMMARY"},
)

# Add the Checkpoint to the Data Context
gx_context.checkpoints.add_or_update(checkpoint)

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
# InvalidKeyError: Data Docs Site `obds_to_fhir_data_docs_site` already exists in the Data Context.
# gx_context.add_data_docs_site(site_name=site_name, site_config=site_config)

gx_context.update_data_docs_site(
    site_name=site_name,
    site_config=site_config,
)

# Build the Data Docs
gx_context.build_data_docs()

# Run the Checkpoint
validation_results = checkpoint.run(batch_parameters=batch_parameters)

logger.info(validation_results.describe())

if not validation_results.success:
    logger.error("Validation run failed!")
    sys.exit(1)
