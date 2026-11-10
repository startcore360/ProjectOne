-- Rollback of 12_v41_platform_roles.sql and its template rows. Custom role assignments are lost, and
-- each person falls back to the standard rights of platform_user.platform_role.
DROP TABLE IF EXISTS platform.platform_step_in;
ALTER TABLE platform.platform_user DROP CONSTRAINT IF EXISTS ck_platform_user_custom_role;
ALTER TABLE platform.platform_user DROP CONSTRAINT IF EXISTS fk_platform_user_platform_role;
ALTER TABLE platform.platform_user DROP COLUMN IF EXISTS platform_role_id;
DROP TABLE IF EXISTS platform.platform_role_action;
DROP TABLE IF EXISTS platform.platform_role;
DROP TABLE IF EXISTS platform.platform_role_template_action;
DROP TABLE IF EXISTS platform.platform_role_template;
