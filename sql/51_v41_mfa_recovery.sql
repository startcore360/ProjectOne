-- EDU 360 V4.1 - two-step login recovery (decision MFA-01, docs/GOVERNANCE_DECISION_LOG.md).
-- When an authenticator no longer matches (lost or new phone, deleted app), a user gets back in by
--   1. an admin's "Reset two-step login" (tenant: USER_MFA_RESET; platform: PLATFORM_USER_MANAGE), or
--   2. a one-time backup code (this file's two platform tables), or
--   3. the emergency procedure in docs/MFA_EMERGENCY_RESET_RUNBOOK.md.
-- Additive and repeatable. Rollback: 51r_v41_mfa_recovery_rollback.sql.

-- 1. The tenant action. Same module and phase as USER_PASSWORD_RESET_UNLOCK; also listed in
--    05_ref_data.sql so fresh databases and the Java reference-code check both know it.
INSERT INTO ref.action_catalog VALUES ('USER_MFA_RESET',282,'Users and roles',1,
    'Reset a user''s two-step login so they can set up a new authenticator',false,NULL,'4.1')
    ON CONFLICT DO NOTHING;

-- 2. Standard grants: exactly the standard roles that may reset a password, with the same scope.
INSERT INTO ref.standard_role_action (standard_role_action_id, role_code, action_code, limited_to)
SELECT 9200 + row_number() OVER (ORDER BY s.role_code), s.role_code, 'USER_MFA_RESET', s.limited_to
  FROM ref.standard_role_action s
 WHERE s.action_code = 'USER_PASSWORD_RESET_UNLOCK'
   AND NOT EXISTS (SELECT 1 FROM ref.standard_role_action x
                    WHERE x.role_code = s.role_code AND x.action_code = 'USER_MFA_RESET')
ON CONFLICT DO NOTHING;

-- 3. Existing tenants copied their standard grants at provisioning, so give the new action to every
--    STANDARD role that already holds password reset. Custom roles are left alone: their Admin
--    chose their actions, and this must not widen them silently.
INSERT INTO app.role_action (tenant_id, role_action_id, role_definition_id, action_code, created_by, updated_by)
SELECT ra.tenant_id, gen_random_uuid(), ra.role_definition_id, 'USER_MFA_RESET',
       '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid
  FROM app.role_action ra
  JOIN app.role_definition rd
    ON rd.tenant_id = ra.tenant_id AND rd.role_definition_id = ra.role_definition_id
 WHERE ra.action_code = 'USER_PASSWORD_RESET_UNLOCK'
   AND rd.role_origin = 'STANDARD'
ON CONFLICT DO NOTHING;

-- 4. Backup codes. Kept beside the Keycloak identity they unlock (realm + Keycloak user id), not in
--    tenant business data: they are sign-in secrets, read before any tenant context exists. Only a
--    BCrypt hash is stored; the plain codes are shown to the user once and never again.
CREATE TABLE IF NOT EXISTS platform.mfa_recovery_code (
    mfa_recovery_code_id uuid NOT NULL,
    realm text NOT NULL,
    keycloak_user_id text NOT NULL,
    code_hash text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    used_at timestamptz,
    CONSTRAINT pk_mfa_recovery_code PRIMARY KEY (mfa_recovery_code_id),
    CONSTRAINT ck_mfa_recovery_code_realm CHECK (char_length(realm) BETWEEN 1 AND 100),
    CONSTRAINT ck_mfa_recovery_code_user CHECK (char_length(keycloak_user_id) BETWEEN 1 AND 100),
    CONSTRAINT ck_mfa_recovery_code_hash CHECK (char_length(code_hash) BETWEEN 20 AND 200),
    CONSTRAINT ck_mfa_recovery_code_used CHECK (used_at IS NULL OR used_at >= created_at)
);
CREATE INDEX IF NOT EXISTS ix_mfa_recovery_code_unused
    ON platform.mfa_recovery_code (realm, keycloak_user_id) WHERE used_at IS NULL;

-- Every backup-code attempt, so wrong guesses are limited (5 per hour per account) on every node.
CREATE TABLE IF NOT EXISTS platform.mfa_recovery_attempt (
    mfa_recovery_attempt_id uuid NOT NULL,
    realm text NOT NULL,
    keycloak_user_id text NOT NULL,
    succeeded boolean NOT NULL,
    attempted_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_mfa_recovery_attempt PRIMARY KEY (mfa_recovery_attempt_id),
    CONSTRAINT ck_mfa_recovery_attempt_realm CHECK (char_length(realm) BETWEEN 1 AND 100),
    CONSTRAINT ck_mfa_recovery_attempt_user CHECK (char_length(keycloak_user_id) BETWEEN 1 AND 100)
);
CREATE INDEX IF NOT EXISTS ix_mfa_recovery_attempt_recent
    ON platform.mfa_recovery_attempt (realm, keycloak_user_id, attempted_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON platform.mfa_recovery_code TO edu_platform;
GRANT SELECT, INSERT ON platform.mfa_recovery_attempt TO edu_platform;

COMMENT ON TABLE platform.mfa_recovery_code IS
    'One-time backup codes for two-step login (MFA-01). BCrypt hash only; plain codes are shown once.';
COMMENT ON TABLE platform.mfa_recovery_attempt IS
    'Backup-code sign-in attempts (MFA-01): limits wrong guesses to 5 per hour per account.';
