-- 55t_tests_term_lock_engine.sql
DO $$
DECLARE v_forced boolean;
BEGIN
    IF to_regclass('app.term_unlock_log') IS NULL THEN RAISE EXCEPTION 'DB-42 FAIL: app.term_unlock_log missing'; END IF;
    IF to_regprocedure('app.is_term_locked(uuid)') IS NULL THEN RAISE EXCEPTION 'DB-42 FAIL: is_term_locked(uuid) missing'; END IF;
    IF to_regprocedure('app.fn_relock_expired_terms(uuid)') IS NULL THEN RAISE EXCEPTION 'DB-42 FAIL: scheduled relock function missing'; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='app.term_lock'::regclass AND tgname='trg_term_lock_immutability' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'DB-42 FAIL: term lock immutability trigger missing';
    END IF;
    SELECT relrowsecurity AND relforcerowsecurity INTO v_forced
      FROM pg_class WHERE oid='app.term_unlock_log'::regclass;
    IF v_forced IS DISTINCT FROM true THEN RAISE EXCEPTION 'DB-42 FAIL: term unlock log must FORCE RLS'; END IF;
    IF app.is_term_locked(gen_random_uuid()) IS DISTINCT FROM true THEN
        RAISE EXCEPTION 'DB-42 FAIL: unknown term must fail closed';
    END IF;
END $$;
