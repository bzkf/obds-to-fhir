# obds-to-fhir data quality checks

Runs data quality checks against the FHIR bundles produced by obds-to-fhir.
It's a single [uv](https://docs.astral.sh/uv/) project, on a single Spark 4.x
session:

1. **[`src/obds_dq/extract.py`](src/obds_dq/extract.py)** turns the
   Patient/Condition/Observation resources in the FHIR bundles into Spark
   DataFrames, using [Pathling](https://pathling.csiro.au/) >=9 and its
   [SQL on FHIR v2](https://sql-on-fhir.org/ig/2.0.0/) view engine.
2. **[`src/obds_dq/sparkdq_checks.py`](src/obds_dq/sparkdq_checks.py)** runs
   the 3 checks against those DataFrames with
   [sparkdq](https://github.com/sparkdq-community/sparkdq).

## Spark version

Pathling >=9 requires PySpark 4.x. sparkdq's own packaging pins PySpark
`<4.0` under its `[spark]` extra, but that pin only applies to that optional
extra — the base `sparkdq` package (meant for environments that already
provide PySpark, e.g. Databricks) has no such constraint. Since Pathling
already pulls in PySpark 4.x, this project depends on plain `sparkdq`
(without `[spark]`) and lets Pathling supply the PySpark version. sparkdq's
checks run against Spark 4.0.x without issue in practice (see the FAILED/
PASSED counts this project's checks produce, verified against the mappings
test snapshots).

If a future sparkdq release exercises Spark-4-only-removed APIs, this may
need revisiting — see sparkdq's release notes.

## The checks

Implemented in `src/obds_dq/sparkdq_checks.py`:

1. **Patient birth dates are within an expected time range**
   (`1900-01-01` to Dec 31 of the current year).
2. **All observations belonging to one diagnosis were recorded after the
   diagnosis** — i.e. for every `Observation` that references a `Condition`
   via `Observation.focus`, `effectiveDateTime >=` that condition's asserted
   date.
3. **Each patient has at most one death observation** — uniqueness of
   `patient_reference` among observations with the `mii-pr-onko-tod`
   profile.

## Running locally

```sh
export BUNDLES_DIR=/path/to/fhir/bundles  # defaults to the mappings test snapshots
uv run python -m obds_dq
```

Exits non-zero if any check fails.

## Running with Docker

```sh
docker build -t obds-dq dq/
docker run --rm \
  -v ./mappings:/mappings:ro \
  -e BUNDLES_DIR=/mappings/src/test/java/snapshots/io/github/bzkf/obdstofhir/mapper/mii/ObdsToFhirBundleMapperTest/ \
  obds-dq
```

## A note on FHIR date/dateTime columns

SQL on FHIR v2 views represent FHIR `date`/`dateTime` values as plain
ISO-8601 strings (to preserve partial-precision values such as year-only
dates), rather than as typed Spark columns like Pathling's old `.extract()`
API did. `src/obds_dq/extract.py` casts the columns used for date
comparisons to Spark's `date` type right after extraction.
