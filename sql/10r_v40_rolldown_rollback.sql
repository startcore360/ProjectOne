-- EDU 360 V4.0 — rollback of 10_v40_rolldown.sql and 10d_v40_rolldown_ref.sql. Overrides are lost (export them first).
DROP TABLE IF EXISTS app.role_rolldown_override;
DROP FUNCTION IF EXISTS app.fn_rolldown_override_guard();
DROP FUNCTION IF EXISTS app.fn_rolldown_resolve(uuid, text, text);
DROP FUNCTION IF EXISTS app.fn_role_has_holder(uuid, uuid, text);
ALTER TABLE app.approval_step DROP CONSTRAINT IF EXISTS ck_approval_step_r5, DROP CONSTRAINT IF EXISTS ck_approval_step_acting_for_role, DROP COLUMN IF EXISTS acting_for_role;
DROP TABLE IF EXISTS ref.role_rolldown_default;
DELETE FROM ref.standard_role_action WHERE action_code IN ('ROLLDOWN_VIEW','ROLLDOWN_OVERRIDE_SET');
DELETE FROM ref.action_catalog WHERE action_code IN ('ROLLDOWN_VIEW','ROLLDOWN_OVERRIDE_SET');
