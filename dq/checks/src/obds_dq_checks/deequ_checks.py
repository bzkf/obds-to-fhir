"""The 3 data quality checks implemented with PyDeequ
(https://github.com/awslabs/python-deequ)."""

import os

# pydeequ picks the matching Deequ JVM artifact from this env var as soon as
# `pydeequ` is imported, so it must be set before that happens.
os.environ.setdefault("SPARK_VERSION", "3.5")

from loguru import logger  # noqa: E402
from pydeequ.checks import Check, CheckLevel, CheckStatus  # noqa: E402
from pydeequ.verification import VerificationResult, VerificationSuite  # noqa: E402
from pyspark.sql import DataFrame, SparkSession  # noqa: E402

from obds_dq_checks.constants import MAX_BIRTH_DATE, MIN_BIRTH_DATE  # noqa: E402


def _run(spark: SparkSession, label: str, df: DataFrame, check: Check) -> bool:
    result = VerificationSuite(spark).onData(df).addCheck(check).run()
    passed = result.status == CheckStatus.Success.value
    logger.info(f"[deequ] {label}: {result.status}")
    if not passed:
        VerificationResult.checkResultsAsDataFrame(spark, result).show(truncate=False)
    return passed


def check_birth_dates_in_range(spark: SparkSession, patients: DataFrame) -> bool:
    """1. Patient birth dates are within an expected time range."""
    check = Check(spark, CheckLevel.Error, "birth date in range").satisfies(
        f"date_of_birth BETWEEN '{MIN_BIRTH_DATE.isoformat()}' "
        f"AND '{MAX_BIRTH_DATE.isoformat()}'",
        "birth_date_in_range",
    )
    return _run(spark, "birth date is within expected range", patients, check)


def check_observations_after_diagnosis(
    spark: SparkSession, observations_with_diagnosis: DataFrame
) -> bool:
    """2. All observations belonging to one diagnosis were recorded after
    the diagnosis."""
    check = Check(spark, CheckLevel.Error, "observation after diagnosis").satisfies(
        "effective_date_time >= asserted_date",
        "observation_after_diagnosis",
    )
    return _run(
        spark,
        "observations were recorded after their diagnosis",
        observations_with_diagnosis,
        check,
    )


def check_one_death_observation_per_patient(
    spark: SparkSession, death_observations: DataFrame
) -> bool:
    """3. Each patient should only have one death observation."""
    check = Check(
        spark, CheckLevel.Error, "one death observation per patient"
    ).isUnique("patient_reference")
    return _run(
        spark,
        "each patient has at most one death observation",
        death_observations,
        check,
    )


def run_checks(
    spark: SparkSession,
    patients: DataFrame,
    observations_with_diagnosis_df: DataFrame,
    death_observations_df: DataFrame,
) -> bool:
    results = [
        check_birth_dates_in_range(spark, patients),
        check_observations_after_diagnosis(spark, observations_with_diagnosis_df),
        check_one_death_observation_per_patient(spark, death_observations_df),
    ]
    return all(results)
