-- Sprint 2 A8 SQL contract check.
SELECT public.expect_ok('S2-DB-10','student login lookup enforces current enrollment and school class switch',$q$
 DO $d$ DECLARE v_definition text; BEGIN
  SELECT lower(pg_get_functiondef('app.fn_student_login_account(text)'::regprocedure)) INTO v_definition;
  IF position('student_enrollment' IN v_definition)=0
     OR position('student_login_enabled' IN v_definition)=0
     OR position('s.semester_id is not null' IN v_definition)=0
     OR position('s.class_id is not null' IN v_definition)=0
     OR position('se.ended_on is null' IN v_definition)=0 THEN
    RAISE EXCEPTION 'STUDENT_LOGIN_SWITCH_GUARD_MISSING';
  END IF;
 END $d$
$q$);


-- Behavioural A8 acceptance: school switch, college current enrolment, and no current enrolment.
SELECT public.expect_ok('S2-DB-11','student login enforces school switch and current enrolment for both pathways',$q$
 DO $d$
 DECLARE
  t uuid := 'a0000000-0000-0000-0000-000000000000';
  actor uuid := 'a0000000-0000-0000-0000-000000000101';
  college_user uuid := 'a0000000-0000-0000-0000-00000000aa01';
  school_user uuid := 'a0000000-0000-0000-0000-00000000aa02';
  school_inst uuid := 'a0000000-0000-0000-0000-00000000b201';
  school_session uuid := 'a0000000-0000-0000-0000-00000000b301';
  school_class uuid := 'a0000000-0000-0000-0000-00000000b401';
  school_section uuid := 'a0000000-0000-0000-0000-00000000b501';
  school_profile uuid := 'a0000000-0000-0000-0000-00000000b601';
 BEGIN
  PERFORM set_config('app.tenant_id',t::text,true);

  INSERT INTO app.user_account(tenant_id,user_account_id,user_type,display_name,status,created_by,updated_by)
   VALUES(t,college_user,'STUDENT','College Login Test','ACTIVE',actor,actor),
         (t,school_user,'STUDENT','School Login Test','ACTIVE',actor,actor);
  UPDATE app.student_profile SET user_account_id=college_user
   WHERE tenant_id=t AND student_profile_id='a0000000-0000-0000-0000-000000000701';
  IF app.fn_student_login_account('ENG26-00001') IS DISTINCT FROM college_user THEN
   RAISE EXCEPTION 'COLLEGE_CURRENT_ENROLLMENT_LOGIN_DENIED';
  END IF;

  UPDATE app.student_enrollment SET outcome='PROMOTED',ended_on='2026-07-01'
   WHERE tenant_id=t AND enrollment_id='a0000000-0000-0000-0000-000000000702';
  IF app.fn_student_login_account('ENG26-00001') IS NOT NULL THEN
   RAISE EXCEPTION 'STUDENT_WITHOUT_CURRENT_ENROLLMENT_LOGIN_ALLOWED';
  END IF;

  INSERT INTO app.institution(tenant_id,institution_id,institution_type,institution_code,institution_name,created_by,updated_by)
   VALUES(t,school_inst,'SCHOOL','SCH-A8','A8 School Test',actor,actor);
  INSERT INTO app.academic_session(tenant_id,academic_session_id,institution_id,session_label,start_date,end_date,academic_year,status,created_by,updated_by)
   VALUES(t,school_session,school_inst,'A8 School Session','2026-06-01','2027-05-31',2026,'ACTIVE',actor,actor);
  INSERT INTO app.school_class(tenant_id,class_id,institution_id,class_name,student_login_enabled,status,created_by,updated_by)
   VALUES(t,school_class,school_inst,'Grade 8',false,'ACTIVE',actor,actor);
  INSERT INTO app.section(tenant_id,section_id,institution_id,academic_session_id,class_id,section_name,status,created_by,updated_by)
   VALUES(t,school_section,school_inst,school_session,school_class,'A','ACTIVE',actor,actor);
  INSERT INTO app.student_profile(tenant_id,student_profile_id,user_account_id,institution_id,student_code,full_name,status,created_by,updated_by)
   VALUES(t,school_profile,school_user,school_inst,'SCH-A8-0001','School Login Test','ACTIVE',actor,actor);
  INSERT INTO app.student_enrollment(tenant_id,enrollment_id,student_profile_id,institution_id,academic_session_id,section_id,started_on,outcome,created_by,updated_by)
   VALUES(t,'a0000000-0000-0000-0000-00000000b701',school_profile,school_inst,school_session,school_section,'2026-06-01','IN_PROGRESS',actor,actor);

  IF app.fn_student_login_account('SCH-A8-0001') IS NOT NULL THEN
   RAISE EXCEPTION 'SCHOOL_LOGIN_ALLOWED_WHEN_CLASS_SWITCH_DISABLED';
  END IF;
  UPDATE app.school_class SET student_login_enabled=true
   WHERE tenant_id=t AND class_id=school_class;
  IF app.fn_student_login_account('SCH-A8-0001') IS DISTINCT FROM school_user THEN
   RAISE EXCEPTION 'SCHOOL_LOGIN_DENIED_WHEN_CLASS_SWITCH_ENABLED';
  END IF;
 END $d$
$q$);
