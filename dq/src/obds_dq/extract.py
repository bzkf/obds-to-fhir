"""Extracts tabular views of obds-to-fhir Patient/Condition/Observation
resources using Pathling's SQL on FHIR v2 view engine.

SQL on FHIR v2 views represent FHIR date/dateTime values as plain ISO-8601
strings (per spec, to preserve partial-precision values such as year-only
dates), rather than as typed Spark columns like Pathling's old `.extract()`
API did. The columns used for date comparisons here are cast to Spark's
``date`` type before being handed to the checks.
"""

from pathling import PathlingContext
from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F

ASSERTED_DATE_EXTENSION = (
    "http://hl7.org/fhir/StructureDefinition/condition-assertedDate"
)

PATIENT_SELECT = [
    {
        "column": [
            {"path": "id", "name": "patient_id"},
            {"path": "birthDate", "name": "date_of_birth"},
        ]
    }
]

CONDITION_SELECT = [
    {
        "column": [
            {"path": "id", "name": "condition_id"},
            {"path": "subject.reference", "name": "patient_reference"},
            {
                "path": f"extension('{ASSERTED_DATE_EXTENSION}').valueDateTime.first()",
                "name": "asserted_date",
            },
        ]
    }
]

OBSERVATION_SELECT = [
    {
        "column": [
            {"path": "id", "name": "observation_id"},
            {"path": "subject.reference", "name": "patient_reference"},
            {"path": "focus.reference.first()", "name": "focus_reference"},
            {"path": "effectiveDateTime", "name": "effective_date_time"},
            {"path": "meta.profile.first()", "name": "meta_profile"},
        ]
    }
]


def build_pathling_context(spark: SparkSession) -> PathlingContext:
    return PathlingContext.create(
        spark, enable_extensions=True, enable_terminology=False
    )


def extract_tables(
    pc: PathlingContext, bundles_dir: str
) -> tuple[DataFrame, DataFrame, DataFrame]:
    """Reads Patient/Condition/Observation resources from `bundles_dir` (a
    local directory or an s3a:// path) and returns (patients, conditions,
    observations) DataFrames with their date columns cast to Spark's `date`
    type."""
    data = pc.read.bundles(bundles_dir, ["Patient", "Condition", "Observation"])

    patients = (
        data.view(resource="Patient", select=PATIENT_SELECT)
        .drop_duplicates()
        .withColumn("date_of_birth", F.to_date("date_of_birth"))
    )
    conditions = (
        data.view(resource="Condition", select=CONDITION_SELECT)
        .drop_duplicates()
        .withColumn("asserted_date", F.to_date("asserted_date"))
    )
    observations = (
        data.view(resource="Observation", select=OBSERVATION_SELECT)
        .drop_duplicates()
        .withColumn("effective_date_time", F.to_date("effective_date_time"))
    )

    return patients, conditions, observations
