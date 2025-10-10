import os
from datetime import datetime
from pathlib import Path

import typed_settings as ts

HERE = Path(os.path.abspath(os.path.dirname(__file__)))

date_columns = ["date_of_birth", "deceased_date_time", "asserted_date"]

snapshots_dir = (
    HERE
    / "../src/test/java/snapshots/org/miracum/streams/ume/obdstofhir/"
    / "mapper/mii/ObdsToFhirBundleMapperTest/"
).as_posix()


@ts.settings
class Config:
    spark_master: str = "local[*]"
    spark_driver_memory: str = "4g"
    spark_install_packages_and_exit: bool = False
    spark_warehouse_dir: str = (HERE / "warehouse").as_posix()
    spark_checkpoint_path: str = (HERE / "checkpoints").as_posix()
    aws_access_key_id: str = "admin"
    aws_secret_access_key: str = ts.secret(default="miniopass")
    s3_endpoint: str = "localhost:9000"
    s3_connection_ssl_enabled: str = "false"
    bundles_dir: str = snapshots_dir
    delta_dir: str = "s3a://fhir/warehouse"
    read_from_delta: bool = False
    ICD_10_GM_SYSTEM = "http://fhir.de/CodeSystem/bfarm/icd-10-gm"
    OPS_SYSTEM = "http://fhir.de/CodeSystem/bfarm/ops"
    ASSERTED_DATE_EXTENSION = (
        "http://hl7.org/fhir/StructureDefinition/condition-assertedDate"
    )
    FERNMETASTASE = (
        "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/"
        "StructureDefinition/"
        "mii-pr-onko-fernmetastasen"
    )
    TOD = (
        "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/"
        "StructureDefinition/mii-pr-onko-tod"
    )
    ALL_LEISTUNSGZUSTAND = (
        "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/"
        "CodeSystem/mii-cs-onko-allgemeiner-leistungszustand-ecog"
    )
    MII_PR_ONKO_WEITERE_KLASSIFIKATIONEN = (
        "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/"
        "StructureDefinition/"
        "mii-pr-onko-weitere-klassifikationen"
    )
    PRIMAERDIAGNOSE = (
        "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/"
        "StructureDefinition/mii-pr-onko-diagnose-primaertumor"
    )
    OP = (
        "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/"
        "StructureDefinition/mii-pr-onko-operation"
    )
    MIN_DATE = datetime(1900, 1, 1)
    MAX_DATE = datetime(year=datetime.now().year, month=12, day=31)


config = ts.load(Config, appname="obds-to-fhir-dq-checks", env_prefix="")
