BEGIN;
ALTER TABLE app.file_object DROP CONSTRAINT IF EXISTS ck_file_object_purpose;
ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_purpose CHECK (purpose IN (
 'MESSAGE_ATTACHMENT','ASSIGNMENT_SUBMISSION','STUDY_MATERIAL','STAFF_DOCUMENT','POLICY','DEPARTMENT_DOCUMENT',
 'ACCREDITATION','COMPLIANCE','LEAVE_DOCUMENT','HALL_TICKET','COMMUNITY_MEDIA','IMPORT_SOURCE','EXPORT',
 'PROFILE_PHOTO','BRANDING','ATTENDANCE_UPLOAD','ATTENDANCE_MEDICAL_EXEMPTION','MARKS_SOURCE','SYLLABUS',
 'CERTIFICATE','CONSENT_FORM','SERVICE_RECORD'));

DO $ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.file_object'::regclass AND conname='ck_file_object_medical_exemption_type') THEN
  ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_medical_exemption_type
   CHECK (purpose<>'ATTENDANCE_MEDICAL_EXEMPTION' OR
    (content_type IN ('application/pdf','image/jpeg','image/png') AND size_bytes<=10485760));
 END IF;
END $;

CREATE TABLE IF NOT EXISTS app.attendance_session (
 tenant_id uuid NOT NULL, attendance_session_id uuid NOT NULL, institution_id uuid NOT NULL,
 academic_session_id uuid NOT NULL, section_id uuid NOT NULL, timetable_slot_id uuid,
 session_date date NOT NULL, taken_by_teacher_id uuid NOT NULL, session_type text NOT NULL,
 session_status text NOT NULL DEFAULT 'HELD', submitted_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid NOT NULL, row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_attendance_session PRIMARY KEY (tenant_id,attendance_session_id),
 CONSTRAINT uq_attendance_session_identity UNIQUE (tenant_id,institution_id,attendance_session_id),
 CONSTRAINT uq_attendance_session_slot UNIQUE NULLS NOT DISTINCT (tenant_id,institution_id,section_id,session_date,timetable_slot_id),
 CONSTRAINT fk_attendance_session_tenant FOREIGN KEY (tenant_id) REFERENCES app.tenant_local(tenant_id) ON DELETE RESTRICT,
 CONSTRAINT fk_attendance_session_institution FOREIGN KEY (tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id) ON DELETE RESTRICT,
 CONSTRAINT fk_attendance_session_academic FOREIGN KEY (tenant_id,academic_session_id) REFERENCES app.academic_session(tenant_id,academic_session_id) ON DELETE RESTRICT,
 CONSTRAINT fk_attendance_session_section FOREIGN KEY (tenant_id,institution_id,section_id) REFERENCES app.section(tenant_id,institution_id,section_id) ON DELETE RESTRICT,
 CONSTRAINT fk_attendance_session_slot FOREIGN KEY (tenant_id,institution_id,timetable_slot_id) REFERENCES app.timetable_slot(tenant_id,institution_id,timetable_slot_id) ON DELETE RESTRICT,
 CONSTRAINT fk_attendance_session_teacher FOREIGN KEY (tenant_id,taken_by_teacher_id) REFERENCES app.staff_profile(tenant_id,staff_profile_id) ON DELETE RESTRICT,
 CONSTRAINT ck_attendance_session_type CHECK (session_type IN ('DAILY','PERIOD')),
 CONSTRAINT ck_attendance_session_status CHECK (session_status IN ('HELD','NOT_HELD','SUBMITTED')),
 CONSTRAINT ck_attendance_session_submit CHECK ((session_status='SUBMITTED' AND submitted_at IS NOT NULL) OR (session_status<>'SUBMITTED' AND submitted_at IS NULL)),
 CONSTRAINT ck_attendance_session_version CHECK (row_version>=1),
 CONSTRAINT ck_attendance_session_updated CHECK (updated_at>=created_at));

CREATE TABLE IF NOT EXISTS app.attendance_entry (
 tenant_id uuid NOT NULL, attendance_entry_id uuid NOT NULL, institution_id uuid NOT NULL,
 attendance_session_id uuid NOT NULL, student_profile_id uuid NOT NULL, enrollment_id uuid NOT NULL,
 status text NOT NULL, remarks text,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid NOT NULL, row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_attendance_entry PRIMARY KEY (tenant_id,attendance_entry_id),
 CONSTRAINT uq_attendance_entry_student UNIQUE (tenant_id,attendance_session_id,student_profile_id),
 CONSTRAINT fk_attendance_entry_session FOREIGN KEY (tenant_id,institution_id,attendance_session_id) REFERENCES app.attendance_session(tenant_id,institution_id,attendance_session_id) ON DELETE RESTRICT,
 CONSTRAINT fk_attendance_entry_student FOREIGN KEY (tenant_id,institution_id,student_profile_id) REFERENCES app.student_profile(tenant_id,institution_id,student_profile_id) ON DELETE RESTRICT,
 CONSTRAINT fk_attendance_entry_enrollment FOREIGN KEY (tenant_id,institution_id,enrollment_id) REFERENCES app.student_enrollment(tenant_id,institution_id,enrollment_id) ON DELETE RESTRICT,
 CONSTRAINT ck_attendance_entry_status CHECK (status IN ('PRESENT','ABSENT','ON_APPROVED_LEAVE','NOT_HELD')),
 CONSTRAINT ck_attendance_entry_remarks CHECK (remarks IS NULL OR char_length(remarks)<=1000),
 CONSTRAINT ck_attendance_entry_version CHECK (row_version>=1),
 CONSTRAINT ck_attendance_entry_updated CHECK (updated_at>=created_at));

CREATE TABLE IF NOT EXISTS app.attendance_medical_exemption (
 tenant_id uuid NOT NULL, attendance_medical_exemption_id uuid NOT NULL, institution_id uuid NOT NULL,
 enrollment_id uuid NOT NULL, student_profile_id uuid NOT NULL, start_date date NOT NULL, end_date date NOT NULL,
 document_ref uuid NOT NULL, status text NOT NULL DEFAULT 'PENDING', requested_at timestamptz NOT NULL DEFAULT now(),
 approved_by_user_id uuid, decided_at timestamptz, decision_note text,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid NOT NULL, row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_attendance_medical_exemption PRIMARY KEY (tenant_id,attendance_medical_exemption_id),
 CONSTRAINT fk_medical_exemption_student FOREIGN KEY (tenant_id,institution_id,student_profile_id) REFERENCES app.student_profile(tenant_id,institution_id,student_profile_id) ON DELETE RESTRICT,
 CONSTRAINT fk_medical_exemption_enrollment FOREIGN KEY (tenant_id,institution_id,enrollment_id) REFERENCES app.student_enrollment(tenant_id,institution_id,enrollment_id) ON DELETE RESTRICT,
 CONSTRAINT fk_medical_exemption_document FOREIGN KEY (tenant_id,document_ref) REFERENCES app.file_object(tenant_id,file_id) ON DELETE RESTRICT,
 CONSTRAINT fk_medical_exemption_approver FOREIGN KEY (tenant_id,approved_by_user_id) REFERENCES app.user_account(tenant_id,user_account_id) ON DELETE RESTRICT,
 CONSTRAINT ck_medical_exemption_dates CHECK (end_date>=start_date),
 CONSTRAINT ck_medical_exemption_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
 CONSTRAINT ck_medical_exemption_decision CHECK ((status='PENDING' AND approved_by_user_id IS NULL AND decided_at IS NULL) OR (status IN ('APPROVED','REJECTED') AND approved_by_user_id IS NOT NULL AND decided_at IS NOT NULL)),
 CONSTRAINT ck_medical_exemption_note CHECK (decision_note IS NULL OR char_length(decision_note)<=1000),
 CONSTRAINT ck_medical_exemption_version CHECK (row_version>=1),
 CONSTRAINT ck_medical_exemption_updated CHECK (updated_at>=created_at));

CREATE TABLE IF NOT EXISTS app.attendance_threshold_policy (
 tenant_id uuid NOT NULL, attendance_threshold_policy_id uuid NOT NULL, institution_id uuid NOT NULL,
 department_id uuid, minimum_attendance_percentage numeric(5,2) NOT NULL DEFAULT 75.00,
 effective_from date NOT NULL DEFAULT CURRENT_DATE, effective_to date, is_active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid NOT NULL, row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_attendance_threshold_policy PRIMARY KEY (tenant_id,attendance_threshold_policy_id),
 CONSTRAINT fk_attendance_threshold_institution FOREIGN KEY (tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id) ON DELETE RESTRICT,
 CONSTRAINT fk_attendance_threshold_department FOREIGN KEY (tenant_id,institution_id,department_id) REFERENCES app.department(tenant_id,institution_id,department_id) ON DELETE RESTRICT,
 CONSTRAINT ck_attendance_threshold_value CHECK (minimum_attendance_percentage BETWEEN 0 AND 100),
 CONSTRAINT ck_attendance_threshold_dates CHECK (effective_to IS NULL OR effective_to>=effective_from),
 CONSTRAINT ck_attendance_threshold_version CHECK (row_version>=1),
 CONSTRAINT ck_attendance_threshold_updated CHECK (updated_at>=created_at));
CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_threshold_scope_active ON app.attendance_threshold_policy
 (tenant_id,institution_id,COALESCE(department_id,'00000000-0000-0000-0000-000000000000'::uuid)) WHERE is_active;
CREATE INDEX IF NOT EXISTS ix_attendance_session_section_date ON app.attendance_session(tenant_id,institution_id,section_id,session_date);
CREATE INDEX IF NOT EXISTS ix_attendance_entry_student_session ON app.attendance_entry(tenant_id,institution_id,enrollment_id,attendance_session_id);
CREATE INDEX IF NOT EXISTS ix_medical_exemption_student_dates ON app.attendance_medical_exemption(tenant_id,enrollment_id,start_date,end_date,status);
CREATE INDEX IF NOT EXISTS ix_attendance_threshold_lookup ON app.attendance_threshold_policy(tenant_id,institution_id,department_id,effective_from DESC) WHERE is_active;

CREATE OR REPLACE FUNCTION app.fn_seed_attendance_threshold_default()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
BEGIN
 INSERT INTO app.attendance_threshold_policy(tenant_id,attendance_threshold_policy_id,institution_id,department_id,
  minimum_attendance_percentage,effective_from,is_active,created_by,updated_by)
 VALUES(NEW.tenant_id,gen_random_uuid(),NEW.institution_id,NULL,75.00,CURRENT_DATE,true,NEW.created_by,NEW.created_by)
 ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_seed_attendance_threshold_default ON app.institution;
CREATE TRIGGER trg_seed_attendance_threshold_default AFTER INSERT ON app.institution
 FOR EACH ROW EXECUTE FUNCTION app.fn_seed_attendance_threshold_default();
INSERT INTO app.attendance_threshold_policy(tenant_id,attendance_threshold_policy_id,institution_id,department_id,
 minimum_attendance_percentage,effective_from,is_active,created_by,updated_by)
SELECT i.tenant_id,gen_random_uuid(),i.institution_id,NULL,75.00,CURRENT_DATE,true,i.created_by,i.created_by
 FROM app.institution i
WHERE NOT EXISTS(SELECT 1 FROM app.attendance_threshold_policy p WHERE p.tenant_id=i.tenant_id
 AND p.institution_id=i.institution_id AND p.department_id IS NULL AND p.is_active);

CREATE OR REPLACE FUNCTION app.fn_attendance_term_lock_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
DECLARE v_tenant uuid; v_institution uuid; v_session uuid; v_section uuid; v_semester uuid; v_any_term_locked boolean; v_session_status text;
BEGIN
 IF TG_TABLE_NAME='attendance_session' THEN
  IF TG_OP='DELETE' THEN
   IF OLD.session_status='SUBMITTED' THEN RAISE EXCEPTION 'ATTENDANCE_ALREADY_SUBMITTED' USING ERRCODE='P0001'; END IF;
   v_tenant:=OLD.tenant_id; v_institution:=OLD.institution_id; v_session:=OLD.academic_session_id; v_section:=OLD.section_id;
  ELSE
   IF TG_OP='UPDATE' AND OLD.session_status='SUBMITTED' THEN RAISE EXCEPTION 'ATTENDANCE_ALREADY_SUBMITTED' USING ERRCODE='P0001'; END IF;
   v_tenant:=NEW.tenant_id; v_institution:=NEW.institution_id; v_session:=NEW.academic_session_id; v_section:=NEW.section_id;
  END IF;
 ELSIF TG_TABLE_NAME='attendance_entry' THEN
  IF TG_OP='DELETE' THEN v_tenant:=OLD.tenant_id; v_institution:=OLD.institution_id; v_session:=OLD.attendance_session_id;
  ELSE v_tenant:=NEW.tenant_id; v_institution:=NEW.institution_id; v_session:=NEW.attendance_session_id; END IF;
  SELECT academic_session_id,section_id,session_status INTO v_session,v_section,v_session_status FROM app.attendance_session
   WHERE tenant_id=v_tenant AND institution_id=v_institution AND attendance_session_id=v_session;
  IF v_session_status='SUBMITTED' THEN RAISE EXCEPTION 'ATTENDANCE_ALREADY_SUBMITTED' USING ERRCODE='P0001'; END IF;
 ELSIF TG_TABLE_NAME='attendance_medical_exemption' THEN
  IF TG_OP='DELETE' THEN v_tenant:=OLD.tenant_id; v_institution:=OLD.institution_id; v_session:=OLD.enrollment_id;
  ELSE v_tenant:=NEW.tenant_id; v_institution:=NEW.institution_id; v_session:=NEW.enrollment_id; END IF;
  SELECT academic_session_id,section_id INTO v_session,v_section FROM app.student_enrollment
   WHERE tenant_id=v_tenant AND institution_id=v_institution AND enrollment_id=v_session;
 END IF;
 SELECT semester_id INTO v_semester FROM app.section WHERE tenant_id=v_tenant AND institution_id=v_institution AND section_id=v_section;
 SELECT COALESCE(bool_or(app.is_term_locked(term_lock_id)),true) INTO v_any_term_locked
  FROM app.term_lock WHERE tenant_id=v_tenant AND institution_id=v_institution
   AND academic_session_id=v_session AND (semester_id=v_semester OR semester_id IS NULL);
 IF v_any_term_locked THEN RAISE EXCEPTION 'TERM_LOCKED: academic term is locked' USING ERRCODE='P0001'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
END $$;

CREATE OR REPLACE FUNCTION app.fn_attendance_session_scope_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
DECLARE v_start date; v_end date; v_section_session uuid; v_slot_section uuid; v_slot_day integer; v_slot_session uuid; v_teacher_assignment boolean;
BEGIN
 SELECT academic_session_id INTO v_section_session FROM app.section WHERE tenant_id=NEW.tenant_id
  AND institution_id=NEW.institution_id AND section_id=NEW.section_id AND status='ACTIVE';
 SELECT start_date,end_date INTO v_start,v_end FROM app.academic_session WHERE tenant_id=NEW.tenant_id
  AND academic_session_id=NEW.academic_session_id;
 IF v_section_session IS DISTINCT FROM NEW.academic_session_id OR NEW.session_date<v_start OR NEW.session_date>v_end THEN
  RAISE EXCEPTION 'ATTENDANCE_SESSION_SCOPE_INVALID' USING ERRCODE='23514';
 END IF;
 IF NEW.session_type='PERIOD' AND NEW.timetable_slot_id IS NULL THEN
  RAISE EXCEPTION 'ATTENDANCE_PERIOD_REQUIRES_SLOT' USING ERRCODE='23514';
 END IF;
 IF NEW.timetable_slot_id IS NOT NULL THEN
  SELECT section_id,day_of_week,academic_session_id INTO v_slot_section,v_slot_day,v_slot_session
   FROM app.timetable_slot WHERE tenant_id=NEW.tenant_id AND institution_id=NEW.institution_id
    AND timetable_slot_id=NEW.timetable_slot_id AND status='ACTIVE';
  IF v_slot_section IS DISTINCT FROM NEW.section_id OR v_slot_day IS DISTINCT FROM EXTRACT(ISODOW FROM NEW.session_date)::integer
    OR v_slot_session IS DISTINCT FROM NEW.academic_session_id THEN
   RAISE EXCEPTION 'ATTENDANCE_SLOT_MISMATCH' USING ERRCODE='23514';
  END IF;
 END IF;
 SELECT EXISTS(SELECT 1 FROM app.staff_assignment x WHERE x.tenant_id=NEW.tenant_id
  AND x.staff_profile_id=NEW.taken_by_teacher_id AND x.institution_id=NEW.institution_id
  AND x.status='ACTIVE' AND x.effective_from<=NEW.session_date
  AND (x.effective_to IS NULL OR x.effective_to>=NEW.session_date)) INTO v_teacher_assignment;
 IF NOT v_teacher_assignment THEN RAISE EXCEPTION 'ATTENDANCE_TEACHER_NOT_ASSIGNED' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION app.fn_attendance_entry_scope_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
DECLARE v_section uuid; v_status text; v_session_academic uuid; v_enrollment_section uuid; v_student_status text; v_enrollment_student uuid; v_enrollment_session uuid;
BEGIN
 SELECT section_id,session_status,academic_session_id INTO v_section,v_status,v_session_academic FROM app.attendance_session
  WHERE tenant_id=NEW.tenant_id AND institution_id=NEW.institution_id AND attendance_session_id=NEW.attendance_session_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'ATTENDANCE_SESSION_NOT_FOUND' USING ERRCODE='23503'; END IF;
 IF v_status='SUBMITTED' THEN RAISE EXCEPTION 'ATTENDANCE_ALREADY_SUBMITTED' USING ERRCODE='P0001'; END IF;
 SELECT section_id,student_profile_id,academic_session_id INTO v_enrollment_section,v_enrollment_student,v_enrollment_session FROM app.student_enrollment
  WHERE tenant_id=NEW.tenant_id AND institution_id=NEW.institution_id AND enrollment_id=NEW.enrollment_id
   AND outcome='IN_PROGRESS' AND ended_on IS NULL;
 IF NOT FOUND OR v_enrollment_section<>v_section OR v_enrollment_student<>NEW.student_profile_id OR v_enrollment_session<>v_session_academic THEN
  RAISE EXCEPTION 'ATTENDANCE_ENROLLMENT_MISMATCH' USING ERRCODE='23514';
 END IF;
 SELECT status INTO v_student_status FROM app.student_profile WHERE tenant_id=NEW.tenant_id
  AND institution_id=NEW.institution_id AND student_profile_id=NEW.student_profile_id;
 IF v_student_status IS DISTINCT FROM 'ACTIVE' THEN RAISE EXCEPTION 'ATTENDANCE_STUDENT_INACTIVE' USING ERRCODE='23514'; END IF;
 IF (v_status='NOT_HELD' AND NEW.status<>'NOT_HELD') OR (v_status<>'NOT_HELD' AND NEW.status='NOT_HELD') THEN
  RAISE EXCEPTION 'ATTENDANCE_NOT_HELD_MISMATCH' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION app.fn_medical_exemption_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
DECLARE v_student uuid; v_started date; v_ended date; v_session_start date; v_session_end date; v_purpose text; v_scan text; v_file_status text; v_institution uuid; v_uploaded_by uuid;
BEGIN
 SELECT e.student_profile_id,e.started_on,e.ended_on,a.start_date,a.end_date
  INTO v_student,v_started,v_ended,v_session_start,v_session_end
  FROM app.student_enrollment e JOIN app.academic_session a
   ON a.tenant_id=e.tenant_id AND a.academic_session_id=e.academic_session_id
  WHERE e.tenant_id=NEW.tenant_id AND e.institution_id=NEW.institution_id AND e.enrollment_id=NEW.enrollment_id;
 IF v_student IS DISTINCT FROM NEW.student_profile_id THEN RAISE EXCEPTION 'MEDICAL_EXEMPTION_ENROLLMENT_MISMATCH' USING ERRCODE='23514'; END IF;
 IF NEW.start_date<v_started OR NEW.start_date<v_session_start OR NEW.end_date>v_session_end
   OR (v_ended IS NOT NULL AND NEW.end_date>v_ended) THEN
  RAISE EXCEPTION 'MEDICAL_EXEMPTION_DATE_OUTSIDE_ENROLLMENT' USING ERRCODE='23514';
 END IF;
 SELECT purpose,scan_status,status,institution_id,uploaded_by INTO v_purpose,v_scan,v_file_status,v_institution,v_uploaded_by
  FROM app.file_object WHERE tenant_id=NEW.tenant_id AND file_id=NEW.document_ref;
 IF v_purpose IS DISTINCT FROM 'ATTENDANCE_MEDICAL_EXEMPTION' OR v_scan IS DISTINCT FROM 'CLEAN'
   OR v_file_status IS DISTINCT FROM 'ACTIVE' OR v_uploaded_by IS DISTINCT FROM NEW.created_by
   OR (v_institution IS NOT NULL AND v_institution<>NEW.institution_id) THEN
  RAISE EXCEPTION 'MEDICAL_EXEMPTION_DOCUMENT_NOT_AVAILABLE' USING ERRCODE='23514';
 END IF;
 IF TG_OP='UPDATE' AND OLD.status<>'PENDING' THEN
  RAISE EXCEPTION 'MEDICAL_EXEMPTION_DECISION_IMMUTABLE' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION app.fn_attendance_threshold_seed_check()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
BEGIN
 IF NEW.department_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM app.attendance_threshold_policy p
  WHERE p.tenant_id=NEW.tenant_id AND p.institution_id=NEW.institution_id AND p.department_id IS NULL
   AND p.is_active AND p.effective_from<=NEW.effective_from) THEN
  RAISE EXCEPTION 'ATTENDANCE_INSTITUTION_DEFAULT_REQUIRED' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_attendance_threshold_seed_check ON app.attendance_threshold_policy;
CREATE TRIGGER trg_attendance_threshold_seed_check BEFORE INSERT OR UPDATE ON app.attendance_threshold_policy
 FOR EACH ROW EXECUTE FUNCTION app.fn_attendance_threshold_seed_check();

DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.attendance_medical_exemption'::regclass AND conname='ex_medical_exemption_no_overlap') THEN
  ALTER TABLE app.attendance_medical_exemption ADD CONSTRAINT ex_medical_exemption_no_overlap
   EXCLUDE USING gist(tenant_id WITH =,enrollment_id WITH =,daterange(start_date,end_date,'[]') WITH &&) WHERE(status='APPROVED');
 END IF;
END $$;

DROP TRIGGER IF EXISTS trg_check_term_lock_attendance ON app.attendance_session;
CREATE TRIGGER trg_check_term_lock_attendance BEFORE INSERT OR UPDATE OR DELETE ON app.attendance_session
 FOR EACH ROW EXECUTE FUNCTION app.fn_attendance_term_lock_guard();
DROP TRIGGER IF EXISTS trg_check_term_lock_attendance_entry ON app.attendance_entry;
CREATE TRIGGER trg_check_term_lock_attendance_entry BEFORE INSERT OR UPDATE OR DELETE ON app.attendance_entry
 FOR EACH ROW EXECUTE FUNCTION app.fn_attendance_term_lock_guard();
DROP TRIGGER IF EXISTS trg_check_term_lock_medical_exemption ON app.attendance_medical_exemption;
CREATE TRIGGER trg_check_term_lock_medical_exemption BEFORE INSERT OR UPDATE OR DELETE ON app.attendance_medical_exemption
 FOR EACH ROW EXECUTE FUNCTION app.fn_attendance_term_lock_guard();
DROP TRIGGER IF EXISTS trg_attendance_session_scope_guard ON app.attendance_session;
CREATE TRIGGER trg_attendance_session_scope_guard BEFORE INSERT OR UPDATE ON app.attendance_session
 FOR EACH ROW EXECUTE FUNCTION app.fn_attendance_session_scope_guard();
DROP TRIGGER IF EXISTS trg_attendance_entry_scope_guard ON app.attendance_entry;
CREATE TRIGGER trg_attendance_entry_scope_guard BEFORE INSERT OR UPDATE ON app.attendance_entry
 FOR EACH ROW EXECUTE FUNCTION app.fn_attendance_entry_scope_guard();
DROP TRIGGER IF EXISTS trg_medical_exemption_guard ON app.attendance_medical_exemption;
CREATE TRIGGER trg_medical_exemption_guard BEFORE INSERT OR UPDATE ON app.attendance_medical_exemption
 FOR EACH ROW EXECUTE FUNCTION app.fn_medical_exemption_guard();

CREATE OR REPLACE VIEW app.v_attendance_percentage WITH (security_invoker=true) AS
SELECT e.tenant_id,e.institution_id,e.enrollment_id,e.student_profile_id,e.section_id,e.academic_session_id,
 count(DISTINCT s.attendance_session_id) FILTER(WHERE s.session_status IN ('HELD','SUBMITTED')) AS total_valid_sessions,
 count(DISTINCT s.attendance_session_id) FILTER(WHERE s.session_status IN ('HELD','SUBMITTED') AND a.status='PRESENT') AS sessions_attended,
 count(DISTINCT s.attendance_session_id) FILTER(WHERE s.session_status IN ('HELD','SUBMITTED') AND a.status IS DISTINCT FROM 'PRESENT'
  AND EXISTS(SELECT 1 FROM app.attendance_medical_exemption m WHERE m.tenant_id=e.tenant_id AND m.enrollment_id=e.enrollment_id
   AND m.status='APPROVED' AND s.session_date BETWEEN m.start_date AND m.end_date)) AS exempted_medical_sessions,
 CASE WHEN count(DISTINCT s.attendance_session_id) FILTER(WHERE s.session_status IN ('HELD','SUBMITTED'))=0 THEN 0
  ELSE round(100.0*count(DISTINCT s.attendance_session_id) FILTER(WHERE s.session_status IN ('HELD','SUBMITTED') AND a.status='PRESENT')
   /count(DISTINCT s.attendance_session_id) FILTER(WHERE s.session_status IN ('HELD','SUBMITTED')),2) END AS standard_percentage,
 CASE WHEN count(DISTINCT s.attendance_session_id) FILTER(WHERE s.session_status IN ('HELD','SUBMITTED'))=0 THEN 0
  ELSE round(100.0*(count(DISTINCT s.attendance_session_id) FILTER(WHERE s.session_status IN ('HELD','SUBMITTED') AND a.status='PRESENT')
   +count(DISTINCT s.attendance_session_id) FILTER(WHERE s.session_status IN ('HELD','SUBMITTED') AND a.status IS DISTINCT FROM 'PRESENT'
    AND EXISTS(SELECT 1 FROM app.attendance_medical_exemption m WHERE m.tenant_id=e.tenant_id AND m.enrollment_id=e.enrollment_id
     AND m.status='APPROVED' AND s.session_date BETWEEN m.start_date AND m.end_date)))
   /count(DISTINCT s.attendance_session_id) FILTER(WHERE s.session_status IN ('HELD','SUBMITTED')),2) END AS adjusted_percentage
FROM app.student_enrollment e
LEFT JOIN app.attendance_session s ON s.tenant_id=e.tenant_id AND s.institution_id=e.institution_id AND s.section_id=e.section_id
LEFT JOIN app.attendance_entry a ON a.tenant_id=s.tenant_id AND a.institution_id=s.institution_id
 AND a.attendance_session_id=s.attendance_session_id AND a.enrollment_id=e.enrollment_id
WHERE e.outcome='IN_PROGRESS' AND e.ended_on IS NULL
GROUP BY e.tenant_id,e.institution_id,e.enrollment_id,e.student_profile_id,e.section_id,e.academic_session_id;

CREATE OR REPLACE FUNCTION app.fn_attendance_exam_eligibility(p_tenant uuid,p_institution uuid,p_enrollment uuid)
RETURNS TABLE(enrollment_id uuid,attendance_percentage numeric,threshold_percentage numeric,eligible boolean,threshold_scope text)
LANGUAGE sql STABLE SECURITY INVOKER SET search_path=pg_catalog,app AS $$
 WITH target AS (
  SELECT v.*,d.department_id FROM app.v_attendance_percentage v
  LEFT JOIN app.section s ON s.tenant_id=v.tenant_id AND s.section_id=v.section_id
  LEFT JOIN app.semester sm ON sm.tenant_id=s.tenant_id AND sm.semester_id=s.semester_id
  LEFT JOIN app.program_year py ON py.tenant_id=sm.tenant_id AND py.program_year_id=sm.program_year_id
  LEFT JOIN app.program pr ON pr.tenant_id=py.tenant_id AND pr.program_id=py.program_id
  LEFT JOIN app.department d ON d.tenant_id=pr.tenant_id AND d.department_id=pr.department_id
  WHERE v.tenant_id=p_tenant AND v.institution_id=p_institution AND v.enrollment_id=p_enrollment
 ), policy AS (
  SELECT p.minimum_attendance_percentage,p.department_id FROM app.attendance_threshold_policy p
  JOIN target t ON t.tenant_id=p.tenant_id AND t.institution_id=p.institution_id
  WHERE p.is_active AND p.effective_from<=CURRENT_DATE AND (p.effective_to IS NULL OR p.effective_to>=CURRENT_DATE)
   AND (p.department_id=t.department_id OR p.department_id IS NULL)
  ORDER BY (p.department_id IS NOT NULL) DESC,p.effective_from DESC LIMIT 1
 )
 SELECT t.enrollment_id,t.adjusted_percentage,p.minimum_attendance_percentage,
  (t.adjusted_percentage>=p.minimum_attendance_percentage),
  CASE WHEN p.department_id IS NULL THEN 'INSTITUTION' ELSE 'DEPARTMENT' END
 FROM target t CROSS JOIN policy p
$$;
COMMENT ON FUNCTION app.fn_attendance_exam_eligibility(uuid,uuid,uuid) IS
 'PO-T02: returns medically adjusted attendance against the active department override or institution default threshold; default policy rows are seeded at 75 percent and are configurable.';

DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['attendance_session','attendance_entry','attendance_medical_exemption','attendance_threshold_policy'] LOOP
  EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE app.%I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('DROP POLICY IF EXISTS p_tenant ON app.%I',t);
  EXECUTE format('CREATE POLICY p_tenant ON app.%I USING (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid) WITH CHECK (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid)',t);
  EXECUTE format('DROP POLICY IF EXISTS p_institution ON app.%I',t);
  EXECUTE format('CREATE POLICY p_institution ON app.%I AS RESTRICTIVE USING (current_setting(''app.institution_scope'',true)=''ALL'' OR institution_id=ANY(string_to_array(NULLIF(current_setting(''app.institution_ids'',true),''''),'','')::uuid[])) WITH CHECK (current_setting(''app.institution_scope'',true)=''ALL'' OR institution_id=ANY(string_to_array(NULLIF(current_setting(''app.institution_ids'',true),''''),'','')::uuid[]))',t);
 END LOOP;
END $$;
DROP TRIGGER IF EXISTS trg_touch ON app.attendance_session;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.attendance_session FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
DROP TRIGGER IF EXISTS trg_touch ON app.attendance_entry;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.attendance_entry FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
DROP TRIGGER IF EXISTS trg_touch ON app.attendance_medical_exemption;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.attendance_medical_exemption FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
DROP TRIGGER IF EXISTS trg_touch ON app.attendance_threshold_policy;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.attendance_threshold_policy FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
GRANT SELECT,INSERT,UPDATE,DELETE ON app.attendance_session,app.attendance_entry,app.attendance_medical_exemption,app.attendance_threshold_policy TO edu_app;
GRANT SELECT ON app.attendance_session,app.attendance_entry,app.attendance_medical_exemption,app.attendance_threshold_policy,app.v_attendance_percentage TO edu_report;
GRANT SELECT ON app.v_attendance_percentage TO edu_app;
GRANT EXECUTE ON FUNCTION app.fn_attendance_exam_eligibility(uuid,uuid,uuid) TO edu_app,edu_report;
COMMENT ON TABLE app.attendance_session IS 'Daily and period attendance; NOT_HELD sessions are excluded from valid totals.';
COMMENT ON COLUMN app.attendance_entry.status IS 'Canonical V4.0 statuses: PRESENT, ABSENT, ON_APPROVED_LEAVE, NOT_HELD.';
COMMENT ON COLUMN app.attendance_medical_exemption.document_ref IS 'Tenant-scoped, malware-scanned file_object with purpose ATTENDANCE_MEDICAL_EXEMPTION.';
COMMENT ON TABLE app.attendance_threshold_policy IS 'PO-T02 institution default and department-specific attendance eligibility overrides; 75 percent default is seeded per institution.';
COMMIT;