ALTER SESSION SET CONTAINER = FREEPDB1;
create table DWH_ROUTINE.PROZEDUR
(
    id                            NUMBER,
    patient_id                    NUMBER
)
/

INSERT INTO DWH_ROUTINE.PROZEDUR (id, patient_id)
values (21, 1);
INSERT INTO DWH_ROUTINE.PROZEDUR (id, patient_id)
values (22, 2);
INSERT INTO DWH_ROUTINE.PROZEDUR (id, patient_id)
values (23, 3);
