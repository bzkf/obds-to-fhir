import great_expectations as gx
from config import config

expectations_conditions = [
    # Birth date validations
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="date_of_birth", min_value=config.MIN_DATE, max_value=config.MAX_DATE
    ),
    # Deceased date validations
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="deceased_date_time",
        column_B="date_of_birth",
        ignore_row_if="either_value_is_missing",
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="deceased_date_time",
        min_value=config.MIN_DATE,
        max_value=config.MAX_DATE,
    ),
    # asserted datetime validations
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="asserted_date", min_value=config.MIN_DATE, max_value=config.MAX_DATE
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="asserted_date",
        column_B="date_of_birth",
        or_equal=True,
        description="Check if diagnosis asserted date is on or after the date of birth",
    ),
    # Gender expectation with condition code
    gx.expectations.ExpectColumnValuesToBeInSet(
        column="gender",
        value_set=["male"],
        condition_parser="great_expectations",
        row_condition='col("icd_code") == "C61"',
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="patient_id",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="condition_id",
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
    gx.expectations.ExpectColumnValuesToBeInSet(
        column="gender",
        value_set=["male"],
        condition_parser="great_expectations",
        row_condition='col("icd_code").notnull() and'
        + 'col("condition_icd_code").rlike("^C61")',
    ),
]


expectations_observations = [
    # Birth date validations
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="date_of_birth", min_value=config.MIN_DATE, max_value=config.MAX_DATE
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="code_system",
        condition_parser="great_expectations",
        row_condition='col("meta_profile") != '
        + f'"{config.MII_PR_ONKO_WEITERE_KLASSIFIKATIONEN}"',
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="code_code",
        condition_parser="great_expectations",
        row_condition='col("meta_profile") != '
        + f'"{config.MII_PR_ONKO_WEITERE_KLASSIFIKATIONEN}"',
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="patient_id",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="observation_id",
    ),
    # EffectiveDateTime validations
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="effective_date_time",
        column_B="date_of_birth",
        or_equal=True,
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="deceased_date_time",
        column_B="effective_date_time",
        or_equal=True,
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="effective_date_time",
        min_value=config.MIN_DATE,
        max_value=config.MAX_DATE,
    ),
    gx.expectations.ExpectColumnValuesToBeUnique(
        column="patient_id",
        row_condition='col("meta_profile") == ' + f'"{config.TOD}"',
        condition_parser="great_expectations",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="deceased",
        row_condition='col("deceased_date_time").notnull()',
        condition_parser="great_expectations",
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
        column="patient_id",
    ),
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="procedure_id",
    ),
    # EffectiveDateTime validations
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="performed_date_time",
        column_B="date_of_birth",
        or_equal=True,
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="deceased_date_time",
        column_B="performed_date_time",
        or_equal=True,
    ),
    gx.expectations.ExpectColumnValuesToNotMatchRegex(
        # bfarm OPS 2023 version, 5-65...5-71 - Operations on the female genital organs
        column="ops_code",
        regex=r"^5-6[5-9]|^5-7[0-1]",
        condition_parser="great_expectations",
        row_condition='col("gender") == "male" and col("ops_code").notnull()"',
    ),
    # bfarm OPS 2023 version, 5-60...5-64 Operations on the male genital organs
    gx.expectations.ExpectColumnValuesToNotMatchRegex(
        column="ops_code",
        regex=r"^5-6[0-4]",
        condition_parser="great_expectations",
        row_condition='col("gender") == "female" and col("ops_code").notnull()" ',
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="performed_date_time",
        min_value=config.MIN_DATE,
        max_value=config.MAX_DATE,
    ),
]

expectations_medicationStatements = [
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="effectivePeriod_end",
        column_B="effectivePeriod_start",
        ignore_row_if="either_value_is_missing",
        or_equal=True,
        # parse_strings_as_datetimes=True
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="effectivePeriod_start",
        min_value=config.MIN_DATE,
        max_value=config.MAX_DATE,
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="effectivePeriod_start",
        column_B="date_of_birth",
        ignore_row_if="either_value_is_missing",
    ),
]

expectations_condition_procedure = [
    gx.expectations.ExpectColumnValuesToNotMatchRegex(
        column="ops_code",
        regex=r"^5-8[7-8]",
        row_condition='col("gender") == "male" and'
        + 'col("icd_code") != "C50.9" and col("ops_code").notnull()',
        condition_parser="great_expectations",
    ),
    # procedure date  > diagnosis date
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="performed_date_time",
        column_B="asserted_date",
        ignore_row_if="either_value_is_missing",
        condition_parser="great_expectations",
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="performed_date_time",
        column_B="asserted_date",
        ignore_row_if="either_value_is_missing",
        row_condition='col("meta_profile_con") =='
        + f'"{config.PRIMAERDIAGNOSE}"'
        + 'and col("meta_profile_procedure") == '
        + f'"{config.OP}"',
        condition_parser="great_expectations",
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
    )
]

expectations_ecog_death = [
    gx.expectations.ExpectColumnValuesToNotBeNull(
        column="meta_profile_death",
        row_condition='col("value_codeable_concept_coding_code") == 5',
        condition_parser="great_expectations",
        # description="check if ecog == 5, there must be a death observation",
    )
]
