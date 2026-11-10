-- EDU 360 V4.1 — platform role tests DB-98 … DB-112 (12_v41_platform_roles.sql). Run after
-- 11_tests_rolldown.sql in the same database, because the expect_* helpers come from 04_tests.sql.
\set ON_ERROR_STOP 1
SET client_min_messages = warning;

INSERT INTO ref.action_catalog (action_code, action_no, module_name, phase, description, is_approval_action)
VALUES ('V41_TEST_ACTION', 271, 'Platform', 1, 'Test action', false),
       ('TENANT_DELETION_APPROVE', 272, 'Governance and data protection', 1, 'Approve tenant deletion', true),
       ('PLATFORM_USER_MANAGE', 273, 'Platform', 1, 'Manage platform users', false)
ON CONFLICT DO NOTHING;

INSERT INTO platform.platform_user (platform_user_id, email, full_name, platform_role, status, mfa_required, identity_subject, created_by, updated_by)
VALUES ('00000000-0000-0000-0000-00000000f001', 'owner.v41@example.test', 'Owner V41', 'PRODUCT_OWNER', 'ACTIVE', true, 'v41-owner', '00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-00000000f001'),
       ('00000000-0000-0000-0000-00000000f002', 'staff.v41@example.test', 'Staff V41', 'BILLING_MANAGER', 'ACTIVE', true, 'v41-staff', '00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-00000000f001');

INSERT INTO platform.platform_role (platform_role_id, role_name, base_platform_role, created_by, updated_by)
VALUES ('00000000-0000-0000-0000-00000000f101', 'Test role V41', 'BILLING_MANAGER', '00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-00000000f001');

INSERT INTO platform.platform_role_template (platform_role_template_id, template_name, base_platform_role, created_by, updated_by)
VALUES ('00000000-0000-0000-0000-00000000f201', 'Test template V41', 'BILLING_MANAGER', '00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-00000000f001');

SELECT public.expect_fail('DB-98', 'TPA-04 a custom role can never hold a data protection approval',
  $q$INSERT INTO platform.platform_role_action (platform_role_action_id, platform_role_id, action_code, created_by) VALUES ('00000000-0000-0000-0000-00000000f301', '00000000-0000-0000-0000-00000000f101', 'TENANT_DELETION_APPROVE', '00000000-0000-0000-0000-00000000f001')$q$,
  'ck_platform_role_action_reserved');

SELECT public.expect_fail('DB-99', 'Addendum rule 11 a custom role can never add platform staff',
  $q$INSERT INTO platform.platform_role_action (platform_role_action_id, platform_role_id, action_code, created_by) VALUES ('00000000-0000-0000-0000-00000000f302', '00000000-0000-0000-0000-00000000f101', 'PLATFORM_USER_MANAGE', '00000000-0000-0000-0000-00000000f001')$q$,
  'ck_platform_role_action_reserved');

SELECT public.expect_ok('DB-100', 'TPA-02 a permission can be handed over',
  $q$INSERT INTO platform.platform_role_action (platform_role_action_id, platform_role_id, action_code, access_mode, created_by) VALUES ('00000000-0000-0000-0000-00000000f303', '00000000-0000-0000-0000-00000000f101', 'V41_TEST_ACTION', 'HANDED_OVER', '00000000-0000-0000-0000-00000000f001')$q$);

SELECT public.expect_fail('DB-101', 'TPA-02 only shared or handed over are valid access modes',
  $q$UPDATE platform.platform_role_action SET access_mode = 'BOTH' WHERE platform_role_action_id = '00000000-0000-0000-0000-00000000f303'$q$,
  'ck_platform_role_action_mode');

SELECT public.expect_fail('DB-102', 'TPA-01 a custom role can never be based on the Product Owner',
  $q$INSERT INTO platform.platform_role (platform_role_id, role_name, base_platform_role, created_by, updated_by) VALUES ('00000000-0000-0000-0000-00000000f102', 'Owner copy', 'PRODUCT_OWNER', '00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-00000000f001')$q$,
  'ck_platform_role_base');

SELECT public.expect_fail('DB-103', 'TPA-04 a custom role can never be based on the Data Protection Officer',
  $q$INSERT INTO platform.platform_role (platform_role_id, role_name, base_platform_role, created_by, updated_by) VALUES ('00000000-0000-0000-0000-00000000f103', 'DPO copy', 'DATA_PROTECTION_OFFICER', '00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-00000000f001')$q$,
  'ck_platform_role_base');

SELECT public.expect_fail('DB-104', 'TPA-01 a Product Owner never holds a custom role',
  $q$UPDATE platform.platform_user SET platform_role_id = '00000000-0000-0000-0000-00000000f101' WHERE platform_user_id = '00000000-0000-0000-0000-00000000f001'$q$,
  'ck_platform_user_custom_role');

SELECT public.expect_ok('DB-105', 'TPA-03 staff can hold a custom role',
  $q$UPDATE platform.platform_user SET platform_role_id = '00000000-0000-0000-0000-00000000f101' WHERE platform_user_id = '00000000-0000-0000-0000-00000000f002'$q$);

SELECT public.expect_fail('DB-106', 'TPA-02 a step-in needs a real reason',
  $q$INSERT INTO platform.platform_step_in (platform_step_in_id, actor_user_id, action_code, reason, expires_at) VALUES ('00000000-0000-0000-0000-00000000f401', '00000000-0000-0000-0000-00000000f001', 'V41_TEST_ACTION', 'no', now() + interval '30 minutes')$q$,
  'ck_platform_step_in_reason');

SELECT public.expect_fail('DB-107', 'TPA-02 a step-in lasts at most one hour',
  $q$INSERT INTO platform.platform_step_in (platform_step_in_id, actor_user_id, action_code, reason, expires_at) VALUES ('00000000-0000-0000-0000-00000000f402', '00000000-0000-0000-0000-00000000f001', 'V41_TEST_ACTION', 'Covering for an absent colleague', now() + interval '2 hours')$q$,
  'ck_platform_step_in_window');

SELECT public.expect_ok('DB-108', 'TPA-02 a valid step-in is recorded',
  $q$INSERT INTO platform.platform_step_in (platform_step_in_id, actor_user_id, action_code, reason, expires_at) VALUES ('00000000-0000-0000-0000-00000000f403', '00000000-0000-0000-0000-00000000f001', 'V41_TEST_ACTION', 'Covering for an absent colleague', now() + interval '30 minutes')$q$);

SELECT public.expect_fail('DB-109', 'TPA-02 a step-in record can never be changed',
  $q$UPDATE platform.platform_step_in SET reason = 'Changed after the fact' WHERE platform_step_in_id = '00000000-0000-0000-0000-00000000f403'$q$,
  'APPEND_ONLY');

SELECT public.expect_fail('DB-110', 'TPA-02 a step-in record can never be deleted',
  $q$DELETE FROM platform.platform_step_in WHERE platform_step_in_id = '00000000-0000-0000-0000-00000000f403'$q$,
  'APPEND_ONLY');

SELECT public.expect_fail('DB-111', 'Addendum rule 16 only the four lifecycle states are valid',
  $q$UPDATE platform.platform_role SET status = 'RETIRED' WHERE platform_role_id = '00000000-0000-0000-0000-00000000f101'$q$,
  'ck_platform_role_status');

SELECT public.expect_fail('DB-112', 'GUARD_PLATFORM_ISOLATION edu_app has no access to platform roles',
  $q$SET LOCAL ROLE edu_app; SELECT count(*) FROM platform.platform_role$q$,
  'permission denied');
