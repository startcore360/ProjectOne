-- patch_audit_logs_constraint.sql
-- -----------------------------------------------------------------------
-- PURPOSE: fixes the legacy public.audit_logs CHECK constraint and schema
-- to accept all OD-09 audit event types and metadata columns.
-- -----------------------------------------------------------------------

ALTER TABLE public.audit_logs
    DROP CONSTRAINT IF EXISTS audit_logs_event_type_check;

ALTER TABLE public.audit_logs
    ADD COLUMN IF NOT EXISTS actor_role_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS scope_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS institution_id UUID,
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(64);

ALTER TABLE public.audit_logs
    ADD CONSTRAINT audit_logs_event_type_check
    CHECK (event_type IN (
        'LOGIN_SUCCESS',
        'LOGIN_FAILURE',
        'LOGOUT',
        'ACCOUNT_CREATED',
        'PASSWORD_CHANGED',
        'PROFILE_UPDATED',
        'SESSION_REVOKED',
        'INSTITUTION_CREATED_DRAFT',
        'INSTITUTION_ACTIVATED',
        'INSTITUTION_BLOCK_REQUESTED',
        'INSTITUTION_BLOCKED',
        'INSTITUTION_BLOCK_REJECTED',
        'ROLE_ASSIGNED',
        'ROLE_ASSIGNMENT_ENDED',
        'ROLE_SWITCHED',
        'USER_BULK_UPLOADED',
        'TOP_USER_CREATED',
        'USER_DEACTIVATED',
        'USER_PASSWORD_RESET',
        'USER_UNLOCKED',
        'DEPARTMENT_CREATED',
        'PROGRAM_CREATED',
        'ACADEMIC_SESSION_CREATED',
        'MASTER_CALENDAR_EVENT_CREATED',
        'TENANT_ACTIVATED',
        'PRINCIPAL_LEAVE_APPROVER_SET',
        'VC_LEAVE_APPROVER_SET',
        'PLAN_CREATED',
        'PLAN_UPDATED',
        'TENANT_SUSPENDED',
        'TENANT_ARCHIVED',
        'TENANT_REACTIVATED',
        'TENANT_SETTINGS_UPDATED',
        'CUSTOM_ROLE_CREATED',
        'CUSTOM_ROLE_RETIRED',
        'APPROVAL_DELEGATED',
        'APPROVAL_DELEGATION_CANCELLED',
        'APPROVAL_BULK_DECIDED',
        'TERM_LOCKED',
        'TERM_UNLOCKED',
        'GUARDIAN_LINKED',
        'GUARDIAN_CHANGE_REQUESTED',
        'GUARDIAN_CHANGE_APPROVED',
        'CREATE',
        'EDIT',
        'DELETE',
        'APPROVAL',
        'EXPORT',
        'DENIED_ATTEMPT',
        'SENSITIVE_VIEW'
    ));

-- Fix for PLATFORM_AUDIT_VIEW: allow edu_app to SELECT from platform.audit_outbox
GRANT USAGE ON SCHEMA platform TO edu_app;
GRANT SELECT ON platform.audit_outbox TO edu_app;
