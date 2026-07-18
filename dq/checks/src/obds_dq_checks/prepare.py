"""Builds the input DataFrames shared by all three check frameworks."""

from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from obds_dq_checks.constants import TOD_PROFILE


def observations_with_diagnosis(
    conditions: DataFrame, observations: DataFrame
) -> DataFrame:
    """Observations that reference a diagnosis (Condition) via `focus`,
    joined with that diagnosis' asserted date."""
    return (
        observations.filter(F.col("focus_reference").isNotNull())
        .join(
            conditions,
            observations["focus_reference"]
            == F.concat(F.lit("Condition/"), conditions["condition_id"]),
            how="inner",
        )
        .select(
            observations["observation_id"],
            observations["effective_date_time"],
            conditions["condition_id"],
            conditions["asserted_date"],
        )
    )


def death_observations(observations: DataFrame) -> DataFrame:
    """Observations recording a patient's death."""
    return observations.filter(F.col("meta_profile") == TOD_PROFILE).select(
        "observation_id", "patient_reference", "effective_date_time"
    )
