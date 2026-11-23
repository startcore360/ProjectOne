-- 56r_v41_rolldown_management_revocation_rollback.sql
BEGIN;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM app.gba_rolldown_revocation) THEN
    RAISE EXCEPTION 'RD-G8 ROLLBACK BLOCKED: permanent revocation history exists; authority must not be restored';
  END IF;
END $$;
DROP TRIGGER IF EXISTS trg_revoke_gba_rolldown ON app.user_role_assignment;
DROP FUNCTION IF EXISTS app.fn_revoke_gba_rolldown();
DROP TABLE IF EXISTS app.gba_rolldown_revocation;
CREATE OR REPLACE FUNCTION app.fn_rolldown_resolve(p_institution uuid, p_role text, p_action text)
RETURNS TABLE (effective_role text, resolved_via text, path text[]) LANGUAGE plpgsql STABLE AS $$
DECLARE v_tenant uuid := NULLIF(current_setting('app.tenant_id', true), '')::uuid;
        v_ctx text; v_kind text; v_cur text := p_role; v_path text[] := ARRAY[p_role]; v_next text; v_mode text; i int;
BEGIN
  IF v_tenant IS NULL THEN RAISE EXCEPTION 'TENANT_CONTEXT_MISSING: app.tenant_id not set' USING ERRCODE = '42501'; END IF;
  SELECT CASE WHEN a.is_approval_action THEN 'APPROVE' ELSE 'OTHER' END INTO v_kind FROM ref.action_catalog a WHERE a.action_code = p_action;
  IF v_kind IS NULL THEN RAISE EXCEPTION 'UNKNOWN_ACTION: %', p_action USING ERRCODE = '22023'; END IF;
  IF p_institution IS NULL THEN v_ctx := 'UNIVERSITY_WIDE';
  ELSE SELECT institution_type INTO v_ctx FROM app.institution WHERE tenant_id = v_tenant AND institution_id = p_institution;
       IF v_ctx IS NULL THEN RAISE EXCEPTION 'UNKNOWN_INSTITUTION: %', p_institution USING ERRCODE = '22023'; END IF;
  END IF;
  FOR i IN 1..6 LOOP
    IF app.fn_role_has_holder(v_tenant, p_institution, v_cur) THEN
      effective_role := v_cur; resolved_via := CASE WHEN i = 1 THEN 'HOLDER' ELSE 'ROLLDOWN' END; path := v_path; RETURN NEXT; RETURN;
    END IF;
    v_next := NULL; v_mode := NULL;
    SELECT o.mode, o.target_role_code INTO v_mode, v_next FROM app.role_rolldown_override o
     WHERE o.tenant_id = v_tenant AND o.status = 'ACTIVE' AND o.missing_role_code = v_cur
       AND o.institution_id IS NOT DISTINCT FROM p_institution AND o.permission_kind = v_kind
       AND (o.action_code = p_action OR o.action_code IS NULL)
     ORDER BY (o.action_code IS NULL) LIMIT 1;
    IF v_mode = 'WITHHOLD' THEN effective_role := NULL; resolved_via := 'WITHHELD'; path := v_path; RETURN NEXT; RETURN; END IF;
    IF v_mode IS NULL THEN
      SELECT d.stand_in_role_code INTO v_next FROM ref.role_rolldown_default d
       WHERE d.missing_role_code = v_cur AND d.context = v_ctx AND d.permission_kind = v_kind
       ORDER BY (NOT app.fn_role_has_holder(v_tenant, p_institution, d.stand_in_role_code)), d.preference LIMIT 1;
    END IF;
    IF v_next IS NULL OR v_next = ANY (v_path) THEN   -- no stand-in, or a loop: the level above must decide
      effective_role := NULL; resolved_via := 'UNRESOLVED'; path := v_path || v_next; RETURN NEXT; RETURN;
    END IF;
    v_path := v_path || v_next; v_cur := v_next;
  END LOOP;
  effective_role := NULL; resolved_via := 'UNRESOLVED'; path := v_path; RETURN NEXT;
END $$;

CREATE OR REPLACE FUNCTION app.fn_rolldown_override_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_type text; v_kind text;
BEGIN
  IF NEW.missing_role_code IN ('APPLICANT','BILLING_MANAGER','DATA_PROTECTION_OFFICER','DRIVER','GOVERNING_BODY_APPROVER','HOSTEL_STAFF','HOUSEKEEPING_SUPERVISOR','PARENT_COLLEGE','PARENT_SCHOOL','PLATFORM_ADMIN','PRODUCT_OWNER','RELEASE_MANAGER','SECURITY_STAFF','SECURITY_SUPERVISOR','STUDENT_COLLEGE','STUDENT_SCHOOL','SUBJECT_TEACHER','SUPPORT_MANAGER','TRANSPORT_STAFF') THEN
    RAISE EXCEPTION 'ROLLDOWN_ROLE_FIXED: % never rolls down [RD-G4, RD-G5]', NEW.missing_role_code USING ERRCODE = '23514'; END IF;
  IF NEW.mode = 'WITHHOLD' AND NEW.permission_kind = 'APPROVE' THEN
    RAISE EXCEPTION 'ROLLDOWN_WITHHOLD_APPROVAL: an approval can only be given to another role, never withheld (requests would wait forever)' USING ERRCODE = '23514'; END IF;
  IF NEW.mode = 'ASSIGN' THEN
    IF NEW.target_role_code = NEW.missing_role_code OR NEW.target_role_code IN ('APPLICANT','BILLING_MANAGER','DATA_PROTECTION_OFFICER','GOVERNING_BODY_APPROVER','PARENT_COLLEGE','PARENT_SCHOOL','PLATFORM_ADMIN','PRODUCT_OWNER','RELEASE_MANAGER','STUDENT_COLLEGE','STUDENT_SCHOOL','SUPPORT_MANAGER') OR NOT EXISTS (
         SELECT 1 FROM app.role_definition r WHERE r.tenant_id = NEW.tenant_id AND r.status = 'ACTIVE' AND r.role_code = NEW.target_role_code
            AND coalesce(r.base_standard_role, r.role_code) NOT IN ('APPLICANT','BILLING_MANAGER','DATA_PROTECTION_OFFICER','GOVERNING_BODY_APPROVER','PARENT_COLLEGE','PARENT_SCHOOL','PLATFORM_ADMIN','PRODUCT_OWNER','RELEASE_MANAGER','STUDENT_COLLEGE','STUDENT_SCHOOL','SUPPORT_MANAGER')) THEN
      RAISE EXCEPTION 'ROLLDOWN_TARGET_NOT_ALLOWED: % cannot act for another role [RD-G5]', NEW.target_role_code USING ERRCODE = '23514'; END IF;
  END IF;
  IF NEW.action_code IS NOT NULL THEN
    SELECT CASE WHEN is_approval_action THEN 'APPROVE' ELSE 'OTHER' END INTO v_kind FROM ref.action_catalog WHERE action_code = NEW.action_code;
    IF v_kind <> NEW.permission_kind THEN
      RAISE EXCEPTION 'ROLLDOWN_KIND_MISMATCH: % is an % action', NEW.action_code, v_kind USING ERRCODE = '23514'; END IF;
  END IF;
  SELECT tenant_type INTO v_type FROM app.tenant_local WHERE tenant_id = NEW.tenant_id;
  IF NOT app.fn_role_has_holder(NEW.tenant_id, NEW.institution_id, NEW.set_by_role) OR NOT EXISTS (
       SELECT 1 FROM app.user_role_assignment a JOIN app.role_definition r ON r.tenant_id = a.tenant_id AND r.role_definition_id = a.role_definition_id
        WHERE a.tenant_id = NEW.tenant_id AND a.user_account_id = NEW.set_by_user_id AND a.status = 'ACTIVE'
          AND coalesce(r.base_standard_role, r.role_code) = NEW.set_by_role) THEN
    RAISE EXCEPTION 'ROLLDOWN_SETTER_NOT_ALLOWED: the setter does not hold %', NEW.set_by_role USING ERRCODE = '42501'; END IF;
  IF NEW.set_by_role = 'GOVERNING_BODY_APPROVER' AND EXISTS (
       SELECT 1 FROM app.user_role_assignment a JOIN app.role_definition r ON r.tenant_id = a.tenant_id AND r.role_definition_id = a.role_definition_id
        WHERE a.tenant_id = NEW.tenant_id AND a.status = 'ACTIVE' AND coalesce(r.base_standard_role, r.role_code) = 'MANAGEMENT') THEN
    RAISE EXCEPTION 'ROLLDOWN_SETTER_NOT_ALLOWED: Management exists, so the Governing Body Approver cannot change roll-down [C13]' USING ERRCODE = '42501'; END IF;
  IF NEW.set_by_role IN ('VICE_CHANCELLOR', 'UNIVERSITY_ADMIN') AND v_type <> 'UNIVERSITY_GROUP' THEN
    RAISE EXCEPTION 'ROLLDOWN_SETTER_NOT_ALLOWED: % exists only in a university tenant', NEW.set_by_role USING ERRCODE = '42501'; END IF;
  IF NEW.set_by_role = 'UNIVERSITY_ADMIN' AND NEW.permission_kind = 'APPROVE' THEN
    RAISE EXCEPTION 'ROLLDOWN_SETTER_NOT_ALLOWED: the University Admin may change non-approval roll-down only' USING ERRCODE = '42501'; END IF;
  RETURN NEW;
END$$;
COMMIT;
