from pathlib import Path
from pathling import PathlingContext, Expression as exp
import os
import great_expectations as gx
from great_expectations.checkpoint import (
    UpdateDataDocsAction,
)

pc = PathlingContext.create(enable_extensions=True)

HERE = Path(os.path.abspath(os.path.dirname(__file__)))

snapshots_dir = (
    HERE
    / "../../test/java/snapshots/org/miracum/streams/ume/obdstofhir/mapper/mii"
    / "ObdsToFhirBundleMapperTest.map_withGivenObds_shouldCreateBundleMatchingSnapshot./"
).as_posix()

data = pc.read.bundles(snapshots_dir, ["Patient", "Condition"])

result = data.extract(
    "Patient",
    columns=[
        exp("id", "patient_id"),
        exp("gender", "gender"),
        exp("birthDate", "birth_date"),
        exp(
            "reverseResolve(Condition.subject).code.coding.where(system='http://fhir.de/CodeSystem/bfarm/icd-10-gm').code",
            "icd_code",
        ),
        exp(
            "reverseResolve(Condition.subject).id",
            "condition_id",
        ),
        exp(
            "reverseResolve(Condition.subject).recordedDate",
            "condition_recorded_date",
        ),
    ],
)

result.show(truncate=False)

gx_context = gx.get_context(mode="file")

suite = gx.ExpectationSuite(name="condition_expectations")

# Check if Date of Diagnosis is after date of birth
expectation_10 = gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
    column_A="condition_recorded_date",
    column_B="birth_date",
    or_equal=True,
)
suite.add_expectation(expectation_10)

suite = gx_context.suites.add_or_update(suite=suite)

data_source_name = "snapshot_bundles"
data_asset_name = "patients_and_conditions"
batch_definition_name = "all_rows"

# Add Pandas Data Source and Data Asset (if not already set up)
data_source = gx_context.data_sources.add_or_update_spark(name=data_source_name)
data_asset = data_source.add_dataframe_asset(name=data_asset_name)

# Add the Batch Definition
batch_definition = data_asset.add_batch_definition_whole_dataframe(
    batch_definition_name
)

# Create runtime parameters for the DataFrame
batch_parameters = {"dataframe": result}
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
# performs a set of Actions based on the Validation Results each Validation Definition returns.

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

# Step 2: Add the Checkpoint to the Data Context
gx_context.checkpoints.add_or_update(checkpoint)

# Step 8: Build Data Docs
# Create and build Data Docs before running the Checkpoint

# Define an absolute path for the Data Docs directory
base_directory_path = os.path.abspath("./fhir_data_docs")
# Add Data Docs Site
site_name = "my_data_docs_site"
gx_context.update_data_docs_site(
    site_name=site_name,
    site_config={
        "class_name": "SiteBuilder",
        "store_backend": {
            "class_name": "TupleFilesystemStoreBackend",
            "base_directory": base_directory_path,  # Use absolute path here
        },
        "site_index_builder": {"class_name": "DefaultSiteIndexBuilder"},
    },
)

# Build the Data Docs
gx_context.build_data_docs()

# Run the Checkpoint
validation_results = checkpoint.run(batch_parameters=batch_parameters)
