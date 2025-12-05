# can't yet upgrade to `trixie` as it doesn't include JRE 17
FROM ghcr.io/astral-sh/uv:0.9.15-python3.12-bookworm-slim@sha256:2cf9a9913c86a8ca91c708c810d60f429b9051d6436c04fd456b195b265c4fcd
ENV UV_LINK_MODE=copy \
    UV_COMPILE_BYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    SPARK_CONF_DIR=/app/spark/conf

WORKDIR /app

# hadolint ignore=DL3008
RUN <<EOF
set -e
apt-get update -y
apt-get install -y --no-install-recommends openjdk-17-jre-headless
rm -rf /var/lib/apt/lists/*
apt-get purge -y --auto-remove -o APT::AutoRemove::RecommendsImportant=false

groupadd -r -g 65532 nonroot
useradd --create-home --shell /bin/bash --uid 65532 -g nonroot nonroot

uv venv /app/.venv
EOF

# Install dependencies
RUN --mount=type=cache,target=/root/.cache/uv \
    --mount=type=bind,source=requirements.txt,target=requirements.txt \
    uv pip install --no-cache-dir -r requirements.txt

# Copy the project into the image
COPY --chown=65532:65532 . /app

ENV PATH="/app/.venv/bin:$PATH"

USER 65532:65532

RUN SPARK_INSTALL_PACKAGES_AND_EXIT=1 python3 /app/check_dq.py

ENTRYPOINT [ "python3", "/app/check_dq.py" ]
