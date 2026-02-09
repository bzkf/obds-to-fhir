# Configuration

<!-- Update this file by running helm-docs --sort-values-order file --chart-search-root src/main/resources/ -f ../../../mappings/src/main/resources/application-mappings.yml -f application.yml -t config.md.gotmpl -o config.md && mv src/main/resources/config.md docs/config.md -->

## FHIR systems

See [application-mappings.yml](../mappings/src/main/resources/application-mappings.yml) for FHIR systems configuration settings.

## Environment Variables

The config values below can be set as environment variables by replacing all `.` and `-` with `_`
and optionally upper-casing the value. E.g. to change the default system for the Specimen.id, change:

`fhir.systems.identifiers.histologie-specimen-id`

to

`FHIR_SYSTEMS_IDENTIFIERS_HISTOLOGIE_SPECIMEN_ID`

and pass it as an environment variable to the container.

## Configuration file

You can also load extra configuration files. See the [official Spring Boot docs](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files) for details.

## Patient Reference Generation

By default, the job creates FHIR Patient resources which are referenced by each resource.
If you already create Patient resources from a different source, you may want to set
`FHIR_MAPPINGS_CREATE_PATIENT_RESOURCES_ENABLED=false` and specify a Reference generation
strategy that allows for creating matching logical IDs to your existing Patient resources:

Set `FHIR_MAPPINGS_PATIENT_REFERENCE_GENERATION_STRATEGY` to one of the values below:

- `SHA256_HASHED_PATIENT_IDENTIFIER_SYSTEM_AND_PATIENT_ID` (default)

    The reference is computed as the SHA-256 hash of `fhir.systems.identifiers.patient-id` + "|" + Patient_ID.
- `MD5_HASHED_PATIENT_ID`

    The reference is computed as the MD5 hash of Patient_ID.
- `PATIENT_ID_UNDERSCORES_REPLACED_WITH_DASHES`

    Patient_ID and any occurrence of `_` is replaced by `-`.
- `PATIENT_ID`

    Patient_ID is taken as-is.
- `FHIR_SERVER_LOOKUP`

    Query a FHIR server to get the logical ID of the Patient resource from the identifier value.
    This sends a `GET /Patient?identifier=<fhir.systems.identifiers.patient-id>|<Patient_ID>` query
    to the server specified in `fhir.mappings.patient-reference-generation.fhir-server.base-url` and returns the
    `Patient.id` in the response. If the result is empty or more than one Patient resource is found,
    the job exits with an error.

- `RECORD_ID_DATABASE_LOOKUP`

    Query a database table to lookup an internal ID from the given Patient_ID. The database connection
    and SQL query to run can be configured in the `fhir.mappings.patient-reference-generation.record-id-database`
    settings.

> [!IMPORTANT]
> For both the `FHIR_SERVER_LOOKUP` and `RECORD_ID_DATABASE_LOOKUP` strategy, if the ID wasn't found in either the
> or the record database and `FHIR_MAPPINGS_CREATE_PATIENT_RESOURCES_ENABLED` is set to `true`, a FHIR Patient
> resource is still created and added to the resulting Bundle using the default
> `SHA256_HASHED_PATIENT_IDENTIFIER_SYSTEM_AND_PATIENT_ID` strategy to create its `Patient.id` and all references
> to it.
> If no ID is found and `FHIR_MAPPINGS_CREATE_PATIENT_RESOURCES_ENABLED` is set to `false`, an exception is thrown
> and the application is stopped.

## All Available Settings

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| fhir.mappings.meta.source | string | `""` | Value to set for the meta.source field in all generated resources |
| fhir.mappings.patient-id-regex | string | `"^(.*)$"` | regex to apply to the `Patient_ID` before setting it to `Patient.identifier.value`. Must contain one capture group. Use, e.g. `^0*([1-9]\d*)$` to match any numeric value, ignoring any prefixed 0s. |
| fhir.mappings.create-patient-resources.enabled | bool | `true` | Whether Patient resources should be created. Useful to disable if you already create FHIR resources from a different source. |
| fhir.mappings.patient-reference-generation.strategy | string | `"SHA256_HASHED_PATIENT_IDENTIFIER_SYSTEM_AND_PATIENT_ID"` | How the Resource.subject.reference to the Patient resources should be generated. You should set `fhir.mappings.create-patient-resources.enabled=false` when changing this from the default to avoid creating additional, unreferenced Patient resources. |
| fhir.mappings.patient-reference-generation.fhir-server.base-url | string | `""` | the base URL of a FHIR server used to lookup an existing logical Patient id from a local identifier. E.g. `https://fhir.example.com/fhir` |
| fhir.mappings.patient-reference-generation.fhir-server.auth.basic.enabled | bool | `false` | use HTTP Basic Authentication for FHIR server authentication |
| fhir.mappings.patient-reference-generation.fhir-server.auth.basic.username | string | `""` | the username |
| fhir.mappings.patient-reference-generation.fhir-server.auth.basic.password | string | `""` | the password |
| fhir.mappings.patient-reference-generation.pseudonymize-patient-id.enabled | bool | `false` | if enabled, before looking up the Patient resource from its identifier, the oBDS Patient_ID is first pseudonymized using the FHIR Pseudonymizer service at the `fhir-pseudonymizer.base-url` |
| fhir.mappings.patient-reference-generation.pseudonymize-patient-id.fhir-pseudonymizer.base-url | string | `""` | the base URL of the FHIR Pseudonymizer used to pseudonymize the Patient_ID before looking up the actual Patient.id in the FHIR server. Invokes the `$de-identify` operation. E.g. `https://fhir.example.com/fhir` |
| fhir.mappings.patient-reference-generation.record-id-database.jdbc-url | string | `""` | JDBC url to connect to to query an internal RecordID from a Patient_ID |
| fhir.mappings.patient-reference-generation.record-id-database.username | string | `""` | username for authentication |
| fhir.mappings.patient-reference-generation.record-id-database.password | string | `""` | password for authentication |
| fhir.mappings.patient-reference-generation.record-id-database.query | string | `"SELECT RecordID\nFROM lookup\nWHERE PatientID = ?\n"` | the SQL query to run. It should include one `?` placeholder which is replaced with the Patient_ID from the oBDS message. |
| fhir.mappings.substanz-to-atc.extra-mappings-file-path | string | `""` | path to a CSV file containing additional Substanz -> ATC code mappings. The CSV file needs to have two headings: `Substanzbezeichnung;ATC-Code`, all columns must be seperated by `;`. The job always ships with the mappings from <https://plattform65c.atlassian.net/wiki/spaces/UMK/pages/15532506/Substanzen>, if the extra file contains duplicate mappings to the default ones, the ones from this file take precedence. |
| obdsv2-to-v3.mapper.disable-schema-validation | bool | `false` | Disable XML Schema validation for oBDS v2 -> v3 mapped Meldungen |
| spring.profiles.active | string | `"mappings,default"` |  |
| spring.application.name | string | `"obds-to-fhir"` |  |
| spring.cloud.function.definition | string | `"getMeldungExportObdsV3Processor"` |  |
| spring.cloud.stream.bindings.getMeldungExportObdsProcessor-in-0.destination | string | `"onkostar.MELDUNG_EXPORT"` |  |
| spring.cloud.stream.bindings.getMeldungExportObdsProcessor-in-1.destination | string | `"fhir.obds.Observation"` |  |
| spring.cloud.stream.bindings.getMeldungExportObdsProcessor-out-0.destination | string | `"fhir.obds.MedicationStatement"` |  |
| spring.cloud.stream.bindings.getMeldungExportObdsProcessor-out-0.producer.partition-count | string | `"${FHIR_OUTPUT_TOPIC_PARTITION_COUNT:12}"` |  |
| spring.cloud.stream.bindings.getMeldungExportObdsProcessor-out-1.destination | string | `"fhir.obds.Observation"` |  |
| spring.cloud.stream.bindings.getMeldungExportObdsProcessor-out-1.producer.partition-count | string | `"${FHIR_OUTPUT_TOPIC_PARTITION_COUNT:12}"` |  |
| spring.cloud.stream.bindings.getMeldungExportObdsProcessor-out-2.destination | string | `"fhir.obds.Procedure"` |  |
| spring.cloud.stream.bindings.getMeldungExportObdsProcessor-out-2.producer.partition-count | string | `"${FHIR_OUTPUT_TOPIC_PARTITION_COUNT:12}"` |  |
| spring.cloud.stream.bindings.getMeldungExportObdsProcessor-out-3.destination | string | `"fhir.obds.Condition"` |  |
| spring.cloud.stream.bindings.getMeldungExportObdsProcessor-out-3.producer.partition-count | string | `"${FHIR_OUTPUT_TOPIC_PARTITION_COUNT:12}"` |  |
| spring.cloud.stream.bindings.getMeldungExportObdsProcessor-out-4.destination | string | `"fhir.obds.Patient"` |  |
| spring.cloud.stream.bindings.getMeldungExportObdsProcessor-out-4.producer.partition-count | string | `"${FHIR_OUTPUT_TOPIC_PARTITION_COUNT:12}"` |  |
| spring.cloud.stream.bindings.getMeldungExportObdsV3Processor-in-0.destination | string | `"${INPUT_TOPIC_NAME:onkostar.MELDUNG_EXPORT}"` | Name of the topic where ONKOSTAR oBDS Meldungen are read from |
| spring.cloud.stream.bindings.getMeldungExportObdsV3Processor-out-0.destination | string | `"${FHIR_OUTPUT_TOPIC_NAME:fhir.obds.bundles}"` | Name of the topic where the FHIR resources are written to |
| spring.cloud.stream.bindings.getMeldungExportObdsV3Processor-out-0.producer.partition-count | string | `"${FHIR_OUTPUT_TOPIC_PARTITION_COUNT:12}"` |  |
| spring.cloud.stream.kafka.streams.binder.configuration."cache.max.bytes.buffering" | int | `0` |  |
| spring.cloud.stream.kafka.streams.binder.configuration."max.request.size" | string | `"${MAX_REQUEST_SIZE:20971520}"` |  |
| spring.cloud.stream.kafka.streams.binder.configuration."num.stream.threads" | string | `"${NUM_STREAM_THREADS:1}"` |  |
| spring.cloud.stream.kafka.streams.binder.configuration.default."key.serde" | string | `"org.apache.kafka.common.serialization.Serdes$StringSerde"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsProcessor-in-0.consumer.application-id | string | `"obds-meldung-exp-grouped-processor"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsProcessor-in-1.consumer.valueSerde | string | `"org.miracum.kafka.serializers.KafkaFhirSerde"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsProcessor-in-1.consumer.application-id | string | `"obds-meldung-exp-condition-processor"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsProcessor-out-0.producer.valueSerde | string | `"org.miracum.kafka.serializers.KafkaFhirSerde"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsProcessor-out-0.producer.configuration."compression.type" | string | `"${COMPRESSION_TYPE:gzip}"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsProcessor-out-1.producer.valueSerde | string | `"org.miracum.kafka.serializers.KafkaFhirSerde"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsProcessor-out-1.producer.configuration."compression.type" | string | `"${COMPRESSION_TYPE:gzip}"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsProcessor-out-2.producer.valueSerde | string | `"org.miracum.kafka.serializers.KafkaFhirSerde"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsProcessor-out-2.producer.configuration."compression.type" | string | `"${COMPRESSION_TYPE:gzip}"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsProcessor-out-3.producer.valueSerde | string | `"org.miracum.kafka.serializers.KafkaFhirSerde"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsProcessor-out-3.producer.configuration."compression.type" | string | `"${COMPRESSION_TYPE:gzip}"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsProcessor-out-4.producer.valueSerde | string | `"org.miracum.kafka.serializers.KafkaFhirSerde"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsProcessor-out-4.producer.configuration."compression.type" | string | `"${COMPRESSION_TYPE:gzip}"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsV3Processor-in-0.consumer.application-id | string | `"${KAFKA_GROUP_ID:obds-meldung-exp-v3-processor}"` | the Kafka consumer group id. Useful to change if multiple versions of this job are run concurrently |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsV3Processor-out-0.producer.valueSerde | string | `"org.miracum.kafka.serializers.KafkaFhirSerde"` |  |
| spring.cloud.stream.kafka.streams.bindings.getMeldungExportObdsV3Processor-out-0.producer.configuration."compression.type" | string | `"${COMPRESSION_TYPE:gzip}"` |  |
| spring.kafka.bootstrapServers | string | `"${BOOTSTRAP_SERVERS:localhost:9094}"` |  |
| spring.kafka."security.protocol" | string | `"${SECURITY_PROTOCOL:PLAINTEXT}"` |  |
| spring.kafka.ssl.trust-store-type | string | `"PKCS12"` |  |
| spring.kafka.ssl.trust-store-location | string | `"file://${SSL_TRUST_STORE:/opt/kafka-certs/ca.p12}"` |  |
| spring.kafka.ssl.trust-store-password | string | `"${SSL_TRUST_STORE_PASSWORD}"` |  |
| spring.kafka.ssl.key-store-type | string | `"PKCS12"` |  |
| spring.kafka.ssl.key-store-location | string | `"file://${SSL_KEY_STORE_FILE:/opt/kafka-certs/user.p12}"` |  |
| spring.kafka.ssl.key-store-password | string | `"${SSL_KEY_STORE_PASSWORD}"` |  |
| spring.kafka.producer.compression-type | string | `"gzip"` |  |
| spring.kafka.producer.value-serializer | string | `"org.miracum.kafka.serializers.KafkaFhirSerializer"` |  |
| spring.kafka.producer.properties."max.request.size" | string | `"${MAX_REQUEST_SIZE:20971520}"` |  |
| logging.pattern.console | string | `"%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr(${LOG_LEVEL_PATTERN:%5p}) %clr(-){faint} %clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %m %X %n${LOG_EXCEPTION_CONVERSION_WORD:%wEx}"` |  |
| management.endpoint.health.show-details | string | `"always"` |  |
| management.endpoint.health.probes.enabled | bool | `true` |  |
| management.endpoint.health.probes.add-additional-paths | bool | `true` |  |
| management.endpoints.web.exposure.include | string | `"health,prometheus,kafkastreamstopology"` |  |
| management.health.livenessstate.enabled | bool | `true` |  |
| management.health.readinessstate.enabled | bool | `true` |  |
| obds.process-from-directory.enabled | bool | `false` | if enabled, read oBDS XML exports from a directory instead of from Kafka |
| obds.process-from-directory.path | string | `""` | the folder path to read from. All XML files within this folder are read |
| obds.process-from-directory.output-to-kafka.enabled | bool | `false` | write the FHIR bundles to a Kafka topic |
| obds.process-from-directory.output-to-kafka.topic | string | `""` | name of the topic to write to |
| obds.process-from-directory.output-to-directory.enabled | bool | `false` | output the fir Bundles to a directory, 1 file per Bundle |
| obds.process-from-directory.output-to-directory.path | string | `""` | path to the directory to write Bundles to |
| obds.write-grouped-obds-to-kafka.enabled | bool | `false` | the oBDS Einzelmeldungen are internally combined to a single oBDS Export. If enabled, write the combined oBDS exports to a topic: one message per Patient_ID+Tumor_ID |
| obds.write-grouped-obds-to-kafka.topic | string | `"obds.v3.grouped"` | the name of the topic to write the combined oBDS export to. Helpful for debugging. |
