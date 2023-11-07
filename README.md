# obds-to-fhir

This project contains a Kafka Stream processor that creates FHIR resources from Onkostar oBDS-XML data and writes them to a FHIR Topic.

## Used FHIR profiles

See [package.json](package.json) for a list of used profiles and their versions.

## Dev

### Dev Stack

- Zookeeper: `$DOCKER_HOST_IP:2181`
- Kafka Broker: `$DOCKER_HOST_IP:9092`
- Kafka Rest Proxy: `$DOCKER_HOST_IP:8082`
- Kafka Connect: `$DOCKER_HOST_IP:8083`
- AKHQ: `$DOCKER_HOST_IP:8080`
- Oracle DB: `jdbc:oracle:thin:@//$DOCKER_HOST_IP:1521/COGN12` (Use credentials from init script)

### Prerequisites

Download Confluent JDBC Connector and place it in folder `./connectors` before starting up.

JDBC Connector Download: <https://www.confluent.io/hub/confluentinc/kafka-connect-jdbc>

### Run with

dc up:

```sh
\$ sh up.sh
```

dc down (except of Oracle database)

```sh
\$ sh down.sh
```

deploy connectors

```sh
\$ sh deploy-connectors.sh
```

reset topics

```sh
\$ sh reset-topics.sh
```
