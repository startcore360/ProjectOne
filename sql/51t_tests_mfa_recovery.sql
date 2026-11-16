-- Contract checks for migration 51 (MFA-01). Run on the reference database after 51_v41_mfa_recovery.sql.
\set ON_ERROR_STOP 1
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ref.action_catalog WHERE action_code = 'USER_MFA_RESET' AND phase = 1) THEN
        RAISE EXCEPTION 'MFA_RESET_ACTION_MISSING';
    END IF;
    -- Every standard role that may reset a password may also reset two-step login, and no other.
    IF EXISTS (
        SELECT role_code FROM ref.standard_role_action WHERE action_code = 'USER_PASSWORD_RESET_UNLOCK'
        EXCEPT
        SELECT role_code FROM ref.standard_role_action WHERE action_code = 'USER_MFA_RESET'
    ) OR EXISTS (
        SELECT role_code FROM ref.standard_role_action WHERE action_code = 'USER_MFA_RESET'
        EXCEPT
        SELECT role_code FROM ref.standard_role_action WHERE action_code = 'USER_PASSWORD_RESET_UNLOCK'
    ) THEN
        RAISE EXCEPTION 'MFA_RESET_GRANTS_DO_NOT_MATCH_PASSWORD_RESET';
    END IF;
    IF (SELECT count(*) FROM ref.standard_role_action WHERE action_code = 'USER_MFA_RESET') = 0 THEN
        RAISE EXCEPTION 'MFA_RESET_HAS_NO_STANDARD_ROLE';
    END IF;
    IF app.fn_never_allowed_class('USER_MFA_RESET') IS NOT NULL THEN
        RAISE EXCEPTION 'MFA_RESET_MUST_NOT_BE_NEVER_ALLOWED';
    END IF;
    IF to_regclass('platform.mfa_recovery_code') IS NULL OR to_regclass('platform.mfa_recovery_attempt') IS NULL THEN
        RAISE EXCEPTION 'MFA_RECOVERY_TABLES_MISSING';
    END IF;
    IF NOT has_table_privilege('edu_platform', 'platform.mfa_recovery_code', 'UPDATE')
       OR NOT has_table_privilege('edu_platform', 'platform.mfa_recovery_attempt', 'INSERT') THEN
        RAISE EXCEPTION 'EDU_PLATFORM_MFA_RECOVERY_GRANTS_MISSING';
    END IF;
    IF has_table_privilege('edu_app', 'platform.mfa_recovery_code', 'SELECT') THEN
        RAISE EXCEPTION 'EDU_APP_MUST_NOT_READ_MFA_RECOVERY_CODES';
    END IF;
    -- A plain-text code can never be stored: the hash column refuses short values.
    BEGIN
        INSERT INTO platform.mfa_recovery_code (mfa_recovery_code_id, realm, keycloak_user_id, code_hash)
        VALUES (gen_random_uuid(), 'edu360-platform', 'kc-user', 'ABCDE-12345');
        RAISE EXCEPTION 'MFA_RECOVERY_PLAIN_CODE_ACCEPTED';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
END $$;
