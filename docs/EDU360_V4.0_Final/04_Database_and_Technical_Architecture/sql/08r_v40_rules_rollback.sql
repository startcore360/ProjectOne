-- Rollback of 08_v40_rules.sql: restores the V3.3 function body exactly as in 02_rules.sql.
CREATE OR REPLACE FUNCTION app.fn_leave_approver_not_subject() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_kind text;
BEGIN
  SELECT user_type INTO v_kind FROM app.user_account WHERE tenant_id = NEW.tenant_id AND user_account_id = NEW.approver_user_id;
  IF NEW.approver_kind IN ('MANAGEMENT','GOVERNING_BODY_APPROVER') AND v_kind IS DISTINCT FROM 'GOVERNANCE' THEN
    RAISE EXCEPTION 'APPROVER_KIND_MISMATCH: Management / Governing Body Approver must be a governance account [C6]' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END$$;
COMMENT ON FUNCTION app.fn_leave_approver_not_subject() IS NULL;
DROP INDEX IF EXISTS ref.fkx_v40_feature_catalog_module, ref.fkx_v40_module_dependency_requires, ref.fkx_v40_standard_role_action_action,
  ref.fkx_v40_permission_matrix_cell_area, ref.fkx_v40_approval_chain_step_action, ref.fkx_v40_report_catalog_module,
  ref.fkx_v40_report_catalog_action, ref.fkx_v40_setting_definition_action;
