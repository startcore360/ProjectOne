-- Rollback for 32_v41_staff_login_aliases.sql
DROP TABLE IF EXISTS app.user_login_alias CASCADE;
DROP FUNCTION IF EXISTS app.fn_user_login_alias_guard();
DROP TRIGGER IF EXISTS trg_user_account_email_alias_guard ON app.user_account;
DROP FUNCTION IF EXISTS app.fn_user_account_email_alias_guard();
