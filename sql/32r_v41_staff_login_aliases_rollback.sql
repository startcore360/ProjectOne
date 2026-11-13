-- Rollback for 32_v41_staff_login_aliases.sql
DROP TABLE IF EXISTS app.user_login_alias CASCADE;
DROP FUNCTION IF EXISTS app.fn_user_login_alias_guard();
