"""Runtime configuration, loaded from environment variables.

Mirrors the subset of settings that mattered from the old GX-based
`dq/config.py`: Spark tuning (driver memory) and the ability to read the FHIR
bundles from S3 instead of a local directory.
"""

import os
from dataclasses import dataclass
from pathlib import Path

from pathling._spark_defaults import managed_spark_defaults
from pyspark.sql import SparkSession

REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_BUNDLES_DIR = (
    REPO_ROOT
    / "mappings/src/test/java/snapshots/io/github/bzkf"
    / "obdstofhir/mapper/mii/ObdsToFhirBundleMapperTest"
).as_posix()

# Must match the Hadoop client version bundled with the installed PySpark
# (see the `hadoop-client-*.jar` files under `pyspark/jars/`), otherwise
# fs.s3a will fail to load at runtime.
HADOOP_AWS_COORDINATE = "org.apache.hadoop:hadoop-aws:3.4.1"


def _env_bool(name: str, default: bool) -> bool:
    value = os.environ.get(name)
    return default if value is None else value.strip().lower() in ("1", "true", "yes")


@dataclass(frozen=True)
class Config:
    spark_master: str = os.environ.get("SPARK_MASTER", "local[*]")
    spark_driver_memory: str = os.environ.get("SPARK_DRIVER_MEMORY", "8g")
    # A local directory, or an S3 path (s3a://bucket/path), containing the
    # FHIR bundles to check.
    bundles_dir: str = os.environ.get("BUNDLES_DIR", DEFAULT_BUNDLES_DIR)
    # Only needed when bundles_dir is an s3a:// path. s3_endpoint is required
    # for S3-compatible services (e.g. MinIO); leave unset to use AWS S3 with
    # its default endpoint and the usual credential provider chain.
    s3_endpoint: str | None = os.environ.get("S3_ENDPOINT")
    s3_connection_ssl_enabled: bool = _env_bool("S3_CONNECTION_SSL_ENABLED", True)
    aws_access_key_id: str | None = os.environ.get("AWS_ACCESS_KEY_ID")
    aws_secret_access_key: str | None = os.environ.get("AWS_SECRET_ACCESS_KEY")


def build_spark_session(config: Config) -> SparkSession:
    """Builds the Spark session used for both extraction (Pathling) and the
    sparkdq checks, applying Pathling's managed package/Delta configuration
    plus the driver memory and optional S3 settings from `config`."""
    packages = managed_spark_defaults()["spark.jars.packages"]
    if config.s3_endpoint:
        packages += HADOOP_AWS_COORDINATE

    builder = (
        SparkSession.builder.master(config.spark_master)
        .appName("obds-to-fhir-dq")
        .config("spark.driver.memory", config.spark_driver_memory)
    )
    for key, value in managed_spark_defaults().items():
        builder = builder.config(key, value)
    builder = builder.config("spark.jars.packages", packages)

    if config.s3_endpoint:
        builder = (
            builder.config("spark.hadoop.fs.s3a.endpoint", config.s3_endpoint)
            .config("spark.hadoop.fs.s3a.path.style.access", "true")
            .config(
                "spark.hadoop.fs.s3a.connection.ssl.enabled",
                str(config.s3_connection_ssl_enabled).lower(),
            )
        )
        if config.aws_access_key_id:
            builder = builder.config(
                "spark.hadoop.fs.s3a.access.key", config.aws_access_key_id
            )
        if config.aws_secret_access_key:
            builder = builder.config(
                "spark.hadoop.fs.s3a.secret.key", config.aws_secret_access_key
            )

    return builder.getOrCreate()
