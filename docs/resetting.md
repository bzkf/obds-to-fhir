# Resetting the Kafka Streams Application

Note that you will have to change the `--input-topics` to match the name of the topic where the ONKOSTAR Meldungen are sent to.

## obds-to-fhir v3

```sh
bin/kafka-streams-application-reset.sh --bootstrap-server=localhost:9092 --application-id=obds-meldung-exp-v3-processor --input-topics=onkostar.meldung-export --to-earliest
bin/kafka-topics.sh --bootstrap-server=localhost:9092 --delete --topic fhir.obds.bundles,obds.v3.grouped,fhir.pseudonymized.obds.bundles
```

## obds-to-fhir v2

```sh
bin/kafka-streams-application-reset.sh --bootstrap-server=localhost:9092 --application-id=obds-meldung-exp-grouped-processor --input-topics=dwh.onkostar.meldung-export-obdsv3-adtv2,fhir.obds.Observation --to-earliest

# if pseudonymization is enabled, also add this to `--topic`:
# ,fhir.pseudonymized.obds.Patient,fhir.pseudonymized.obds.Procedure,fhir.pseudonymized.obds.Condition,fhir.pseudonymized.obds.Observation,fhir.pseudonymized.obds.MedicationStatement
bin/kafka-topics.sh --bootstrap-server=localhost:9092 --delete --topic fhir.obds.Patient,fhir.obds.Procedure,fhir.obds.Condition,fhir.obds.Observation,fhir.obds.MedicationStatement
```
