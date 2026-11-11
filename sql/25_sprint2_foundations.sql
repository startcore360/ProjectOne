-- Sprint 2 Chunk 1 foundations. 01_schema.sql remains immutable.
-- DEC-10: only a HOD may self-approve a correction for a session that HOD personally marked.
CREATE OR REPLACE FUNCTION app.fn_approval_step_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_req uuid; v_process text; v_subject text; v_marked_by uuid; v_exception boolean := false;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'APPROVAL_IMMUTABLE: steps are never deleted' USING ERRCODE='42501'; END IF;
 IF TG_OP='UPDATE' AND OLD.decision<>'PENDING' THEN
  RAISE EXCEPTION 'APPROVAL_IMMUTABLE: decided step % cannot change [R2-01]',OLD.approval_step_id USING ERRCODE='42501';
 END IF;
 IF NEW.decision IN ('APPROVED','REJECTED','FORWARDED') THEN
  SELECT requested_by_user_id,process_code,subject_type INTO v_req,v_process,v_subject
   FROM app.approval_request WHERE tenant_id=NEW.tenant_id AND approval_request_id=NEW.approval_request_id;
  IF NEW.acted_by_user_id=v_req
    AND (v_process IN ('ATTENDANCE_CORRECTION_COLLEGE','ATTENDANCE_CORRECTION_SCHOOL') OR v_subject='ATTENDANCE_CORRECTION_REQUEST')
    AND NEW.acted_active_role='HEAD_OF_DEPARTMENT' THEN
   SELECT s.marked_by_user_id INTO v_marked_by
    FROM app.attendance_correction_request cr
    JOIN app.attendance_session s ON s.tenant_id=cr.tenant_id AND s.institution_id=cr.institution_id
      AND s.attendance_session_id=cr.attendance_session_id
    WHERE cr.tenant_id=NEW.tenant_id
      AND cr.attendance_correction_request_id=(SELECT subject_id FROM app.approval_request
       WHERE tenant_id=NEW.tenant_id AND approval_request_id=NEW.approval_request_id);
   v_exception := COALESCE(v_marked_by=NEW.acted_by_user_id,false);
  END IF;
  IF NEW.acted_by_user_id=v_req AND NOT v_exception THEN
   RAISE EXCEPTION 'SELF_APPROVAL: requester cannot decide own request [AP-04, PQ-18, D-002]' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END$$;

-- attendance_entry is partitioned by academic_year. Provision the next three academic years.
CREATE TABLE IF NOT EXISTS app.attendance_entry_y2028 PARTITION OF app.attendance_entry FOR VALUES IN (2028);
CREATE TABLE IF NOT EXISTS app.attendance_entry_y2029 PARTITION OF app.attendance_entry FOR VALUES IN (2029);
CREATE TABLE IF NOT EXISTS app.attendance_entry_y2030 PARTITION OF app.attendance_entry FOR VALUES IN (2030);
COMMENT ON TABLE app.attendance_entry_y2028 IS 'Attendance partition for academic year 2028; provision the next year annually before opening.';
COMMENT ON TABLE app.attendance_entry_y2029 IS 'Attendance partition for academic year 2029; provision the next year annually before opening.';
COMMENT ON TABLE app.attendance_entry_y2030 IS 'Attendance partition for academic year 2030; provision the next year annually before opening.';

-- Persist the complete idempotent response for replay (D5).
ALTER TABLE app.idempotency_key ADD COLUMN IF NOT EXISTS response_body jsonb;

-- DEC-5 attendance defaults; do not mutate existing tenant overrides.
INSERT INTO ref.setting_definition(setting_key,value_type,default_value,allowed_scope_levels,validation,owner_action_code,is_overridable,source_ref)
VALUES
 ('attendance.minimum_overall_percent','NUMERIC','75'::jsonb,ARRAY['TENANT','INSTITUTION'],'{"min":0,"max":100}'::jsonb,'ATTENDANCE_RULES_SET',true,'Sprint 2 DEC-5'),
 ('attendance.minimum_subject_percent','NUMERIC','75'::jsonb,ARRAY['TENANT','INSTITUTION'],'{"min":0,"max":100}'::jsonb,'ATTENDANCE_RULES_SET',true,'Sprint 2 DEC-5'),
 ('attendance.formula','TEXT','"SECTION_20_DEFAULT"'::jsonb,ARRAY['TENANT','INSTITUTION'],'{"allowed":["SECTION_20_DEFAULT"]}'::jsonb,'ATTENDANCE_RULES_SET',true,'Sprint 2 DEC-5'),
 ('attendance.school_marking_pattern','TEXT','"TWICE_DAILY"'::jsonb,ARRAY['TENANT','INSTITUTION'],'{"allowed":["TWICE_DAILY"]}'::jsonb,'ATTENDANCE_RULES_SET',true,'Sprint 2 DEC-5'),
 ('attendance.risk_warning_percent','NUMERIC','75'::jsonb,ARRAY['TENANT','INSTITUTION'],'{"min":0,"max":100}'::jsonb,'ATTENDANCE_RULES_SET',true,'Sprint 2 DEC-5'),
 ('attendance.risk_critical_percent','NUMERIC','65'::jsonb,ARRAY['TENANT','INSTITUTION'],'{"min":0,"max":100}'::jsonb,'ATTENDANCE_RULES_SET',true,'Sprint 2 DEC-5')
ON CONFLICT(setting_key) DO NOTHING;

-- DEC-14 standard templates only; tenant-local role-action copies are deliberately unchanged.
WITH desired(role_code,limited_to) AS (VALUES
 ('PROFESSOR','Assigned subjects'),('ASSOCIATE_PROFESSOR','Assigned subjects'),
 ('ASSISTANT_PROFESSOR','Assigned subjects'),('HEAD_OF_DEPARTMENT','Own department'),
 ('CLASS_TEACHER','Own classes'),('SUBJECT_TEACHER','Assigned subjects'),
 ('DEPARTMENT_COORDINATOR','Own department'),('PRINCIPAL_COLLEGE','Own institution'),
 ('SCHOOL_PRINCIPAL','Own institution'),('VICE_PRINCIPAL_COLLEGE','Own institution'),
 ('VICE_PRINCIPAL_SCHOOL','Own institution')
), missing AS (
 SELECT d.role_code,d.limited_to FROM desired d
 WHERE EXISTS(SELECT 1 FROM ref.standard_role r WHERE r.role_code=d.role_code)
 AND NOT EXISTS(SELECT 1 FROM ref.standard_role_action a WHERE a.role_code=d.role_code AND a.action_code='SYLLABUS_VIEW')
), numbered AS (
 SELECT (SELECT coalesce(max(standard_role_action_id),0) FROM ref.standard_role_action)
        + row_number() OVER(ORDER BY role_code) AS id,role_code,limited_to FROM missing
)
INSERT INTO ref.standard_role_action(standard_role_action_id,role_code,action_code,limited_to)
SELECT id,role_code,'SYLLABUS_VIEW',limited_to FROM numbered;

DO $
DECLARE missing text; missing_grants text;
BEGIN
 SELECT string_agg(x.code,', ' ORDER BY x.code) INTO missing FROM (VALUES
 ('TIMETABLE_BUILD'),('ONLINE_PERIOD_SET'),('TIMETABLE_VIEW'),('SYLLABUS_VIEW'),('SYLLABUS_MANAGE'),('ATTENDANCE_MARK'),('ATTENDANCE_NOT_HELD'),
 ('SUBSTITUTE_ASSIGN'),('ATTENDANCE_CORRECTION_REQUEST'),('ATTENDANCE_CORRECTION_APPROVE'),
 ('ATTENDANCE_BULK_UPLOAD'),('ATTENDANCE_BULK_UPLOAD_APPROVE'),('ATTENDANCE_RULES_SET'),
 ('ATTENDANCE_VIEW'),('ATTENDANCE_VIEW_OWN'),('ASSIGNMENT_CREATE'),('ASSIGNMENT_CLOSE'),
 ('STUDY_MATERIAL_UPLOAD'),('ASSIGNMENT_SUBMIT'),('ASSIGNMENT_EVALUATE'),('HOMEWORK_STATUS_MARK'),
 ('ASSIGNMENT_VIEW_OWN'),('HOMEWORK_COMPLIANCE_VIEW'),('STAFF_RECORD_MANAGE'),
 ('STAFF_DOCUMENT_VERIFY'),('STAFF_RECORD_VIEW')) x(code)
 WHERE NOT EXISTS(SELECT 1 FROM ref.action_catalog a WHERE a.action_code=x.code);
 IF missing IS NOT NULL THEN RAISE EXCEPTION 'SPRINT2_ACTION_CATALOG_INCOMPLETE: %',missing; END IF;
 SELECT string_agg(x.code,', ' ORDER BY x.code) INTO missing_grants FROM (VALUES
 ('TIMETABLE_BUILD'),('ONLINE_PERIOD_SET'),('TIMETABLE_VIEW'),('SYLLABUS_VIEW'),('SYLLABUS_MANAGE'),('ATTENDANCE_MARK'),('ATTENDANCE_NOT_HELD'),
 ('SUBSTITUTE_ASSIGN'),('ATTENDANCE_CORRECTION_REQUEST'),('ATTENDANCE_CORRECTION_APPROVE'),
 ('ATTENDANCE_BULK_UPLOAD'),('ATTENDANCE_BULK_UPLOAD_APPROVE'),('ATTENDANCE_RULES_SET'),
 ('ATTENDANCE_VIEW'),('ATTENDANCE_VIEW_OWN'),('ASSIGNMENT_CREATE'),('ASSIGNMENT_CLOSE'),
 ('STUDY_MATERIAL_UPLOAD'),('ASSIGNMENT_SUBMIT'),('ASSIGNMENT_EVALUATE'),('HOMEWORK_STATUS_MARK'),
 ('ASSIGNMENT_VIEW_OWN'),('HOMEWORK_COMPLIANCE_VIEW'),('STAFF_RECORD_MANAGE'),
 ('STAFF_DOCUMENT_VERIFY'),('STAFF_RECORD_VIEW')) x(code)
 WHERE NOT EXISTS(SELECT 1 FROM ref.standard_role_action a WHERE a.action_code=x.code);
 IF missing_grants IS NOT NULL THEN RAISE EXCEPTION 'SPRINT2_ACTION_GRANTS_INCOMPLETE: %',missing_grants; END IF;
END$;

-- Operations: before each academic year, create app.attendance_entry_yYYYY for the next year,
-- verify it is attached to app.attendance_entry, and retain a rollback script that refuses to drop nonempty partitions.
