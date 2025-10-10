import sys

import expectations
import great_expectations as gx
from config import config
from great_expectations.checkpoint.actions import UpdateDataDocsAction
from great_expectations.core.result_format import ResultFormat
from loguru import logger
from pathling import DataSource
from pathling import Expression as exp
from pathling import PathlingContext
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, concat, lit

spark_config = (
    SparkSession.builder.master(config.spark_master)  # type: ignore
    .appName("fhir_to_lakehouse")
    .config(
        "spark.jars.packages",
        ",".join(
            [
                "au.csiro.pathling:library-runtime:7.2.0",
                "io.delta:delta-spark_2.12:3.3.2",
                "org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.6",
                "org.apache.hadoop:hadoop-aws:3.3.4",
            ]
        ),
    )
    .config(
        "spark.sql.extensions",
        "io.delta.sql.DeltaSparkSessionExtension",
    )
    .config(
        "spark.driver.memory",
        config.spark_driver_memory,
    )
    .config("spark.sql.warehouse.dir", config.spark_warehouse_dir)
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
    .config(
        "spark.sql.catalog.spark_catalog",
        "org.apache.spark.sql.delta.catalog.DeltaCatalog",
    )
    .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    .config(
        "spark.hadoop.fs.s3a.path.style.access",
        "true",
    )
    .config(
        "spark.hadoop.fs.s3a.endpoint",
        config.s3_endpoint,
    )
    .config(
        "spark.hadoop.fs.s3a.connection.ssl.enabled",
        config.s3_connection_ssl_enabled,
    )
    .config("fs.s3a.committer.name", "magic")
    .config("fs.s3a.committer.magic.enabled", "true")
    .config("fs.s3a.access.key", config.aws_access_key_id)
    .config("fs.s3a.secret.key", config.aws_secret_access_key)
)

spark = spark_config.getOrCreate()

if config.spark_install_packages_and_exit:
    logger.info("Exiting after having installed packages")
    sys.exit()

spark.sparkContext.setCheckpointDir(config.spark_checkpoint_path)

pc = PathlingContext.create(
    spark,
    enable_extensions=True,
    enable_delta=True,
    enable_terminology=False,
    terminology_server_url="http://localhost/not-a-real-server",
)

data: DataSource

if config.read_from_delta:
    data = pc.read.delta(config.delta_dir)
else:
    data = pc.read.bundles(
        config.bundles_dir,
        ["Patient", "Condition", "Observation", "Procedure", "MedicationStatement"],
    )

data.read("Patient").cache()
patients = data.extract(
    "Patient",
    columns=[
        exp("id", "patient_id"),
        exp("gender", "gender"),
        exp("birthDate", "date_of_birth"),
        exp("deceasedDateTime", "deceased_date_time"),
        exp("deceasedBoolean", "deceased"),
    ],
).drop_duplicates()
patients = patients.checkpoint(eager=True)
patients.show(truncate=False)

data.read("Condition").cache()
conditions = data.extract(
    "Condition",
    columns=[
        exp("id", "condition_id"),
        exp(f"code.coding.where(system='{config.ICD_10_GM_SYSTEM}').code", "icd_code"),
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
        exp("meta.profile", "meta_profile_con"),
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

data.read("Observation").cache()
observations = data.extract(
    "Observation",
    columns=[
        exp("id", "observation_id"),
        exp("subject.reference", "subject_reference"),
        exp("code.coding.system", "code_system"),
        exp("code.coding.code", "code_code"),
        exp(
            "valueCodeableConcept.coding.code",
            "value_codeable_concept_coding_code",
        ),
        exp(
            "valueCodeableConcept.coding.system",
            "value_codeable_concept_coding_system",
        ),
        exp("effectiveDateTime", "effective_date_time"),
        exp("meta.profile", "meta_profile"),
        exp("bodySite.coding.code", "bodySite_code"),
    ],
).drop_duplicates()
observations = observations.checkpoint(eager=True)
patients_with_observations = observations.join(
    patients,
    observations["subject_reference"] == concat(lit("Patient/"), col("patient_id")),
    how="outer",
)
patients_with_observations.show(truncate=False)


# Fernmetastasen
patients_with_fernmetastasen = patients_with_observations.filter(
    col("meta_profile") == config.FERNMETASTASE
)


# Allgemeiner Leisungszustand
patients_with_ecog = patients_with_observations.filter(
    col("value_codeable_concept_coding_system") == config.ALL_LEISTUNSGZUSTAND
).select(
    "subject_reference",
    "value_codeable_concept_coding_code",
    "value_codeable_concept_coding_system",
)
patients_with_death = patients_with_observations.filter(
    col("meta_profile") == config.TOD
).select("subject_reference", "meta_profile")
patients_with_ecog_death = patients_with_ecog.join(
    patients_with_death,
    patients_with_ecog["subject_reference"] == patients_with_death["subject_reference"],
    how="outer",
)
patients_with_ecog_death.show(truncate=False)


data.read("Procedure").cache()
procedures = data.extract(
    "Procedure",
    columns=[
        exp("id", "procedure_id"),
        exp(f"code.coding.where(system='{config.OPS_SYSTEM}').system", "ops_system"),
        exp(f"code.coding.where(system='{config.OPS_SYSTEM}').version", "ops_version"),
        exp(f"code.coding.where(system='{config.OPS_SYSTEM}').code", "ops_code"),
        exp("subject.reference", "subject_reference_procedure"),
        exp("performedDateTime", "performed_date_time"),
        exp("performedPeriod.start", "performedPeriod_start"),
        exp("meta.profile", "meta_profile_procedure"),
        exp(
            "Procedure.reasonReference.resolve().ofType(Condition).id",
            "reason_reference_condition_id",
        ),
    ],
).drop_duplicates()
print(procedures.columns)
procedures = procedures.checkpoint(eager=True)
patients_with_procedures = procedures.join(
    patients,
    procedures["subject_reference_procedure"]
    == concat(lit("Patient/"), col("patient_id")),
    how="inner",
)

data.read("MedicationStatement").cache()
medicationStatements = data.extract(
    "MedicationStatement",
    columns=[
        exp("id", "MedicationStatement_id"),
        exp("subject.reference", "subject_reference"),
        exp("effectivePeriod.start", "effectivePeriod_start"),
        exp("effectivePeriod.end", "effectivePeriod_end"),
        exp("meta.profile", "meta_profile"),
    ],
).drop_duplicates()
medicationStatements = medicationStatements.checkpoint(eager=True)
print(medicationStatements.columns)

patients_with_medications = medicationStatements.join(
    patients,
    medicationStatements["subject_reference"]
    == concat(lit("Patient/"), col("patient_id")),
    how="inner",
)

condition_with_procedure = conditions.join(
    procedures,
    conditions["condition_id"] == procedures["reason_reference_condition_id"],
    how="left",
).join(
    patients,
    conditions["subject_reference"] == concat(lit("Patient/"), col("patient_id")),
    how="inner",
)
condition_with_procedure.show()

# Great Expectations
# ------------------
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


data_source_name = "snapshot_bundles"
data_source = gx_context.data_sources.add_or_update_spark(name=data_source_name)
data_asset = data_source.add_dataframe_asset(name="patients_and_conditions")
data_asset_observations = data_source.add_dataframe_asset(
    name="patients_and_observations"
)
data_asset_procedure = data_source.add_dataframe_asset(name="patients_and_procedures")
data_asset_medicationStatements = data_source.add_dataframe_asset(
    name="patients_and_MedicationStatements"
)
data_asset_condition_procedure = data_source.add_dataframe_asset(
    name="condition_with_procedure"
)
data_asset_fernmetastasen = data_source.add_dataframe_asset(
    name="patients_and_fernmetastasen"
)
data_asset_ecog_death = data_source.add_dataframe_asset(name="patients_and_ecog_death")

validation_targets = [
    {
        "name": "conditions",
        "dataframe": patients_with_conditions,
        "data_asset": data_asset,
        "expectations": expectations.expectations_conditions,
    },
    {
        "name": "observations",
        "dataframe": patients_with_observations,
        "data_asset": data_asset_observations,
        "expectations": expectations.expectations_observations,
    },
    {
        "name": "procedure",
        "dataframe": patients_with_procedures,
        "data_asset": data_asset_procedure,
        "expectations": expectations.expectations_procedure,
    },
    {
        "name": "medicationStatements",
        "dataframe": patients_with_medications,
        "data_asset": data_asset_medicationStatements,
        "expectations": expectations.expectations_medicationStatements,
    },
    {
        "name": "condition_procedure",
        "dataframe": condition_with_procedure,
        "data_asset": data_asset_condition_procedure,
        "expectations": expectations.expectations_condition_procedure,
    },
    {
        "name": "fernmetastasen",
        "dataframe": patients_with_fernmetastasen,
        "data_asset": data_asset_fernmetastasen,
        "expectations": expectations.expectations_fernmetastasen,
    },
    {
        "name": "ecog_death",
        "dataframe": patients_with_ecog_death,
        "data_asset": data_asset_ecog_death,
        "expectations": expectations.expectations_ecog_death,
    },
]


def validate_dataframe(gx_context, target):
    suite_name = f"{target['name']}_suite"
    data_asset = target["data_asset"]
    name = f"validate_{target['name']}"
    batch_name = f"patients_and_{target['name']}_all_rows"
    # create Suite
    suite = create_expectations_and_suite(
        target["expectations"], suite_name, gx_context
    )

    # validation_definition
    validation_definition = create_validation_definition(
        gx_context, data_asset, suite, name, batch_name
    )

    # Checkpoints
    checkpoint = create_checkpoint(
        gx_context,
        f"validate_{target['name']}_checkpoint",
        [validation_definition],
        action_list,
    )
    # Run
    validation_results = checkpoint.run(
        batch_parameters={"dataframe": target["dataframe"]}
    )
    logger.info(validation_results.describe())
    if not validation_results.success:
        logger.error("Validation run failed!")


# Actions for checkpoints
action_list = [
    UpdateDataDocsAction(
        name="update_all_data_docs",
    ),
]

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

# gx_context.update_data_docs_site(
#    site_name=site_name,
#    site_config=site_config,
# )

# Build the Data Docs
gx_context.build_data_docs(site_names=[site_name])

for target in validation_targets:
    validate_dataframe(gx_context, target)
