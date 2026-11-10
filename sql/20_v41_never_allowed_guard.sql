-- EDU 360 V4.1 — database guard: "never allowed" actions stay out of tenant roles and tenant
-- role templates (Q-04 / DB-A10; D3 CR-03; TPA-03 "limits that stay"; TPA-04; Decision D).
-- Apply after 16_v41_tenant_top_post_authority.sql. Rollback: 20r_v41_never_allowed_guard_rollback.sql.
-- Idempotent.
--
-- WHAT is refused (sources in brackets; the service layer checks the same rules first, this is
-- the last line of defence so no code path or manual SQL can get round them):
--   1. DATA_PROTECTION — TENANT_EXPORT_APPROVE, TENANT_DELETION_APPROVE, RETENTION_CHANGE_APPROVE.
--      They stay with the platform Data Protection Officer; no tenant role or template may ever
--      hold them, standard or custom [TPA-04, D2 action 198, D1 §7]. Platform-side twin:
--      ck_platform_role_action_reserved (12_v41_platform_roles.sql).
--   2. DECISION_D — SALARY_INDIVIDUAL_VIEW, PAYROLL_APPROVE_TOTALS ("keep locked", decision log
--      2026-11-07). The register gives them to a few standard roles (e.g. Payroll Officer,
--      HR Manager; Principal / Finance Director for totals), so they are allowed ONLY on a
--      STANDARD role (or a standard template) whose ref.standard_role_action row grants that exact
--      action to that role. A custom role, a custom template, or a standard role the register
--      does not give the action to, is refused [CR-03, CR-01].
-- The list is kept in one function, app.fn_never_allowed_class, so the Java lock list (B1, A-03)
-- and this guard can be compared against a single place.
--
-- WHAT is NOT here (deliberately): the 45 "Locked for everyone" register rules are limits on HOW
-- an action is used (no self-approval, receipts never edited, ...), not "never grant this action";
-- most of those actions are held by standard roles (e.g. MESSAGE_SEND, RESULT_PUBLISH). Turning
-- them into grant bans would break the register, so they are enforced by their own guards.

CREATE OR REPLACE FUNCTION app.fn_never_allowed_class(p_action_code text)
RETURNS text LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE
    WHEN p_action_code IN ('TENANT_EXPORT_APPROVE', 'TENANT_DELETION_APPROVE', 'RETENTION_CHANGE_APPROVE') THEN 'DATA_PROTECTION'
    WHEN p_action_code IN ('SALARY_INDIVIDUAL_VIEW', 'PAYROLL_APPROVE_TOTALS') THEN 'DECISION_D'
    ELSE NULL
  END
$$;
COMMENT ON FUNCTION app.fn_never_allowed_class(text) IS 'Never-allowed class of an action for tenant roles/templates: DATA_PROTECTION (never), DECISION_D (standard role that the register grants it to only), NULL (no grant ban) [Q-04, CR-03, TPA-04, Decision D].';

-- app.role_action: one row per (role, action).
CREATE OR REPLACE FUNCTION app.fn_role_action_never_allowed() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_class text := app.fn_never_allowed_class(NEW.action_code);
  v_origin text;
  v_code text;
  v_base text;
BEGIN
  IF v_class IS NULL THEN
    RETURN NEW;
  END IF;
  IF v_class = 'DATA_PROTECTION' THEN
    RAISE EXCEPTION 'NEVER_ALLOWED_ACTION: % stays with the platform Data Protection Officer and cannot be held by a tenant role [TPA-04, CR-03]', NEW.action_code
      USING ERRCODE = 'check_violation';
  END IF;
  SELECT role_origin, role_code, base_standard_role INTO v_origin, v_code, v_base
    FROM app.role_definition
   WHERE tenant_id = NEW.tenant_id AND role_definition_id = NEW.role_definition_id;
  IF v_origin = 'STANDARD' AND EXISTS (
       SELECT 1 FROM ref.standard_role_action s
        WHERE s.action_code = NEW.action_code AND s.role_code IN (v_code, v_base)) THEN
    RETURN NEW;
  END IF;
  RAISE EXCEPTION 'NEVER_ALLOWED_ACTION: % is locked (Decision D); only a standard role the register grants it to may hold it [CR-03]', NEW.action_code
    USING ERRCODE = 'check_violation';
END $$;

-- app.tenant_role_template_action: template actions (16_v41).
CREATE OR REPLACE FUNCTION app.fn_template_action_never_allowed() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_class text := app.fn_never_allowed_class(NEW.action_code);
  v_standard boolean;
  v_base text;
BEGIN
  IF v_class IS NULL THEN
    RETURN NEW;
  END IF;
  IF v_class = 'DATA_PROTECTION' THEN
    RAISE EXCEPTION 'NEVER_ALLOWED_ACTION: % stays with the platform Data Protection Officer and cannot be in a tenant role template [TPA-04, CR-03]', NEW.action_code
      USING ERRCODE = 'check_violation';
  END IF;
  SELECT is_standard, base_role_code INTO v_standard, v_base
    FROM app.tenant_role_template
   WHERE tenant_id = NEW.tenant_id AND tenant_role_template_id = NEW.tenant_role_template_id;
  IF v_standard AND EXISTS (
       SELECT 1 FROM ref.standard_role_action s
        WHERE s.action_code = NEW.action_code AND s.role_code = v_base) THEN
    RETURN NEW;
  END IF;
  RAISE EXCEPTION 'NEVER_ALLOWED_ACTION: % is locked (Decision D); only a standard template whose base role the register grants it to may hold it [CR-03]', NEW.action_code
    USING ERRCODE = 'check_violation';
END $$;

DROP TRIGGER IF EXISTS trg_never_allowed ON app.role_action;
CREATE TRIGGER trg_never_allowed BEFORE INSERT OR UPDATE OF action_code, role_definition_id ON app.role_action
  FOR EACH ROW EXECUTE FUNCTION app.fn_role_action_never_allowed();

DROP TRIGGER IF EXISTS trg_never_allowed ON app.tenant_role_template_action;
CREATE TRIGGER trg_never_allowed BEFORE INSERT OR UPDATE OF action_code, tenant_role_template_id ON app.tenant_role_template_action
  FOR EACH ROW EXECUTE FUNCTION app.fn_template_action_never_allowed();

-- A role's origin or a template's standard flag could otherwise be flipped after the fact to
-- turn a legitimately held Decision-D action into a custom-role grant.
CREATE OR REPLACE FUNCTION app.fn_role_definition_never_allowed() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_bad text;
BEGIN
  SELECT ra.action_code INTO v_bad
    FROM app.role_action ra
   WHERE ra.tenant_id = NEW.tenant_id AND ra.role_definition_id = NEW.role_definition_id
     AND app.fn_never_allowed_class(ra.action_code) IS NOT NULL
     AND NOT (NEW.role_origin = 'STANDARD' AND EXISTS (
           SELECT 1 FROM ref.standard_role_action s
            WHERE s.action_code = ra.action_code AND s.role_code IN (NEW.role_code, NEW.base_standard_role)))
   LIMIT 1;
  IF v_bad IS NOT NULL THEN
    RAISE EXCEPTION 'NEVER_ALLOWED_ACTION: role % would hold locked action % [CR-03]', NEW.role_code, v_bad
      USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_never_allowed ON app.role_definition;
CREATE TRIGGER trg_never_allowed BEFORE UPDATE OF role_origin, role_code, base_standard_role ON app.role_definition
  FOR EACH ROW EXECUTE FUNCTION app.fn_role_definition_never_allowed();

CREATE OR REPLACE FUNCTION app.fn_template_never_allowed() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_bad text;
BEGIN
  SELECT ta.action_code INTO v_bad
    FROM app.tenant_role_template_action ta
   WHERE ta.tenant_id = NEW.tenant_id AND ta.tenant_role_template_id = NEW.tenant_role_template_id
     AND app.fn_never_allowed_class(ta.action_code) IS NOT NULL
     AND NOT (NEW.is_standard AND EXISTS (
           SELECT 1 FROM ref.standard_role_action s
            WHERE s.action_code = ta.action_code AND s.role_code = NEW.base_role_code))
   LIMIT 1;
  IF v_bad IS NOT NULL THEN
    RAISE EXCEPTION 'NEVER_ALLOWED_ACTION: template % would hold locked action % [CR-03]', NEW.template_name, v_bad
      USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_never_allowed ON app.tenant_role_template;
CREATE TRIGGER trg_never_allowed BEFORE UPDATE OF is_standard, base_role_code ON app.tenant_role_template
  FOR EACH ROW EXECUTE FUNCTION app.fn_template_never_allowed();
