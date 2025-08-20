import os
from pathlib import Path
from datetime import datetime

HERE = Path(os.path.abspath(os.path.dirname(__file__)))
ICD_10_GM_SYSTEM = "http://fhir.de/CodeSystem/bfarm/icd-10-gm"
OPS_SYSTEM = "http://fhir.de/CodeSystem/bfarm/ops"
ASSERTED_DATE_EXTENSION = (
    "http://hl7.org/fhir/StructureDefinition/condition-assertedDate"
)
FERNMETASTASE = "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-fernmetastasen"
TOD = "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-tod"
ALL_LEISTUNSGZUSTAND ="https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-allgemeiner-leistungszustand-ecog"
MII_PR_ONKO_WEITERE_KLASSIFIKATIONEN = (
        "https://www.medizininformatik-initiative.de/"
        + "fhir/ext/modul-onko/StructureDefinition/"
        + "mii-pr-onko-weitere-klassifikationen"
)
PRIMAERDIAGNOSE = "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-diagnose-primaertumor"
OP = "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-operation"
MIN_DATE = datetime(1900, 1, 1)
MAX_DATE = datetime(year=datetime.now().year, month=12, day=31)
checkpoint_path = (HERE / "dq/check_dq.py").as_posix()
date_columns = [
    "date_of_birth",
    "deceased_date_time",
    "asserted_date"
]

snapshots_dir = (
        HERE
        / "../src/test/java/snapshots/org/miracum/streams/ume/obdstofhir/"
        / "mapper/mii/ObdsToFhirBundleMapperTest/"
).as_posix()

checkpoint_path = (HERE / "dq/check_dq.py").as_posix()
