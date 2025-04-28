# obds-to-fhir

[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/bzkf/obds-to-fhir/badge)](https://scorecard.dev/viewer/?uri=github.com/bzkf/obds-to-fhir)
[![SLSA 3](https://slsa.dev/images/gh-badge-level3.svg)](https://slsa.dev)

This project maps [oBDS XML reports](https://www.basisdatensatz.de/basisdatensatz) to FHIR® resources conforming to the [Medizininformatik Initiative - Modul Onkologie](https://simplifier.net/guide/mii-ig-modul-onkologie-2024-de?version=current) profiles.

## Getting Started

Prerequisites:

- Container Runtime (Docker >= v28.0.1, containerd >= v1.7.25, Podman >= v5.4.1)
- Plugin to run [Compose files](https://www.compose-spec.io/) (docker compose, nerdctl, podman-compose)

Obds-to-fhir can be configured to either read oBDS XMLs from a filesystem directory or from an ONKOSTAR database table.

### Reading oBDS XML from a directory

> [!NOTE]
> This requires the oBDS XMLs to be in version 3 format.

A configuration profile called `process-from-directory` exists, which configures the application to read oBDS XMLs from
a directory called `/opt/obds-to-fhir/obds-input` inside the container, map them to FHIR resources and write the output
bundles to both `/opt/obds-to-fhir/fhir-output` and a Kafka topic called `fhir.obds`: [application-process-from-directory.yml](src/main/resources/application-process-from-directory.yml).

See [compose.yaml](tests/compose/compose.yaml) and [compose.process-from-directory.yaml](tests/compose/compose.process-from-directory.yaml)
for an example configuration used as part of the integration tests.

### Reading oBDS XML from an ONKOSTAR database

<!--TODO-->

## Used FHIR profiles

See [package.json](package.json) for a list of used packages and their versions.

## Dev

### oBDS v3 code generation

This task is included in default build task. In case you want to just generate oBDS v3 classes run:

```sh
./gradlew xsd2java
```

### Topology

![Stream Topology generated via https://zz85.github.io/kafka-streams-viz/ using http://localhost:8080/actuator/kafkastreamstopology](docs/img/obds-to-fhir-topology-v3.png)

### Dev Stack

- Kafka Broker: `$DOCKER_HOST_IP:9094`
- Kafka Connect: `$DOCKER_HOST_IP:8083`
- AKHQ: `$DOCKER_HOST_IP:8084`
- Oracle DB: `jdbc:oracle:thin:@//$DOCKER_HOST_IP:1521/FREEPDB1` (User: `DWH_ROUTINE`, Password: `devPassword`)

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
