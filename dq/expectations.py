import great_expectations as gx
from great_expectations.core.result_format import ResultFormat
from config import config

expectations_conditions = [
    # Birth date validations
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="date_of_birth",
        min_value=config.MIN_DATE,
        max_value=config.MAX_DATE,
        description="date of birth is within range of 1900-today",
    ),
    # Deceased date validations
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="deceased_date_time",
        column_B="date_of_birth",
        ignore_row_if="either_value_is_missing",
        or_equal=True,
        description="deceased date is after date of birth",
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="deceased_date_time",
        min_value=config.MIN_DATE,
        max_value=config.MAX_DATE,
        description="deceased date is within range of 1900-today",
    ),
    # asserted datetime validations
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="asserted_date",
        min_value=config.MIN_DATE,
        max_value=config.MAX_DATE,
        description="asserted date is within range of 1900-today",
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="asserted_date",
        column_B="date_of_birth",
        or_equal=True,
        description="diagnosis asserted date >= the date of birth",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="asserted_date",
        description="Asserted date (Diagnosedatum) should not be null",
    ),
    # Gender expectation with condition code
    gx.expectations.ExpectColumnValuesToBeInSet(
        column="gender",
        value_set=["male"],
        condition_parser="great_expectations",
        row_condition='col("icd_code") == "C61"',
        description="Check if gender is male for prostate cancer diagnoses",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="patient_id", description="Check if patient_id is always provided"
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="condition_id", description="Check if condition_id is always provided"
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="icd_version", description="Check if ICD version is always provided"
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="icd_code",
        description="Check if ICD code is always provided.",
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="recorded_date",
        column_B="date_of_birth",
        or_equal=True,
        description="Check if diagnosis recorded date is on or after the date of birth",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="recorded_date",
        description="Recorded date (Meldedatum) should not be null",
    ),
]


expectations_observations = [
    # Birth date validations
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="date_of_birth",
        min_value=config.MIN_DATE,
        max_value=config.MAX_DATE,
        description="date of birth is within range of 1900-today",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="code_system",
        condition_parser="great_expectations",
        row_condition='col("meta_profile") != '
        + f'"{config.MII_PR_ONKO_WEITERE_KLASSIFIKATIONEN}"',
        description="code system should not be null "
        + "for observations except Weitere Klassifikationen",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="code_code",
        condition_parser="great_expectations",
        row_condition='col("meta_profile") != '
        + f'"{config.MII_PR_ONKO_WEITERE_KLASSIFIKATIONEN}"',
        description="code should not be null "
        + "for observations except Weitere Klassifikationen",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="patient_id", description="Check if patient_id is always provided"
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="observation_id",
        description="Check if observation_id is always provided",
    ),
    # EffectiveDateTime validations
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="effective_date_time",
        column_B="date_of_birth",
        or_equal=True,
        description="Effective date time (Observation) >= date of birth",
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="deceased_date_time",
        column_B="effective_date_time",
        or_equal=True,
        description="Deceased date time >= effective date time (Observation)",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="effective_date_time",
        description="Effective date time should not be null for observations",
        condition_parser="great_expectations",
        row_condition='col("value_codeable_concept_coding_system") != '
        + f'"{config.MII_CS_ONKO_STUDIENTEILNAHME}" or '
        + 'col("value_codeable_concept_coding_code") != "N")',
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="effective_date_time",
        min_value=config.MIN_DATE,
        max_value=config.MAX_DATE,
        description="effective date time (Observation) is within range of 1900-today",
    ),
    gx.expectations.ExpectColumnValuesToBeUnique(
        column="patient_id",
        row_condition='col("meta_profile") == ' + f'"{config.TOD}"',
        condition_parser="great_expectations",
        description="There should be only one death observation per patient",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="deceased",
        row_condition='col("deceased_date_time").notnull()',
        condition_parser="great_expectations",
        description="deceased_date_time.notNull <-> Patient.deceased.notNull",
    ),
    # Wenn Todesmeldung <->dann Patient.deceased=true
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="deceased_date_time",
        row_condition='col("meta_profile") == ' + f'"{config.TOD}"',
        condition_parser="great_expectations",
        description="Todesmeldung <->dann Patient.deceasedDateTime.notNull",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="deceased_date_time",
        row_condition='col("meta_profile") == ' + f'"{config.TOD}"',
        condition_parser="great_expectations",
        description="Todesmeldung <->dann Patient.deceased=true",
    ),
]

expectations_procedure = [
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="patient_id", description="Check if patient_id is always provided"
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="procedure_id", description="Check if procedure_id is always provided"
    ),
    # EffectiveDateTime validations
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="performed_date_time",
        column_B="date_of_birth",
        or_equal=True,
        description="performed date time (Procedure) >= date of birth",
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="deceased_date_time",
        column_B="performed_date_time",
        or_equal=True,
        description="Deceased date time >= performed date time (Procedure)",
    ),
    gx.expectations.ExpectColumnValuesToNotMatchRegex(
        # bfarm OPS 2023 version, 5-65...5-71 - Operations on the female genital organs
        column="ops_code",
        regex=r"^5-6[5-9]|^5-7[0-1]",
        condition_parser="great_expectations",
        row_condition='col("gender") == "male" and col("ops_code").notnull()"',
        description=(
            "OPS codes 5-65 to 5-71 (Operations on the female genital organs) "
            + "should not be present for male patients"
        ),
    ),
    # bfarm OPS 2023 version, 5-60...5-64 Operations on the male genital organs
    gx.expectations.ExpectColumnValuesToNotMatchRegex(
        column="ops_code",
        regex=r"^5-6[0-4]",
        condition_parser="great_expectations",
        row_condition='col("gender") == "female" and col("ops_code").notnull()" ',
        description=(
            "OPS codes 5-60 to 5-64 (Operations on the male genital organs) "
            + "should not be present for female patients"
        ),
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="performed_date_time",
        min_value=config.MIN_DATE,
        max_value=config.MAX_DATE,
        description="performed date time (Procedure) is within range of 1900-today",
    ),
    # performed date time should not be null when performedPeriod_start is null,
    # aka at least one of them should be present
    # !...notnull() because I couldn't get .isNull() to work in GX...
    gx.expectations.ExpectColumnValuesToBeNull(
        column="performed_date_time",
        row_condition='col("performedPeriod_start").notnull()',
        condition_parser="great_expectations",
        description=(
            "performed date time can be null when performedPeriod_start is not null"
        ),
    ),
    gx.expectations.ExpectColumnValuesToBeNull(
        column="performedPeriod_start",
        row_condition='col("performed_date_time").notnull()',
        condition_parser="great_expectations",
        description="performedPeriod_start can be null when performed date time "
        + "is not null",
    ),
]

expectations_medicationStatements = [
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="effectivePeriod_end",
        column_B="effectivePeriod_start",
        ignore_row_if="either_value_is_missing",
        or_equal=True,
        description="medicationStatement effectivePeriod_end >= effectivePeriod_start",
        # parse_strings_as_datetimes=True
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="effectivePeriod_start",
        min_value=config.MIN_DATE,
        max_value=config.MAX_DATE,
        description=(
            "medicationStatement effectivePeriod_start is within range of 1900-today"
        ),
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="effectivePeriod_start",
        column_B="date_of_birth",
        ignore_row_if="either_value_is_missing",
        description="medicationStatement effectivePeriod_start >= date of birth",
        or_equal=True,
    ),
]

expectations_condition_procedure = [
    gx.expectations.ExpectColumnValuesToNotMatchRegex(
        column="ops_code",
        regex=r"^5-8[7-8]",
        row_condition='col("gender") == "male" and'
        + 'col("icd_code") != "C50.9" and col("ops_code").notnull()',
        condition_parser="great_expectations",
        description=(
            "OPS codes 5-87 and 5-88 (Breast surgery) should not be present for"
            + " male patients except for prostate cancer patients (ICD C50.9)"
        ),
    ),
    # procedure date  > diagnosis date
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="performed_date_time",
        column_B="asserted_date",
        ignore_row_if="either_value_is_missing",
        or_equal=True,
        condition_parser="great_expectations",
        description="Procedure date should be on or after diagnosis date",
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="performed_date_time",
        column_B="asserted_date",
        ignore_row_if="either_value_is_missing",
        row_condition='col("meta_profile_con") =='
        + f'"{config.PRIMAERDIAGNOSE}"'
        + 'and col("meta_profile_procedure") == '
        + f'"{config.OP}"',
        or_equal=True,
        condition_parser="great_expectations",
        description=(
            "Procedure date (mii-onko-operation) >= "
            "diagnosis date (mii-pr-onko-diagnose-primaertumor)"
        ),
    ),
]
# Second Proto - Duplicate entries
# 1. Expectation for Fernmetastasen, logic- a patient can have multiple fernmetastasen
# but if the records are describing as same event we need to catch that.
expectations_fernmetastasen = [
    gx.expectations.ExpectCompoundColumnsToBeUnique(
        column_list=[
            "patient_id",
            "effective_date_time",
            "value_codeable_concept_coding_code",
        ],
        ignore_row_if="any_value_is_missing",
        condition_parser="great_expectations",
        description=(
            "There should be no duplicate fernmetastasen records for the same patient"
        ),
    )
]

expectations_ecog_death = [
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="meta_profile_death",
        row_condition='col("value_codeable_concept_coding_code") == 5',
        condition_parser="great_expectations",
        description="there should be a death observation if ecog == 5",
        # description="check if ecog == 5, there must be a death observation",
    )
]

expectations_death_observations = [
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="value_codeable_concept_coding_code",
        description="there should be an ICD code representing the cause of death",
        result_format=ResultFormat.COMPLETE,
    ),
    # at least from quick testing, it looks like GX automatically adds a check
    # for NULLs:
    # df.filter(F.expr((interpretation_tod_tumorbedingt_code IS NOT NULL)
    # AND (NOT (NOT (interpretation_tod_tumorbedingt_code IN (U, NULL))))))
    # So we have to create 2 expectations, one for NULL and one for 'U'.
    # There might be a better way to do this.
    gx.expectations.ExpectColumnValuesToNotBeInSet(
        column="interpretation_tod_tumorbedingt_code",
        value_set=["U"],
        description="whether or not the death is caused by the tumor"
        + " should be indicated, not 'Unknown'",
        result_format=ResultFormat.COMPLETE,
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="interpretation_tod_tumorbedingt_code",
        description="whether or not the death is caused by the tumor"
        + " should be indicated, not NULL",
        result_format=ResultFormat.COMPLETE,
    ),
]

expectations_death_observations_distinct_dates = [
    gx.expectations.ExpectColumnValuesToBeInSet(
        column="distinct_dates_count",
        value_set=[1],
        description="there should only be one distinct death date per"
        + " patient across all death observations",
    ),
]
