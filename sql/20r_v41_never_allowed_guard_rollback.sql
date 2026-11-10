-- EDU 360 V4.1 — rollback for 20_v41_never_allowed_guard.sql (Q-04).
-- Removes only the guard; no data is changed. Run before 16r if both are rolled back.

DO $$
BEGIN
  IF to_regclass('app.tenant_role_template') IS NOT NULL THEN
    DROP TRIGGER IF EXISTS trg_never_allowed ON app.tenant_role_template;
  END IF;
  IF to_regclass('app.tenant_role_template_action') IS NOT NULL THEN
    DROP TRIGGER IF EXISTS trg_never_allowed ON app.tenant_role_template_action;
  END IF;
END $$;
DROP TRIGGER IF EXISTS trg_never_allowed ON app.role_definition;
DROP TRIGGER IF EXISTS trg_never_allowed ON app.role_action;

DROP FUNCTION IF EXISTS app.fn_template_never_allowed();
DROP FUNCTION IF EXISTS app.fn_role_definition_never_allowed();
DROP FUNCTION IF EXISTS app.fn_template_action_never_allowed();
DROP FUNCTION IF EXISTS app.fn_role_action_never_allowed();
DROP FUNCTION IF EXISTS app.fn_never_allowed_class(text);
