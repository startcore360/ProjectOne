-- 56_v41_rolldown_management_revocation.sql
-- RD-G8: once a Management assignment exists, GBA-authored stand-in authority is permanently revoked.
BEGIN;
-- RD-G8 audit action. Action number 283 is reserved for the permanent GBA revocation event.
INSERT INTO ref.action_catalog
    (action_code, action_no, module_name, phase, description, is_approval_action, sensitive_class, changed_in)
VALUES
    ('ROLLDOWN_GBA_REVOKED', 283, 'Users and roles', 1,
     'Record permanent revocation of Governing Body Approver roll-down authority when Management is assigned',
     false, NULL, '4.1')
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS app.gba_rolldown_revocation (
    tenant_id uuid PRIMARY KEY,
    management_assignment_id uuid NOT NULL,
    revoked_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    CONSTRAINT fk_gba_rolldown_revocation_assignment
      FOREIGN KEY (tenant_id, management_assignment_id)
      REFERENCES app.user_role_assignment (tenant_id, user_role_assignment_id) ON DELETE RESTRICT,
    CONSTRAINT ck_gba_rolldown_revocation_updated CHECK (updated_at >= created_at)
);
ALTER TABLE app.gba_rolldown_revocation ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.gba_rolldown_revocation FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_tenant ON app.gba_rolldown_revocation;
CREATE POLICY p_tenant ON app.gba_rolldown_revocation
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
GRANT SELECT, INSERT ON app.gba_rolldown_revocation TO edu_app;
GRANT SELECT ON app.gba_rolldown_revocation TO edu_report;

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
       AND NOT (o.set_by_role = 'GOVERNING_BODY_APPROVER' AND EXISTS (
           SELECT 1 FROM app.gba_rolldown_revocation g WHERE g.tenant_id = o.tenant_id))
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
  IF TG_OP = 'UPDATE' AND OLD.status = 'REVOKED' AND NEW.status <> 'REVOKED' THEN
    RAISE EXCEPTION 'ROLLDOWN_GBA_REVOCATION_PERMANENT: revoked authority cannot be reactivated [RD-G8]' USING ERRCODE = '23514';
  END IF;
  IF TG_OP = 'UPDATE' AND OLD.status = 'ACTIVE' AND NEW.status = 'REVOKED'
     AND current_setting('app.rd_g8_revocation', true) = 'on' THEN
    RETURN NEW;
  END IF;
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
  IF NEW.set_by_role = 'GOVERNING_BODY_APPROVER' AND EXISTS (
       SELECT 1 FROM app.gba_rolldown_revocation g WHERE g.tenant_id = NEW.tenant_id) THEN
    RAISE EXCEPTION 'ROLLDOWN_GBA_REVOCATION_PERMANENT: GBA authority was permanently revoked when Management was assigned [RD-G8]' USING ERRCODE = '42501'; END IF;
  IF NEW.set_by_role IN ('VICE_CHANCELLOR', 'UNIVERSITY_ADMIN') AND v_type <> 'UNIVERSITY_GROUP' THEN
    RAISE EXCEPTION 'ROLLDOWN_SETTER_NOT_ALLOWED: % exists only in a university tenant', NEW.set_by_role USING ERRCODE = '42501'; END IF;
  IF NEW.set_by_role = 'UNIVERSITY_ADMIN' AND NEW.permission_kind = 'APPROVE' THEN
    RAISE EXCEPTION 'ROLLDOWN_SETTER_NOT_ALLOWED: the University Admin may change non-approval roll-down only' USING ERRCODE = '42501'; END IF;
  RETURN NEW;
END$$;

CREATE OR REPLACE FUNCTION app.fn_revoke_gba_rolldown()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_role text; v_override app.role_rolldown_override%ROWTYPE; v_event uuid;
        v_now timestamptz; v_payload jsonb; v_old_scope text;
BEGIN
    IF NEW.status <> 'ACTIVE' OR NEW.effective_from > now()
       OR (NEW.effective_to IS NOT NULL AND NEW.effective_to <= now()) THEN RETURN NEW; END IF;
    SELECT coalesce(r.base_standard_role, r.role_code) INTO v_role
      FROM app.role_definition r
     WHERE r.tenant_id = NEW.tenant_id AND r.role_definition_id = NEW.role_definition_id
       AND r.status = 'ACTIVE';
    IF v_role IS DISTINCT FROM 'MANAGEMENT' THEN RETURN NEW; END IF;

    INSERT INTO app.gba_rolldown_revocation
        (tenant_id, management_assignment_id, revoked_at, created_at, created_by, updated_at, updated_by)
    VALUES (NEW.tenant_id, NEW.user_role_assignment_id, clock_timestamp(),
            clock_timestamp(), NEW.user_account_id, clock_timestamp(), NEW.user_account_id)
    ON CONFLICT (tenant_id) DO NOTHING;

    v_now := clock_timestamp();
    v_old_scope := current_setting('app.institution_scope', true);
    PERFORM set_config('app.institution_scope', 'ALL', true);
    PERFORM set_config('app.rd_g8_revocation', 'on', true);
    FOR v_override IN
        UPDATE app.role_rolldown_override
           SET status = 'REVOKED', updated_at = v_now, updated_by = NEW.user_account_id,
               row_version = row_version + 1
         WHERE tenant_id = NEW.tenant_id AND set_by_role = 'GOVERNING_BODY_APPROVER'
           AND status = 'ACTIVE'
        RETURNING *
    LOOP
        v_event := gen_random_uuid();
        v_payload := jsonb_build_object('override_id', v_override.role_rolldown_override_id,
                                        'status', 'REVOKED',
                                        'reason', 'Management assignment permanently revoked GBA roll-down authority');
        INSERT INTO app.audit_outbox
            (tenant_id, audit_event_id, institution_id, occurred_at, actor_kind, actor_user_id,
             active_role_code, operation, action_code, entity_type, entity_id, outcome,
             new_values, correlation_id, payload_hash, created_at, created_by, updated_at, updated_by)
        VALUES
            (NEW.tenant_id, v_event, v_override.institution_id, v_now, 'USER', NEW.user_account_id,
             'MANAGEMENT', 'CONFIG_CHANGE', 'ROLLDOWN_GBA_REVOKED', 'ROLE_ROLLDOWN_OVERRIDE',
             v_override.role_rolldown_override_id, 'SUCCESS', v_payload, v_event::text,
             encode(public.digest(convert_to(v_payload::text, 'UTF8'), 'sha256'), 'hex'),
             v_now, NEW.user_account_id, v_now, NEW.user_account_id);
    END LOOP;
    PERFORM set_config('app.rd_g8_revocation', 'off', true);
    PERFORM set_config('app.institution_scope', COALESCE(v_old_scope, 'NONE'), true);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_revoke_gba_rolldown ON app.user_role_assignment;
CREATE TRIGGER trg_revoke_gba_rolldown
  AFTER INSERT OR UPDATE OF status, role_definition_id, effective_from, effective_to
  ON app.user_role_assignment FOR EACH ROW EXECUTE FUNCTION app.fn_revoke_gba_rolldown();

-- Existing Management assignments revoke GBA authority during upgrade as well.
DO $$
DECLARE a record; o app.role_rolldown_override%ROWTYPE; t timestamptz; e uuid; p jsonb; old_scope text;
BEGIN
  FOR a IN
    SELECT DISTINCT ON (x.tenant_id) x.tenant_id, x.user_role_assignment_id, x.user_account_id
      FROM app.user_role_assignment x
      JOIN app.role_definition r ON r.tenant_id=x.tenant_id AND r.role_definition_id=x.role_definition_id
     WHERE x.status='ACTIVE' AND x.effective_from<=now()
       AND (x.effective_to IS NULL OR x.effective_to>now())
       AND r.status='ACTIVE' AND coalesce(r.base_standard_role,r.role_code)='MANAGEMENT'
     ORDER BY x.tenant_id, x.effective_from, x.user_role_assignment_id
  LOOP
    INSERT INTO app.gba_rolldown_revocation
      (tenant_id, management_assignment_id, revoked_at, created_at, created_by, updated_at, updated_by)
    VALUES (a.tenant_id,a.user_role_assignment_id,now(),now(),a.user_account_id,now(),a.user_account_id)
    ON CONFLICT (tenant_id) DO NOTHING;
    old_scope := current_setting('app.institution_scope',true);
    PERFORM set_config('app.institution_scope','ALL',true);
    PERFORM set_config('app.rd_g8_revocation','on',true);
    t := clock_timestamp();
    FOR o IN
      UPDATE app.role_rolldown_override
         SET status='REVOKED', updated_at=t, updated_by=a.user_account_id, row_version=row_version+1
       WHERE tenant_id=a.tenant_id AND set_by_role='GOVERNING_BODY_APPROVER' AND status='ACTIVE'
      RETURNING *
    LOOP
      e := gen_random_uuid();
      p := jsonb_build_object('override_id',o.role_rolldown_override_id,'status','REVOKED',
                              'reason','Existing Management assignment backfill');
      INSERT INTO app.audit_outbox
        (tenant_id,audit_event_id,institution_id,occurred_at,actor_kind,actor_user_id,active_role_code,
         operation,action_code,entity_type,entity_id,outcome,new_values,correlation_id,payload_hash,
         created_at,created_by,updated_at,updated_by)
      VALUES
        (a.tenant_id,e,o.institution_id,t,'USER',a.user_account_id,'MANAGEMENT','CONFIG_CHANGE',
         'ROLLDOWN_GBA_REVOKED','ROLE_ROLLDOWN_OVERRIDE',o.role_rolldown_override_id,'SUCCESS',
         p,e::text,encode(public.digest(convert_to(p::text,'UTF8'),'sha256'),'hex'),
         t,a.user_account_id,t,a.user_account_id);
    END LOOP;
    PERFORM set_config('app.rd_g8_revocation','off',true);
    PERFORM set_config('app.institution_scope',COALESCE(old_scope,'NONE'),true);
  END LOOP;
END $$;
COMMIT;
