-- Rollback for 26_v41_student_login_switch_guard.sql: restore the pre-Sprint-2 lookup.
CREATE OR REPLACE FUNCTION app.fn_student_login_account(p_student_code text)
RETURNS uuid LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, app
AS $$
 SELECT sp.user_account_id
 FROM app.student_profile sp
 JOIN app.user_account ua ON ua.tenant_id=sp.tenant_id AND ua.user_account_id=sp.user_account_id
 WHERE sp.tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid
   AND sp.student_code=p_student_code AND sp.status='ACTIVE' AND ua.status='ACTIVE'
$$;
COMMENT ON FUNCTION app.fn_student_login_account(text) IS
'B3/I-02: Student ID sign-in - the ACTIVE account owning this student code in the current app.tenant_id; NULL otherwise.';
REVOKE ALL ON FUNCTION app.fn_student_login_account(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app.fn_student_login_account(text) TO edu_app;
