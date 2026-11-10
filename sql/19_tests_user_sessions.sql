-- EDU 360 V4.1 — multi-device session tests DB-126 … DB-137 (18_v41_user_sessions.sql, SES-01).
-- Run after 17_tests_tenant_top_post.sql in the test database (expect_* helpers: 04_tests.sql).
\set ON_ERROR_STOP 1
SET client_min_messages = warning;

INSERT INTO app.tenant_local (tenant_id, tenant_type, time_zone)
VALUES ('00000000-0000-0000-0000-00000000a501', 'UNIVERSITY_GROUP', 'Asia/Kolkata')
ON CONFLICT (tenant_id) DO NOTHING;
INSERT INTO app.tenant_local (tenant_id, tenant_type, time_zone)
VALUES ('00000000-0000-0000-0000-00000000a502', 'UNIVERSITY_GROUP', 'Asia/Kolkata')
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO app.user_account (tenant_id, user_account_id, user_type, display_name, email, status, created_by, updated_by)
VALUES ('00000000-0000-0000-0000-00000000a501', '00000000-0000-0000-0000-00000000a511', 'STAFF', 'Session Tester', 'sessions.ses01@example.test', 'ACTIVE', '00000000-0000-0000-0000-00000000a511', '00000000-0000-0000-0000-00000000a511')
ON CONFLICT DO NOTHING;

-- DB-126: SES-01 replaces R2-11 — two computers may be signed in at the same time.
SELECT public.expect_ok('DB-126', 'SES-01 two active COMPUTER devices for one user are allowed',
  $q$INSERT INTO app.user_device (tenant_id, user_device_id, user_account_id, device_class, device_label, keycloak_session_id, browser_key_hash, ip_address, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000a501', '00000000-0000-0000-0000-00000000a521', '00000000-0000-0000-0000-00000000a511', 'COMPUTER', 'Chrome on Windows', 'sid-ses01-1', repeat('a', 64), '10.0.0.1', '00000000-0000-0000-0000-00000000a511', '00000000-0000-0000-0000-00000000a511'),
            ('00000000-0000-0000-0000-00000000a501', '00000000-0000-0000-0000-00000000a522', '00000000-0000-0000-0000-00000000a511', 'COMPUTER', 'Firefox on macOS', 'sid-ses01-2', repeat('b', 64), '10.0.0.2', '00000000-0000-0000-0000-00000000a511', '00000000-0000-0000-0000-00000000a511')$q$);

-- DB-127: the same browser cannot hold two active sessions.
SELECT public.expect_fail('DB-127', 'SES-01 one active session per browser',
  $q$INSERT INTO app.user_device (tenant_id, user_device_id, user_account_id, device_class, keycloak_session_id, browser_key_hash, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000a501', '00000000-0000-0000-0000-00000000a523', '00000000-0000-0000-0000-00000000a511', 'COMPUTER', 'sid-ses01-3', repeat('a', 64), '00000000-0000-0000-0000-00000000a511', '00000000-0000-0000-0000-00000000a511')$q$,
  'ux_user_device_browser');

-- DB-128: once that browser's old row is revoked (NEW_LOGIN), the browser can sign in again.
UPDATE app.user_device SET revoked_at = now(), revoked_reason = 'NEW_LOGIN'
 WHERE tenant_id = '00000000-0000-0000-0000-00000000a501' AND user_device_id = '00000000-0000-0000-0000-00000000a521';
SELECT public.expect_ok('DB-128', 'SES-01 same browser signs in again after its old session is replaced',
  $q$INSERT INTO app.user_device (tenant_id, user_device_id, user_account_id, device_class, keycloak_session_id, browser_key_hash, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000a501', '00000000-0000-0000-0000-00000000a523', '00000000-0000-0000-0000-00000000a511', 'COMPUTER', 'sid-ses01-3', repeat('a', 64), '00000000-0000-0000-0000-00000000a511', '00000000-0000-0000-0000-00000000a511')$q$);

-- DB-129: a Keycloak session id maps to at most one device row.
SELECT public.expect_fail('DB-129', 'SES-01 a Keycloak session belongs to one device row',
  $q$INSERT INTO app.user_device (tenant_id, user_device_id, user_account_id, device_class, keycloak_session_id, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000a501', '00000000-0000-0000-0000-00000000a524', '00000000-0000-0000-0000-00000000a511', 'PHONE', 'sid-ses01-2', '00000000-0000-0000-0000-00000000a511', '00000000-0000-0000-0000-00000000a511')$q$,
  'ux_user_device_session');

-- DB-130: EXPIRED is a valid revocation reason (Keycloak ended the session itself).
SELECT public.expect_ok('DB-130', 'SES-01 EXPIRED revocation reason accepted',
  $q$UPDATE app.user_device SET revoked_at = now(), revoked_reason = 'EXPIRED'
      WHERE tenant_id = '00000000-0000-0000-0000-00000000a501' AND user_device_id = '00000000-0000-0000-0000-00000000a522'$q$);

-- DB-131: the browser key is stored hashed, never raw.
SELECT public.expect_fail('DB-131', 'SES-01 browser key must be a SHA-256 hex digest',
  $q$INSERT INTO app.user_device (tenant_id, user_device_id, user_account_id, device_class, browser_key_hash, created_by, updated_by)
     VALUES ('00000000-0000-0000-0000-00000000a501', '00000000-0000-0000-0000-00000000a525', '00000000-0000-0000-0000-00000000a511', 'PHONE', 'raw-browser-id', '00000000-0000-0000-0000-00000000a511', '00000000-0000-0000-0000-00000000a511')$q$,
  'ck_user_device_browser_key_hash');

-- DB-132: R1 still holds — a revocation needs a reason.
SELECT public.expect_fail('DB-132', 'R1 revoked_at requires revoked_reason',
  $q$UPDATE app.user_device SET revoked_at = now(), revoked_reason = NULL
      WHERE tenant_id = '00000000-0000-0000-0000-00000000a501' AND user_device_id = '00000000-0000-0000-0000-00000000a523'$q$,
  'ck_user_device_r1');

-- DB-133: RLS — under edu_app, another tenant sees none of these devices.
SET ROLE edu_app;
SELECT set_config('app.tenant_id', '00000000-0000-0000-0000-00000000a502', false);
SELECT public.expect_value('DB-133', 'PTD-02 user_device rows are invisible to another tenant',
  $q$SELECT count(*)::text FROM app.user_device WHERE user_account_id = '00000000-0000-0000-0000-00000000a511'$q$, '0');
SELECT set_config('app.tenant_id', '00000000-0000-0000-0000-00000000a501', false);
SELECT public.expect_value('DB-134', 'PTD-02 the owning tenant sees its own device rows',
  $q$SELECT count(*)::text FROM app.user_device WHERE user_account_id = '00000000-0000-0000-0000-00000000a511'$q$, '3');
RESET ROLE;
SELECT set_config('app.tenant_id', '', false);

-- Platform side
INSERT INTO platform.platform_user (platform_user_id, email, full_name, platform_role, status, mfa_required, identity_subject, created_by, updated_by)
VALUES ('00000000-0000-0000-0000-00000000a531', 'sessions.ses01@platform.test', 'Platform Session Tester', 'PLATFORM_ADMIN', 'ACTIVE', true, 'ses01-platform', '00000000-0000-0000-0000-00000000a531', '00000000-0000-0000-0000-00000000a531')
ON CONFLICT DO NOTHING;

-- DB-135: platform staff may be signed in on several devices.
SELECT public.expect_ok('DB-135', 'SES-01 platform user may hold several active devices',
  $q$INSERT INTO platform.platform_user_device (platform_user_device_id, platform_user_id, device_class, device_label, keycloak_session_id, browser_key_hash)
     VALUES ('00000000-0000-0000-0000-00000000a541', '00000000-0000-0000-0000-00000000a531', 'COMPUTER', 'Edge on Windows', 'sid-ses01-p1', repeat('c', 64)),
            ('00000000-0000-0000-0000-00000000a542', '00000000-0000-0000-0000-00000000a531', 'PHONE', 'Safari on iPhone', 'sid-ses01-p2', repeat('d', 64))$q$);

-- DB-136: ...but not twice from the same browser.
SELECT public.expect_fail('DB-136', 'SES-01 platform: one active session per browser',
  $q$INSERT INTO platform.platform_user_device (platform_user_device_id, platform_user_id, device_class, keycloak_session_id, browser_key_hash)
     VALUES ('00000000-0000-0000-0000-00000000a543', '00000000-0000-0000-0000-00000000a531', 'COMPUTER', 'sid-ses01-p3', repeat('c', 64))$q$,
  'ux_platform_user_device_browser');

-- DB-137: edu_app (the tenant runtime role) has no access to platform devices.
SET ROLE edu_app;
SELECT public.expect_fail('DB-137', 'Least privilege: edu_app cannot read platform devices',
  $q$SELECT count(*) FROM platform.platform_user_device$q$,
  'permission denied');
RESET ROLE;
