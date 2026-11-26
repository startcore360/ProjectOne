-- T9 / M16. PO-T05: ICC and Principal only; accused and immediate HOD always denied.
-- The zero-argument fn_complaint_guard() trigger from 02_rules.sql is intentionally preserved.
BEGIN;

ALTER TABLE app.file_object DROP CONSTRAINT IF EXISTS ck_file_object_purpose;
ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_purpose CHECK (purpose IN (
 'MESSAGE_ATTACHMENT','ASSIGNMENT_SUBMISSION','STUDY_MATERIAL','STAFF_DOCUMENT','POLICY','DEPARTMENT_DOCUMENT',
 'ACCREDITATION','COMPLIANCE','LEAVE_DOCUMENT','HALL_TICKET','COMMUNITY_MEDIA','IMPORT_SOURCE','EXPORT',
 'PROFILE_PHOTO','BRANDING','ATTENDANCE_UPLOAD','ATTENDANCE_MEDICAL_EXEMPTION','MARKS_SOURCE','SYLLABUS',
 'CERTIFICATE','CONSENT_FORM','SERVICE_RECORD','GRIEVANCE_EVIDENCE','DISCIPLINARY_EVIDENCE'
));
ALTER TABLE app.file_object DROP CONSTRAINT IF EXISTS ck_file_object_grievance_evidence_type;
ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_grievance_evidence_type
 CHECK (purpose<>'GRIEVANCE_EVIDENCE' OR content_type IN ('application/pdf','image/jpeg','image/png'));
ALTER TABLE app.file_object DROP CONSTRAINT IF EXISTS ck_file_object_disciplinary_evidence_type;
ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_disciplinary_evidence_type
 CHECK (purpose<>'DISCIPLINARY_EVIDENCE' OR content_type IN ('application/pdf','image/jpeg','image/png'));

UPDATE app.role_definition SET is_sensitive_role=true,updated_at=now(),row_version=row_version+1
WHERE role_origin='STANDARD' AND role_code IN
 ('INTERNAL_COMPLAINTS_COMMITTEE','PRINCIPAL_COLLEGE','SCHOOL_PRINCIPAL')
 AND is_sensitive_role=false;


-- T9 action catalogue extension. Action numbers continue after the authoritative seeded
-- sql/05_ref_data.sql catalogue (highest seeded action_no: 283). Generic COMPLAINT_* actions
-- and their grants are intentionally untouched.
INSERT INTO ref.standard_role(role_code,role_name,role_level,introduced_in_version,requires_two_step_login,is_staff)
VALUES ('INTERNAL_COMPLAINTS_COMMITTEE','Internal Complaints Committee','COLLEGE','4.1-T9',true,true)
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO ref.action_catalog
 (action_code,action_no,module_name,phase,description,is_approval_action,sensitive_class,changed_in)
VALUES
 ('ID_CARD_ISSUE',284,'Identity cards',1,'Issue a student or staff identity card',false,'Personal – Create / edit','4.1-T9'),
 ('ID_CARD_REPLACE',285,'Identity cards',1,'Review and process a lost or damaged identity card replacement',false,'Personal – Correct','4.1-T9'),
 ('ID_CARD_TEMPLATE_MANAGE',286,'Identity cards',1,'Create and maintain institution identity card templates',false,'Personal – Create / edit','4.1-T9'),
 ('ID_CARD_REVOKE',287,'Identity cards',1,'Revoke an identity card issuance',true,'Personal – Correct','4.1-T9'),
 ('CERTIFICATE_TEMPLATE_MANAGE',288,'Certificates',1,'Create and maintain official certificate templates',false,'Personal – Create / edit','4.1-T9'),
 ('CERTIFICATE_REVOKE',289,'Certificates',1,'Revoke an issued official certificate',true,'Personal – Correct','4.1-T9'),
 ('DISCIPLINARY_INCIDENT_VIEW',290,'Disciplinary',1,'View institution disciplinary incident records',false,'Personal – View','4.1-T9'),
 ('DISCIPLINARY_INCIDENT_MANAGE',291,'Disciplinary',1,'Record and update institution disciplinary incidents',false,'Personal – Create / edit','4.1-T9'),
 ('GRIEVANCE_CASE_SUBMIT',292,'Confidential grievances',1,'Submit a named or anonymous grievance without access to case records',false,'Personal – Create (confidential submission)','4.1-T9'),
 ('GRIEVANCE_CASE_VIEW',293,'Confidential grievances',1,'View confidential ICC grievance cases under PO-T05',false,'Personal – View (confidential grievance cases)','4.1-T9'),
 ('GRIEVANCE_CASE_ASSIGN',294,'Confidential grievances',1,'Assign a confidential grievance case to an investigator',false,'Personal – Correct (confidential grievance cases)','4.1-T9'),
 ('GRIEVANCE_HEARING_MANAGE',295,'Confidential grievances',1,'Schedule hearings and record confidential hearing minutes',false,'Personal – Correct (confidential grievance cases)','4.1-T9'),
 ('GRIEVANCE_CASE_RESOLVE',296,'Confidential grievances',1,'Resolve or dismiss a confidential grievance case',true,'Personal – Correct (confidential grievance cases)','4.1-T9'),
 ('GRIEVANCE_EVIDENCE_VIEW',297,'Confidential grievances',1,'View scanned evidence attached to a confidential grievance case',false,'Personal – View (confidential grievance evidence)','4.1-T9'),
 ('GRIEVANCE_EVIDENCE_UPLOAD',298,'Confidential grievances',1,'Upload scanned evidence to a confidential grievance case',false,'Personal – Create (confidential grievance evidence)','4.1-T9'),
 ('ID_CARD_REPLACEMENT_REQUEST',299,'Identity cards',1,'Request replacement of own lost or damaged identity card',false,'Personal – Create (own identity card replacement)','4.1-T9')
-- T9 / M16. PO-T05: ICC and Principal only; accused and immediate HOD always denied.
-- The zero-argument fn_complaint_guard() trigger from 02_rules.sql is intentionally preserved.
BEGIN;

ALTER TABLE app.file_object DROP CONSTRAINT IF EXISTS ck_file_object_purpose;
ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_purpose CHECK (purpose IN (
 'MESSAGE_ATTACHMENT','ASSIGNMENT_SUBMISSION','STUDY_MATERIAL','STAFF_DOCUMENT','POLICY','DEPARTMENT_DOCUMENT',
 'ACCREDITATION','COMPLIANCE','LEAVE_DOCUMENT','HALL_TICKET','COMMUNITY_MEDIA','IMPORT_SOURCE','EXPORT',
 'PROFILE_PHOTO','BRANDING','ATTENDANCE_UPLOAD','ATTENDANCE_MEDICAL_EXEMPTION','MARKS_SOURCE','SYLLABUS',
 'CERTIFICATE','CONSENT_FORM','SERVICE_RECORD','GRIEVANCE_EVIDENCE','DISCIPLINARY_EVIDENCE'
));
ALTER TABLE app.file_object DROP CONSTRAINT IF EXISTS ck_file_object_grievance_evidence_type;
ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_grievance_evidence_type
 CHECK (purpose<>'GRIEVANCE_EVIDENCE' OR content_type IN ('application/pdf','image/jpeg','image/png'));
ALTER TABLE app.file_object DROP CONSTRAINT IF EXISTS ck_file_object_disciplinary_evidence_type;
ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_disciplinary_evidence_type
 CHECK (purpose<>'DISCIPLINARY_EVIDENCE' OR content_type IN ('application/pdf','image/jpeg','image/png'));

UPDATE app.role_definition SET is_sensitive_role=true,updated_at=now(),row_version=row_version+1
WHERE role_origin='STANDARD' AND role_code IN
 ('INTERNAL_COMPLAINTS_COMMITTEE','PRINCIPAL_COLLEGE','SCHOOL_PRINCIPAL')
 AND is_sensitive_role=false;


-- T9 action catalogue extension. Action numbers continue after the authoritative seeded
-- sql/05_ref_data.sql catalogue (highest seeded action_no: 283). Generic COMPLAINT_* actions
-- and their grants are intentionally untouched.
INSERT INTO ref.standard_role(role_code,role_name,role_level,introduced_in_version,requires_two_step_login,is_staff)
VALUES ('INTERNAL_COMPLAINTS_COMMITTEE','Internal Complaints Committee','COLLEGE','4.1-T9',true,true)
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO ref.action_catalog
 (action_code,action_no,module_name,phase,description,is_approval_action,sensitive_class,changed_in)
VALUES
 ('ID_CARD_ISSUE',284,'Identity cards',1,'Issue a student or staff identity card',false,'Personal – Create / edit','4.1-T9'),
 ('ID_CARD_REPLACE',285,'Identity cards',1,'Review and process a lost or damaged identity card replacement',false,'Personal – Correct','4.1-T9'),
 ('ID_CARD_TEMPLATE_MANAGE',286,'Identity cards',1,'Create and maintain institution identity card templates',false,'Personal – Create / edit','4.1-T9'),
 ('ID_CARD_REVOKE',287,'Identity cards',1,'Revoke an identity card issuance',true,'Personal – Correct','4.1-T9'),
 ('CERTIFICATE_TEMPLATE_MANAGE',288,'Certificates',1,'Create and maintain official certificate templates',false,'Personal – Create / edit','4.1-T9'),
 ('CERTIFICATE_REVOKE',289,'Certificates',1,'Revoke an issued official certificate',true,'Personal – Correct','4.1-T9'),
 ('DISCIPLINARY_INCIDENT_VIEW',290,'Disciplinary',1,'View institution disciplinary incident records',false,'Personal – View','4.1-T9'),
 ('DISCIPLINARY_INCIDENT_MANAGE',291,'Disciplinary',1,'Record and update institution disciplinary incidents',false,'Personal – Create / edit','4.1-T9'),
 ('GRIEVANCE_CASE_SUBMIT',292,'Confidential grievances',1,'Submit a named or anonymous grievance without access to case records',false,'Personal – Create (confidential submission)','4.1-T9'),
 ('GRIEVANCE_CASE_VIEW',293,'Confidential grievances',1,'View confidential ICC grievance cases under PO-T05',false,'Personal – View (confidential grievance cases)','4.1-T9'),
 ('GRIEVANCE_CASE_ASSIGN',294,'Confidential grievances',1,'Assign a confidential grievance case to an investigator',false,'Personal – Correct (confidential grievance cases)','4.1-T9'),
 ('GRIEVANCE_HEARING_MANAGE',295,'Confidential grievances',1,'Schedule hearings and record confidential hearing minutes',false,'Personal – Correct (confidential grievance cases)','4.1-T9'),
 ('GRIEVANCE_CASE_RESOLVE',296,'Confidential grievances',1,'Resolve or dismiss a confidential grievance case',true,'Personal – Correct (confidential grievance cases)','4.1-T9'),
 ('GRIEVANCE_EVIDENCE_VIEW',297,'Confidential grievances',1,'View scanned evidence attached to a confidential grievance case',false,'Personal – View (confidential grievance evidence)','4.1-T9'),
 ('GRIEVANCE_EVIDENCE_UPLOAD',298,'Confidential grievances',1,'Upload scanned evidence to a confidential grievance case',false,'Personal – Create (confidential grievance evidence)','4.1-T9'),
 ('ID_CARD_REPLACEMENT_REQUEST',299,'Identity cards',1,'Request replacement of own lost or damaged identity card',false,'Personal – Create (own identity card replacement)','4.1-T9')
ON CONFLICT (action_code) DO NOTHING;
UPDATE ref.action_catalog SET is_approval_action=true WHERE action_code='ID_CARD_REPLACE';


-- Only the ICC and the two Principal roles receive confidential case access or case-management
-- actions. Ordinary grievance submission is a separate action available to tenant-side roles.
WITH pairs(role_code,action_code,limited_to) AS (
 VALUES
 ('RECORDS_OFFICER','ID_CARD_ISSUE','Own institution'),
 ('COLLEGE_ADMIN','ID_CARD_ISSUE','Own institution'),
 ('SCHOOL_ADMIN','ID_CARD_ISSUE','Own institution'),
 ('PRINCIPAL_COLLEGE','ID_CARD_ISSUE','Own institution'),
 ('SCHOOL_PRINCIPAL','ID_CARD_ISSUE','Own institution'),
 ('RECORDS_OFFICER','ID_CARD_REPLACE','Own institution'),
 ('COLLEGE_ADMIN','ID_CARD_REPLACE','Own institution'),
 ('SCHOOL_ADMIN','ID_CARD_REPLACE','Own institution'),
 ('PRINCIPAL_COLLEGE','ID_CARD_REPLACE','Own institution'),
 ('SCHOOL_PRINCIPAL','ID_CARD_REPLACE','Own institution'),
 ('COLLEGE_ADMIN','ID_CARD_TEMPLATE_MANAGE','Own institution'),
 ('SCHOOL_ADMIN','ID_CARD_TEMPLATE_MANAGE','Own institution'),
 ('PRINCIPAL_COLLEGE','ID_CARD_TEMPLATE_MANAGE','Own institution'),
 ('SCHOOL_PRINCIPAL','ID_CARD_TEMPLATE_MANAGE','Own institution'),
 ('PRINCIPAL_COLLEGE','ID_CARD_REVOKE','Own institution'),
 ('SCHOOL_PRINCIPAL','ID_CARD_REVOKE','Own institution'),
 ('COLLEGE_ADMIN','CERTIFICATE_TEMPLATE_MANAGE','Own institution'),
 ('SCHOOL_ADMIN','CERTIFICATE_TEMPLATE_MANAGE','Own institution'),
 ('PRINCIPAL_COLLEGE','CERTIFICATE_TEMPLATE_MANAGE','Own institution'),
 ('SCHOOL_PRINCIPAL','CERTIFICATE_TEMPLATE_MANAGE','Own institution'),
 ('PRINCIPAL_COLLEGE','CERTIFICATE_REVOKE','Own institution'),
 ('SCHOOL_PRINCIPAL','CERTIFICATE_REVOKE','Own institution'),
 ('PRINCIPAL_COLLEGE','DISCIPLINARY_INCIDENT_VIEW','Own institution'),
 ('SCHOOL_PRINCIPAL','DISCIPLINARY_INCIDENT_VIEW','Own institution'),
 ('COLLEGE_ADMIN','DISCIPLINARY_INCIDENT_VIEW','Own institution'),
 ('SCHOOL_ADMIN','DISCIPLINARY_INCIDENT_VIEW','Own institution'),
 ('HEAD_OF_DEPARTMENT','DISCIPLINARY_INCIDENT_VIEW','Own department'),
 ('PRINCIPAL_COLLEGE','DISCIPLINARY_INCIDENT_MANAGE','Own institution'),
 ('SCHOOL_PRINCIPAL','DISCIPLINARY_INCIDENT_MANAGE','Own institution'),
 ('COLLEGE_ADMIN','DISCIPLINARY_INCIDENT_MANAGE','Own institution'),
 ('SCHOOL_ADMIN','DISCIPLINARY_INCIDENT_MANAGE','Own institution'),
 ('HEAD_OF_DEPARTMENT','DISCIPLINARY_INCIDENT_MANAGE','Own department'),
 ('INTERNAL_COMPLAINTS_COMMITTEE','GRIEVANCE_CASE_VIEW','Own institution; PO-T05; accused and immediate HOD excluded'),
 ('PRINCIPAL_COLLEGE','GRIEVANCE_CASE_VIEW','Own institution; PO-T05; accused and immediate HOD excluded'),
 ('SCHOOL_PRINCIPAL','GRIEVANCE_CASE_VIEW','Own institution; PO-T05; accused and immediate HOD excluded'),
 ('INTERNAL_COMPLAINTS_COMMITTEE','GRIEVANCE_CASE_ASSIGN','Own institution; PO-T05'),
 ('PRINCIPAL_COLLEGE','GRIEVANCE_CASE_ASSIGN','Own institution; PO-T05'),
 ('SCHOOL_PRINCIPAL','GRIEVANCE_CASE_ASSIGN','Own institution; PO-T05'),
 ('INTERNAL_COMPLAINTS_COMMITTEE','GRIEVANCE_HEARING_MANAGE','Own institution; PO-T05'),
 ('PRINCIPAL_COLLEGE','GRIEVANCE_HEARING_MANAGE','Own institution; PO-T05'),
 ('SCHOOL_PRINCIPAL','GRIEVANCE_HEARING_MANAGE','Own institution; PO-T05'),
 ('INTERNAL_COMPLAINTS_COMMITTEE','GRIEVANCE_CASE_RESOLVE','Own institution; PO-T05'),
 ('PRINCIPAL_COLLEGE','GRIEVANCE_CASE_RESOLVE','Own institution; PO-T05'),
 ('SCHOOL_PRINCIPAL','GRIEVANCE_CASE_RESOLVE','Own institution; PO-T05'),
 ('INTERNAL_COMPLAINTS_COMMITTEE','GRIEVANCE_EVIDENCE_VIEW','Own institution; PO-T05'),
 ('PRINCIPAL_COLLEGE','GRIEVANCE_EVIDENCE_VIEW','Own institution; PO-T05'),
 ('SCHOOL_PRINCIPAL','GRIEVANCE_EVIDENCE_VIEW','Own institution; PO-T05'),
 ('INTERNAL_COMPLAINTS_COMMITTEE','GRIEVANCE_EVIDENCE_UPLOAD','Own institution; PO-T05'),
 ('PRINCIPAL_COLLEGE','GRIEVANCE_EVIDENCE_UPLOAD','Own institution; PO-T05'),
 ('SCHOOL_PRINCIPAL','GRIEVANCE_EVIDENCE_UPLOAD','Own institution; PO-T05')
 ('INTERNAL_COMPLAINTS_COMMITTEE','GRIEVANCE_EVIDENCE_UPLOAD','Own institution; PO-T05'),
 ('PRINCIPAL_COLLEGE','GRIEVANCE_EVIDENCE_UPLOAD','Own institution; PO-T05'),
 ('SCHOOL_PRINCIPAL','GRIEVANCE_EVIDENCE_UPLOAD','Own institution; PO-T05'),
 ('STUDENT_COLLEGE','ID_CARD_REPLACEMENT_REQUEST','Own identity card'),
 ('STUDENT_SCHOOL','ID_CARD_REPLACEMENT_REQUEST','Own identity card')
), submitters AS (
 SELECT role_code,'GRIEVANCE_CASE_SUBMIT'::text AS action_code,
        'Submit own named or anonymous grievance only; no case-list or case-detail access'::text AS limited_to
 FROM ref.standard_role
 WHERE role_level IN ('TENANT','UNIVERSITY','COLLEGE','SCHOOL','EXTERNAL')
), all_pairs AS (
 SELECT * FROM pairs UNION ALL SELECT * FROM submitters
), missing AS (
 SELECT p.* FROM all_pairs p
 WHERE NOT EXISTS (
  SELECT 1 FROM ref.standard_role_action x
  WHERE x.role_code=p.role_code AND x.action_code=p.action_code
)
), numbered AS (
 SELECT COALESCE((SELECT max(standard_role_action_id) FROM ref.standard_role_action),0)
        + row_number() OVER (ORDER BY role_code,action_code) AS id,
        role_code,action_code,limited_to
 FROM missing
)
INSERT INTO ref.standard_role_action(standard_role_action_id,role_code,action_code,limited_to)
SELECT id,role_code,action_code,limited_to FROM numbered
ON CONFLICT (standard_role_action_id) DO NOTHING;

-- Bring already-provisioned standard Principal/admin/records/HOD role definitions up to date.
-- Newly assigned ICC roles copy the same standard-role template through RoleAssignmentService.
INSERT INTO app.role_action
 (tenant_id,role_action_id,role_definition_id,action_code,created_by,updated_by)
SELECT rd.tenant_id,gen_random_uuid(),rd.role_definition_id,sra.action_code,rd.created_by,rd.created_by
FROM app.role_definition rd
JOIN ref.standard_role_action sra ON sra.role_code=rd.base_standard_role
WHERE rd.role_origin='STANDARD'
 AND sra.action_code IN (
  'ID_CARD_ISSUE','ID_CARD_REPLACE','ID_CARD_TEMPLATE_MANAGE','ID_CARD_REVOKE','ID_CARD_REPLACEMENT_REQUEST',
  'CERTIFICATE_TEMPLATE_MANAGE','CERTIFICATE_REVOKE',
  'DISCIPLINARY_INCIDENT_VIEW','DISCIPLINARY_INCIDENT_MANAGE',
  'GRIEVANCE_CASE_SUBMIT','GRIEVANCE_CASE_VIEW','GRIEVANCE_CASE_ASSIGN',
  'GRIEVANCE_HEARING_MANAGE','GRIEVANCE_CASE_RESOLVE','GRIEVANCE_EVIDENCE_VIEW','GRIEVANCE_EVIDENCE_UPLOAD'
 )
 AND NOT EXISTS (
  SELECT 1 FROM app.role_action existing
  WHERE existing.tenant_id=rd.tenant_id
   AND existing.role_definition_id=rd.role_definition_id
   AND existing.action_code=sra.action_code
 );

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
 complainant_user_id uuid,accused_user_id uuid,assigned_to_user_id uuid,category text NOT NULL,case_number text NOT NULL,
 details text NOT NULL,anonymous_submission boolean NOT NULL DEFAULT false,status text NOT NULL DEFAULT 'SUBMITTED',
 next_hearing_at timestamptz,resolution_notes text,
 submitted_at timestamptz NOT NULL DEFAULT now(),created_at timestamptz NOT NULL DEFAULT now(),
 created_by uuid NOT NULL,updated_at timestamptz NOT NULL DEFAULT now(),updated_by uuid NOT NULL,row_version integer NOT NULL DEFAULT 1,
 PRIMARY KEY(tenant_id,grievance_case_id),
 CHECK(category IN ('HARASSMENT','DISCRIMINATION','ACADEMIC_MISCONDUCT','GENERAL')),
 CHECK(char_length(case_number) BETWEEN 6 AND 60),CHECK(char_length(details) BETWEEN 1 AND 10000),
 CHECK(NOT anonymous_submission OR complainant_user_id IS NULL),
 CHECK(status IN ('SUBMITTED','UNDER_INVESTIGATION','HEARING_SCHEDULED','RESOLVED','DISMISSED')),
 CHECK(resolution_notes IS NULL OR char_length(resolution_notes)<=5000),CHECK(row_version>=1)
);
CREATE UNIQUE INDEX uq_grievance_case_number ON app.grievance_case(tenant_id,case_number);
CREATE INDEX ix_grievance_case_institution_status ON app.grievance_case(tenant_id,institution_id,status,submitted_at DESC);
CREATE TABLE app.grievance_evidence (
 tenant_id uuid NOT NULL,grievance_evidence_id uuid NOT NULL,grievance_case_id uuid NOT NULL,
 file_id uuid NOT NULL,uploaded_by_user_id uuid NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),
 created_by uuid NOT NULL,updated_at timestamptz NOT NULL DEFAULT now(),updated_by uuid NOT NULL,row_version integer NOT NULL DEFAULT 1,
 PRIMARY KEY(tenant_id,grievance_evidence_id),
 FOREIGN KEY(tenant_id,grievance_case_id) REFERENCES app.grievance_case(tenant_id,grievance_case_id),
 FOREIGN KEY(tenant_id,file_id) REFERENCES app.file_object(tenant_id,file_id),CHECK(row_version>=1)
);
CREATE TABLE app.grievance_action (
 tenant_id uuid NOT NULL,grievance_action_id uuid NOT NULL,grievance_case_id uuid NOT NULL,
 action_type text NOT NULL,action_notes text NOT NULL,action_at timestamptz NOT NULL DEFAULT now(),
 performed_by_user_id uuid NOT NULL,notice_file_id uuid,minutes_file_id uuid,penalty text,
 created_at timestamptz NOT NULL DEFAULT now(),created_by uuid NOT NULL,
 PRIMARY KEY(tenant_id,grievance_action_id),
 FOREIGN KEY(tenant_id,grievance_case_id) REFERENCES app.grievance_case(tenant_id,grievance_case_id),
 FOREIGN KEY(tenant_id,notice_file_id) REFERENCES app.file_object(tenant_id,file_id),
 FOREIGN KEY(tenant_id,minutes_file_id) REFERENCES app.file_object(tenant_id,file_id),
 CHECK(action_type IN ('ASSIGNED','INVESTIGATION_STARTED','HEARING_SCHEDULED','NOTICE_ISSUED','MINUTES_RECORDED','PENALTY_ISSUED','RESOLVED','DISMISSED')),
 CHECK(char_length(action_notes) BETWEEN 1 AND 5000),CHECK(penalty IS NULL OR char_length(penalty)<=2000)
);

CREATE TABLE app.disciplinary_evidence (
 tenant_id uuid NOT NULL,disciplinary_evidence_id uuid NOT NULL,disciplinary_incident_id uuid NOT NULL,
 file_id uuid NOT NULL,uploaded_by_user_id uuid NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),
 created_by uuid NOT NULL,PRIMARY KEY(tenant_id,disciplinary_evidence_id),
 FOREIGN KEY(tenant_id,disciplinary_incident_id) REFERENCES app.disciplinary_incident(tenant_id,disciplinary_incident_id),
 FOREIGN KEY(tenant_id,file_id) REFERENCES app.file_object(tenant_id,file_id)
);
CREATE TABLE app.disciplinary_action (
 tenant_id uuid NOT NULL,disciplinary_action_id uuid NOT NULL,disciplinary_incident_id uuid NOT NULL,
 action_type text NOT NULL,action_notes text NOT NULL,action_at timestamptz NOT NULL DEFAULT now(),
 performed_by_user_id uuid NOT NULL,notice_file_id uuid,minutes_file_id uuid,penalty text,
 created_at timestamptz NOT NULL DEFAULT now(),created_by uuid NOT NULL,
 PRIMARY KEY(tenant_id,disciplinary_action_id),
 FOREIGN KEY(tenant_id,disciplinary_incident_id) REFERENCES app.disciplinary_incident(tenant_id,disciplinary_incident_id),
 FOREIGN KEY(tenant_id,notice_file_id) REFERENCES app.file_object(tenant_id,file_id),
 FOREIGN KEY(tenant_id,minutes_file_id) REFERENCES app.file_object(tenant_id,file_id),
 CHECK(action_type IN ('HEARING_NOTICE','MINUTES_RECORDED','PENALTY_ISSUED','FOLLOW_UP','CLOSED')),
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
    AND ura.scope_type='INSTITUTION' AND ura.institution_id=p_institution_id AND rd.status='ACTIVE'
    AND rd.role_origin='STANDARD' AND rd.base_standard_role=rd.role_code
    AND rd.is_sensitive_role=true
    AND rd.role_code IN ('INTERNAL_COMPLAINTS_COMMITTEE','PRINCIPAL_COLLEGE','SCHOOL_PRINCIPAL')
  ) THEN true
  ELSE false
 END
$$;

DO $$
DECLARE t text;
BEGIN
 FOREACH t IN ARRAY ARRAY['disciplinary_incident','grievance_case'] LOOP
  EXECUTE format('CREATE TRIGGER trg_touch BEFORE UPDATE ON app.%I FOR EACH ROW EXECUTE FUNCTION app.fn_touch()',t);
  EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE app.%I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY p_tenant ON app.%I USING (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid) WITH CHECK (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid)',t);
  EXECUTE format('GRANT SELECT,INSERT,UPDATE ON app.%I TO edu_app',t);
  EXECUTE format('GRANT SELECT ON app.%I TO edu_report',t);
 END LOOP;
 -- Evidence and action ledgers are append-only; no UPDATE or DELETE privilege.
 FOREACH t IN ARRAY ARRAY['grievance_evidence','disciplinary_evidence'] LOOP
  EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE app.%I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY p_tenant ON app.%I USING (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid) WITH CHECK (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid)',t);
  EXECUTE format('GRANT SELECT,INSERT ON app.%I TO edu_app',t);
  EXECUTE format('GRANT SELECT ON app.%I TO edu_report',t);
 END LOOP;
 ALTER TABLE app.disciplinary_action ENABLE ROW LEVEL SECURITY;
 ALTER TABLE app.disciplinary_action FORCE ROW LEVEL SECURITY;
 CREATE POLICY p_tenant ON app.disciplinary_action USING(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid) WITH CHECK(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid);
 CREATE TRIGGER trg_disc_action_append_only BEFORE UPDATE OR DELETE ON app.disciplinary_action FOR EACH ROW EXECUTE FUNCTION app.fn_append_only();
 GRANT SELECT,INSERT ON app.disciplinary_action TO edu_app;
 GRANT SELECT ON app.disciplinary_action TO edu_report;
 CREATE TRIGGER trg_append_only BEFORE UPDATE OR DELETE ON app.grievance_action FOR EACH ROW EXECUTE FUNCTION app.fn_append_only();
 ALTER TABLE app.grievance_action ENABLE ROW LEVEL SECURITY;
 ALTER TABLE app.grievance_action FORCE ROW LEVEL SECURITY;
 CREATE POLICY p_tenant ON app.grievance_action USING(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid) WITH CHECK(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid);
 GRANT SELECT,INSERT ON app.grievance_action TO edu_app;
 GRANT SELECT ON app.grievance_action TO edu_report;
END $$;
CREATE POLICY p_disciplinary_institution ON app.disciplinary_incident AS RESTRICTIVE
 USING(institution_id IS NULL OR current_setting('app.institution_scope',true)='ALL'
  OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[]))
 WITH CHECK(institution_id IS NULL OR current_setting('app.institution_scope',true)='ALL'
  OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[]));
CREATE POLICY p_disciplinary_evidence_select ON app.disciplinary_evidence AS RESTRICTIVE FOR SELECT
 USING(EXISTS(SELECT 1 FROM app.disciplinary_incident di WHERE di.tenant_id=disciplinary_evidence.tenant_id
  AND di.disciplinary_incident_id=disciplinary_evidence.disciplinary_incident_id));
CREATE POLICY p_disciplinary_evidence_insert ON app.disciplinary_evidence AS RESTRICTIVE FOR INSERT
 WITH CHECK(EXISTS(SELECT 1 FROM app.disciplinary_incident di WHERE di.tenant_id=disciplinary_evidence.tenant_id
  AND di.disciplinary_incident_id=disciplinary_evidence.disciplinary_incident_id));
CREATE POLICY p_disciplinary_action_select ON app.disciplinary_action AS RESTRICTIVE FOR SELECT
 USING(EXISTS(SELECT 1 FROM app.disciplinary_incident di WHERE di.tenant_id=disciplinary_action.tenant_id
  AND di.disciplinary_incident_id=disciplinary_action.disciplinary_incident_id));
CREATE POLICY p_disciplinary_action_insert ON app.disciplinary_action AS RESTRICTIVE FOR INSERT
 WITH CHECK(EXISTS(SELECT 1 FROM app.disciplinary_incident di WHERE di.tenant_id=disciplinary_action.tenant_id
  AND di.disciplinary_incident_id=disciplinary_action.disciplinary_incident_id));
CREATE POLICY p_grievance_confidentiality ON app.grievance_case AS RESTRICTIVE FOR SELECT
 USING(app.fn_complaint_guard(tenant_id,accused_user_id,institution_id));
CREATE POLICY p_grievance_confidentiality ON app.grievance_evidence AS RESTRICTIVE FOR SELECT
 USING(EXISTS(SELECT 1 FROM app.grievance_case gc WHERE gc.tenant_id=grievance_evidence.tenant_id AND gc.grievance_case_id=grievance_evidence.grievance_case_id));
CREATE POLICY p_grievance_confidentiality ON app.grievance_action AS RESTRICTIVE FOR SELECT
 USING(EXISTS(SELECT 1 FROM app.grievance_case gc WHERE gc.tenant_id=grievance_action.tenant_id AND gc.grievance_case_id=grievance_action.grievance_case_id));
CREATE POLICY p_grievance_confidentiality_update ON app.grievance_case AS RESTRICTIVE FOR UPDATE
 USING(app.fn_complaint_guard(tenant_id,accused_user_id,institution_id))
 WITH CHECK(app.fn_complaint_guard(tenant_id,accused_user_id,institution_id));
CREATE POLICY p_grievance_evidence_insert ON app.grievance_evidence AS RESTRICTIVE FOR INSERT
 WITH CHECK(EXISTS(SELECT 1 FROM app.grievance_case gc WHERE gc.tenant_id=grievance_evidence.tenant_id
  AND gc.grievance_case_id=grievance_evidence.grievance_case_id
  AND app.fn_complaint_guard(gc.tenant_id,gc.accused_user_id,gc.institution_id)));
CREATE POLICY p_grievance_action_insert ON app.grievance_action AS RESTRICTIVE FOR INSERT
 WITH CHECK(EXISTS(SELECT 1 FROM app.grievance_case gc WHERE gc.tenant_id=grievance_action.tenant_id
  AND gc.grievance_case_id=grievance_action.grievance_case_id
  AND app.fn_complaint_guard(gc.tenant_id,gc.accused_user_id,gc.institution_id)));
GRANT EXECUTE ON FUNCTION app.fn_complaint_guard(uuid,uuid,uuid) TO edu_app;
COMMENT ON FUNCTION app.fn_complaint_guard(uuid,uuid,uuid) IS 'PO-T05: ICC and Principal only, using the one active role; accused and immediate HOD always excluded. Missing context denies. Tenant DPO is not authorized. ICC must be provisioned from the institution-scoped standard role INTERNAL_COMPLAINTS_COMMITTEE; unconfigured roles fail closed.';
COMMENT ON TABLE app.grievance_case IS 'Confidential grievance cases protected by PO-T05 restrictive RLS; only ICC and Principal may SELECT.';
COMMIT;
