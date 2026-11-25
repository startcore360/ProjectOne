-- Destructive rollback for migration 72; use only when T9 grievance data may be discarded.
BEGIN;
DROP POLICY IF EXISTS p_grievance_confidentiality ON app.grievance_action;
DROP POLICY IF EXISTS p_grievance_confidentiality ON app.grievance_evidence;
DROP POLICY IF EXISTS p_grievance_confidentiality ON app.grievance_case;
DROP FUNCTION IF EXISTS app.fn_complaint_guard(uuid,uuid,uuid);
DROP TABLE IF EXISTS app.grievance_action;
DROP TABLE IF EXISTS app.grievance_evidence;
DROP TABLE IF EXISTS app.grievance_case;
DROP TABLE IF EXISTS app.disciplinary_incident;
COMMIT;
