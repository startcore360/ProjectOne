-- Run after migrations 57 and 58 in a disposable database with the V4.0 schema loaded.
DO $$
DECLARE
  v_cols text[];
BEGIN
  SELECT array_agg(column_name ORDER BY column_name) INTO v_cols
  FROM information_schema.columns
  WHERE table_schema='app' AND table_name='academic_session'
    AND column_name='is_current';
  IF v_cols IS NULL THEN RAISE EXCEPTION 'DB-T2-01 academic_session.is_current missing'; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ex_academic_session_nonoverlap') THEN
    RAISE EXCEPTION 'DB-T2-02 academic session exclusion missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_program_degree_level') THEN
    RAISE EXCEPTION 'DB-T2-03 program degree level check missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_section_capacity') THEN
    RAISE EXCEPTION 'DB-T2-04 section capacity check missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                 WHERE n.nspname='app' AND c.relname='program_curriculum_version') THEN
    RAISE EXCEPTION 'DB-T2-05 curriculum version table missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='program_curriculum_version') THEN
    RAISE EXCEPTION 'DB-T2-06 curriculum version RLS policy missing';
  END IF;
END $$;
