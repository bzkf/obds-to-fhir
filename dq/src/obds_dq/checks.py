"""The 3 data quality checks implemented with sparkdq
(https://github.com/sparkdq-community/sparkdq)."""

from loguru import logger
from pyspark.sql import DataFrame
from sparkdq.checks import (
    ColumnGreaterThanCheckConfig,
    DateBetweenCheckConfig,
    UniqueRowsCheckConfig,
)
from sparkdq.core import Severity
from sparkdq.engine import BatchDQEngine
from sparkdq.management import CheckSet

from obds_dq.constants import MAX_BIRTH_DATE, MIN_BIRTH_DATE


def _run(label: str, check_set: CheckSet, df: DataFrame) -> bool:
    result = BatchDQEngine(check_set).run_batch(df)
    summary = result.summary()
    logger.info(f"{label}: {summary}")
    if not summary.all_passed:
        logger.warning(f"{label}: failing rows")
        result.fail_df().show(truncate=False)
    return summary.all_passed


def check_birth_dates_in_range(patients: DataFrame) -> bool:
    """1. Patient birth dates are within an expected time range."""
    check_set = CheckSet()
    check_set.add_check(
        DateBetweenCheckConfig(
            check_id="birth-date-in-range",
            columns=["date_of_birth"],
            min_value=MIN_BIRTH_DATE.isoformat(),
            max_value=MAX_BIRTH_DATE.isoformat(),
            inclusive=(True, True),
            severity=Severity.CRITICAL,
        )
    )
    return _run("birth date is within expected range", check_set, patients)


def check_observations_after_diagnosis(observations_with_diagnosis: DataFrame) -> bool:
    """2. All observations belonging to one diagnosis were recorded after
    the diagnosis."""
    check_set = CheckSet()
    check_set.add_check(
        ColumnGreaterThanCheckConfig(
            check_id="observation-after-diagnosis",
            column="effective_date_time",
            limit="asserted_date",
            inclusive=True,
            severity=Severity.CRITICAL,
        )
    )
    return _run(
        "observations were recorded after their diagnosis",
        check_set,
        observations_with_diagnosis,
    )


def check_one_death_observation_per_patient(death_observations: DataFrame) -> bool:
    """3. Each patient should only have one death observation."""
    check_set = CheckSet()
    check_set.add_check(
        UniqueRowsCheckConfig(
            check_id="one-death-observation-per-patient",
            subset_columns=["patient_reference"],
            severity=Severity.CRITICAL,
        )
    )
    return _run(
        "each patient has at most one death observation",
        check_set,
        death_observations,
    )


def run_checks(
    patients: DataFrame,
    observations_with_diagnosis_df: DataFrame,
    death_observations_df: DataFrame,
) -> bool:
    results = [
        check_birth_dates_in_range(patients),
        check_observations_after_diagnosis(observations_with_diagnosis_df),
        check_one_death_observation_per_patient(death_observations_df),
    ]
    return all(results)
