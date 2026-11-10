-- EDU 360 V4.1 — tenant top-post authority tests DB-113 … DB-125 (14_v41, 15_v41, 16_v41).
-- Run after 13_tests_platform_roles.sql in the test database.
\set ON_ERROR_STOP 1
SET client_min_messages = warning;

-- Test tenant setup
INSERT INTO app.tenant_local (tenant_id, tenant_type, time_zone)
VALUES ('00000000-0000-0000-0000-00000000a001', 'UNIVERSITY_GROUP', 'Asia/Kolkata')
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO app.user_account (tenant_id, user_account_id, user_type, display_name, email, status, created_by, updated_by)
VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-00000000a002', 'STAFF', 'Test Admin', 'admin.tpa@example.test', 'ACTIVE', '00000000-0000-0000-0000-00000000a002', '00000000-0000-0000-0000-00000000a002')
ON CONFLICT DO NOTHING;

-- DB-113 & DB-114: public.audit_logs append-only trigger (OD-09)
-- Seed row uses the AuditLog entity shape (bigint identity id, email NOT NULL, occurred_at) that
-- 14_v41 now creates (Q-01 / DB-A01); the assertions below are unchanged.
INSERT INTO public.audit_logs (id, event_type, email, occurred_at, details)
VALUES (900001, 'LOGIN_SUCCESS', 'test-actor@example.test', now(), 'Initial audit row')
ON CONFLICT DO NOTHING;

SELECT public.expect_fail('DB-113', 'OD-09 audit_logs prevents update',
  $q$UPDATE public.audit_logs SET details = 'Tampered' WHERE id = 900001$q$,
  'audit_logs is append-only');

SELECT public.expect_fail('DB-114', 'OD-09 audit_logs prevents delete',
  $q$DELETE FROM public.audit_logs WHERE id = 900001$q$,
  'audit_logs is append-only');

-- DB-115: app.user_active_role primary key and active role recording
INSERT INTO ref.standard_role (role_code, role_name, role_level, introduced_in_version)
VALUES ('ACADEMIC_LEAD_TPA', 'Academic Lead TPA', 'COLLEGE', '4.1')
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO app.role_definition (tenant_id, role_definition_id, role_code, role_name, role_origin, role_level, status, is_sensitive_role, created_by, updated_by)
VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-00000000c001', 'ACADEMIC_LEAD_TPA', 'Academic Lead TPA', 'STANDARD', 'COLLEGE', 'ACTIVE', false, '00000000-0000-0000-0000-00000000a002', '00000000-0000-0000-0000-00000000a002')
ON CONFLICT DO NOTHING;

INSERT INTO app.user_role_assignment (tenant_id, user_role_assignment_id, user_account_id, role_definition_id, scope_type, status, created_by, updated_by)
VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-00000000d001', '00000000-0000-0000-0000-00000000a002', '00000000-0000-0000-0000-00000000c001', 'TENANT', 'ACTIVE', '00000000-0000-0000-0000-00000000a002', '00000000-0000-0000-0000-00000000a002')
ON CONFLICT DO NOTHING;

SELECT public.expect_ok('DB-115', '15_v41 user_active_role accepts chosen role assignment',
  $q$INSERT INTO app.user_active_role (tenant_id, user_account_id, user_role_assignment_id)
     VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-00000000a002', '00000000-0000-0000-0000-00000000d001')
     ON CONFLICT (tenant_id, user_account_id) DO UPDATE SET user_role_assignment_id = EXCLUDED.user_role_assignment_id$q$);

-- DB-116: tenant_role_template unique template_name per tenant
INSERT INTO app.tenant_role_template (tenant_id, tenant_role_template_id, template_name, base_role_code, owner_level, created_by, updated_by)
VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-00000000e001', 'Dean Template', 'ACADEMIC_LEAD_TPA', 'COLLEGE', '00000000-0000-0000-0000-00000000a002', '00000000-0000-0000-0000-00000000a002')
ON CONFLICT DO NOTHING;

SELECT public.expect_fail('DB-116', '16_v41 template_name must be unique per tenant',
  $q$INSERT INTO app.tenant_role_template (tenant_id, tenant_role_template_id, template_name, base_role_code, owner_level, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-00000000e002', 'Dean Template', 'ACADEMIC_LEAD_TPA', 'COLLEGE', '00000000-0000-0000-0000-00000000a002', '00000000-0000-0000-0000-00000000a002')$q$,
  'uq_tenant_role_template_name');

-- DB-117: tenant_role_template status CHECK
SELECT public.expect_fail('DB-117', '16_v41 template status invalid',
  $q$INSERT INTO app.tenant_role_template (tenant_id, tenant_role_template_id, template_name, base_role_code, owner_level, status, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-00000000e003', 'Invalid Status Template', 'ACADEMIC_LEAD_TPA', 'COLLEGE', 'SUSPENDED', '00000000-0000-0000-0000-00000000a002', '00000000-0000-0000-0000-00000000a002')$q$,
  'ck_tenant_role_template_status');

-- DB-118: tenant_role_template owner_level CHECK
SELECT public.expect_fail('DB-118', '16_v41 template owner level invalid',
  $q$INSERT INTO app.tenant_role_template (tenant_id, tenant_role_template_id, template_name, base_role_code, owner_level, status, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-00000000e004', 'Invalid Level Template', 'ACADEMIC_LEAD_TPA', 'GLOBAL', 'ACTIVE', '00000000-0000-0000-0000-00000000a002', '00000000-0000-0000-0000-00000000a002')$q$,
  'ck_tenant_role_template_owner_level');

-- DB-119: tenant_role_template_action action_code foreign key check
SELECT public.expect_fail('DB-119', '16_v41 template action code invalid',
  $q$INSERT INTO app.tenant_role_template_action (tenant_id, tenant_role_template_action_id, tenant_role_template_id, action_code, created_by)
     VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-00000000e101', '00000000-0000-0000-0000-00000000e001', 'NONEXISTENT_ACTION_XYZ', '00000000-0000-0000-0000-00000000a002')$q$,
  'fk_tenant_role_template_action_code');

-- DB-120: role_definition status allows DRAFT/INACTIVE/ARCHIVED
SELECT public.expect_ok('DB-120', '16_v41 role_definition status allows DRAFT',
  $q$INSERT INTO app.role_definition (tenant_id, role_definition_id, role_code, role_name, role_origin, role_level, custom_scope, status, is_sensitive_role, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-00000000c002', 'DRAFT_ROLE_TPA', 'Draft Role TPA', 'CUSTOM', 'COLLEGE', 'INSTITUTION', 'DRAFT', false, '00000000-0000-0000-0000-00000000a002', '00000000-0000-0000-0000-00000000a002')$q$);

-- DB-121: role_action access_mode check
INSERT INTO ref.action_catalog (action_code, action_no, module_name, phase, description, is_approval_action)
VALUES ('ACTION_MODE_TEST', 274, 'Academic', 1, 'Mode test action', false)
ON CONFLICT DO NOTHING;

INSERT INTO app.role_action (tenant_id, role_action_id, role_definition_id, action_code, access_mode, created_by, updated_by)
VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-00000000c001', 'ACTION_MODE_TEST', 'SHARED', '00000000-0000-0000-0000-00000000a002', '00000000-0000-0000-0000-00000000a002')
ON CONFLICT DO NOTHING;

SELECT public.expect_fail('DB-121', '16_v41 role_action access_mode must be SHARED or HANDED_OVER',
  $q$UPDATE app.role_action SET access_mode = 'EXCLUSIVE' WHERE tenant_id = '00000000-0000-0000-0000-00000000a001' AND action_code = 'ACTION_MODE_TEST'$q$,
  'ck_role_action_mode');

-- DB-122 & DB-123: tenant_step_in append-only trigger
INSERT INTO app.tenant_step_in (tenant_id, step_in_id, actor_user_id, action_code, reason, started_at, expires_at)
VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-000000005001', '00000000-0000-0000-0000-00000000a002', 'ACTION_MODE_TEST', 'Emergency step-in for grading', now(), now() + interval '20 minutes')
ON CONFLICT DO NOTHING;

SELECT public.expect_fail('DB-122', '16_v41 tenant_step_in append-only prevents update',
  $q$UPDATE app.tenant_step_in SET reason = 'Modified reason' WHERE step_in_id = '00000000-0000-0000-0000-000000005001'$q$,
  'APPEND_ONLY');

SELECT public.expect_fail('DB-123', '16_v41 tenant_step_in append-only prevents delete',
  $q$DELETE FROM app.tenant_step_in WHERE step_in_id = '00000000-0000-0000-0000-000000005001'$q$,
  'APPEND_ONLY');

-- DB-124: tenant_step_in window <= 30 minutes
SELECT public.expect_fail('DB-124', '16_v41 tenant_step_in window cannot exceed 30 minutes',
  $q$INSERT INTO app.tenant_step_in (tenant_id, step_in_id, actor_user_id, action_code, reason, started_at, expires_at)
     VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-000000005002', '00000000-0000-0000-0000-00000000a002', 'ACTION_MODE_TEST', 'Step in exceeding max window', now(), now() + interval '45 minutes')$q$,
  'ck_tenant_step_in_window');

-- DB-125: tenant_step_in reason length check
SELECT public.expect_fail('DB-125', '16_v41 tenant_step_in reason must be at least 5 chars',
  $q$INSERT INTO app.tenant_step_in (tenant_id, step_in_id, actor_user_id, action_code, reason, started_at, expires_at)
     VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-000000005003', '00000000-0000-0000-0000-00000000a002', 'ACTION_MODE_TEST', 'Why', now(), now() + interval '15 minutes')$q$,
  'ck_tenant_step_in_reason');
