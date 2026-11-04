-- EDU 360 V4.0 — role roll-down (decision V4-PD-01, Product Owner, 30 Oct 2026). Forward migration; additive only.
-- Apply after 08_v40_rules.sql. Reference rows: 10d_v40_rolldown_ref.sql. Rollback: 10r_v40_rolldown_rollback.sql.
-- Rule: when a role has no active holder for the institution (or university-wide), its permissions roll to a stand-in:
--   approval actions to the head of the next level down, other actions to the admin of the next level down
--   (ref.role_rolldown_default, preference order, first stand-in with an active holder wins). The level above
--   (Management; the Governing Body Approver where there is no Management; in a university tenant the Vice-Chancellor,
--   and the University Admin for non-approval actions) may assign a different stand-in or withhold a non-approval action
--   (app.role_rolldown_override). Guards RD-G1..G8 are in Deliverable 1 V4-19.

CREATE TABLE ref.role_rolldown_default (
  role_rolldown_default_id integer NOT NULL,
  missing_role_code text NOT NULL,
  context text NOT NULL,
  permission_kind text NOT NULL,
  preference smallint NOT NULL,
  stand_in_role_code text NOT NULL,
  note text,
  CONSTRAINT pk_role_rolldown_default PRIMARY KEY (role_rolldown_default_id),
  CONSTRAINT ck_role_rolldown_default_context CHECK (context IN ('COLLEGE', 'SCHOOL', 'UNIVERSITY_WIDE')),
  CONSTRAINT ck_role_rolldown_default_kind CHECK (permission_kind IN ('APPROVE', 'OTHER')),
  CONSTRAINT ck_role_rolldown_default_preference CHECK (preference BETWEEN 1 AND 5),
  CONSTRAINT ck_role_rolldown_default_r1 CHECK (stand_in_role_code <> missing_role_code),
  CONSTRAINT ck_role_rolldown_default_r2 CHECK (missing_role_code NOT IN ('APPLICANT','BILLING_MANAGER','DATA_PROTECTION_OFFICER','DRIVER','GOVERNING_BODY_APPROVER','HOSTEL_STAFF','HOUSEKEEPING_SUPERVISOR','PARENT_COLLEGE','PARENT_SCHOOL','PLATFORM_ADMIN','PRODUCT_OWNER','RELEASE_MANAGER','SECURITY_STAFF','SECURITY_SUPERVISOR','STUDENT_COLLEGE','STUDENT_SCHOOL','SUBJECT_TEACHER','SUPPORT_MANAGER','TRANSPORT_STAFF')),
  CONSTRAINT ck_role_rolldown_default_r3 CHECK (stand_in_role_code NOT IN ('APPLICANT','BILLING_MANAGER','DATA_PROTECTION_OFFICER','GOVERNING_BODY_APPROVER','PARENT_COLLEGE','PARENT_SCHOOL','PLATFORM_ADMIN','PRODUCT_OWNER','RELEASE_MANAGER','STUDENT_COLLEGE','STUDENT_SCHOOL','SUPPORT_MANAGER')),
  CONSTRAINT ck_role_rolldown_default_note CHECK (char_length(note) BETWEEN 0 AND 500),
  CONSTRAINT uq_role_rolldown_default_1 UNIQUE (missing_role_code, context, permission_kind, preference),
  CONSTRAINT fk_role_rolldown_default_missing FOREIGN KEY (missing_role_code) REFERENCES ref.standard_role (role_code) ON DELETE RESTRICT,
  CONSTRAINT fk_role_rolldown_default_stand_in FOREIGN KEY (stand_in_role_code) REFERENCES ref.standard_role (role_code) ON DELETE RESTRICT
);
CREATE INDEX fkx_role_rolldown_default_stand_in ON ref.role_rolldown_default (stand_in_role_code);
COMMENT ON TABLE ref.role_rolldown_default IS 'V4.0 (V4-PD-01): default stand-in for a role with no active holder, per context and permission kind; Deliverable 3 sheet "Roll-down Map".';
COMMENT ON COLUMN ref.role_rolldown_default.context IS 'COLLEGE / SCHOOL = inside an institution of that type; UNIVERSITY_WIDE = items with no institution (university tenant).';
COMMENT ON COLUMN ref.role_rolldown_default.permission_kind IS 'APPROVE = ref.action_catalog.is_approval_action; OTHER = every other action.';
COMMENT ON COLUMN ref.role_rolldown_default.preference IS 'Order: the first stand-in with an active holder is used.';
GRANT SELECT ON ref.role_rolldown_default TO edu_app, edu_report, edu_platform;

CREATE TABLE app.role_rolldown_override (
  tenant_id uuid NOT NULL,
  role_rolldown_override_id uuid NOT NULL,
  institution_id uuid,
  missing_role_code text NOT NULL,
  permission_kind text NOT NULL,
  action_code text,
  mode text NOT NULL,
  target_role_code text,
  reason text NOT NULL,
  set_by_user_id uuid NOT NULL,
  set_by_role text NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  effective_from timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_role_rolldown_override PRIMARY KEY (tenant_id, role_rolldown_override_id),
  CONSTRAINT ck_role_rolldown_override_kind CHECK (permission_kind IN ('APPROVE', 'OTHER')),
  CONSTRAINT ck_role_rolldown_override_mode CHECK (mode IN ('ASSIGN', 'WITHHOLD')),
  CONSTRAINT ck_role_rolldown_override_status CHECK (status IN ('ACTIVE', 'REVOKED')),
  CONSTRAINT ck_role_rolldown_override_reason CHECK (char_length(reason) BETWEEN 5 AND 500),
  CONSTRAINT ck_role_rolldown_override_set_by_role CHECK (set_by_role IN ('MANAGEMENT','GOVERNING_BODY_APPROVER','VICE_CHANCELLOR','UNIVERSITY_ADMIN')),
  CONSTRAINT ck_role_rolldown_override_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_role_rolldown_override_r1 CHECK ((mode = 'ASSIGN') = (target_role_code IS NOT NULL)),
  CONSTRAINT ck_role_rolldown_override_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_role_rolldown_override_tid UNIQUE (tenant_id, role_rolldown_override_id),
  CONSTRAINT fk_role_rolldown_override_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT,
  CONSTRAINT fk_role_rolldown_override_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT,
  CONSTRAINT fk_role_rolldown_override_set_by FOREIGN KEY (tenant_id, set_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT,
  CONSTRAINT fk_role_rolldown_override_action FOREIGN KEY (action_code) REFERENCES ref.action_catalog (action_code) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX ux_role_rolldown_override_1 ON app.role_rolldown_override (tenant_id, institution_id, missing_role_code, permission_kind, action_code) NULLS NOT DISTINCT WHERE status = 'ACTIVE';
CREATE INDEX ix_role_rolldown_override_tenant_id ON app.role_rolldown_override (tenant_id);
CREATE INDEX ix_role_rolldown_override_institution_id ON app.role_rolldown_override (tenant_id, institution_id);
CREATE INDEX ix_role_rolldown_override_set_by ON app.role_rolldown_override (tenant_id, set_by_user_id);
CREATE INDEX ix_role_rolldown_override_action ON app.role_rolldown_override (action_code);
COMMENT ON TABLE app.role_rolldown_override IS 'V4.0 (V4-PD-01): change made by the level above to the default roll-down — another stand-in (ASSIGN) or, for non-approval actions only, nobody (WITHHOLD). Action ROLLDOWN_OVERRIDE_SET.';
COMMENT ON COLUMN app.role_rolldown_override.institution_id IS 'NULL = university-wide items (university tenant).';
COMMENT ON COLUMN app.role_rolldown_override.action_code IS 'NULL = every action of this kind of the missing role; a specific action wins over NULL.';
COMMENT ON COLUMN app.role_rolldown_override.mode IS 'ASSIGN to target_role_code, or WITHHOLD (non-approval actions only: the action is unavailable until the role is filled).';
COMMENT ON COLUMN app.role_rolldown_override.set_by_role IS 'Active role of the setter (checked by trigger): Management; Governing Body Approver only where no Management; Vice-Chancellor / University Admin only in a university tenant; University Admin only for OTHER.';

ALTER TABLE app.approval_step ADD COLUMN acting_for_role text;
ALTER TABLE app.approval_step ADD CONSTRAINT ck_approval_step_acting_for_role CHECK (char_length(acting_for_role) BETWEEN 2 AND 60);
ALTER TABLE app.approval_step ADD CONSTRAINT ck_approval_step_r5 CHECK (acting_for_role IS NULL OR decision <> 'PENDING');
COMMENT ON COLUMN app.approval_step.acting_for_role IS 'V4.0 (RD-G7): role whose authority the decider used through roll-down ("Acting for Vice-Chancellor"); NULL when the decider holds the step role.';

CREATE OR REPLACE FUNCTION app.fn_role_has_holder(p_tenant uuid, p_institution uuid, p_role text) RETURNS boolean LANGUAGE sql STABLE AS $$
  SELECT EXISTS (SELECT 1 FROM app.user_role_assignment a JOIN app.role_definition r
                   ON r.tenant_id = a.tenant_id AND r.role_definition_id = a.role_definition_id
                  JOIN app.user_account u ON u.tenant_id = a.tenant_id AND u.user_account_id = a.user_account_id
                 WHERE a.tenant_id = p_tenant AND a.status = 'ACTIVE' AND r.status = 'ACTIVE' AND u.status = 'ACTIVE'
                   AND coalesce(r.base_standard_role, r.role_code) = p_role
                   AND a.effective_from <= now() AND (a.effective_to IS NULL OR a.effective_to > now())
                   AND (a.institution_id IS NULL OR a.institution_id = p_institution))
$$;
COMMENT ON FUNCTION app.fn_role_has_holder(uuid, uuid, text) IS 'V4.0: true when an active user holds the role (or a custom role built on it) for the institution; tenant-scope assignments count everywhere.';

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
END$$;
COMMENT ON FUNCTION app.fn_rolldown_resolve(uuid, text, text) IS 'V4.0 (V4-PD-01): who exercises p_role''s right p_action for the institution (NULL = university-wide). HOLDER, ROLLDOWN (via path), WITHHELD, or UNRESOLVED (no stand-in / loop: shown to the level above).';

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
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.role_rolldown_override FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
CREATE TRIGGER trg_rule_1 BEFORE INSERT OR UPDATE ON app.role_rolldown_override FOR EACH ROW EXECUTE FUNCTION app.fn_rolldown_override_guard();
ALTER TABLE app.role_rolldown_override ENABLE ROW LEVEL SECURITY; ALTER TABLE app.role_rolldown_override FORCE ROW LEVEL SECURITY;
CREATE POLICY p_tenant ON app.role_rolldown_override USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY p_institution ON app.role_rolldown_override AS RESTRICTIVE USING (institution_id IS NULL OR current_setting('app.institution_scope', true) = 'ALL' OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]));
GRANT SELECT, INSERT, UPDATE ON app.role_rolldown_override TO edu_app;
GRANT SELECT ON app.role_rolldown_override TO edu_report;
GRANT EXECUTE ON FUNCTION app.fn_rolldown_resolve(uuid, text, text), app.fn_role_has_holder(uuid, uuid, text) TO edu_app, edu_report;
