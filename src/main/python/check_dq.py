import os
import sys
from pathlib import Path

import great_expectations as gx
import pandas as pd
from great_expectations.checkpoint import UpdateDataDocsAction
from great_expectations.core.result_format import ResultFormat
from loguru import logger
from pathling import Expression as exp
from pathling import PathlingContext
from pyspark.sql.functions import concat, lit, col
from datetime import datetime

pc = PathlingContext.create(enable_extensions=True, enable_delta=True)

HERE = Path(os.path.abspath(os.path.dirname(__file__)))
ICD_10_GM_SYSTEM = "http://fhir.de/CodeSystem/bfarm/icd-10-gm"
ASSERTED_DATE_EXTENSION = (
    "http://hl7.org/fhir/StructureDefinition/condition-assertedDate"
)
MII_PR_ONKO_WEITERE_KLASSIFIKATIONEN = "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-weitere-klassifikationen"
MIN_DATE = pd.Timestamp("1900-01-01")
MAX_DATE = pd.Timestamp(year=datetime.now().year, month=12, day=31)

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
).drop_duplicates()

patients.show(truncate=False)


conditions = data.extract(
    "Condition",
    columns=[
        exp("id", "condition_id"),
        exp(
            f"code.coding.where(system='{ICD_10_GM_SYSTEM}').code",
            "icd_code",
        ),
        exp(
            f"code.coding.where(system='{ICD_10_GM_SYSTEM}').version",
            "icd_version",
        ),
        exp("subject.reference", "subject_reference"),
        exp(
            f"extension('{ASSERTED_DATE_EXTENSION}').valueDateTime",
            "asserted_date",
        ),
        exp("recordedDate", "recorded_date"),
    ],
).drop_duplicates()

# (full) outer join to retain null values if either side is missing
patients_with_conditions = conditions.join(
    patients,
    conditions["subject_reference"] == concat(lit("Patient/"), col("patient_id")),
    how="outer",
)

patients_with_conditions.show(truncate=False)

observations = data.extract(
    "Observation",
    columns=[
        exp("id", "observation_id"),
        exp("subject.reference", "subject_reference"),
        exp("code.coding.system", "code_system"),
        exp("code.coding.code", "code_code"),
        exp(
            """
                Observation.where(code.coding.exists(system='http://loinc.org' or system='http://snomed.info/sct')).valueCodeableConcept.coding.code
            """,
            "value_codeable_concept_coding_code",
        ),
        exp("effectiveDateTime", "effective_date_time"),
        exp("meta.profile", "meta_profile"),
    ],
).drop_duplicates()

patients_with_observations = observations.join(
    patients,
    observations["subject_reference"] == concat(lit("Patient/"), col("patient_id")),
    how="outer",
)

patients_with_observations.show(truncate=False)

patients_with_observations.write.mode("overwrite").csv(
    "patients_with_observations.csv", header=True
)

gx_context = gx.get_context(mode="file")


expectations = [
    # Birth date validations
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="date_of_birth", min_value=MIN_DATE, max_value=MAX_DATE
    ),
    # Deceased date validations
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="deceased_date_time",
        column_B="date_of_birth",
        ignore_row_if="either_value_is_missing",
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="deceased_date_time", min_value=MIN_DATE, max_value=MAX_DATE
    ),
    # asserted datetime validations
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="asserted_date", min_value=MIN_DATE, max_value=MAX_DATE
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="asserted_date",
        column_B="date_of_birth",
        or_equal=True,
        description="Check if diagnosis asserted date is on or after the date of birth",
    ),
    # Gender expectation with condition code
    # see <https://docs.greatexpectations.io/docs/core/customize_expectations/expectation_conditions/?condition_parser=spark>
    gx.expectations.ExpectColumnValuesToBeInSet(
        column="gender",
        value_set=["male"],
        condition_parser="great_expectations",
        row_condition='col("icd_code") == "C61"',
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="patient_id",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="condition_id",
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
]

expectations2 = [
    # Birth date validations
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="date_of_birth", min_value=MIN_DATE, max_value=MAX_DATE
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="code_system",
        condition_parser="great_expectations",
        row_condition=f'col("meta_profile") != "{MII_PR_ONKO_WEITERE_KLASSIFIKATIONEN}"',
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="code_code",
        condition_parser="great_expectations",
        row_condition=f'col("meta_profile") != "{MII_PR_ONKO_WEITERE_KLASSIFIKATIONEN}"',
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="patient_id",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="observation_id",
    ),
    # EffectiveDateTime validations
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="effective_date_time",
        column_B="date_of_birth",
        or_equal=True,
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="deceased_date_time",
        column_B="effective_date_time",
        or_equal=True,
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="effective_date_time", min_value=MIN_DATE, max_value=MAX_DATE
    ),
]

suite = gx.ExpectationSuite(name="patients_with_conditions_suite")
for expectation in expectations:
    suite.add_expectation(expectation)

suite2 = gx.ExpectationSuite(name="patients_with_observations_suite")
for expectation in expectations2:
    suite2.add_expectation(expectation)

suite = gx_context.suites.add_or_update(suite=suite)
suite2 = gx_context.suites.add_or_update(suite=suite2)

data_source_name = "snapshot_bundles"

data_source = gx_context.data_sources.add_or_update_spark(name=data_source_name)

data_asset = data_source.add_dataframe_asset(name="patients_and_conditions")
data_asset2 = data_source.add_dataframe_asset(name="patients_and_observations")

# Add the Batch Definition
validation_definition = gx_context.validation_definitions.add_or_update(
    gx.ValidationDefinition(
        name="validate_conditions",
        data=data_asset.add_batch_definition_whole_dataframe(
            "patients_and_conditions_all_rows"
        ),
        suite=suite,
    )
)

validation_definition2 = gx_context.validation_definitions.add_or_update(
    gx.ValidationDefinition(
        name="validate_observations",
        data=data_asset2.add_batch_definition_whole_dataframe(
            "patients_and_observations_all_rows"
        ),
        suite=suite2,
    )
)

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

checkpoint = gx_context.checkpoints.add_or_update(
    gx.Checkpoint(
        name="validate_conditions_checkpoint",
        validation_definitions=[validation_definition],
        actions=action_list,
        result_format=ResultFormat.COMPLETE,
    )
)

checkpoint2 = gx_context.checkpoints.add_or_update(
    gx.Checkpoint(
        name="validate_observations_checkpoint",
        validation_definitions=[validation_definition2],
        actions=action_list,
        result_format=ResultFormat.COMPLETE,
    )
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
# gx_context.add_data_docs_site(site_name=site_name, site_config=site_config)

gx_context.update_data_docs_site(
    site_name=site_name,
    site_config=site_config,
)

# Build the Data Docs
gx_context.build_data_docs()

# Run the Checkpoint
validation_results = checkpoint.run(
    batch_parameters={"dataframe": patients_with_conditions}
)

logger.info(validation_results.describe())

if not validation_results.success:
    logger.error("Validation run failed!")
    sys.exit(1)


validation_results2 = checkpoint2.run(
    batch_parameters={"dataframe": patients_with_observations}
)

logger.info(validation_results2.describe())

if not validation_results2.success:
    logger.error("Validation run failed!")
    sys.exit(1)
