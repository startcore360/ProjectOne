-- EDU 360 M15: admission intake and application scrutiny.
BEGIN;
CREATE TABLE IF NOT EXISTS app.admission_cycle (
 tenant_id uuid NOT NULL, id uuid NOT NULL, institution_id uuid NOT NULL, academic_year_id uuid NOT NULL,
 program_id uuid NOT NULL, cycle_name text NOT NULL, start_date date NOT NULL, end_date date NOT NULL,
 application_fee numeric(12,2) NOT NULL DEFAULT 0, total_seats integer NOT NULL,
 quota_capacity jsonb NOT NULL DEFAULT '{"GENERAL":0,"MERIT":0,"SPORTS":0,"MANAGEMENT":0}'::jsonb,
 status text NOT NULL DEFAULT 'DRAFT', created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid NOT NULL, row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_admission_cycle PRIMARY KEY(tenant_id,id), CONSTRAINT uq_admission_cycle_tid UNIQUE(tenant_id,id),
 CONSTRAINT fk_admission_cycle_institution FOREIGN KEY(tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id),
 CONSTRAINT fk_admission_cycle_session FOREIGN KEY(tenant_id,academic_year_id) REFERENCES app.academic_session(tenant_id,academic_session_id),
 CONSTRAINT fk_admission_cycle_program FOREIGN KEY(tenant_id,program_id) REFERENCES app.program(tenant_id,program_id),
 CONSTRAINT ck_admission_cycle_name CHECK(char_length(btrim(cycle_name)) BETWEEN 2 AND 120),
 CONSTRAINT ck_admission_cycle_dates CHECK(end_date>=start_date), CONSTRAINT ck_admission_cycle_fee CHECK(application_fee>=0),
 CONSTRAINT ck_admission_cycle_seats CHECK(total_seats>0), CONSTRAINT ck_admission_cycle_status CHECK(status IN('DRAFT','OPEN','CLOSED','ARCHIVED')),
 CONSTRAINT ck_admission_cycle_quota CHECK(jsonb_typeof(quota_capacity)='object'), CONSTRAINT ck_admission_cycle_version CHECK(row_version>=1)
);
CREATE TABLE IF NOT EXISTS app.admission_application (
 tenant_id uuid NOT NULL, id uuid NOT NULL, institution_id uuid NOT NULL, cycle_id uuid NOT NULL,
 application_number text NOT NULL, applicant_name text NOT NULL, email text NOT NULL, phone text NOT NULL,
 date_of_birth date, qualifying_marks_percent numeric(5,2) NOT NULL, category text NOT NULL DEFAULT 'GENERAL',
 documents_json jsonb NOT NULL DEFAULT '[]'::jsonb, verification_status text NOT NULL DEFAULT 'PENDING',
 status text NOT NULL DEFAULT 'SUBMITTED', defect_notes text, correction_requested_at timestamptz,
 fee_status text NOT NULL DEFAULT 'PENDING', admission_fee_status text NOT NULL DEFAULT 'PENDING',
 student_profile_id uuid, application_fee_receipt_file_id uuid, admission_fee_receipt_file_id uuid,
 application_fee_reference text, admission_fee_reference text, application_fee_event_id text, admission_fee_event_id text,
 submitted_at timestamptz NOT NULL DEFAULT now(), created_at timestamptz NOT NULL DEFAULT now(),
 created_by uuid NOT NULL, updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid NOT NULL, row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_admission_application PRIMARY KEY(tenant_id,id), CONSTRAINT uq_admission_application_tid UNIQUE(tenant_id,id),
 CONSTRAINT uq_admission_application_number UNIQUE(tenant_id,application_number),
 CONSTRAINT fk_admission_application_cycle FOREIGN KEY(tenant_id,cycle_id) REFERENCES app.admission_cycle(tenant_id,id),
 CONSTRAINT fk_admission_application_institution FOREIGN KEY(tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id),
 CONSTRAINT fk_admission_application_application_fee_receipt FOREIGN KEY(tenant_id,application_fee_receipt_file_id) REFERENCES app.file_object(tenant_id,file_id),
 CONSTRAINT fk_admission_application_admission_fee_receipt FOREIGN KEY(tenant_id,admission_fee_receipt_file_id) REFERENCES app.file_object(tenant_id,file_id),
 CONSTRAINT fk_admission_application_student FOREIGN KEY(tenant_id,student_profile_id) REFERENCES app.student_profile(tenant_id,student_profile_id),
 CONSTRAINT ck_admission_application_marks CHECK(qualifying_marks_percent BETWEEN 0 AND 100),
 CONSTRAINT ck_admission_application_category CHECK(category IN('GENERAL','MERIT','SPORTS','MANAGEMENT')),
 CONSTRAINT ck_admission_application_verification CHECK(verification_status IN('PENDING','VERIFIED','DEFECTIVE','REJECTED')),
 CONSTRAINT ck_admission_application_status CHECK(status IN('SUBMITTED','SCRUTINIZED','SHORTLISTED','ADMITTED','WITHDRAWN')),
 CONSTRAINT ck_admission_application_fee CHECK(fee_status IN('PENDING','CONFIRMED','WAIVED','FAILED')),
 CONSTRAINT ck_admission_application_admission_fee CHECK(admission_fee_status IN('PENDING','CONFIRMED','WAIVED','FAILED')),
 CONSTRAINT ck_admission_application_documents CHECK(jsonb_typeof(documents_json)='array'),
 CONSTRAINT ck_admission_application_version CHECK(row_version>=1)
);
ALTER TABLE app.file_object DROP CONSTRAINT IF EXISTS ck_file_object_purpose;
ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_purpose CHECK(purpose IN(
 'MESSAGE_ATTACHMENT','ASSIGNMENT_SUBMISSION','STUDY_MATERIAL','STAFF_DOCUMENT','POLICY','DEPARTMENT_DOCUMENT',
 'ACCREDITATION','COMPLIANCE','LEAVE_DOCUMENT','HALL_TICKET','COMMUNITY_MEDIA','IMPORT_SOURCE','EXPORT',
 'PROFILE_PHOTO','BRANDING','ATTENDANCE_UPLOAD','MARKS_SOURCE','SYLLABUS','CERTIFICATE','CONSENT_FORM',
 'SERVICE_RECORD','ADMISSION_DOCUMENT'));
ALTER TABLE app.file_object DROP CONSTRAINT IF EXISTS ck_file_object_admission_document_size;
ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_admission_document_size
 CHECK(purpose<>'ADMISSION_DOCUMENT' OR (content_type IN('application/pdf','image/jpeg','image/png') AND size_bytes<=5242880));
ALTER TABLE app.admission_application ADD COLUMN IF NOT EXISTS application_fee_event_id text;
ALTER TABLE app.admission_application ADD COLUMN IF NOT EXISTS admission_fee_event_id text;
CREATE UNIQUE INDEX IF NOT EXISTS ux_admission_application_fee_reference
 ON app.admission_application(tenant_id,application_fee_reference) WHERE application_fee_reference IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_admission_admission_fee_reference
 ON app.admission_application(tenant_id,admission_fee_reference) WHERE admission_fee_reference IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_admission_application_fee_event
 ON app.admission_application(tenant_id,application_fee_event_id) WHERE application_fee_event_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_admission_admission_fee_event
 ON app.admission_application(tenant_id,admission_fee_event_id) WHERE admission_fee_event_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_admission_cycle_institution_status ON app.admission_cycle(tenant_id,institution_id,status,start_date);
CREATE INDEX IF NOT EXISTS ix_admission_application_cycle_status ON app.admission_application(tenant_id,cycle_id,status,submitted_at);
CREATE INDEX IF NOT EXISTS ix_admission_application_scrutiny ON app.admission_application(tenant_id,institution_id,verification_status,status);
DROP TRIGGER IF EXISTS trg_touch ON app.admission_cycle;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.admission_cycle FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
DROP TRIGGER IF EXISTS trg_touch ON app.admission_application;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.admission_application FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
ALTER TABLE app.admission_cycle ENABLE ROW LEVEL SECURITY; ALTER TABLE app.admission_cycle FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_tenant ON app.admission_cycle;
CREATE POLICY p_tenant ON app.admission_cycle USING(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid) WITH CHECK(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid);
DROP POLICY IF EXISTS p_institution ON app.admission_cycle;
CREATE POLICY p_institution ON app.admission_cycle AS RESTRICTIVE USING(institution_id IS NULL OR current_setting('app.institution_scope',true)='ALL' OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[])) WITH CHECK(institution_id IS NULL OR current_setting('app.institution_scope',true)='ALL' OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[]));
GRANT SELECT,INSERT,UPDATE,DELETE ON app.admission_cycle TO edu_app; GRANT SELECT ON app.admission_cycle TO edu_report;
ALTER TABLE app.admission_application ENABLE ROW LEVEL SECURITY; ALTER TABLE app.admission_application FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_tenant ON app.admission_application;
CREATE POLICY p_tenant ON app.admission_application USING(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid) WITH CHECK(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid);
DROP POLICY IF EXISTS p_institution ON app.admission_application;
CREATE POLICY p_institution ON app.admission_application AS RESTRICTIVE USING(institution_id IS NULL OR current_setting('app.institution_scope',true)='ALL' OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[])) WITH CHECK(institution_id IS NULL OR current_setting('app.institution_scope',true)='ALL' OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[]));
GRANT SELECT,INSERT,UPDATE,DELETE ON app.admission_application TO edu_app; GRANT SELECT ON app.admission_application TO edu_report;
COMMIT;
