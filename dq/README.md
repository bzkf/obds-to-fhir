# obds-to-fhir data quality checks

Runs data quality checks against the FHIR bundles produced by obds-to-fhir.
The pipeline has two stages, implemented as two independent [uv](https://docs.astral.sh/uv/)
projects:

1. **[`extract/`](extract)** turns the Patient/Condition/Observation resources
   in the FHIR bundles into tabular Parquet files, using
   [Pathling](https://pathling.csiro.au/) >=9 and its
   [SQL on FHIR v2](https://sql-on-fhir.org/ig/2.0.0/) view engine.
2. **[`checks/`](checks)** reads those Parquet files and runs the same 3
   checks through three different data quality frameworks:
   [sparkdq](https://github.com/sparkdq-community/sparkdq),
   [Databricks Labs DQX](https://github.com/databrickslabs/dqx) and
   [PyDeequ](https://github.com/awslabs/python-deequ).

## Why two projects?

Pathling >=9 requires PySpark 4.x. None of the three check frameworks support
Spark 4 yet:

- `sparkdq` and `databricks-labs-dqx` currently pin/target Spark 3.5.
- `pydeequ` wraps [Deequ](https://github.com/awslabs/deequ), whose JVM
  artifacts are only published up to Spark 3.5.

Rather than downgrading Pathling, the pipeline is split so each stage gets
the Spark version it needs, with a Parquet hand-off in between. `extract/`
and `checks/` are therefore separate `pyproject.toml`/`uv.lock` pairs with
their own virtual environments, not a single workspace.

## The checks

Implemented identically (module `<framework>_checks.py`) in all three
frameworks under `checks/src/obds_dq_checks/`:

1. **Patient birth dates are within an expected time range**
   (`1900-01-01` to Dec 31 of the current year).
2. **All observations belonging to one diagnosis were recorded after the
   diagnosis** — i.e. for every `Observation` that references a `Condition`
   via `Observation.focus`, `effectiveDateTime >=` that condition's asserted
   date.
3. **Each patient has at most one death observation** — uniqueness of
   `patient_reference` among observations with the
   `mii-pr-onko-tod` profile.

## Running locally

```sh
export BUNDLES_DIR=/path/to/fhir/bundles  # defaults to the mappings test snapshots

cd extract && uv run python -m obds_dq_extract.extract && cd ..
cd checks && uv run python -m obds_dq_checks
```

`extract` writes `patients.parquet`, `conditions.parquet` and
`observations.parquet` to `dq/data/` by default (override with
`OUTPUT_DIR`/`DQ_DATA_DIR`). `checks` exits non-zero if any check fails in
any of the three frameworks.

## Running with Docker

```sh
docker build -t obds-dq-checks dq/
docker run --rm \
  -v ./mappings:/mappings:ro \
  -e BUNDLES_DIR=/mappings/src/test/java/snapshots/io/github/bzkf/obdstofhir/mapper/mii/ObdsToFhirBundleMapperTest/ \
  obds-dq-checks
```

## A note on FHIR date/dateTime columns

SQL on FHIR v2 views represent FHIR `date`/`dateTime` values as plain
ISO-8601 strings (to preserve partial-precision values such as year-only
dates), rather than as typed Spark columns like Pathling's old `.extract()`
API did. `checks/src/obds_dq_checks/io.py` casts the columns used for date
comparisons to Spark's `date` type on load.
