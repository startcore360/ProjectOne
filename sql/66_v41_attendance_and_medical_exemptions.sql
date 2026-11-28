BEGIN;

ALTER TABLE app.attendance_session DROP CONSTRAINT IF EXISTS ck_attendance_session_session_kind;
ALTER TABLE app.attendance_session ADD CONSTRAINT ck_attendance_session_session_kind
 CHECK (session_kind IN ('DAILY','PERIOD','SCHOOL_FIRST_HALF','SCHOOL_SECOND_HALF'));

ALTER TABLE app.file_object DROP CONSTRAINT IF EXISTS ck_file_object_purpose;
ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_purpose CHECK (purpose IN (
 'MESSAGE_ATTACHMENT','ASSIGNMENT_SUBMISSION','STUDY_MATERIAL','STAFF_DOCUMENT','POLICY','DEPARTMENT_DOCUMENT',
 'ACCREDITATION','COMPLIANCE','LEAVE_DOCUMENT','HALL_TICKET','COMMUNITY_MEDIA','IMPORT_SOURCE','EXPORT',
 'PROFILE_PHOTO','BRANDING','ATTENDANCE_UPLOAD','ATTENDANCE_MEDICAL_EXEMPTION','MARKS_SOURCE','SYLLABUS',
 'CERTIFICATE','CONSENT_FORM','SERVICE_RECORD'
));
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.file_object'::regclass AND conname='ck_file_object_medical_exemption_type') THEN
  ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_medical_exemption_type
   CHECK (purpose<>'ATTENDANCE_MEDICAL_EXEMPTION' OR
    (content_type IN ('application/pdf','image/jpeg','image/png') AND size_bytes<=10485760));
 END IF;
END $$;

CREATE TABLE IF NOT EXISTS app.attendance_medical_exemption (
 tenant_id uuid NOT NULL, attendance_medical_exemption_id uuid NOT NULL, institution_id uuid NOT NULL,
 enrollment_id uuid NOT NULL, student_profile_id uuid NOT NULL,
 start_date date NOT NULL, end_date date NOT NULL, document_ref uuid NOT NULL,
 status text NOT NULL DEFAULT 'PENDING', requested_at timestamptz NOT NULL DEFAULT now(),
 approved_by_user_id uuid, decided_at timestamptz, decision_note text,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid NOT NULL,
 row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_attendance_medical_exemption PRIMARY KEY (tenant_id,attendance_medical_exemption_id),
 CONSTRAINT uq_attendance_medical_exemption_identity UNIQUE (tenant_id,institution_id,attendance_medical_exemption_id),
 CONSTRAINT fk_medical_exemption_tenant FOREIGN KEY (tenant_id) REFERENCES app.tenant_local(tenant_id) ON DELETE RESTRICT,
 CONSTRAINT fk_medical_exemption_institution FOREIGN KEY (tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id) ON DELETE RESTRICT,
 CONSTRAINT fk_medical_exemption_enrollment FOREIGN KEY (tenant_id,institution_id,enrollment_id) REFERENCES app.student_enrollment(tenant_id,institution_id,enrollment_id) ON DELETE RESTRICT,
 CONSTRAINT fk_medical_exemption_student FOREIGN KEY (tenant_id,institution_id,student_profile_id) REFERENCES app.student_profile(tenant_id,institution_id,student_profile_id) ON DELETE RESTRICT,
 CONSTRAINT fk_medical_exemption_document FOREIGN KEY (tenant_id,document_ref) REFERENCES app.file_object(tenant_id,file_id) ON DELETE RESTRICT,
 CONSTRAINT fk_medical_exemption_approver FOREIGN KEY (tenant_id,approved_by_user_id) REFERENCES app.user_account(tenant_id,user_account_id) ON DELETE RESTRICT,
 CONSTRAINT ck_medical_exemption_dates CHECK (end_date>=start_date),
 CONSTRAINT ck_medical_exemption_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
 CONSTRAINT ck_medical_exemption_decision CHECK (
  (status='PENDING' AND approved_by_user_id IS NULL AND decided_at IS NULL)
  OR (status IN ('APPROVED','REJECTED') AND approved_by_user_id IS NOT NULL AND decided_at IS NOT NULL)),
 CONSTRAINT ck_medical_exemption_note CHECK (decision_note IS NULL OR char_length(btrim(decision_note)) BETWEEN 1 AND 1000),
 CONSTRAINT ck_medical_exemption_version CHECK (row_version>=1),
 CONSTRAINT ck_medical_exemption_updated CHECK (updated_at>=created_at)
);
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.attendance_entry'::regclass AND conname='uq_attendance_entry_session_enrollment') THEN
  ALTER TABLE app.attendance_entry ADD CONSTRAINT uq_attendance_entry_session_enrollment
   UNIQUE (tenant_id,institution_id,attendance_session_id,enrollment_id,academic_year);
 END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_medical_exemption_student_dates
 ON app.attendance_medical_exemption(tenant_id,student_profile_id,start_date,end_date,status);
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.attendance_medical_exemption'::regclass AND conname='ex_medical_exemption_no_overlap') THEN
  ALTER TABLE app.attendance_medical_exemption ADD CONSTRAINT ex_medical_exemption_no_overlap
   EXCLUDE USING gist (tenant_id WITH =,student_profile_id WITH =,daterange(start_date,end_date,'[]') WITH &&)
   WHERE (status='APPROVED');
 END IF;
END $$;

CREATE TABLE IF NOT EXISTS app.attendance_threshold_policy (
 tenant_id uuid NOT NULL, attendance_threshold_policy_id uuid NOT NULL, institution_id uuid NOT NULL,
 department_id uuid, minimum_attendance_percentage numeric(5,2) NOT NULL DEFAULT 75.00,
 effective_from date NOT NULL, effective_to date, is_active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid NOT NULL,
 row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_attendance_threshold_policy PRIMARY KEY (tenant_id,attendance_threshold_policy_id),
 CONSTRAINT fk_attendance_threshold_tenant FOREIGN KEY (tenant_id) REFERENCES app.tenant_local(tenant_id) ON DELETE RESTRICT,
 CONSTRAINT fk_attendance_threshold_institution FOREIGN KEY (tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id) ON DELETE RESTRICT,
 CONSTRAINT fk_attendance_threshold_department FOREIGN KEY (tenant_id,institution_id,department_id) REFERENCES app.department(tenant_id,institution_id,department_id) ON DELETE RESTRICT,
 CONSTRAINT ck_attendance_threshold_value CHECK (minimum_attendance_percentage BETWEEN 0 AND 100),
 CONSTRAINT ck_attendance_threshold_dates CHECK (effective_to IS NULL OR effective_to>=effective_from),
 CONSTRAINT ck_attendance_threshold_version CHECK (row_version>=1),
 CONSTRAINT ck_attendance_threshold_updated CHECK (updated_at>=created_at)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_threshold_scope_active
 ON app.attendance_threshold_policy(tenant_id,institution_id,COALESCE(department_id,'00000000-0000-0000-0000-000000000000'::uuid))
 WHERE is_active;
CREATE INDEX IF NOT EXISTS ix_attendance_threshold_lookup
 ON app.attendance_threshold_policy(tenant_id,institution_id,department_id,effective_from DESC) WHERE is_active;

CREATE OR REPLACE FUNCTION app.is_term_locked(
 p_tenant_id uuid,p_institution_id uuid,p_academic_session_id uuid,p_semester_id uuid
)
RETURNS boolean
LANGUAGE sql STABLE SECURITY INVOKER SET search_path=pg_catalog,app AS $T6$
 SELECT CASE
  WHEN p_tenant_id IS NULL OR p_institution_id IS NULL OR p_academic_session_id IS NULL
    OR NULLIF(current_setting('app.tenant_id',true),'')::uuid IS DISTINCT FROM p_tenant_id
   THEN true
  ELSE COALESCE((
   SELECT bool_or(app.is_term_locked(l.term_lock_id))
    FROM app.term_lock l
   WHERE l.tenant_id=p_tenant_id AND l.institution_id=p_institution_id
    AND l.academic_session_id=p_academic_session_id
    AND (l.semester_id IS NULL OR l.semester_id IS NOT DISTINCT FROM p_semester_id)
  ),true)
 END
$T6$;
REVOKE ALL ON FUNCTION app.is_term_locked(uuid,uuid,uuid,uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app.is_term_locked(uuid,uuid,uuid,uuid) TO edu_app,edu_report;

CREATE OR REPLACE FUNCTION app.fn_attendance_session_scope_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
DECLARE
 v_session uuid; v_year smallint; v_section uuid; v_group uuid; v_course_offering uuid;
 v_slot_section uuid; v_slot_group uuid; v_slot_allocation uuid; v_slot_status text; v_slot_day smallint;
 v_allocation_staff uuid; v_expected_user uuid; v_section_teacher uuid;
 v_session_start date; v_session_end date; v_teacher_status text; v_date_start date; v_date_end date;
BEGIN
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 v_section:=NEW.section_id; v_group:=NEW.subject_group_id;
 IF v_section IS NOT NULL THEN
  SELECT academic_session_id,class_teacher_user_id INTO v_session,v_section_teacher
   FROM app.section WHERE tenant_id=NEW.tenant_id AND institution_id=NEW.institution_id
    AND section_id=v_section AND status='ACTIVE';
 ELSE
  SELECT co.academic_session_id INTO v_session
   FROM app.subject_group g JOIN app.course_offering co
    ON co.tenant_id=g.tenant_id AND co.institution_id=g.institution_id AND co.course_offering_id=g.course_offering_id
   WHERE g.tenant_id=NEW.tenant_id AND g.institution_id=NEW.institution_id
    AND g.subject_group_id=v_group AND g.status='ACTIVE';
 END IF;
 IF v_session IS NULL THEN RAISE EXCEPTION 'ATTENDANCE_SCOPE_INVALID' USING ERRCODE='23514'; END IF;
 SELECT academic_year,start_date,end_date INTO v_year,v_session_start,v_session_end
  FROM app.academic_session WHERE tenant_id=NEW.tenant_id AND academic_session_id=v_session;
 IF NEW.academic_year<>v_year OR NEW.attendance_date NOT BETWEEN v_session_start AND v_session_end THEN
  RAISE EXCEPTION 'ATTENDANCE_ACADEMIC_YEAR_MISMATCH' USING ERRCODE='23514';
 END IF;
 IF NEW.session_kind='PERIOD' THEN
  IF NEW.timetable_slot_id IS NULL OR NEW.subject_allocation_id IS NULL THEN
   RAISE EXCEPTION 'ATTENDANCE_PERIOD_REQUIRES_SLOT_AND_ALLOCATION' USING ERRCODE='23514';
  END IF;
  SELECT section_id,subject_group_id,subject_allocation_id,status,valid_from,valid_to,day_of_week
   INTO v_slot_section,v_slot_group,v_slot_allocation,v_slot_status,v_date_start,v_date_end,v_slot_day
   FROM app.timetable_slot WHERE tenant_id=NEW.tenant_id AND institution_id=NEW.institution_id
    AND timetable_slot_id=NEW.timetable_slot_id AND academic_session_id=v_session;
  IF NOT FOUND OR v_slot_status<>'PUBLISHED' OR v_slot_section IS DISTINCT FROM v_section
    OR v_slot_group IS DISTINCT FROM v_group OR v_slot_allocation IS DISTINCT FROM NEW.subject_allocation_id
    OR NEW.attendance_date<v_date_start OR (v_date_end IS NOT NULL AND NEW.attendance_date>v_date_end)
    OR extract(isodow FROM NEW.attendance_date)::smallint<>v_slot_day THEN
   RAISE EXCEPTION 'ATTENDANCE_SLOT_MISMATCH' USING ERRCODE='23514';
  END IF;
  SELECT staff_assignment_id INTO v_allocation_staff FROM app.subject_allocation
   WHERE tenant_id=NEW.tenant_id AND institution_id=NEW.institution_id
    AND subject_allocation_id=NEW.subject_allocation_id AND status='ACTIVE';
  IF NEW.marked_as='ASSIGNED' THEN
   SELECT sp.user_account_id INTO v_expected_user FROM app.staff_assignment st
    JOIN app.staff_profile sp ON sp.tenant_id=st.tenant_id AND sp.staff_profile_id=st.staff_profile_id
    WHERE st.tenant_id=NEW.tenant_id AND st.staff_assignment_id=v_allocation_staff
     AND st.status='ACTIVE' AND sp.employment_status='ACTIVE';
  ELSE
   SELECT sp.user_account_id INTO v_expected_user FROM app.substitute_assignment sub
    JOIN app.staff_assignment st ON st.tenant_id=sub.tenant_id AND st.staff_assignment_id=sub.substitute_staff_assignment_id
    JOIN app.staff_profile sp ON sp.tenant_id=st.tenant_id AND sp.staff_profile_id=st.staff_profile_id
    WHERE sub.tenant_id=NEW.tenant_id AND sub.substitute_assignment_id=NEW.substitute_assignment_id
     AND sub.subject_allocation_id=NEW.subject_allocation_id AND NEW.attendance_date BETWEEN sub.start_date AND sub.end_date
     AND st.status='ACTIVE' AND sp.employment_status='ACTIVE';
  END IF;
  IF v_expected_user IS DISTINCT FROM NEW.marked_by_user_id THEN
   RAISE EXCEPTION 'ATTENDANCE_MARKER_NOT_ASSIGNED' USING ERRCODE='23514';
  END IF;
 ELSE
  IF NEW.session_kind NOT IN ('DAILY','SCHOOL_FIRST_HALF','SCHOOL_SECOND_HALF') OR v_section IS NULL
    OR NEW.timetable_slot_id IS NOT NULL OR NEW.subject_allocation_id IS NOT NULL
    OR NEW.marked_as<>'ASSIGNED' OR v_section_teacher IS DISTINCT FROM NEW.marked_by_user_id THEN
   RAISE EXCEPTION 'ATTENDANCE_SCHOOL_HALF_SCOPE_INVALID' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION app.fn_attendance_entry_scope_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
DECLARE v_section uuid; v_group uuid; v_date date; v_year smallint; v_enrollment_section uuid;
 v_student uuid; v_student_status text; v_outcome text; v_started date; v_ended date;
BEGIN
 SELECT section_id,subject_group_id,attendance_date,academic_year
  INTO v_section,v_group,v_date,v_year FROM app.attendance_session
  WHERE tenant_id=NEW.tenant_id AND institution_id=NEW.institution_id
   AND attendance_session_id=NEW.attendance_session_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'ATTENDANCE_SESSION_NOT_FOUND' USING ERRCODE='23503'; END IF;
 SELECT section_id,student_profile_id,outcome,started_on,ended_on
  INTO v_enrollment_section,v_student,v_outcome,v_started,v_ended FROM app.student_enrollment
  WHERE tenant_id=NEW.tenant_id AND institution_id=NEW.institution_id AND enrollment_id=NEW.enrollment_id;
 IF NOT FOUND OR v_outcome<>'IN_PROGRESS' OR v_ended IS NOT NULL
   OR v_date<v_started OR v_date>COALESCE(v_ended,v_date) THEN
  RAISE EXCEPTION 'ATTENDANCE_ENROLLMENT_INVALID' USING ERRCODE='23514';
 END IF;
 IF v_section IS NOT NULL AND v_enrollment_section<>v_section THEN
  RAISE EXCEPTION 'ATTENDANCE_ENROLLMENT_SECTION_MISMATCH' USING ERRCODE='23514';
 END IF;
 IF v_group IS NOT NULL AND NOT EXISTS(SELECT 1 FROM app.subject_group_member gm
   WHERE gm.tenant_id=NEW.tenant_id AND gm.institution_id=NEW.institution_id
    AND gm.subject_group_id=v_group AND gm.enrollment_id=NEW.enrollment_id) THEN
  RAISE EXCEPTION 'ATTENDANCE_ENROLLMENT_GROUP_MISMATCH' USING ERRCODE='23514';
 END IF;
 SELECT status INTO v_student_status FROM app.student_profile
  WHERE tenant_id=NEW.tenant_id AND institution_id=NEW.institution_id AND student_profile_id=v_student;
 IF v_student_status IS DISTINCT FROM 'ACTIVE' THEN RAISE EXCEPTION 'ATTENDANCE_STUDENT_INACTIVE' USING ERRCODE='23514'; END IF;
 IF NEW.academic_year<>v_year THEN RAISE EXCEPTION 'PARTITION_YEAR_MISMATCH' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION app.fn_attendance_scope_is_term_locked(
 p_tenant_id uuid,p_institution_id uuid,p_section_id uuid,p_subject_group_id uuid
)
RETURNS boolean
LANGUAGE plpgsql STABLE SECURITY INVOKER SET search_path=pg_catalog,app AS $T6$
DECLARE v_session uuid; v_semester uuid;
BEGIN
 IF p_section_id IS NOT NULL THEN
  SELECT academic_session_id,semester_id INTO v_session,v_semester FROM app.section
   WHERE tenant_id=p_tenant_id AND institution_id=p_institution_id AND section_id=p_section_id;
 ELSIF p_subject_group_id IS NOT NULL THEN
  SELECT co.academic_session_id,co.semester_id INTO v_session,v_semester
   FROM app.subject_group g JOIN app.course_offering co
    ON co.tenant_id=g.tenant_id AND co.institution_id=g.institution_id AND co.course_offering_id=g.course_offering_id
   WHERE g.tenant_id=p_tenant_id AND g.institution_id=p_institution_id AND g.subject_group_id=p_subject_group_id;
 END IF;
 IF v_session IS NULL THEN RETURN true; END IF;
 RETURN app.is_term_locked(p_tenant_id,p_institution_id,v_session,v_semester);
END
$T6$;

CREATE OR REPLACE FUNCTION app.fn_attendance_term_lock_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
DECLARE
 v_tenant uuid; v_institution uuid; v_section uuid; v_group uuid;
 v_enrollment uuid; v_session_id uuid; v_old_section uuid; v_old_group uuid; v_old_enrollment uuid;
BEGIN
 IF TG_TABLE_NAME='attendance_session' THEN
  v_tenant:=CASE WHEN TG_OP='DELETE' THEN OLD.tenant_id ELSE NEW.tenant_id END;
  v_institution:=CASE WHEN TG_OP='DELETE' THEN OLD.institution_id ELSE NEW.institution_id END;
  v_section:=CASE WHEN TG_OP='DELETE' THEN OLD.section_id ELSE NEW.section_id END;
  v_group:=CASE WHEN TG_OP='DELETE' THEN OLD.subject_group_id ELSE NEW.subject_group_id END;
  IF TG_OP='UPDATE' AND app.fn_attendance_scope_is_term_locked(OLD.tenant_id,OLD.institution_id,OLD.section_id,OLD.subject_group_id) THEN
   RAISE EXCEPTION 'TERM_LOCKED: attendance cannot change while the previous term is locked' USING ERRCODE='P0001';
  END IF;
 ELSIF TG_TABLE_NAME='attendance_entry' THEN
  v_tenant:=CASE WHEN TG_OP='DELETE' THEN OLD.tenant_id ELSE NEW.tenant_id END;
  v_institution:=CASE WHEN TG_OP='DELETE' THEN OLD.institution_id ELSE NEW.institution_id END;
  v_session_id:=CASE WHEN TG_OP='DELETE' THEN OLD.attendance_session_id ELSE NEW.attendance_session_id END;
  SELECT section_id,subject_group_id INTO v_section,v_group FROM app.attendance_session
   WHERE tenant_id=v_tenant AND institution_id=v_institution AND attendance_session_id=v_session_id;
  IF TG_OP='UPDATE' THEN
   SELECT section_id,subject_group_id INTO v_old_section,v_old_group FROM app.attendance_session
    WHERE tenant_id=OLD.tenant_id AND institution_id=OLD.institution_id AND attendance_session_id=OLD.attendance_session_id;
   IF app.fn_attendance_scope_is_term_locked(OLD.tenant_id,OLD.institution_id,v_old_section,v_old_group) THEN
    RAISE EXCEPTION 'TERM_LOCKED: attendance cannot change while the previous term is locked' USING ERRCODE='P0001';
   END IF;
  END IF;
 ELSE
  v_tenant:=CASE WHEN TG_OP='DELETE' THEN OLD.tenant_id ELSE NEW.tenant_id END;
  v_institution:=CASE WHEN TG_OP='DELETE' THEN OLD.institution_id ELSE NEW.institution_id END;
  v_enrollment:=CASE WHEN TG_OP='DELETE' THEN OLD.enrollment_id ELSE NEW.enrollment_id END;
  SELECT section_id INTO v_section FROM app.student_enrollment
   WHERE tenant_id=v_tenant AND institution_id=v_institution AND enrollment_id=v_enrollment;
  IF TG_OP='UPDATE' THEN
   SELECT section_id INTO v_old_section FROM app.student_enrollment
    WHERE tenant_id=OLD.tenant_id AND institution_id=OLD.institution_id AND enrollment_id=OLD.enrollment_id;
   IF app.fn_attendance_scope_is_term_locked(OLD.tenant_id,OLD.institution_id,v_old_section,NULL) THEN
    RAISE EXCEPTION 'TERM_LOCKED: medical exemption cannot change while the previous term is locked' USING ERRCODE='P0001';
   END IF;
  END IF;
 END IF;
 IF app.fn_attendance_scope_is_term_locked(v_tenant,v_institution,v_section,v_group) THEN
  RAISE EXCEPTION 'TERM_LOCKED: attendance cannot change while the term is locked' USING ERRCODE='P0001';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION app.fn_medical_exemption_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
DECLARE v_student uuid; v_section uuid; v_academic_session uuid; v_semester uuid; v_started date; v_ended date; v_session_start date; v_session_end date;
 v_purpose text; v_scan text; v_file_status text; v_file_institution uuid; v_uploaded_by uuid;
BEGIN
 IF TG_OP='UPDATE' AND OLD.status<>'PENDING' THEN
  RAISE EXCEPTION 'MEDICAL_EXEMPTION_FINAL' USING ERRCODE='55000';
 END IF;
 SELECT e.student_profile_id,e.section_id,s.academic_session_id,s.semester_id,e.started_on,e.ended_on,a.start_date,a.end_date
  INTO v_student,v_section,v_academic_session,v_semester,v_started,v_ended,v_session_start,v_session_end
  FROM app.student_enrollment e JOIN app.section s
   ON s.tenant_id=e.tenant_id AND s.institution_id=e.institution_id AND s.section_id=e.section_id
  JOIN app.academic_session a ON a.tenant_id=s.tenant_id AND a.academic_session_id=s.academic_session_id
  WHERE e.tenant_id=NEW.tenant_id AND e.institution_id=NEW.institution_id AND e.enrollment_id=NEW.enrollment_id;
 IF v_student IS DISTINCT FROM NEW.student_profile_id THEN RAISE EXCEPTION 'MEDICAL_EXEMPTION_ENROLLMENT_MISMATCH' USING ERRCODE='23514'; END IF;
 IF NEW.start_date<v_started OR NEW.start_date<v_session_start OR NEW.end_date>v_session_end
   OR (v_ended IS NOT NULL AND NEW.end_date>v_ended) THEN
  RAISE EXCEPTION 'MEDICAL_EXEMPTION_DATE_OUTSIDE_ENROLLMENT' USING ERRCODE='23514';
 END IF;
 SELECT purpose,scan_status,status,institution_id,uploaded_by
  INTO v_purpose,v_scan,v_file_status,v_file_institution,v_uploaded_by
  FROM app.file_object WHERE tenant_id=NEW.tenant_id AND file_id=NEW.document_ref;
 IF v_purpose IS DISTINCT FROM 'ATTENDANCE_MEDICAL_EXEMPTION' OR v_scan IS DISTINCT FROM 'CLEAN'
   OR v_file_status IS DISTINCT FROM 'ACTIVE' OR v_uploaded_by IS DISTINCT FROM NEW.created_by
   OR (v_file_institution IS NOT NULL AND v_file_institution<>NEW.institution_id) THEN
  RAISE EXCEPTION 'MEDICAL_EXEMPTION_DOCUMENT_NOT_AVAILABLE' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION app.fn_seed_attendance_threshold_default()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
BEGIN
 INSERT INTO app.attendance_threshold_policy(tenant_id,attendance_threshold_policy_id,institution_id,department_id,
  minimum_attendance_percentage,effective_from,is_active,created_by,updated_by)
 VALUES(NEW.tenant_id,gen_random_uuid(),NEW.institution_id,NULL,75.00,CURRENT_DATE,true,NEW.created_by,NEW.created_by)
 ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION app.fn_attendance_threshold_department_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
BEGIN
 IF NEW.department_id IS NOT NULL AND NOT EXISTS(
  SELECT 1 FROM app.attendance_threshold_policy p WHERE p.tenant_id=NEW.tenant_id
   AND p.institution_id=NEW.institution_id AND p.department_id IS NULL AND p.is_active
   AND p.effective_from<=NEW.effective_from) THEN
  RAISE EXCEPTION 'INSTITUTION_ATTENDANCE_THRESHOLD_REQUIRED' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_attendance_session_scope_guard ON app.attendance_session;
CREATE TRIGGER trg_attendance_session_scope_guard BEFORE INSERT OR UPDATE ON app.attendance_session
 FOR EACH ROW EXECUTE FUNCTION app.fn_attendance_session_scope_guard();
DROP TRIGGER IF EXISTS trg_check_term_lock_attendance ON app.attendance_session;
CREATE TRIGGER trg_check_term_lock_attendance BEFORE INSERT OR UPDATE OR DELETE ON app.attendance_session
 FOR EACH ROW EXECUTE FUNCTION app.fn_attendance_term_lock_guard();
DROP TRIGGER IF EXISTS trg_attendance_entry_scope_guard ON app.attendance_entry;
CREATE TRIGGER trg_attendance_entry_scope_guard BEFORE INSERT OR UPDATE ON app.attendance_entry
 FOR EACH ROW EXECUTE FUNCTION app.fn_attendance_entry_scope_guard();
DROP TRIGGER IF EXISTS trg_check_term_lock_attendance_entry_delete ON app.attendance_entry;
DROP TRIGGER IF EXISTS trg_check_term_lock_attendance_entry ON app.attendance_entry;
CREATE TRIGGER trg_check_term_lock_attendance_entry BEFORE INSERT OR UPDATE OR DELETE ON app.attendance_entry
 FOR EACH ROW EXECUTE FUNCTION app.fn_attendance_term_lock_guard();
DROP TRIGGER IF EXISTS trg_medical_exemption_guard ON app.attendance_medical_exemption;
CREATE TRIGGER trg_medical_exemption_guard BEFORE INSERT OR UPDATE ON app.attendance_medical_exemption
 FOR EACH ROW EXECUTE FUNCTION app.fn_medical_exemption_guard();
DROP TRIGGER IF EXISTS trg_check_term_lock_medical_exemption ON app.attendance_medical_exemption;
CREATE TRIGGER trg_check_term_lock_medical_exemption BEFORE INSERT OR UPDATE OR DELETE ON app.attendance_medical_exemption
 FOR EACH ROW EXECUTE FUNCTION app.fn_attendance_term_lock_guard();

DROP TRIGGER IF EXISTS trg_seed_attendance_threshold_default ON app.institution;
CREATE TRIGGER trg_seed_attendance_threshold_default AFTER INSERT ON app.institution
 FOR EACH ROW EXECUTE FUNCTION app.fn_seed_attendance_threshold_default();
DROP TRIGGER IF EXISTS trg_attendance_threshold_department_guard ON app.attendance_threshold_policy;
CREATE TRIGGER trg_attendance_threshold_department_guard BEFORE INSERT OR UPDATE ON app.attendance_threshold_policy
 FOR EACH ROW EXECUTE FUNCTION app.fn_attendance_threshold_department_guard();

INSERT INTO app.attendance_threshold_policy(tenant_id,attendance_threshold_policy_id,institution_id,department_id,
 minimum_attendance_percentage,effective_from,is_active,created_by,updated_by)
SELECT i.tenant_id,gen_random_uuid(),i.institution_id,NULL,75.00,CURRENT_DATE,true,i.created_by,i.created_by
FROM app.institution i
WHERE NOT EXISTS(SELECT 1 FROM app.attendance_threshold_policy p WHERE p.tenant_id=i.tenant_id
 AND p.institution_id=i.institution_id AND p.department_id IS NULL AND p.is_active);

CREATE OR REPLACE VIEW app.v_attendance_percentage WITH (security_invoker=true) AS
SELECT e.tenant_id,e.institution_id,e.enrollment_id,e.student_profile_id,e.section_id,e.academic_session_id,
       count(DISTINCT s.attendance_session_id) FILTER (WHERE s.held_status='HELD') AS total_valid_sessions,
       count(DISTINCT s.attendance_session_id) FILTER (WHERE s.held_status='HELD' AND a.status='PRESENT') AS sessions_attended,
       count(DISTINCT s.attendance_session_id) FILTER (
        WHERE s.held_status='HELD' AND a.status IS DISTINCT FROM 'PRESENT'
         AND EXISTS(SELECT 1 FROM app.attendance_medical_exemption m
          WHERE m.tenant_id=e.tenant_id AND m.institution_id=e.institution_id
           AND m.enrollment_id=e.enrollment_id AND m.status='APPROVED'
           AND s.attendance_date BETWEEN m.start_date AND m.end_date)
       ) AS exempted_medical_sessions,
       CASE WHEN count(DISTINCT s.attendance_session_id) FILTER (WHERE s.held_status='HELD')=0 THEN 0
        ELSE round(100.0*count(DISTINCT s.attendance_session_id) FILTER (WHERE s.held_status='HELD' AND a.status='PRESENT')
          / count(DISTINCT s.attendance_session_id) FILTER (WHERE s.held_status='HELD'),2) END AS standard_percentage,
       CASE WHEN count(DISTINCT s.attendance_session_id) FILTER (WHERE s.held_status='HELD')=0 THEN 0
        ELSE round(100.0*(count(DISTINCT s.attendance_session_id) FILTER (WHERE s.held_status='HELD' AND a.status='PRESENT')
          + count(DISTINCT s.attendance_session_id) FILTER (
             WHERE s.held_status='HELD' AND a.status IS DISTINCT FROM 'PRESENT'
              AND EXISTS(SELECT 1 FROM app.attendance_medical_exemption m
               WHERE m.tenant_id=e.tenant_id AND m.institution_id=e.institution_id
                AND m.enrollment_id=e.enrollment_id AND m.status='APPROVED'
                AND s.attendance_date BETWEEN m.start_date AND m.end_date)))
          / count(DISTINCT s.attendance_session_id) FILTER (WHERE s.held_status='HELD'),2) END AS adjusted_percentage
FROM app.student_enrollment e
LEFT JOIN app.attendance_session s ON s.tenant_id=e.tenant_id AND s.institution_id=e.institution_id
 AND (s.section_id=e.section_id OR EXISTS(SELECT 1 FROM app.subject_group_member gm
   WHERE gm.tenant_id=s.tenant_id AND gm.institution_id=s.institution_id
    AND gm.subject_group_id=s.subject_group_id AND gm.enrollment_id=e.enrollment_id))
LEFT JOIN app.attendance_entry a ON a.tenant_id=s.tenant_id AND a.institution_id=s.institution_id
 AND a.attendance_session_id=s.attendance_session_id AND a.enrollment_id=e.enrollment_id
 AND a.academic_year=s.academic_year
WHERE e.outcome='IN_PROGRESS' AND e.ended_on IS NULL
GROUP BY e.tenant_id,e.institution_id,e.enrollment_id,e.student_profile_id,e.section_id,e.academic_session_id;

CREATE OR REPLACE FUNCTION app.fn_attendance_exam_eligibility(p_tenant uuid,p_institution uuid,p_enrollment uuid)
RETURNS TABLE(enrollment_id uuid,attendance_percentage numeric,threshold_percentage numeric,eligible boolean,threshold_scope text)
LANGUAGE sql STABLE SECURITY INVOKER SET search_path=pg_catalog,app AS $$
 WITH target AS (
  SELECT e.tenant_id,e.institution_id,e.enrollment_id,e.academic_session_id,e.section_id,
         COALESCE(v.adjusted_percentage,0) AS adjusted_percentage,pr.department_id
   FROM app.student_enrollment e
   LEFT JOIN app.v_attendance_percentage v ON v.tenant_id=e.tenant_id AND v.institution_id=e.institution_id AND v.enrollment_id=e.enrollment_id
   LEFT JOIN app.section s ON s.tenant_id=e.tenant_id AND s.institution_id=e.institution_id AND s.section_id=e.section_id
   LEFT JOIN app.semester sm ON sm.tenant_id=s.tenant_id AND sm.semester_id=s.semester_id
   LEFT JOIN app.program_year py ON py.tenant_id=sm.tenant_id AND py.program_year_id=sm.program_year_id
   LEFT JOIN app.program pr ON pr.tenant_id=py.tenant_id AND pr.program_id=py.program_id
  WHERE e.tenant_id=p_tenant AND e.institution_id=p_institution AND e.enrollment_id=p_enrollment
   AND e.outcome='IN_PROGRESS' AND e.ended_on IS NULL
 )
 SELECT t.enrollment_id,t.adjusted_percentage,p.minimum_attendance_percentage,
        t.adjusted_percentage>=p.minimum_attendance_percentage,
        CASE WHEN p.department_id IS NULL THEN 'INSTITUTION' ELSE 'DEPARTMENT' END
 FROM target t JOIN LATERAL (
  SELECT q.department_id,q.minimum_attendance_percentage FROM app.attendance_threshold_policy q
   WHERE q.tenant_id=t.tenant_id AND q.institution_id=t.institution_id AND q.is_active
    AND q.effective_from<=CURRENT_DATE AND (q.effective_to IS NULL OR q.effective_to>=CURRENT_DATE)
    AND (q.department_id=t.department_id OR q.department_id IS NULL)
   ORDER BY (q.department_id IS NOT NULL) DESC,q.effective_from DESC LIMIT 1
 ) p ON true
$$;

DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['attendance_medical_exemption','attendance_threshold_policy'] LOOP
  EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE app.%I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('DROP POLICY IF EXISTS p_tenant ON app.%I',t);
  EXECUTE format('CREATE POLICY p_tenant ON app.%I USING (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid) WITH CHECK (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid)',t);
  EXECUTE format('DROP POLICY IF EXISTS p_institution ON app.%I',t);
  EXECUTE format('CREATE POLICY p_institution ON app.%I AS RESTRICTIVE USING (current_setting(''app.institution_scope'',true)=''ALL'' OR institution_id=ANY(string_to_array(NULLIF(current_setting(''app.institution_ids'',true),''''),'','')::uuid[])) WITH CHECK (current_setting(''app.institution_scope'',true)=''ALL'' OR institution_id=ANY(string_to_array(NULLIF(current_setting(''app.institution_ids'',true),''''),'','')::uuid[]))',t);
 END LOOP;
END $$;
DROP TRIGGER IF EXISTS trg_touch ON app.attendance_medical_exemption;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.attendance_medical_exemption FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
DROP TRIGGER IF EXISTS trg_touch ON app.attendance_threshold_policy;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.attendance_threshold_policy FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
GRANT SELECT,INSERT,UPDATE,DELETE ON app.attendance_medical_exemption,app.attendance_threshold_policy TO edu_app;
GRANT SELECT ON app.attendance_medical_exemption,app.attendance_threshold_policy,app.v_attendance_percentage TO edu_report;
GRANT SELECT ON app.v_attendance_percentage TO edu_app;
GRANT EXECUTE ON FUNCTION app.fn_attendance_exam_eligibility(uuid,uuid,uuid) TO edu_app,edu_report;
COMMENT ON TABLE app.attendance_medical_exemption IS 'Doctor-certified attendance exemption request; approval adjusts the attendance denominator.';
COMMENT ON TABLE app.attendance_threshold_policy IS 'PO-T02: institution default and optional department override; default 75%, fully configurable.';
COMMENT ON COLUMN app.attendance_entry.status IS 'Canonical V4.0 values: PRESENT, ABSENT, ON_APPROVED_LEAVE, NOT_HELD.';
COMMIT;