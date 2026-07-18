"""Runs the 3 obds-to-fhir data quality checks against sparkdq, dqx and
pydeequ, using the tables produced by the dq/extract project."""

import os
import sys

# pydeequ picks the matching Deequ JVM artifact from this env var as soon as
# `pydeequ` is imported, so it must be set before that happens.
os.environ.setdefault("SPARK_VERSION", "3.5")

import pydeequ  # noqa: E402
from loguru import logger  # noqa: E402
from pyspark.sql import SparkSession  # noqa: E402

from obds_dq_checks import deequ_checks, dqx_checks, sparkdq_checks  # noqa: E402
from obds_dq_checks.io import load_tables  # noqa: E402
from obds_dq_checks.prepare import (  # noqa: E402
    death_observations,
    observations_with_diagnosis,
)


def build_spark_session() -> SparkSession:
    return (
        SparkSession.builder.appName("obds-to-fhir-dq-checks")
        .config("spark.jars.packages", pydeequ.deequ_maven_coord)
        # f2j is unused, and slf4j-api is already provided by PySpark itself;
        # excluding it here sidesteps a stale/incomplete slf4j-api entry that
        # some local Maven caches carry, which would otherwise break the
        # Ivy dependency resolution of the Deequ package above.
        .config(
            "spark.jars.excludes",
            f"{pydeequ.f2j_maven_coord},org.slf4j:slf4j-api",
        )
        .getOrCreate()
    )


def main() -> None:
    spark = build_spark_session()
    spark.sparkContext.setLogLevel("WARN")

    patients, conditions, observations = load_tables(spark)
    obs_with_diagnosis = observations_with_diagnosis(conditions, observations).cache()
    death_obs = death_observations(observations).cache()

    results = {
        "sparkdq": sparkdq_checks.run_checks(patients, obs_with_diagnosis, death_obs),
        "dqx": dqx_checks.run_checks(patients, obs_with_diagnosis, death_obs),
        "deequ": deequ_checks.run_checks(
            spark, patients, obs_with_diagnosis, death_obs
        ),
    }

    for framework, passed in results.items():
        logger.info(f"{framework}: {'PASSED' if passed else 'FAILED'}")

    if not all(results.values()):
        logger.error("One or more data quality checks failed")
        sys.exit(1)


if __name__ == "__main__":
    main()
