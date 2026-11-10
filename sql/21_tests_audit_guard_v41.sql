-- EDU 360 V4.1 — batch B6 database tests DB-150 … DB-201.
--   Q-01 tenant audit isolation on app.audit_outbox (D4 §5.23) + legacy public.audit_logs shape.
--   Q-04 never-allowed-action guard (20_v41_never_allowed_guard.sql).
--   Q-05 RLS isolation, append-only, step-in window, foreign keys and checks of files 14 to 16.
-- Run after 19_tests_user_sessions.sql in the test database (expect_* helpers: 04_tests.sql).
-- Seeding runs as the superuser; every isolation / guard check runs as edu_app with RLS forced.
\set ON_ERROR_STOP 1
SET client_min_messages = warning;

-- ------------------------------------------------------------------ seed (superuser)
INSERT INTO app.tenant_local (tenant_id, tenant_type, time_zone) VALUES
  ('00000000-0000-0000-0000-00000000b601', 'UNIVERSITY_GROUP', 'Asia/Kolkata'),
  ('00000000-0000-0000-0000-00000000b602', 'STANDALONE_COLLEGE', 'Asia/Kolkata')
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO app.user_account (tenant_id, user_account_id, user_type, display_name, email, status, created_by, updated_by) VALUES
  ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b611', 'STAFF', 'B6 Tenant One User', 'b6.one@example.test', 'ACTIVE', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611'),
  ('00000000-0000-0000-0000-00000000b602', '00000000-0000-0000-0000-00000000b612', 'STAFF', 'B6 Tenant Two User', 'b6.two@example.test', 'ACTIVE', '00000000-0000-0000-0000-00000000b612', '00000000-0000-0000-0000-00000000b612')
ON CONFLICT DO NOTHING;

INSERT INTO app.institution (tenant_id, institution_id, institution_type, institution_code, institution_name, status, created_by, updated_by) VALUES
  ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b621', 'COLLEGE', 'B6-C1', 'B6 College One', 'DRAFT', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611'),
  ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b622', 'COLLEGE', 'B6-C2', 'B6 College Two', 'DRAFT', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')
ON CONFLICT DO NOTHING;

INSERT INTO ref.action_catalog (action_code, action_no, module_name, phase, description, is_approval_action) VALUES
  ('TENANT_EXPORT_APPROVE',    901, 'Governance and data protection', 1, 'Approve tenant export', true),
  ('TENANT_DELETION_APPROVE',  902, 'Governance and data protection', 1, 'Approve tenant deletion', true),
  ('RETENTION_CHANGE_APPROVE', 903, 'Governance and data protection', 1, 'Approve retention change', true),
  ('SALARY_INDIVIDUAL_VIEW',   904, 'HR and payroll', 1, 'View individual salaries', false),
  ('PAYROLL_APPROVE_TOTALS',   905, 'HR and payroll', 1, 'Approve payroll totals', true),
  ('ACTION_B6_NORMAL',         906, 'Academic', 1, 'B6 ordinary action', false),
  ('ACTION_B6_LOGIN',          907, 'Sign-in and account', 1, 'B6 audit action', false)
ON CONFLICT (action_code) DO NOTHING;

INSERT INTO ref.standard_role (role_code, role_name, role_level, introduced_in_version) VALUES
  ('PAYROLL_OFFICER_B6', 'Payroll Officer B6', 'COLLEGE', '4.1'),
  ('CLERK_B6', 'Clerk B6', 'COLLEGE', '4.1')
ON CONFLICT (role_code) DO NOTHING;
INSERT INTO ref.standard_role_action (standard_role_action_id, role_code, action_code) VALUES
  (990601, 'PAYROLL_OFFICER_B6', 'SALARY_INDIVIDUAL_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO app.role_definition (tenant_id, role_definition_id, role_code, role_name, role_origin, role_level, base_standard_role, custom_scope, status, is_sensitive_role, created_by, updated_by) VALUES
  ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b631', 'CUSTOM_B6', 'Custom B6', 'CUSTOM', 'COLLEGE', 'PAYROLL_OFFICER_B6', 'INSTITUTION', 'ACTIVE', false, '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611'),
  ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b632', 'PAYROLL_OFFICER_B6', 'Payroll Officer B6', 'STANDARD', 'COLLEGE', NULL, NULL, 'ACTIVE', false, '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611'),
  ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b633', 'CLERK_B6', 'Clerk B6', 'STANDARD', 'COLLEGE', NULL, NULL, 'ACTIVE', false, '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611'),
  ('00000000-0000-0000-0000-00000000b602', '00000000-0000-0000-0000-00000000b634', 'CLERK_B6', 'Clerk B6', 'STANDARD', 'COLLEGE', NULL, NULL, 'ACTIVE', false, '00000000-0000-0000-0000-00000000b612', '00000000-0000-0000-0000-00000000b612')
ON CONFLICT DO NOTHING;

INSERT INTO app.user_role_assignment (tenant_id, user_role_assignment_id, user_account_id, role_definition_id, scope_type, status, created_by, updated_by) VALUES
  ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b641', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b632', 'TENANT', 'ACTIVE', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611'),
  ('00000000-0000-0000-0000-00000000b602', '00000000-0000-0000-0000-00000000b642', '00000000-0000-0000-0000-00000000b612', '00000000-0000-0000-0000-00000000b634', 'TENANT', 'ACTIVE', '00000000-0000-0000-0000-00000000b612', '00000000-0000-0000-0000-00000000b612')
ON CONFLICT DO NOTHING;

INSERT INTO app.user_active_role (tenant_id, user_account_id, user_role_assignment_id) VALUES
  ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b641'),
  ('00000000-0000-0000-0000-00000000b602', '00000000-0000-0000-0000-00000000b612', '00000000-0000-0000-0000-00000000b642')
ON CONFLICT DO NOTHING;

INSERT INTO app.tenant_role_template (tenant_id, tenant_role_template_id, template_name, base_role_code, owner_level, is_standard, created_by, updated_by) VALUES
  ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b651', 'B6 Custom Payroll', 'PAYROLL_OFFICER_B6', 'COLLEGE', false, '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611'),
  ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b652', 'B6 Standard Payroll', 'PAYROLL_OFFICER_B6', 'COLLEGE', true, '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611'),
  ('00000000-0000-0000-0000-00000000b602', '00000000-0000-0000-0000-00000000b653', 'B6 Tenant Two Clerk', 'CLERK_B6', 'COLLEGE', false, '00000000-0000-0000-0000-00000000b612', '00000000-0000-0000-0000-00000000b612')
ON CONFLICT DO NOTHING;
INSERT INTO app.tenant_role_template_action (tenant_id, tenant_role_template_action_id, tenant_role_template_id, action_code, created_by) VALUES
  ('00000000-0000-0000-0000-00000000b602', '00000000-0000-0000-0000-00000000b669', '00000000-0000-0000-0000-00000000b653', 'ACTION_B6_NORMAL', '00000000-0000-0000-0000-00000000b612')
ON CONFLICT DO NOTHING;

INSERT INTO app.tenant_step_in (tenant_id, step_in_id, actor_user_id, action_code, reason, started_at, expires_at) VALUES
  ('00000000-0000-0000-0000-00000000b602', '00000000-0000-0000-0000-00000000b679', '00000000-0000-0000-0000-00000000b612', 'ACTION_B6_NORMAL', 'Tenant two step-in evidence', now(), now() + interval '10 minutes')
ON CONFLICT DO NOTHING;

-- Tenant two's audit row, written directly (the superuser bypasses RLS).
INSERT INTO app.audit_outbox (tenant_id, audit_event_id, actor_kind, actor_user_id, active_role_code, operation, entity_type, outcome, correlation_id, payload_hash, created_by, updated_by) VALUES
  ('00000000-0000-0000-0000-00000000b602', '00000000-0000-0000-0000-00000000b692', 'USER', '00000000-0000-0000-0000-00000000b612', 'CLERK_B6', 'LOGIN', 'LOGIN_SUCCESS', 'SUCCESS', 'b6-corr-t2', repeat('b', 64), '00000000-0000-0000-0000-00000000b612', '00000000-0000-0000-0000-00000000b612')
ON CONFLICT DO NOTHING;

-- ================================================================== Q-01: tenant audit store
SET ROLE edu_app;
SELECT set_config('app.tenant_id', '00000000-0000-0000-0000-00000000b601', false);
SELECT set_config('app.institution_scope', 'ALL', false);
SELECT set_config('app.institution_ids', '', false);

SELECT public.expect_ok('DB-150', 'Q-01 tenant audit row written to app.audit_outbox under the own tenant',
  $q$INSERT INTO app.audit_outbox (tenant_id, audit_event_id, actor_kind, actor_user_id, active_role_code, operation, entity_type, outcome, correlation_id, payload_hash, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b691', 'USER', '00000000-0000-0000-0000-00000000b611', 'PAYROLL_OFFICER_B6', 'LOGIN', 'LOGIN_SUCCESS', 'SUCCESS', 'b6-corr-t1', repeat('a', 64), '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$);

SELECT public.expect_fail('DB-151', 'Q-01 a tenant session cannot write an audit row for another tenant (RLS WITH CHECK)',
  $q$INSERT INTO app.audit_outbox (tenant_id, audit_event_id, actor_kind, actor_user_id, active_role_code, operation, entity_type, outcome, correlation_id, payload_hash, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b602', '00000000-0000-0000-0000-00000000b694', 'USER', '00000000-0000-0000-0000-00000000b611', 'PAYROLL_OFFICER_B6', 'LOGIN', 'LOGIN_SUCCESS', 'SUCCESS', 'b6-corr-x', repeat('a', 64), '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$,
  'row-level security');

SELECT public.expect_value('DB-152', 'Q-01 tenant A cannot read tenant B audit rows',
  $q$SELECT count(*)::text FROM app.audit_outbox WHERE audit_event_id = '00000000-0000-0000-0000-00000000b692'$q$, '0');

SELECT public.expect_value('DB-153', 'Q-01 tenant A reads its own audit rows',
  $q$SELECT count(*)::text FROM app.audit_outbox WHERE correlation_id = 'b6-corr-t1'$q$, '1');

SELECT public.expect_fail('DB-154', 'Q-01 OD-09 the app role cannot update tenant audit rows',
  $q$UPDATE app.audit_outbox SET outcome = 'DENIED' WHERE audit_event_id = '00000000-0000-0000-0000-00000000b691'$q$,
  'permission denied|AUDIT_IMMUTABLE');

SELECT public.expect_fail('DB-155', 'Q-01 OD-09 the app role cannot delete tenant audit rows',
  $q$DELETE FROM app.audit_outbox WHERE audit_event_id = '00000000-0000-0000-0000-00000000b691'$q$,
  'permission denied|AUDIT_IMMUTABLE');

SELECT public.expect_fail('DB-156', 'Q-01 D4 r2: a USER audit row must carry the active role',
  $q$INSERT INTO app.audit_outbox (tenant_id, audit_event_id, actor_kind, actor_user_id, operation, entity_type, outcome, correlation_id, payload_hash, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b695', 'USER', '00000000-0000-0000-0000-00000000b611', 'LOGIN', 'LOGIN_SUCCESS', 'SUCCESS', 'b6-corr-r2', repeat('a', 64), '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$,
  'ck_audit_outbox_r2');

-- institution restriction (p_institution, RESTRICTIVE): a LIST scope of institution one only
SELECT set_config('app.institution_scope', 'LIST', false);
SELECT set_config('app.institution_ids', '00000000-0000-0000-0000-00000000b621', false);
SELECT public.expect_fail('DB-157', 'Q-01 OD-03 an institution-scoped session cannot write audit rows for another institution',
  $q$INSERT INTO app.audit_outbox (tenant_id, audit_event_id, institution_id, actor_kind, actor_user_id, active_role_code, operation, entity_type, outcome, correlation_id, payload_hash, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b696', '00000000-0000-0000-0000-00000000b622', 'USER', '00000000-0000-0000-0000-00000000b611', 'PAYROLL_OFFICER_B6', 'UPDATE', 'ROLE_ASSIGNED', 'SUCCESS', 'b6-corr-i2', repeat('a', 64), '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$,
  'row-level security');
SELECT public.expect_ok('DB-158', 'Q-01 OD-03 an institution-scoped session writes audit rows for its own institution',
  $q$INSERT INTO app.audit_outbox (tenant_id, audit_event_id, institution_id, actor_kind, actor_user_id, active_role_code, operation, entity_type, outcome, correlation_id, payload_hash, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b697', '00000000-0000-0000-0000-00000000b621', 'USER', '00000000-0000-0000-0000-00000000b611', 'PAYROLL_OFFICER_B6', 'UPDATE', 'ROLE_ASSIGNED', 'SUCCESS', 'b6-corr-i1', repeat('a', 64), '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$);

-- no tenant in the session: fail closed
SELECT set_config('app.tenant_id', '', false);
SELECT set_config('app.institution_scope', '', false);
SELECT set_config('app.institution_ids', '', false);
SELECT public.expect_value('DB-159', 'Q-01 PTD-02 a session with no tenant sees no tenant audit rows',
  $q$SELECT count(*)::text FROM app.audit_outbox WHERE audit_event_id IN ('00000000-0000-0000-0000-00000000b691', '00000000-0000-0000-0000-00000000b692')$q$, '0');
RESET ROLE;

SELECT public.expect_fail('DB-160', 'Q-01 OD-09 tenant audit rows are immutable even for the table owner (trigger)',
  $q$UPDATE app.audit_outbox SET outcome = 'DENIED' WHERE audit_event_id = '00000000-0000-0000-0000-00000000b691'$q$,
  'AUDIT_IMMUTABLE');

-- legacy sink: its shape is the one the AuditLog entity maps (no id supplied: identity)
SELECT public.expect_ok('DB-161', 'Q-01 legacy public.audit_logs accepts the AuditLog entity insert shape',
  $q$INSERT INTO public.audit_logs (event_type, user_id, email, ip_address, occurred_at, details, actor_role_code, scope_type, institution_id, correlation_id)
     VALUES ('LOGIN_FAILURE', NULL, 'unknown@example.test', '10.0.0.9', now(), repeat('d', 400), NULL, NULL, NULL, 'b6-corr-legacy')$q$);
SELECT public.expect_value('DB-162', 'Q-01 legacy public.audit_logs id is bigint identity (entity Long id)',
  $q$SELECT data_type || '/' || is_identity FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'audit_logs' AND column_name = 'id'$q$, 'bigint/YES');

-- ================================================================== Q-04: never-allowed actions
SET ROLE edu_app;
SELECT set_config('app.tenant_id', '00000000-0000-0000-0000-00000000b601', false);

SELECT public.expect_fail('DB-163', 'Q-04 TPA-04 a standard tenant role can never hold TENANT_EXPORT_APPROVE',
  $q$INSERT INTO app.role_action (tenant_id, role_action_id, role_definition_id, action_code, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b681', '00000000-0000-0000-0000-00000000b632', 'TENANT_EXPORT_APPROVE', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$,
  'NEVER_ALLOWED_ACTION');
SELECT public.expect_fail('DB-164', 'Q-04 TPA-04 a custom role can never hold TENANT_DELETION_APPROVE',
  $q$INSERT INTO app.role_action (tenant_id, role_action_id, role_definition_id, action_code, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b682', '00000000-0000-0000-0000-00000000b631', 'TENANT_DELETION_APPROVE', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$,
  'NEVER_ALLOWED_ACTION');
SELECT public.expect_fail('DB-165', 'Q-04 TPA-04 a custom role can never hold RETENTION_CHANGE_APPROVE',
  $q$INSERT INTO app.role_action (tenant_id, role_action_id, role_definition_id, action_code, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b683', '00000000-0000-0000-0000-00000000b631', 'RETENTION_CHANGE_APPROVE', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$,
  'NEVER_ALLOWED_ACTION');
SELECT public.expect_fail('DB-166', 'Q-04 Decision D / CR-03 a custom role cannot hold SALARY_INDIVIDUAL_VIEW',
  $q$INSERT INTO app.role_action (tenant_id, role_action_id, role_definition_id, action_code, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b684', '00000000-0000-0000-0000-00000000b631', 'SALARY_INDIVIDUAL_VIEW', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$,
  'NEVER_ALLOWED_ACTION');
SELECT public.expect_fail('DB-167', 'Q-04 Decision D / CR-03 a custom role cannot hold PAYROLL_APPROVE_TOTALS',
  $q$INSERT INTO app.role_action (tenant_id, role_action_id, role_definition_id, action_code, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b685', '00000000-0000-0000-0000-00000000b631', 'PAYROLL_APPROVE_TOTALS', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$,
  'NEVER_ALLOWED_ACTION');
SELECT public.expect_fail('DB-168', 'Q-04 Decision D a standard role the register does not grant it to cannot hold SALARY_INDIVIDUAL_VIEW',
  $q$INSERT INTO app.role_action (tenant_id, role_action_id, role_definition_id, action_code, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b686', '00000000-0000-0000-0000-00000000b633', 'SALARY_INDIVIDUAL_VIEW', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$,
  'NEVER_ALLOWED_ACTION');
SELECT public.expect_ok('DB-169', 'Q-04 the standard role the register grants SALARY_INDIVIDUAL_VIEW to keeps it',
  $q$INSERT INTO app.role_action (tenant_id, role_action_id, role_definition_id, action_code, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b687', '00000000-0000-0000-0000-00000000b632', 'SALARY_INDIVIDUAL_VIEW', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$);
SELECT public.expect_ok('DB-170', 'Q-04 the guard does not block ordinary actions on a custom role',
  $q$INSERT INTO app.role_action (tenant_id, role_action_id, role_definition_id, action_code, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b688', '00000000-0000-0000-0000-00000000b631', 'ACTION_B6_NORMAL', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$);
SELECT public.expect_fail('DB-171', 'Q-04 an existing grant cannot be changed into a never-allowed action',
  $q$UPDATE app.role_action SET action_code = 'TENANT_EXPORT_APPROVE'
      WHERE tenant_id = '00000000-0000-0000-0000-00000000b601' AND role_action_id = '00000000-0000-0000-0000-00000000b688'$q$,
  'NEVER_ALLOWED_ACTION');
SELECT public.expect_fail('DB-172', 'Q-04 a standard role holding a Decision-D action cannot be turned into a custom role',
  $q$UPDATE app.role_definition SET role_origin = 'CUSTOM', custom_scope = 'INSTITUTION'
      WHERE tenant_id = '00000000-0000-0000-0000-00000000b601' AND role_definition_id = '00000000-0000-0000-0000-00000000b632'$q$,
  'NEVER_ALLOWED_ACTION');
SELECT public.expect_fail('DB-173', 'Q-04 TPA-04 a tenant role template can never hold TENANT_EXPORT_APPROVE',
  $q$INSERT INTO app.tenant_role_template_action (tenant_id, tenant_role_template_action_id, tenant_role_template_id, action_code, created_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b661', '00000000-0000-0000-0000-00000000b652', 'TENANT_EXPORT_APPROVE', '00000000-0000-0000-0000-00000000b611')$q$,
  'NEVER_ALLOWED_ACTION');
SELECT public.expect_fail('DB-174', 'Q-04 Decision D a custom template cannot hold SALARY_INDIVIDUAL_VIEW',
  $q$INSERT INTO app.tenant_role_template_action (tenant_id, tenant_role_template_action_id, tenant_role_template_id, action_code, created_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b662', '00000000-0000-0000-0000-00000000b651', 'SALARY_INDIVIDUAL_VIEW', '00000000-0000-0000-0000-00000000b611')$q$,
  'NEVER_ALLOWED_ACTION');
SELECT public.expect_ok('DB-175', 'Q-04 a standard template keeps the Decision-D action its base role holds in the register',
  $q$INSERT INTO app.tenant_role_template_action (tenant_id, tenant_role_template_action_id, tenant_role_template_id, action_code, created_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b663', '00000000-0000-0000-0000-00000000b652', 'SALARY_INDIVIDUAL_VIEW', '00000000-0000-0000-0000-00000000b611')$q$);
SELECT public.expect_fail('DB-176', 'Q-04 that standard template cannot then be flipped to a custom template',
  $q$UPDATE app.tenant_role_template SET is_standard = false
      WHERE tenant_id = '00000000-0000-0000-0000-00000000b601' AND tenant_role_template_id = '00000000-0000-0000-0000-00000000b652'$q$,
  'NEVER_ALLOWED_ACTION');

-- ================================================================== Q-05: files 14 to 16
-- RLS isolation (tenant one's session; tenant two's rows were seeded above)
SELECT public.expect_value('DB-177', 'Q-05 PTD-02 user_active_role rows of another tenant are invisible',
  $q$SELECT count(*)::text FROM app.user_active_role WHERE user_account_id = '00000000-0000-0000-0000-00000000b612'$q$, '0');
SELECT public.expect_value('DB-178', 'Q-05 PTD-02 tenant_role_template rows of another tenant are invisible',
  $q$SELECT count(*)::text FROM app.tenant_role_template WHERE tenant_role_template_id = '00000000-0000-0000-0000-00000000b653'$q$, '0');
SELECT public.expect_value('DB-179', 'Q-05 PTD-02 tenant_role_template_action rows of another tenant are invisible',
  $q$SELECT count(*)::text FROM app.tenant_role_template_action WHERE tenant_role_template_action_id = '00000000-0000-0000-0000-00000000b669'$q$, '0');
SELECT public.expect_value('DB-180', 'Q-05 PTD-02 tenant_step_in rows of another tenant are invisible',
  $q$SELECT count(*)::text FROM app.tenant_step_in WHERE step_in_id = '00000000-0000-0000-0000-00000000b679'$q$, '0');
SELECT public.expect_value('DB-181', 'Q-05 the own tenant sees its own active-role row',
  $q$SELECT count(*)::text FROM app.user_active_role WHERE user_account_id = '00000000-0000-0000-0000-00000000b611'$q$, '1');
SELECT public.expect_fail('DB-182', 'Q-05 PTD-02 a tenant session cannot record a step-in for another tenant',
  $q$INSERT INTO app.tenant_step_in (tenant_id, step_in_id, actor_user_id, action_code, reason, started_at, expires_at)
     VALUES ('00000000-0000-0000-0000-00000000b602', '00000000-0000-0000-0000-00000000b671', '00000000-0000-0000-0000-00000000b612', 'ACTION_B6_NORMAL', 'Cross tenant attempt', now(), now() + interval '5 minutes')$q$,
  'row-level security');

-- step-in window and checks (TPA-02: 30 minutes, append-only)
SELECT public.expect_ok('DB-183', 'Q-05 TPA-02 a step-in of exactly 30 minutes is accepted',
  $q$INSERT INTO app.tenant_step_in (tenant_id, step_in_id, actor_user_id, action_code, reason, started_at, expires_at)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b672', '00000000-0000-0000-0000-00000000b611', 'ACTION_B6_NORMAL', 'Teacher absent, marking for class', '2026-11-09 10:00+00', '2026-11-09 10:30+00')$q$);
SELECT public.expect_fail('DB-184', 'Q-05 TPA-02 a step-in of 30 minutes and 1 second is refused',
  $q$INSERT INTO app.tenant_step_in (tenant_id, step_in_id, actor_user_id, action_code, reason, started_at, expires_at)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b673', '00000000-0000-0000-0000-00000000b611', 'ACTION_B6_NORMAL', 'Teacher absent, marking for class', '2026-11-09 10:00+00', '2026-11-09 10:30:01+00')$q$,
  'ck_tenant_step_in_window');
SELECT public.expect_fail('DB-185', 'Q-05 TPA-02 a step-in must end after it starts',
  $q$INSERT INTO app.tenant_step_in (tenant_id, step_in_id, actor_user_id, action_code, reason, started_at, expires_at)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b674', '00000000-0000-0000-0000-00000000b611', 'ACTION_B6_NORMAL', 'Teacher absent, marking for class', '2026-11-09 10:00+00', '2026-11-09 10:00+00')$q$,
  'ck_tenant_step_in_window');
SELECT public.expect_fail('DB-186', 'Q-05 TPA-02 a step-in reason longer than 500 characters is refused',
  $q$INSERT INTO app.tenant_step_in (tenant_id, step_in_id, actor_user_id, action_code, reason, started_at, expires_at)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b675', '00000000-0000-0000-0000-00000000b611', 'ACTION_B6_NORMAL', repeat('r', 501), now(), now() + interval '5 minutes')$q$,
  'ck_tenant_step_in_reason');
SELECT public.expect_fail('DB-187', 'Q-05 FK a step-in actor must be a user of the same tenant',
  $q$INSERT INTO app.tenant_step_in (tenant_id, step_in_id, actor_user_id, action_code, reason, started_at, expires_at)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b676', '00000000-0000-0000-0000-00000000b612', 'ACTION_B6_NORMAL', 'Actor from tenant two', now(), now() + interval '5 minutes')$q$,
  'fk_tenant_step_in_actor');
SELECT public.expect_fail('DB-188', 'Q-05 FK a step-in action must be in the action catalogue',
  $q$INSERT INTO app.tenant_step_in (tenant_id, step_in_id, actor_user_id, action_code, reason, started_at, expires_at)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b677', '00000000-0000-0000-0000-00000000b611', 'NO_SUCH_ACTION_B6', 'Unknown action code', now(), now() + interval '5 minutes')$q$,
  'fk_tenant_step_in_action');
SELECT public.expect_fail('DB-189', 'Q-05 FK a step-in target role must belong to the same tenant',
  $q$INSERT INTO app.tenant_step_in (tenant_id, step_in_id, actor_user_id, action_code, reason, target_role_definition_id, started_at, expires_at)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b678', '00000000-0000-0000-0000-00000000b611', 'ACTION_B6_NORMAL', 'Target role of tenant two', '00000000-0000-0000-0000-00000000b634', now(), now() + interval '5 minutes')$q$,
  'fk_tenant_step_in_target_role');
SELECT public.expect_fail('DB-190', 'Q-05 append-only: the app role cannot update a step-in',
  $q$UPDATE app.tenant_step_in SET reason = 'Rewritten reason' WHERE step_in_id = '00000000-0000-0000-0000-00000000b672'$q$,
  'permission denied|APPEND_ONLY');

-- user_active_role (15_v41)
SELECT public.expect_fail('DB-191', 'Q-05 FK the active role must be an assignment of the same tenant',
  $q$UPDATE app.user_active_role SET user_role_assignment_id = '00000000-0000-0000-0000-00000000b642'
      WHERE tenant_id = '00000000-0000-0000-0000-00000000b601' AND user_account_id = '00000000-0000-0000-0000-00000000b611'$q$,
  'fk_user_active_role_assignment');
SELECT public.expect_fail('DB-192', 'Q-05 FK the active-role user must exist in the tenant',
  $q$INSERT INTO app.user_active_role (tenant_id, user_account_id, user_role_assignment_id)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b6ff', '00000000-0000-0000-0000-00000000b641')$q$,
  'fk_user_active_role_user');
SELECT public.expect_fail('DB-193', 'Q-05 PK one active role per user',
  $q$INSERT INTO app.user_active_role (tenant_id, user_account_id, user_role_assignment_id)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b641')$q$,
  'pk_user_active_role');

-- tenant_role_template / template actions / role_definition additions (16_v41)
SELECT public.expect_fail('DB-194', 'Q-05 FK a template base role must be a standard role',
  $q$INSERT INTO app.tenant_role_template (tenant_id, tenant_role_template_id, template_name, base_role_code, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b654', 'B6 Unknown Base', 'NO_SUCH_ROLE_B6', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$,
  'fk_tenant_role_template_base_role');
SELECT public.expect_fail('DB-195', 'Q-05 CHECK a template name is 2 to 100 characters',
  $q$INSERT INTO app.tenant_role_template (tenant_id, tenant_role_template_id, template_name, base_role_code, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b655', 'X', 'CLERK_B6', '00000000-0000-0000-0000-00000000b611', '00000000-0000-0000-0000-00000000b611')$q$,
  'ck_tenant_role_template_name');
SELECT public.expect_fail('DB-196', 'Q-05 FK a template action must point at a template of the same tenant',
  $q$INSERT INTO app.tenant_role_template_action (tenant_id, tenant_role_template_action_id, tenant_role_template_id, action_code, created_by)
     VALUES ('00000000-0000-0000-0000-00000000b601', '00000000-0000-0000-0000-00000000b664', '00000000-0000-0000-0000-00000000b653', 'ACTION_B6_NORMAL', '00000000-0000-0000-0000-00000000b611')$q$,
  'fk_tenant_role_template_action_template');
SELECT public.expect_fail('DB-197', 'Q-05 CHECK role_definition owner_level is TENANT/UNIVERSITY/COLLEGE/SCHOOL',
  $q$UPDATE app.role_definition SET owner_level = 'GLOBAL'
      WHERE tenant_id = '00000000-0000-0000-0000-00000000b601' AND role_definition_id = '00000000-0000-0000-0000-00000000b631'$q$,
  'ck_role_definition_owner_level');
SELECT public.expect_fail('DB-198', 'Q-05 FK role_definition owner institution must be an institution of the tenant',
  $q$UPDATE app.role_definition SET owner_institution_id = '00000000-0000-0000-0000-00000000b6fe'
      WHERE tenant_id = '00000000-0000-0000-0000-00000000b601' AND role_definition_id = '00000000-0000-0000-0000-00000000b631'$q$,
  'fk_role_definition_owner_institution');
SELECT public.expect_fail('DB-199', 'Q-05 CHECK role_definition status is one of the lifecycle states',
  $q$UPDATE app.role_definition SET status = 'SUSPENDED'
      WHERE tenant_id = '00000000-0000-0000-0000-00000000b601' AND role_definition_id = '00000000-0000-0000-0000-00000000b631'$q$,
  'ck_role_definition_status');
SELECT public.expect_value('DB-200', 'Q-05 TPA-02 a new grant defaults to SHARED access',
  $q$SELECT access_mode FROM app.role_action WHERE tenant_id = '00000000-0000-0000-0000-00000000b601' AND role_action_id = '00000000-0000-0000-0000-00000000b688'$q$, 'SHARED');
SELECT public.expect_ok('DB-201', 'Q-05 ROLE_SWITCH a saved active role can be updated again (trg_touch has row_version)',
  $q$UPDATE app.user_active_role SET switched_at = now()
      WHERE tenant_id = '00000000-0000-0000-0000-00000000b601' AND user_account_id = '00000000-0000-0000-0000-00000000b611'$q$);
RESET ROLE;
SELECT set_config('app.tenant_id', '', false);
