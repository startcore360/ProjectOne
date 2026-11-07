-- 14_v41_audit_foundation.sql
-- PURPOSE: OD-09 Audit Foundation migration.
-- Extends the audit model with active role, scope type, institution ID and correlation ID,
-- and enforces append-only security semantics.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'audit_logs') THEN
        ALTER TABLE public.audit_logs
            ADD COLUMN IF NOT EXISTS actor_role_code VARCHAR(64),
            ADD COLUMN IF NOT EXISTS scope_type VARCHAR(32),
            ADD COLUMN IF NOT EXISTS institution_id UUID,
            ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(64);

        CREATE OR REPLACE FUNCTION public.fn_audit_logs_guard()
        RETURNS trigger AS $fn$
        BEGIN
            RAISE EXCEPTION 'audit_logs is append-only: updates and deletes are prohibited [OD-09]';
        END;
        $fn$ LANGUAGE plpgsql;

        DROP TRIGGER IF EXISTS trg_audit_logs_guard ON public.audit_logs;
        CREATE TRIGGER trg_audit_logs_guard
            BEFORE UPDATE OR DELETE ON public.audit_logs
            FOR EACH ROW EXECUTE FUNCTION public.fn_audit_logs_guard();
    END IF;
END $$;
