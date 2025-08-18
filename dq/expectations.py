import great_expectations as gx
import config

expectations_conditions = [
    # Birth date validations
    #   gx.expectations.py.ExpectColumnValuesToBeBetween(
    #      column="date_of_birth", min_value=MIN_DATE, max_value=MAX_DATE
    #   ),
    # Deceased date validations
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="deceased_date_time",
        column_B="date_of_birth",
        ignore_row_if="either_value_is_missing",
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="deceased_date_time", min_value=config.MIN_DATE, max_value=config.MAX_DATE
    ),
    # asserted datetime validations
    #  gx.expectations.py.ExpectColumnValuesToBeBetween(
    #      column="asserted_date", min_value=MIN_DATE, max_value=MAX_DATE
    #  ),
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
        row_condition='col("icd_code").notnull() and col("condition_icd_code").rlike("^C61")'
    )
]

expectations_observations = [
    # Birth date validations
    # gx.expectations.py.ExpectColumnValuesToBeBetween(
    #      column="date_of_birth", min_value=MIN_DATE, max_value=MAX_DATE
    #    ),

    #TODO prüfen: machen wenig Sinn code System und meta Profil
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
    #  gx.expectations.py.ExpectColumnValuesToBeBetween(
    #    column="effective_date_time", min_value=MIN_DATE, max_value=MAX_DATE
    # ),
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
        column="code_code",
        regex=r"^5-6[5-9]|^5-7[0-1]",
        condition_parser="great_expectations",
        row_condition='col("gender") == "male" and col("code_code").notnull()"'
    ),
    gx.expectations.ExpectColumnValuesToNotMatchRegex(  ## bfarm OPS 2023 version, 5-60...5-64 Operations on the male genital organs
        column="code_code",
        regex=r"^5-6[0-4]",
        condition_parser="great_expectations",
        row_condition='col("gender") == "female" and col("code_code").notnull()" '
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="performed_date_time", min_value=config.MIN_DATE, max_value=config.MAX_DATE
    ),
]

expectations_medicationStatements = [
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="effectivePeriod_end", column_B="effectivePeriod_start",
        ignore_row_if="either_value_is_missing",
        #parse_strings_as_datetimes=True
        #ignore_row_if="both_values_are_equal" # wont work 2
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="effectivePeriod_start", min_value=config.MIN_DATE, max_value=config.MAX_DATE,
        # parse_strings_as_datetimes=True
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="effectivePeriod_start", column_B="date_of_birth", ignore_row_if="either_value_is_missing",
        # parse_strings_as_datetimes=True
    )
]

expectations_observations = [
    # Birth date validations
    # gx.expectations.py.ExpectColumnValuesToBeBetween(
    #      column="date_of_birth", min_value=MIN_DATE, max_value=MAX_DATE
    #    ),

    #TODO prüfen: machen wenig Sinn code System und meta Profil
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
    #  gx.expectations.py.ExpectColumnValuesToBeBetween(
    #    column="effective_date_time", min_value=MIN_DATE, max_value=MAX_DATE
    # ),
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
        column="code_code",
        regex=r"^5-6[5-9]|^5-7[0-1]",
        condition_parser="great_expectations",
        row_condition='col("gender") == "male" and col("code_code").notnull()"'
    ),
    gx.expectations.ExpectColumnValuesToNotMatchRegex(  ## bfarm OPS 2023 version, 5-60...5-64 Operations on the male genital organs
        column="code_code",
        regex=r"^5-6[0-4]",
        condition_parser="great_expectations",
        row_condition='col("gender") == "female" and col("code_code").notnull()" '
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="performed_date_time", min_value=config.MIN_DATE, max_value=config.MAX_DATE
    ),
]

expectations_medicationStatements = [
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="effectivePeriod_end", column_B="effectivePeriod_start",
        ignore_row_if="either_value_is_missing",
        #parse_strings_as_datetimes=True
        #ignore_row_if="both_values_are_equal" # wont work 2
    ),
    gx.expectations.ExpectColumnValuesToBeBetween(
        column="effectivePeriod_start", min_value=config.MIN_DATE, max_value=config.MAX_DATE,
        # parse_strings_as_datetimes=True
    ),
    gx.expectations.ExpectColumnPairValuesAToBeGreaterThanB(
        column_A="effectivePeriod_start", column_B="date_of_birth", ignore_row_if="either_value_is_missing",
        # parse_strings_as_datetimes=True
    )
]

