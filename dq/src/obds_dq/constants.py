from datetime import date, datetime

# Patient birth dates must fall within this range.
MIN_BIRTH_DATE: date = date(1900, 1, 1)
MAX_BIRTH_DATE: date = date(datetime.now().year, 12, 31)

TOD_PROFILE = (
    "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/"
    "StructureDefinition/mii-pr-onko-tod"
)
