-- Rollback for 24_v41_sign_in_security.sql / 24d_v41_action_module_map.sql (B3).
DROP FUNCTION IF EXISTS app.fn_student_login_account(text);
DROP TABLE IF EXISTS ref.action_module_map;
