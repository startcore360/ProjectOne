-- Sprint 2 Chunk 1 / A8: enforce the school-class login switch at the sign-in lookup.
-- College students (semester sections) retain Student ID login. School students (class sections)
-- are returned only when their current enrollment points to a class with student_login_enabled.
CREATE OR REPLACE FUNCTION app.fn_student_login_account(p_student_code text)
RETURNS uuid LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, app
AS $$
 SELECT sp.user_account_id
 FROM app.student_profile sp
 JOIN app.user_account ua ON ua.tenant_id=sp.tenant_id AND ua.user_account_id=sp.user_account_id
 WHERE sp.tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid
   AND sp.student_code=p_student_code AND sp.status='ACTIVE'
   AND ua.user_type='STUDENT' AND ua.status='ACTIVE'
   AND (
    EXISTS (
     SELECT 1 FROM app.student_enrollment se
     JOIN app.section s ON s.tenant_id=se.tenant_id AND s.institution_id=se.institution_id
       AND s.academic_session_id=se.academic_session_id AND s.section_id=se.section_id
     WHERE se.tenant_id=sp.tenant_id AND se.student_profile_id=sp.student_profile_id
       AND se.institution_id=sp.institution_id AND se.outcome='IN_PROGRESS' AND se.ended_on IS NULL
       AND s.status='ACTIVE' AND s.semester_id IS NOT NULL AND s.class_id IS NULL
    )
    OR EXISTS (
     SELECT 1 FROM app.student_enrollment se
     JOIN app.section s ON s.tenant_id=se.tenant_id AND s.institution_id=se.institution_id
       AND s.academic_session_id=se.academic_session_id AND s.section_id=se.section_id
     JOIN app.school_class sc ON sc.tenant_id=s.tenant_id AND sc.institution_id=s.institution_id
       AND sc.class_id=s.class_id
     WHERE se.tenant_id=sp.tenant_id AND se.student_profile_id=sp.student_profile_id
       AND se.institution_id=sp.institution_id AND se.outcome='IN_PROGRESS' AND se.ended_on IS NULL
       AND s.status='ACTIVE' AND s.semester_id IS NULL AND s.class_id IS NOT NULL
       AND sc.status='ACTIVE' AND sc.student_login_enabled IS TRUE
    )
   )
$$;
COMMENT ON FUNCTION app.fn_student_login_account(text) IS
'I-02 / A8: returns only an ACTIVE student account with a current enrollment; school class login additionally requires student_login_enabled=true (PR-04).';
REVOKE ALL ON FUNCTION app.fn_student_login_account(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app.fn_student_login_account(text) TO edu_app;
