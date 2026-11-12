-- B3.0 rollback: remove staff format storage and the student per-institution guard.
DROP TABLE IF EXISTS app.staff_code_sequence;
DROP INDEX IF EXISTS app.ux_student_code_sequence_institution;
