"""Extracts tabular views of obds-to-fhir Patient/Condition/Observation
resources using Pathling's SQL on FHIR v2 view engine.

The output Parquet files are consumed by the dq/checks project, which runs
the actual data quality checks (sparkdq, dqx, pydeequ). This project is kept
separate because Pathling >=9 requires PySpark 4.x, while none of the three
check frameworks support Spark 4 yet (see dq/README.md).
"""

import os
from pathlib import Path

from loguru import logger
from pathling import PathlingContext

REPO_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_BUNDLES_DIR = (
    REPO_ROOT
    / "mappings/src/test/java/snapshots/io/github/bzkf"
    / "obdstofhir/mapper/mii/ObdsToFhirBundleMapperTest"
)
DEFAULT_OUTPUT_DIR = Path(__file__).resolve().parents[3] / "data"

ASSERTED_DATE_EXTENSION = (
    "http://hl7.org/fhir/StructureDefinition/condition-assertedDate"
)

PATIENT_SELECT = [
    {
        "column": [
            {"path": "id", "name": "patient_id"},
            {"path": "birthDate", "name": "date_of_birth"},
        ]
    }
]

CONDITION_SELECT = [
    {
        "column": [
            {"path": "id", "name": "condition_id"},
            {"path": "subject.reference", "name": "patient_reference"},
            {
                "path": f"extension('{ASSERTED_DATE_EXTENSION}').valueDateTime.first()",
                "name": "asserted_date",
            },
        ]
    }
]

OBSERVATION_SELECT = [
    {
        "column": [
            {"path": "id", "name": "observation_id"},
            {"path": "subject.reference", "name": "patient_reference"},
            {"path": "focus.reference.first()", "name": "focus_reference"},
            {"path": "effectiveDateTime", "name": "effective_date_time"},
            {"path": "meta.profile.first()", "name": "meta_profile"},
        ]
    }
]


def main() -> None:
    bundles_dir = Path(os.environ.get("BUNDLES_DIR", DEFAULT_BUNDLES_DIR))
    output_dir = Path(os.environ.get("OUTPUT_DIR", DEFAULT_OUTPUT_DIR))
    output_dir.mkdir(parents=True, exist_ok=True)

    logger.info(f"Reading FHIR bundles from {bundles_dir}")
    pc = PathlingContext.create(enable_extensions=True, enable_terminology=False)
    data = pc.read.bundles(str(bundles_dir), ["Patient", "Condition", "Observation"])

    for resource_type, select, name in [
        ("Patient", PATIENT_SELECT, "patients"),
        ("Condition", CONDITION_SELECT, "conditions"),
        ("Observation", OBSERVATION_SELECT, "observations"),
    ]:
        view = data.view(resource=resource_type, select=select).drop_duplicates()
        row_count = view.count()
        logger.info(f"{resource_type}: extracted {row_count} rows")
        destination = output_dir / f"{name}.parquet"
        view.write.mode("overwrite").parquet(str(destination))
        logger.info(f"Wrote {destination}")


if __name__ == "__main__":
    main()
