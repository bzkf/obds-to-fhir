# obds-to-fhir

This project contains a Kafka Stream processor that creates FHIR resources from Onkostar oBDS-XML data and writes them to a FHIR Topic.

## Used FHIR profiles

See [package.json](package.json) for a list of used packages and their versions.

## Observations

- Histologie (<https://simplifier.net/oncology/histologie>)
- Grading (<https://simplifier.net/oncology/grading>)
- TNMc (<https://simplifier.net/oncology/tnmc>)
- TNMp (<https://simplifier.net/oncology/tnmp>)
- Fernmetastasen (<https://simplifier.net/oncology/fernmetastasen-duplicate-2>)
- Tod Ursache (<https://simplifier.net/oncology/todursache>)

## Condition

- Primärdiagnose (<https://simplifier.net/oncology/primaerdiagnose>)

## Procedure

- Operation (<https://simplifier.net/oncology/operation>)
- Strahlentherapie (<https://simplifier.net/oncology/strahlentherapie>)

## MedicationStatement

- Systemtherapie (<https://simplifier.net/oncology/systemtherapie>)

## Patient

- Patient (<https://simplifier.net/medizininformatikinitiative-modulperson>)

## Dev

### Dev Stack

- Kafka Broker: `$DOCKER_HOST_IP:9094`
- Kafka Connect: `$DOCKER_HOST_IP:8083`
- AKHQ: `$DOCKER_HOST_IP:8080`
- Oracle DB: `jdbc:oracle:thin:@//$DOCKER_HOST_IP:1521/FREEPDB1` (Use credentials from init script)

### Run with

From the ./deploy folder:

dc up:

```sh
sh up.sh
```

dc down (except of Oracle database)

```sh
sh down.sh
```

deploy connectors

```sh
sh deploy-connectors.sh
```

reset topics

```sh
sh reset-topics.sh
```
