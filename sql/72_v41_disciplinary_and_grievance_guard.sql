-- T9 / M16. PO-T05: ICC and Principal only; accused and immediate HOD always denied.
-- The zero-argument fn_complaint_guard() trigger from 02_rules.sql is intentionally preserved.
BEGIN;
CREATE TABLE app.disciplinary_incident (
 tenant_id uuid NOT NULL,disciplinary_incident_id uuid NOT NULL,institution_id uuid NOT NULL,
 student_id uuid NOT NULL,reported_by_user_id uuid NOT NULL,incident_date date NOT NULL,
 severity text NOT NULL,description text NOT NULL,action_taken text,status text NOT NULL DEFAULT 'RECORDED',
 created_at timestamptz NOT NULL DEFAULT now(),created_by uuid NOT NULL,updated_at timestamptz NOT NULL DEFAULT now(),
 updated_by uuid NOT NULL,row_version integer NOT NULL DEFAULT 1,
 PRIMARY KEY(tenant_id,disciplinary_incident_id),
 FOREIGN KEY(tenant_id,student_id) REFERENCES app.student_profile(tenant_id,student_profile_id),
 CHECK(severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),CHECK(char_length(description) BETWEEN 1 AND 5000),
 CHECK(action_taken IS NULL OR char_length(action_taken)<=5000),
 CHECK(status IN ('RECORDED','UNDER_REVIEW','CLOSED')),CHECK(row_version>=1)
);
CREATE TABLE app.grievance_case (
 tenant_id uuid NOT NULL,grievance_case_id uuid NOT NULL,institution_id uuid NOT NULL,
 complainant_user_id uuid,accused_user_id uuid,category text NOT NULL,case_number text NOT NULL,
 details text NOT NULL,anonymous_submission boolean NOT NULL DEFAULT false,status text NOT NULL DEFAULT 'SUBMITTED',
 submitted_at timestamptz NOT NULL DEFAULT now(),created_at timestamptz NOT NULL DEFAULT now(),
 created_by uuid NOT NULL,updated_at timestamptz NOT NULL DEFAULT now(),updated_by uuid NOT NULL,row_version integer NOT NULL DEFAULT 1,
 PRIMARY KEY(tenant_id,grievance_case_id),
 CHECK(category IN ('HARASSMENT','DISCRIMINATION','ACADEMIC_MISCONDUCT','GENERAL')),
 CHECK(char_length(case_number) BETWEEN 6 AND 60),CHECK(char_length(details) BETWEEN 1 AND 10000),
 CHECK(NOT anonymous_submission OR complainant_user_id IS NULL),
 CHECK(status IN ('SUBMITTED','UNDER_INVESTIGATION','HEARING_SCHEDULED','RESOLVED','DISMISSED')),CHECK(row_version>=1)
);
CREATE UNIQUE INDEX uq_grievance_case_number ON app.grievance_case(tenant_id,case_number);
CREATE INDEX ix_grievance_case_institution_status ON app.grievance_case(tenant_id,institution_id,status,submitted_at DESC);
CREATE TABLE app.grievance_evidence (
 tenant_id uuid NOT NULL,grievance_evidence_id uuid NOT NULL,grievance_case_id uuid NOT NULL,
 file_id uuid NOT NULL,uploaded_by_user_id uuid NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),
 created_by uuid NOT NULL,updated_at timestamptz NOT NULL DEFAULT now(),updated_by uuid NOT NULL,row_version integer NOT NULL DEFAULT 1,
 PRIMARY KEY(tenant_id,grievance_evidence_id),
 FOREIGN KEY(tenant_id,grievance_case_id) REFERENCES app.grievance_case(tenant_id,grievance_case_id),CHECK(row_version>=1)
);
CREATE TABLE app.grievance_action (
 tenant_id uuid NOT NULL,grievance_action_id uuid NOT NULL,grievance_case_id uuid NOT NULL,
 action_type text NOT NULL,action_notes text NOT NULL,action_at timestamptz NOT NULL DEFAULT now(),
 performed_by_user_id uuid NOT NULL,notice_file_id uuid,minutes_file_id uuid,penalty text,
 created_at timestamptz NOT NULL DEFAULT now(),created_by uuid NOT NULL,
 PRIMARY KEY(tenant_id,grievance_action_id),
 FOREIGN KEY(tenant_id,grievance_case_id) REFERENCES app.grievance_case(tenant_id,grievance_case_id),
 CHECK(action_type IN ('ASSIGNED','INVESTIGATION_STARTED','HEARING_SCHEDULED','NOTICE_ISSUED','MINUTES_RECORDED','PENALTY_ISSUED','RESOLVED','DISMISSED')),
 CHECK(char_length(action_notes) BETWEEN 1 AND 5000),CHECK(penalty IS NULL OR char_length(penalty)<=2000)
);

-- A separate overload for SELECT authorization; does not alter the existing trigger signature.
-- Active-role selection is taken from app.user_active_role (HI-15), never unioned across assignments.
CREATE OR REPLACE FUNCTION app.fn_complaint_guard(p_tenant_id uuid,p_accused_user_id uuid,p_institution_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
SET search_path=pg_catalog,app
AS $$
 SELECT CASE
  WHEN p_tenant_id IS NULL OR p_institution_id IS NULL
   OR NULLIF(current_setting('app.tenant_id',true),'') IS NULL
   OR NULLIF(current_setting('app.user_id',true),'') IS NULL
   OR p_tenant_id<>NULLIF(current_setting('app.tenant_id',true),'')::uuid THEN false
  WHEN p_accused_user_id IS NOT NULL AND p_accused_user_id=NULLIF(current_setting('app.user_id',true),'')::uuid THEN false
  WHEN p_accused_user_id IS NOT NULL AND EXISTS(
   SELECT 1
   FROM app.staff_profile accused
   JOIN app.staff_assignment accused_sa ON accused_sa.tenant_id=accused.tenant_id
    AND accused_sa.staff_profile_id=accused.staff_profile_id AND accused_sa.institution_id=p_institution_id
    AND accused_sa.department_id IS NOT NULL AND accused_sa.status='ACTIVE'
    AND accused_sa.effective_from<=current_date AND (accused_sa.effective_to IS NULL OR accused_sa.effective_to>=current_date)
   JOIN app.department d ON d.tenant_id=accused_sa.tenant_id AND d.department_id=accused_sa.department_id
    AND d.hod_staff_assignment_id IS NOT NULL AND d.status='ACTIVE'
   JOIN app.staff_assignment hod_sa ON hod_sa.tenant_id=d.tenant_id
    AND hod_sa.staff_assignment_id=d.hod_staff_assignment_id AND hod_sa.status='ACTIVE'
    AND hod_sa.effective_from<=current_date AND (hod_sa.effective_to IS NULL OR hod_sa.effective_to>=current_date)
   JOIN app.staff_profile hod ON hod.tenant_id=hod_sa.tenant_id AND hod.staff_profile_id=hod_sa.staff_profile_id
   WHERE accused.tenant_id=p_tenant_id AND accused.user_account_id=p_accused_user_id
    AND hod.user_account_id=NULLIF(current_setting('app.user_id',true),'')::uuid
  ) THEN false
  WHEN EXISTS(
   SELECT 1
   FROM app.user_active_role active
   JOIN app.user_role_assignment ura ON ura.tenant_id=active.tenant_id
    AND ura.user_account_id=active.user_account_id AND ura.user_role_assignment_id=active.user_role_assignment_id
   JOIN app.role_definition rd ON rd.tenant_id=ura.tenant_id AND rd.role_definition_id=ura.role_definition_id
   WHERE active.tenant_id=p_tenant_id
    AND active.user_account_id=NULLIF(current_setting('app.user_id',true),'')::uuid
    AND ura.status='ACTIVE' AND ura.effective_from<=now() AND (ura.effective_to IS NULL OR ura.effective_to>now())
    AND ura.institution_id=p_institution_id AND rd.status='ACTIVE'
    AND rd.role_code IN ('INTERNAL_COMPLAINTS_COMMITTEE','PRINCIPAL_COLLEGE','SCHOOL_PRINCIPAL')
  ) THEN true
  ELSE false
 END
$$;

DO $$
DECLARE t text;
BEGIN
 FOREACH t IN ARRAY ARRAY['disciplinary_incident','grievance_case','grievance_evidence','grievance_action'] LOOP
  EXECUTE format('CREATE TRIGGER trg_touch BEFORE UPDATE ON app.%I FOR EACH ROW EXECUTE FUNCTION app.fn_touch()',t);
  EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE app.%I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY p_tenant ON app.%I USING (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid) WITH CHECK (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid)',t);
  EXECUTE format('GRANT SELECT,INSERT,UPDATE ON app.%I TO edu_app',t);
  EXECUTE format('GRANT SELECT ON app.%I TO edu_report',t);
 END LOOP;
END $$;
CREATE POLICY p_grievance_confidentiality ON app.grievance_case AS RESTRICTIVE FOR SELECT
 USING(app.fn_complaint_guard(tenant_id,accused_user_id,institution_id));
CREATE POLICY p_grievance_confidentiality ON app.grievance_evidence AS RESTRICTIVE FOR SELECT
 USING(EXISTS(SELECT 1 FROM app.grievance_case gc WHERE gc.tenant_id=grievance_evidence.tenant_id AND gc.grievance_case_id=grievance_evidence.grievance_case_id));
CREATE POLICY p_grievance_confidentiality ON app.grievance_action AS RESTRICTIVE FOR SELECT
 USING(EXISTS(SELECT 1 FROM app.grievance_case gc WHERE gc.tenant_id=grievance_action.tenant_id AND gc.grievance_case_id=grievance_action.grievance_case_id));
GRANT EXECUTE ON FUNCTION app.fn_complaint_guard(uuid,uuid,uuid) TO edu_app;
COMMENT ON FUNCTION app.fn_complaint_guard(uuid,uuid,uuid) IS 'PO-T05: ICC and Principal only, using the one active role; accused and immediate HOD always excluded. Missing context denies. Tenant DPO is not authorized.';
COMMENT ON TABLE app.grievance_case IS 'Confidential grievance cases protected by PO-T05 restrictive RLS; only ICC and Principal may SELECT.';
COMMIT;
