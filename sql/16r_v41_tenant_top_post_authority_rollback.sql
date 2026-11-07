-- EDU 360 V4.1 — rollback for 16_v41_tenant_top_post_authority.sql.

DROP TABLE IF EXISTS app.tenant_step_in CASCADE;
DROP TABLE IF EXISTS app.tenant_role_template_action CASCADE;
DROP TABLE IF EXISTS app.tenant_role_template CASCADE;

ALTER TABLE app.role_action DROP COLUMN IF EXISTS access_mode;
ALTER TABLE app.role_definition DROP COLUMN IF EXISTS owner_level;
ALTER TABLE app.role_definition DROP COLUMN IF EXISTS owner_institution_id;
