"""Extracts obds-to-fhir FHIR bundles with Pathling and runs the 3 data
quality checks against them with sparkdq, all in a single Spark 4.x session.
"""

import sys

from loguru import logger

from obds_dq.checks import run_checks
from obds_dq.config import Config, build_spark_session
from obds_dq.extract import build_pathling_context, extract_tables
from obds_dq.prepare import death_observations, observations_with_diagnosis


def main() -> None:
    config = Config()
    logger.info(f"Reading FHIR bundles from {config.bundles_dir}")

    spark = build_spark_session(config)
    pc = build_pathling_context(spark)
    patients, conditions, observations = extract_tables(pc, config.bundles_dir)

    obs_with_diagnosis = observations_with_diagnosis(conditions, observations).cache()
    death_obs = death_observations(observations).cache()

    passed = run_checks(patients, obs_with_diagnosis, death_obs)

    if not passed:
        logger.error("One or more data quality checks failed")
        sys.exit(1)


if __name__ == "__main__":
    main()
