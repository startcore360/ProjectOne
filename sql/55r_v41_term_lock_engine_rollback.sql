-- 55r_v41_term_lock_engine_rollback.sql
BEGIN;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM app.term_unlock_log) THEN
        RAISE EXCEPTION 'TERM_LOCK_ROLLBACK_BLOCKED: unlock history exists; export and archive it first';
    END IF;
END $$;
DROP FUNCTION IF EXISTS app.fn_relock_expired_terms(uuid);
DROP FUNCTION IF EXISTS app.is_term_locked(uuid);
DROP TRIGGER IF EXISTS trg_term_lock_immutability ON app.term_lock;
DROP FUNCTION IF EXISTS app.fn_term_lock_immutability();
DROP TABLE IF EXISTS app.term_unlock_log;
COMMIT;
