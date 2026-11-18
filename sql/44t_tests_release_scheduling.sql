-- E7-S1 contract check. Run after 44_v41_release_scheduling.sql.
\set ON_ERROR_STOP 1
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='platform' AND table_name='release' AND column_name='scheduled_at') THEN
  RAISE EXCEPTION 'RELEASE_SCHEDULED_AT_MISSING';
 END IF;
END $$;
