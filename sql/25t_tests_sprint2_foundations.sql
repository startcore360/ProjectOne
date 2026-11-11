-- Sprint 2 Chunk 1 SQL checks. Run after 04_tests.sql.
SELECT public.expect_ok('S2-DB-01','2028-2030 attendance partitions provisioned',$q$
 DO $d$ BEGIN
  IF to_regclass('app.attendance_entry_y2028') IS NULL OR to_regclass('app.attendance_entry_y2029') IS NULL OR to_regclass('app.attendance_entry_y2030') IS NULL
  THEN RAISE EXCEPTION 'ATTENDANCE_PARTITION_MISSING'; END IF;
 END $d$
$q$);
SELECT public.expect_ok('S2-DB-02','six attendance setting definitions and defaults',$q$
 DO $d$ DECLARE n int; BEGIN
  SELECT count(*) INTO n FROM ref.setting_definition WHERE setting_key IN
   ('attendance.minimum_overall_percent','attendance.minimum_subject_percent','attendance.formula',
    'attendance.school_marking_pattern','attendance.risk_warning_percent','attendance.risk_critical_percent');
  IF n<>6 THEN RAISE EXCEPTION 'ATTENDANCE_SETTINGS_INCOMPLETE'; END IF;
  IF (SELECT default_value FROM ref.setting_definition WHERE setting_key='attendance.minimum_overall_percent')<>'75'::jsonb
   OR (SELECT default_value FROM ref.setting_definition WHERE setting_key='attendance.minimum_subject_percent')<>'75'::jsonb
   OR (SELECT default_value FROM ref.setting_definition WHERE setting_key='attendance.formula')<>'"SECTION_20_DEFAULT"'::jsonb
   OR (SELECT default_value FROM ref.setting_definition WHERE setting_key='attendance.school_marking_pattern')<>'"TWICE_DAILY"'::jsonb
   OR (SELECT default_value FROM ref.setting_definition WHERE setting_key='attendance.risk_warning_percent')<>'75'::jsonb
   OR (SELECT default_value FROM ref.setting_definition WHERE setting_key='attendance.risk_critical_percent')<>'65'::jsonb
  THEN RAISE EXCEPTION 'ATTENDANCE_SETTING_DEFAULT_MISMATCH'; END IF;
 END $d$
$q$);
SELECT public.expect_ok('S2-DB-03','DEC-14 syllabus view grants',$q$
 DO $d$ DECLARE n int; BEGIN
  SELECT count(*) INTO n FROM ref.standard_role_action WHERE action_code='SYLLABUS_VIEW'
   AND role_code IN ('PROFESSOR','ASSOCIATE_PROFESSOR','ASSISTANT_PROFESSOR','HEAD_OF_DEPARTMENT','CLASS_TEACHER',
    'SUBJECT_TEACHER','DEPARTMENT_COORDINATOR','PRINCIPAL_COLLEGE','SCHOOL_PRINCIPAL','VICE_PRINCIPAL_COLLEGE','VICE_PRINCIPAL_SCHOOL');
  IF n<>11 THEN RAISE EXCEPTION 'SYLLABUS_VIEW_GRANTS_INCOMPLETE'; END IF;
 END $d$
$q$);

SELECT public.expect_ok('S2-DB-04','HOD requester may self-approve only their own marked attendance session',$q$
 DO $d$ DECLARE t uuid:='a0000000-0000-0000-0000-000000000000'; u uuid:='a0000000-0000-0000-0000-000000000101';
 i uuid:='a0000000-0000-0000-0000-000000000201'; ses uuid:='a0000000-0000-0000-0000-000000000c01';
 corr uuid:='a0000000-0000-0000-0000-000000000d31'; req uuid:='a0000000-0000-0000-0000-000000000e31';
 step uuid:='a0000000-0000-0000-0000-000000000f31';
 BEGIN
  INSERT INTO app.approval_request(tenant_id,approval_request_id,institution_id,process_code,subject_type,subject_id,requested_by_user_id,requester_active_role,created_by,updated_by)
  VALUES(t,req,i,'ATTENDANCE_CORRECTION_COLLEGE','ATTENDANCE_CORRECTION_REQUEST',corr,u,'HEAD_OF_DEPARTMENT',u,u);
  INSERT INTO app.attendance_correction_request(tenant_id,attendance_correction_request_id,institution_id,attendance_session_id,requested_by_user_id,reason,approval_request_id,status,created_by,updated_by)
  VALUES(t,corr,i,ses,u,'S2 own marked session',req,'REQUESTED',u,u);
  INSERT INTO app.approval_step(tenant_id,approval_step_id,approval_request_id,step_no,approver_role_code,created_by,updated_by)
  VALUES(t,step,req,1,'HEAD_OF_DEPARTMENT',u,u);
  UPDATE app.approval_step SET decision='APPROVED',acted_by_user_id=u,acted_active_role='HEAD_OF_DEPARTMENT',decided_at=now()
   WHERE tenant_id=t AND approval_step_id=step;
 END $d$
$q$);

SELECT public.expect_fail('S2-DB-05','HOD cannot self-approve a session marked by another teacher',$q$
 DO $d$ DECLARE t uuid:='a0000000-0000-0000-0000-000000000000'; u uuid:='a0000000-0000-0000-0000-000000000101';
 i uuid:='a0000000-0000-0000-0000-000000000201'; ses uuid:='a0000000-0000-0000-0000-000000000c32';
 corr uuid:='a0000000-0000-0000-0000-000000000d32'; req uuid:='a0000000-0000-0000-0000-000000000e32';
 step uuid:='a0000000-0000-0000-0000-000000000f32';
 BEGIN
  INSERT INTO app.attendance_session(tenant_id,attendance_session_id,institution_id,section_id,session_kind,attendance_date,academic_year,marked_by_user_id,marked_as,created_by,updated_by)
  VALUES(t,ses,i,'a0000000-0000-0000-0000-000000000504','PERIOD','2026-10-22',2026,'a0000000-0000-0000-0000-000000000102','ASSIGNED',u,u);
  INSERT INTO app.approval_request(tenant_id,approval_request_id,institution_id,process_code,subject_type,subject_id,requested_by_user_id,requester_active_role,created_by,updated_by)
  VALUES(t,req,i,'ATTENDANCE_CORRECTION_COLLEGE','ATTENDANCE_CORRECTION_REQUEST',corr,u,'HEAD_OF_DEPARTMENT',u,u);
  INSERT INTO app.attendance_correction_request(tenant_id,attendance_correction_request_id,institution_id,attendance_session_id,requested_by_user_id,reason,approval_request_id,status,created_by,updated_by)
  VALUES(t,corr,i,ses,u,'S2 other teacher session',req,'REQUESTED',u,u);
  INSERT INTO app.approval_step(tenant_id,approval_step_id,approval_request_id,step_no,approver_role_code,created_by,updated_by)
  VALUES(t,step,req,1,'HEAD_OF_DEPARTMENT',u,u);
  UPDATE app.approval_step SET decision='APPROVED',acted_by_user_id=u,acted_active_role='HEAD_OF_DEPARTMENT',decided_at=now()
   WHERE tenant_id=t AND approval_step_id=step;
 END $d$
$q$,'SELF_APPROVAL');

SELECT public.expect_fail('S2-DB-06','another role cannot use the HOD exception',$q$
 DO $d$ DECLARE t uuid:='a0000000-0000-0000-0000-000000000000'; u uuid:='a0000000-0000-0000-0000-000000000101';
 i uuid:='a0000000-0000-0000-0000-000000000201'; corr uuid:='a0000000-0000-0000-0000-000000000d33';
 req uuid:='a0000000-0000-0000-0000-000000000e33'; step uuid:='a0000000-0000-0000-0000-000000000f33';
 BEGIN
  INSERT INTO app.approval_request(tenant_id,approval_request_id,institution_id,process_code,subject_type,subject_id,requested_by_user_id,requester_active_role,created_by,updated_by)
  VALUES(t,req,i,'ATTENDANCE_CORRECTION_COLLEGE','ATTENDANCE_CORRECTION_REQUEST',corr,u,'PRINCIPAL_COLLEGE',u,u);
  INSERT INTO app.attendance_correction_request(tenant_id,attendance_correction_request_id,institution_id,attendance_session_id,requested_by_user_id,reason,approval_request_id,status,created_by,updated_by)
  VALUES(t,corr,i,'a0000000-0000-0000-0000-000000000c01',u,'S2 wrong role',req,'REQUESTED',u,u);
  INSERT INTO app.approval_step(tenant_id,approval_step_id,approval_request_id,step_no,approver_role_code,created_by,updated_by)
  VALUES(t,step,req,1,'PRINCIPAL_COLLEGE',u,u);
  UPDATE app.approval_step SET decision='APPROVED',acted_by_user_id=u,acted_active_role='PRINCIPAL_COLLEGE',decided_at=now()
   WHERE tenant_id=t AND approval_step_id=step;
 END $d$
$q$,'SELF_APPROVAL');

SELECT public.expect_fail('S2-DB-07','non-attendance process cannot use HOD exception',$q$
 DO $d$ DECLARE t uuid:='a0000000-0000-0000-0000-000000000000'; u uuid:='a0000000-0000-0000-0000-000000000101';
 i uuid:='a0000000-0000-0000-0000-000000000201'; req uuid:='a0000000-0000-0000-0000-000000000e34';
 step uuid:='a0000000-0000-0000-0000-000000000f34';
 BEGIN
  INSERT INTO app.approval_request(tenant_id,approval_request_id,institution_id,process_code,subject_type,subject_id,requested_by_user_id,requester_active_role,created_by,updated_by)
  VALUES(t,req,i,'TEST_CHAIN','TEST','a0000000-0000-0000-0000-000000000d34',u,'HEAD_OF_DEPARTMENT',u,u);
  INSERT INTO app.approval_step(tenant_id,approval_step_id,approval_request_id,step_no,approver_role_code,created_by,updated_by)
  VALUES(t,step,req,1,'HEAD_OF_DEPARTMENT',u,u);
  UPDATE app.approval_step SET decision='APPROVED',acted_by_user_id=u,acted_active_role='HEAD_OF_DEPARTMENT',decided_at=now()
   WHERE tenant_id=t AND approval_step_id=step;
 END $d$
$q$,'SELF_APPROVAL');

SELECT public.expect_fail('S2-DB-08','decided step remains immutable after the HOD exception',$q$
 UPDATE app.approval_step SET remark='attempted mutation'
 WHERE tenant_id='a0000000-0000-0000-0000-000000000000'
 AND approval_step_id='a0000000-0000-0000-0000-000000000f31'
$q$,'APPROVAL_IMMUTABLE');

SELECT public.expect_fail('S2-DB-09','HOD requester cannot reject their own correction request',$q$
 DO $d$ DECLARE t uuid:='a0000000-0000-0000-0000-000000000000'; u uuid:='a0000000-0000-0000-0000-000000000101';
 i uuid:='a0000000-0000-0000-0000-000000000201'; corr uuid:='a0000000-0000-0000-0000-000000000d39';
 req uuid:='a0000000-0000-0000-0000-000000000e39'; step uuid:='a0000000-0000-0000-0000-000000000f39';
 BEGIN
  INSERT INTO app.approval_request(tenant_id,approval_request_id,institution_id,process_code,subject_type,subject_id,requested_by_user_id,requester_active_role,created_by,updated_by)
  VALUES(t,req,i,'ATTENDANCE_CORRECTION_COLLEGE','ATTENDANCE_CORRECTION_REQUEST',corr,u,'HEAD_OF_DEPARTMENT',u,u);
  INSERT INTO app.attendance_correction_request(tenant_id,attendance_correction_request_id,institution_id,attendance_session_id,requested_by_user_id,reason,approval_request_id,status,created_by,updated_by)
  VALUES(t,corr,i,'a0000000-0000-0000-0000-000000000c01',u,'S2 self rejection',req,'REQUESTED',u,u);
  INSERT INTO app.approval_step(tenant_id,approval_step_id,approval_request_id,step_no,approver_role_code,created_by,updated_by)
  VALUES(t,step,req,1,'HEAD_OF_DEPARTMENT',u,u);
  UPDATE app.approval_step SET decision='REJECTED',acted_by_user_id=u,acted_active_role='HEAD_OF_DEPARTMENT',decided_at=now()
   WHERE tenant_id=t AND approval_step_id=step;
 END $d$
$q$,'SELF_APPROVAL');
