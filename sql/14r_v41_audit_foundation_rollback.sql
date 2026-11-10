-- 14r_v41_audit_foundation_rollback.sql
-- PURPOSE: Rollback script for 14_v41_audit_foundation.sql.
--
-- DB-A04: the audit.AuditLog entity maps actor_role_code, scope_type, institution_id and
-- correlation_id. Run this rollback ONLY together with a code revert to a build whose entity does
-- not map them; otherwise every legacy audit insert fails (and prod ddl-auto=validate refuses to
-- start). The table itself and its rows are kept: audit evidence is never dropped by a rollback.
-- Tenant audit lives in app.audit_outbox (01_schema/03_bindings) and is not touched here.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'audit_logs') THEN
        DROP TRIGGER IF EXISTS trg_audit_logs_guard ON public.audit_logs;

        ALTER TABLE public.audit_logs
            DROP COLUMN IF EXISTS actor_role_code,
            DROP COLUMN IF EXISTS scope_type,
            DROP COLUMN IF EXISTS institution_id,
            DROP COLUMN IF EXISTS correlation_id;
    END IF;
END $$;

DROP FUNCTION IF EXISTS public.fn_audit_logs_guard();
