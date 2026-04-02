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
    "connection.url": "jdbc:oracle:thin:@//oracle:1521/FREEPDB1",
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
    "transforms.ValueToKey.fields": "ID",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false"
  }
}'


curl -X POST \
  http://localhost:8083/connectors \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{
  "name": "onkostar-patient-connector",
  "config": {
    "tasks.max": "1",
    "connector.class": "io.confluent.connect.jdbc.JdbcSourceConnector",
    "connection.url": "jdbc:oracle:thin:@//oracle:1521/FREEPDB1",
    "connection.user": "DWH_ROUTINE",
    "connection.password": "devPassword",
    "schema.pattern": "DWH_ROUTINE",
    "topic.prefix": "onkostar.PATIENT",
    "query": "SELECT pat.ID, pat.LETZTE_INFORMATION, pat.STERBEDATUM, pat.STERBEDATUM_ACC, pat.PATIENTEN_ID, pat.ANGELEGT_AM, pat.ZU_LOESCHEN, pat.PATIENTEN_IDS_VORHER, pat.BEARBEITET_AM, b.STERBEDATUM AS BT_STERBEDATUM, b.LETZTEINFORMATION AS BT_LETZTEINFORMATION FROM patient pat JOIN prozedur pr ON (pr.patient_id = pat.id) JOIN dk_bestoftumor b ON (b.id = pr.id)",
    "mode": "timestamp",
    "timestamp.column.name": "BEARBEITET_AM",
    "validate.non.null": "true",
    "numeric.mapping": "best_fit_eager_double",
    "transforms": "ValueToKey",
    "transforms.ValueToKey.type": "org.apache.kafka.connect.transforms.ValueToKey",
    "transforms.ValueToKey.fields": "PATIENTEN_ID",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false",
    "db.timezone": "Europe/Berlin",
    "timestamp.granularity": "micros_iso_datetime_string"
  }
}'
