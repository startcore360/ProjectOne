-- EDU 360 V4.0 — database rule hardening (forward migration). Apply after 01_schema → 02_rules → 03_bindings → 03b_fk_indexes (→ 05_ref_data).
-- Backward compatible: replaces one function body; no table, column or data change. Rollback: 08r_v40_rules_rollback.sql.
-- V4-DB-01 (finding V4-F-17): the leave-approver setting checked only the governance-account rule [C6]. It did not stop
--   (a) the subject of the leave (the Principal / Vice-Chancellor / Data Protection Contact) being named as their own approver [D-002, AP-04], or
--   (b) a REGISTRAR / VICE_CHANCELLOR approver kind pointing at a user who does not hold that role [Change Table A30, C7].
--   Either gap would leave the go-live gate (C7) satisfied while every request of that person is unapprovable (blocked at decision time by K-015).
CREATE OR REPLACE FUNCTION app.fn_leave_approver_not_subject() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_kind text; v_subject_roles text[]; v_need text;
BEGIN
  SELECT user_type INTO v_kind FROM app.user_account WHERE tenant_id = NEW.tenant_id AND user_account_id = NEW.approver_user_id;
  IF NEW.approver_kind IN ('MANAGEMENT','GOVERNING_BODY_APPROVER') AND v_kind IS DISTINCT FROM 'GOVERNANCE' THEN
    RAISE EXCEPTION 'APPROVER_KIND_MISMATCH: Management / Governing Body Approver must be a governance account [C6]' USING ERRCODE = '23514';
  END IF;
  v_subject_roles := CASE NEW.subject_role WHEN 'PRINCIPAL' THEN ARRAY['PRINCIPAL_COLLEGE','SCHOOL_PRINCIPAL']
                                           WHEN 'VICE_CHANCELLOR' THEN ARRAY['VICE_CHANCELLOR']
                                           ELSE ARRAY['DATA_PROTECTION_CONTACT'] END;
  IF EXISTS (SELECT 1 FROM app.user_role_assignment a JOIN app.role_definition r
               ON r.tenant_id = a.tenant_id AND r.role_definition_id = a.role_definition_id
              WHERE a.tenant_id = NEW.tenant_id AND a.user_account_id = NEW.approver_user_id AND a.status = 'ACTIVE'
                AND coalesce(r.base_standard_role, r.role_code) = ANY (v_subject_roles)
                AND (NEW.institution_id IS NULL OR a.institution_id IS NULL OR a.institution_id = NEW.institution_id)) THEN
    RAISE EXCEPTION 'APPROVER_IS_SUBJECT: the person whose leave this is can never be its approver [D-002, AP-04]' USING ERRCODE = '23514';
  END IF;
  IF NEW.approver_kind IN ('REGISTRAR','VICE_CHANCELLOR') THEN
    v_need := NEW.approver_kind;
    IF NOT EXISTS (SELECT 1 FROM app.user_role_assignment a JOIN app.role_definition r
                     ON r.tenant_id = a.tenant_id AND r.role_definition_id = a.role_definition_id
                    WHERE a.tenant_id = NEW.tenant_id AND a.user_account_id = NEW.approver_user_id AND a.status = 'ACTIVE'
                      AND coalesce(r.base_standard_role, r.role_code) = v_need) THEN
      RAISE EXCEPTION 'APPROVER_ROLE_MISSING: approver kind % requires an active % assignment [A30, C7]', v_need, v_need USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END$$;
COMMENT ON FUNCTION app.fn_leave_approver_not_subject() IS 'V4.0 (V4-DB-01): governance-kind check [C6]; approver is never the subject [D-002]; Registrar/VC kinds need the matching active role [A30].';

-- V4-DB-02 (finding V4-F-18): 8 foreign keys in the ref schema had no covering index. AC-S4 in V3.3 was measured on app/platform only.
-- Reference tables are small and static, so this is a consistency fix, not a performance fix. Additive; rollback drops them.
CREATE INDEX IF NOT EXISTS fkx_v40_feature_catalog_module ON ref.feature_catalog (module_code);
CREATE INDEX IF NOT EXISTS fkx_v40_module_dependency_requires ON ref.module_dependency (requires_module_code);
CREATE INDEX IF NOT EXISTS fkx_v40_standard_role_action_action ON ref.standard_role_action (action_code);
CREATE INDEX IF NOT EXISTS fkx_v40_permission_matrix_cell_area ON ref.permission_matrix_cell (feature_area_code);
CREATE INDEX IF NOT EXISTS fkx_v40_approval_chain_step_action ON ref.approval_chain_step (action_code);
CREATE INDEX IF NOT EXISTS fkx_v40_report_catalog_module ON ref.report_catalog (module_code);
CREATE INDEX IF NOT EXISTS fkx_v40_report_catalog_action ON ref.report_catalog (required_action_code);
CREATE INDEX IF NOT EXISTS fkx_v40_setting_definition_action ON ref.setting_definition (owner_action_code);
