"""The 3 data quality checks implemented with Databricks Labs DQX
(https://github.com/databrickslabs/dqx).

DQX normally authenticates against a Databricks workspace via
`WorkspaceClient`, but the row- and dataset-level checks used here support
local execution with a mocked client, which is the officially documented way
to run DQX checks outside of a Databricks workspace (see DQX's
`foreachBatch` guide).
"""

from unittest.mock import MagicMock

from databricks.labs.dqx import check_funcs
from databricks.labs.dqx.engine import DQEngine
from databricks.labs.dqx.rule import DQDatasetRule, DQRowRule
from databricks.sdk import WorkspaceClient
from loguru import logger
from pyspark.sql import DataFrame

from obds_dq_checks.constants import MAX_BIRTH_DATE, MIN_BIRTH_DATE


def _engine() -> DQEngine:
    return DQEngine(MagicMock(spec=WorkspaceClient))


def _run(label: str, engine: DQEngine, df: DataFrame, checks: list) -> bool:
    _, invalid_df = engine.apply_checks_and_split(df, checks)
    invalid_count = invalid_df.count()
    passed = invalid_count == 0
    logger.info(f"[dqx] {label}: {'PASSED' if passed else 'FAILED'}")
    if not passed:
        logger.warning(f"[dqx] {label}: {invalid_count} failing rows")
        invalid_df.show(truncate=False)
    return passed


def check_birth_dates_in_range(patients: DataFrame) -> bool:
    """1. Patient birth dates are within an expected time range."""
    checks = [
        DQRowRule(
            name="birth_date_in_range",
            criticality="error",
            check_func=check_funcs.is_in_range,
            column="date_of_birth",
            check_func_kwargs={
                "min_limit": MIN_BIRTH_DATE,
                "max_limit": MAX_BIRTH_DATE,
            },
        ),
    ]
    return _run("birth date is within expected range", _engine(), patients, checks)


def check_observations_after_diagnosis(observations_with_diagnosis: DataFrame) -> bool:
    """2. All observations belonging to one diagnosis were recorded after
    the diagnosis."""
    checks = [
        DQRowRule(
            name="observation_after_diagnosis",
            criticality="error",
            check_func=check_funcs.sql_expression,
            columns=["effective_date_time", "asserted_date"],
            check_func_kwargs={
                "expression": "effective_date_time >= asserted_date",
                "msg": "observation effective date is before the diagnosis date",
            },
        ),
    ]
    return _run(
        "observations were recorded after their diagnosis",
        _engine(),
        observations_with_diagnosis,
        checks,
    )


def check_one_death_observation_per_patient(death_observations: DataFrame) -> bool:
    """3. Each patient should only have one death observation."""
    checks = [
        DQDatasetRule(
            name="one_death_observation_per_patient",
            criticality="error",
            check_func=check_funcs.is_unique,
            columns=["patient_reference"],
        ),
    ]
    return _run(
        "each patient has at most one death observation",
        _engine(),
        death_observations,
        checks,
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
