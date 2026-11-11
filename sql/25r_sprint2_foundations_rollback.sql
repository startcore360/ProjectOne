-- Rollback for 25_sprint2_foundations.sql. Refuses to discard persisted data.
DO $$
BEGIN
 IF EXISTS(SELECT 1 FROM app.idempotency_key WHERE response_body IS NOT NULL) THEN
  RAISE EXCEPTION 'Rollback refused: idempotency response bodies exist; export before removing response_body.';
 END IF;
 IF EXISTS(SELECT 1 FROM app.attendance_entry_y2028) OR EXISTS(SELECT 1 FROM app.attendance_entry_y2029)
    OR EXISTS(SELECT 1 FROM app.attendance_entry_y2030) THEN
  RAISE EXCEPTION 'Rollback refused: attendance partition rows exist; retain or export them.';
 END IF;
END$$;
DROP TABLE IF EXISTS app.attendance_entry_y2030;
DROP TABLE IF EXISTS app.attendance_entry_y2029;
DROP TABLE IF EXISTS app.attendance_entry_y2028;
ALTER TABLE app.idempotency_key DROP COLUMN IF EXISTS response_body;
DELETE FROM ref.setting_definition WHERE source_ref='Sprint 2 DEC-5';
DELETE FROM ref.standard_role_action WHERE action_code='SYLLABUS_VIEW'
 AND limited_to IN ('Assigned subjects','Own department','Own classes','Own institution')
 AND role_code IN ('PROFESSOR','ASSOCIATE_PROFESSOR','ASSISTANT_PROFESSOR','HEAD_OF_DEPARTMENT','CLASS_TEACHER',
 'SUBJECT_TEACHER','DEPARTMENT_COORDINATOR','PRINCIPAL_COLLEGE','SCHOOL_PRINCIPAL','VICE_PRINCIPAL_COLLEGE','VICE_PRINCIPAL_SCHOOL');
CREATE OR REPLACE FUNCTION app.fn_approval_step_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_req uuid;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'APPROVAL_IMMUTABLE: steps are never deleted' USING ERRCODE='42501'; END IF;
 IF TG_OP='UPDATE' AND OLD.decision<>'PENDING' THEN RAISE EXCEPTION 'APPROVAL_IMMUTABLE: decided step % cannot change [R2-01]',OLD.approval_step_id USING ERRCODE='42501'; END IF;
 IF NEW.decision IN ('APPROVED','REJECTED','FORWARDED') THEN
  SELECT requested_by_user_id INTO v_req FROM app.approval_request WHERE tenant_id=NEW.tenant_id AND approval_request_id=NEW.approval_request_id;
  IF NEW.acted_by_user_id=v_req THEN RAISE EXCEPTION 'SELF_APPROVAL: requester cannot decide own request [AP-04, PQ-18, D-002]' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END$$;
