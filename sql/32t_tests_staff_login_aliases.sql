-- Contract checks for staff login aliases.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema='app' AND table_name='user_login_alias'
    ) THEN RAISE EXCEPTION 'LOGIN_ALIAS_TABLE_MISSING'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE schemaname='app' AND indexname='uq_user_login_alias_email'
    ) THEN RAISE EXCEPTION 'LOGIN_ALIAS_UNIQUENESS_MISSING'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
         WHERE schemaname='app' AND tablename='user_login_alias'
           AND policyname='p_tenant' AND permissive='PERMISSIVE'
    ) THEN RAISE EXCEPTION 'LOGIN_ALIAS_TENANT_RLS_MISSING'; END IF;
END$$;
