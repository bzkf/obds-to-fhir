"""Loads the Parquet tables produced by the dq/extract project.

The SQL on FHIR v2 views produced by Pathling represent FHIR date/dateTime
values as plain ISO-8601 strings (per spec, to preserve partial-precision
values such as year-only dates). The columns used for date comparisons here
are cast to Spark's ``date`` type so that all three check frameworks can rely
on proper date semantics rather than string comparison.
"""

import os
from pathlib import Path

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F

DEFAULT_DATA_DIR = Path(__file__).resolve().parents[3] / "data"


def get_data_dir() -> Path:
    return Path(os.environ.get("DQ_DATA_DIR", DEFAULT_DATA_DIR))


def load_tables(spark: SparkSession) -> tuple[DataFrame, DataFrame, DataFrame]:
    """Reads the patients, conditions and observations tables and casts
    their date columns to Spark's ``date`` type."""
    data_dir = get_data_dir()

    patients = spark.read.parquet(str(data_dir / "patients.parquet")).withColumn(
        "date_of_birth", F.to_date("date_of_birth")
    )
    conditions = spark.read.parquet(str(data_dir / "conditions.parquet")).withColumn(
        "asserted_date", F.to_date("asserted_date")
    )
    observations = spark.read.parquet(
        str(data_dir / "observations.parquet")
    ).withColumn("effective_date_time", F.to_date("effective_date_time"))

    return patients, conditions, observations
