#!/usr/bin/env bash

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

docker compose -f "${SCRIPT_DIR}/compose.dev.yaml" exec kafka bash -c "/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --delete --all-groups"
docker compose -f "${SCRIPT_DIR}/compose.dev.yaml" exec kafka bash -c "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic 'fhir.*'"
docker compose -f "${SCRIPT_DIR}/compose.dev.yaml" exec kafka bash -c "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic 'onkostar.*'"
