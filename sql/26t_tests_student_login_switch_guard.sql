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
