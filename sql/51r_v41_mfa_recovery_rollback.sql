-- Rollback for 51_v41_mfa_recovery.sql.
-- Note: removes every USER_MFA_RESET grant, including any an Admin later added to a custom role.
DROP TABLE IF EXISTS platform.mfa_recovery_attempt;
DROP TABLE IF EXISTS platform.mfa_recovery_code;
DELETE FROM app.role_action WHERE action_code = 'USER_MFA_RESET';
DELETE FROM ref.standard_role_action WHERE action_code = 'USER_MFA_RESET';
DELETE FROM ref.action_catalog WHERE action_code = 'USER_MFA_RESET';
