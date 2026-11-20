-- Contract checks for E4 support-session storage and OD-15 category configuration.
\set ON_ERROR_STOP 1
DO $$
DECLARE
    v_categories jsonb;
    v_expiry_check text;
BEGIN
    IF to_regclass('platform.support_session') IS NULL THEN
        RAISE EXCEPTION 'SUPPORT_SESSION_TABLE_MISSING';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'platform.support_session'::regclass
           AND conname = 'fk_support_session_grant'
    ) THEN RAISE EXCEPTION 'SUPPORT_SESSION_GRANT_FK_MISSING'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'platform.support_session'::regclass
           AND conname = 'fk_support_session_platform_user'
    ) THEN RAISE EXCEPTION 'SUPPORT_SESSION_PLATFORM_USER_FK_MISSING'; END IF;
    SELECT pg_get_constraintdef(oid) INTO v_expiry_check
      FROM pg_constraint
     WHERE conrelid = 'platform.support_session'::regclass
       AND conname = 'ck_support_session_expiry';
    IF v_expiry_check IS NULL OR v_expiry_check NOT LIKE '%01:00:00%' THEN
        RAISE EXCEPTION 'SUPPORT_SESSION_ONE_HOUR_CAP_MISSING: %', v_expiry_check;
    END IF;
    SELECT default_value INTO v_categories
      FROM ref.setting_definition
     WHERE setting_key = 'support_ticket_categories';
    IF v_categories IS DISTINCT FROM '["LOGIN","ACCESS","DATA","BILLING","PERFORMANCE","BUG","OTHER"]'::jsonb THEN
        RAISE EXCEPTION 'SUPPORT_TICKET_CATEGORIES_SEED_MISMATCH';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE schemaname='platform' AND indexname='ix_support_session_grant_active'
    ) THEN RAISE EXCEPTION 'SUPPORT_SESSION_ACTIVE_GRANT_INDEX_MISSING'; END IF;
END$$;
