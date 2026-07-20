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

chown 65532:65532 /app
EOF

COPY --chown=65532:65532 pyproject.toml uv.lock /app/

USER 65532:65532

RUN --mount=type=cache,target=/home/nonroot/.cache/uv,uid=65532,gid=65532 \
    uv sync --locked --no-install-project

COPY --chown=65532:65532 src /app/src

RUN --mount=type=cache,target=/home/nonroot/.cache/uv,uid=65532,gid=65532 \
    uv sync --locked

# Warm the Ivy/Maven cache for Pathling's JVM packages so that a container
# run doesn't need network access to Maven Central just to start up.
RUN uv run python -c \
    "from pathling import PathlingContext; PathlingContext.create(enable_extensions=True, enable_terminology=False)"

ENTRYPOINT ["uv", "run", "python", "-m", "obds_dq"]
