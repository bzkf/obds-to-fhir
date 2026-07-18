#!/bin/sh
set -e

uv run --project /app/extract python -m obds_dq_extract.extract
uv run --project /app/checks python -m obds_dq_checks
