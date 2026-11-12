-- B3.0 rollback: remove code-generation functions, generated sequences and staff format storage.
DROP FUNCTION IF EXISTS app.institution_code_format_locked(uuid,uuid,text);
DROP FUNCTION IF EXISTS app.next_institution_code_value(uuid,uuid,text);
DROP FUNCTION IF EXISTS app.ensure_institution_code_sequence(uuid,uuid,text);

DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT schemaname, sequencename
      FROM pg_sequences
     WHERE schemaname='app'
       AND (sequencename ~ '^edu_student_[0-9a-f]{32}$'
         OR sequencename ~ '^edu_staff_[0-9a-f]{32}$')
  LOOP
    EXECUTE format('DROP SEQUENCE IF EXISTS %I.%I', r.schemaname, r.sequencename);
  END LOOP;
END;
$$;

DROP TABLE IF EXISTS app.staff_code_sequence;
DROP INDEX IF EXISTS app.ux_student_code_sequence_institution;
