#!/bin/sh
docker compose -f compose.dev.yaml exec kafka bash -c "kafka-consumer-groups.sh --bootstrap-server localhost:9092 --delete --all-groups"
docker compose -f compose.dev.yaml exec kafka bash -c "kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic 'fhir.obds'"
docker compose -f compose.dev.yaml exec kafka bash -c "kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic 'onkostar.*'"
docker compose -f compose.dev.yaml exec kafka bash -c "kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic 'onkostar-*'"
