-- 14r_v41_audit_foundation_rollback.sql
-- PURPOSE: Rollback script for 14_v41_audit_foundation.sql

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'audit_logs') THEN
        DROP TRIGGER IF EXISTS trg_audit_logs_guard ON public.audit_logs;
        DROP FUNCTION IF EXISTS public.fn_audit_logs_guard();

        ALTER TABLE public.audit_logs
            DROP COLUMN IF EXISTS actor_role_code,
            DROP COLUMN IF EXISTS scope_type,
            DROP COLUMN IF EXISTS institution_id,
            DROP COLUMN IF EXISTS correlation_id;
    END IF;
END $$;
