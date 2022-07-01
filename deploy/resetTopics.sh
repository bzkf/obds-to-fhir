#!/bin/sh
docker compose -f docker-compose.dev.yml exec kafka1 bash -c "kafka-consumer-groups --bootstrap-server localhost:9092 --delete --all-groups"
docker compose -f docker-compose.dev.yml exec kafka1 bash -c "kafka-topics --bootstrap-server localhost:9092 --delete --topic 'fhir.onkostar'"
docker compose -f docker-compose.dev.yml exec kafka1 bash -c "kafka-topics --bootstrap-server localhost:9092 --delete --topic 'onkostar.*'"
docker compose -f docker-compose.dev.yml exec kafka1 bash -c "kafka-topics --bootstrap-server localhost:9092 --delete --topic 'onkostar-*'"
