# Pathling >=9's library-runtime jar is compiled for Java 21, which isn't
# packaged for `bookworm` (only up to JRE 17 there); `trixie` has JRE 21.
FROM ghcr.io/astral-sh/uv:0.11.29-python3.12-trixie-slim@sha256:36cdfbf910c8b0f651355c013e7ece9678f4ecbf030a9fd9e6779de421189805
ENV UV_LINK_MODE=copy \
    UV_COMPILE_BYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HOME=/home/nonroot

WORKDIR /app

# hadolint ignore=DL3008
RUN <<EOF
set -e
apt-get update -y
apt-get install -y --no-install-recommends openjdk-21-jre-headless
rm -rf /var/lib/apt/lists/*
apt-get purge -y --auto-remove -o APT::AutoRemove::RecommendsImportant=false

groupadd -r -g 65532 nonroot
useradd --create-home --shell /bin/bash --uid 65532 -g nonroot -d "${HOME}" nonroot

# `extract` writes its Parquet output here for `checks` to pick up.
mkdir -p /app/data
chown 65532:65532 /app/data
EOF

# Install dependencies for both sub-projects. They are kept separate because
# pathling>=9 (dq/extract) requires PySpark 4.x, while sparkdq, dqx and
# pydeequ (dq/checks) only support Spark 3.5.x, see dq/README.md.
COPY --chown=65532:65532 extract/pyproject.toml extract/uv.lock /app/extract/
COPY --chown=65532:65532 checks/pyproject.toml checks/uv.lock /app/checks/

USER 65532:65532

RUN --mount=type=cache,target=/home/nonroot/.cache/uv,uid=65532,gid=65532 <<EOF
set -e
uv sync --project /app/extract --locked --no-install-project
uv sync --project /app/checks --locked --no-install-project
EOF

COPY --chown=65532:65532 extract /app/extract
COPY --chown=65532:65532 checks /app/checks
COPY --chown=65532:65532 entrypoint.sh /app/entrypoint.sh

RUN --mount=type=cache,target=/home/nonroot/.cache/uv,uid=65532,gid=65532 <<EOF
set -e
uv sync --project /app/extract --locked
uv sync --project /app/checks --locked
EOF

# Warm the Ivy/Maven caches for the JVM packages (Pathling, Delta, Deequ) so
# that a container run doesn't need network access to Maven Central just to
# start up.
RUN uv run --project /app/extract python -c \
    "from pathling import PathlingContext; PathlingContext.create(enable_extensions=True, enable_terminology=False)"
RUN uv run --project /app/checks python -c \
    "import os; os.environ.setdefault('SPARK_VERSION', '3.5'); import pydeequ; from pyspark.sql import SparkSession; SparkSession.builder.config('spark.jars.packages', pydeequ.deequ_maven_coord).config('spark.jars.excludes', f'{pydeequ.f2j_maven_coord},org.slf4j:slf4j-api').getOrCreate()"

ENTRYPOINT ["/app/entrypoint.sh"]
