#!/bin/sh
curl -X POST \
  http://localhost:8083/connectors \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{
  "name": "onkostar-meldung-export-connector",
  "config": {
    "tasks.max": "1",
    "connector.class": "io.confluent.connect.jdbc.JdbcSourceConnector",
    "connection.url": "jdbc:oracle:thin:@//oracle:1521/XEPDB1",
    "connection.user": "DWH_ROUTINE",
    "connection.password": "devPassword",
    "schema.pattern": "DWH_ROUTINE",
    "topic.prefix": "onkostar.MELDUNG_EXPORT",
    "query": "SELECT * FROM (SELECT * FROM STG_ONKOSTAR_LKR_MELDUNG_EXPORT WHERE TYP != '-1' AND VERSIONSNUMMER IS NOT NULL) o",
    "mode": "incrementing",
    "incrementing.column.name": "ID",
    "validate.non.null": "true",
    "numeric.mapping": "best_fit",
    "transforms": "ValueToKey",
    "transforms.ValueToKey.type": "org.apache.kafka.connect.transforms.ValueToKey",
    "transforms.ValueToKey.fields": "ID"
  }
}'
