-- 24_v41_sign_in_security.sql  (Sprint 1 batch B3: sign-in and identity)
-- Reference data for this file: 24d_v41_action_module_map.sql. Rollback: 24r_v41_sign_in_security_rollback.sql.
--
-- 1. ref.action_module_map (I-07): which entitlement module (ref.module_catalog) each
--    ref.action_catalog.module_name belongs to. The two catalogues name modules differently
--    ("Exams" vs EXAMINATIONS) and have no key between them, so module entitlements could not be
--    enforced per action without this table. A NULL module_code means "never switched off"
--    (Sign-in and account: people must always be able to sign in and out).
-- 2. app.fn_student_login_account(student_code) (I-02, D1 §8.1 "College students use Student ID
--    and password"): the sign-in screen receives a Student ID before anyone is signed in, while
--    app.student_profile is institution-scoped under RLS (p_institution). Setting
--    app.institution_scope = 'ALL' for that lookup is reserved for tenant-wide roles (D4 §2.2),
--    so instead this narrow SECURITY DEFINER function answers exactly one question for the
--    tenant already set in app.tenant_id: which ACTIVE login account owns this exact student code.
--    It returns no other column and nothing for any other tenant.
SET search_path = public;

CREATE TABLE IF NOT EXISTS ref.action_module_map (
  action_module_name text NOT NULL,
  module_code text,
  CONSTRAINT pk_action_module_map PRIMARY KEY (action_module_name),
  CONSTRAINT fk_action_module_map_module FOREIGN KEY (module_code) REFERENCES ref.module_catalog (module_code)
);
COMMENT ON TABLE ref.action_module_map IS 'B3/I-07: ref.action_catalog.module_name -> ref.module_catalog.module_code for entitlement checks; NULL = never disabled.';
GRANT SELECT ON ref.action_module_map TO edu_app;

CREATE OR REPLACE FUNCTION app.fn_student_login_account(p_student_code text)
RETURNS uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, app
AS $$
  SELECT sp.user_account_id
  FROM app.student_profile sp
  JOIN app.user_account ua ON ua.tenant_id = sp.tenant_id AND ua.user_account_id = sp.user_account_id
  WHERE sp.tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    AND sp.student_code = p_student_code
    AND sp.status = 'ACTIVE'
    AND ua.status = 'ACTIVE'
$$;
COMMENT ON FUNCTION app.fn_student_login_account(text) IS 'B3/I-02: Student ID sign-in - the ACTIVE account owning this student code in the current app.tenant_id; NULL otherwise.';
REVOKE ALL ON FUNCTION app.fn_student_login_account(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app.fn_student_login_account(text) TO edu_app;
