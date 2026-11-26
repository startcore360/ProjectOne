BEGIN;
DROP VIEW IF EXISTS app.v_attendance_percentage CASCADE;
DROP FUNCTION IF EXISTS app.fn_attendance_exam_eligibility(uuid,uuid,uuid);
DROP TRIGGER IF EXISTS trg_attendance_session_scope_guard ON app.attendance_session;
DROP TRIGGER IF EXISTS trg_check_term_lock_attendance ON app.attendance_session;
DROP TRIGGER IF EXISTS trg_attendance_entry_scope_guard ON app.attendance_entry;
DROP TRIGGER IF EXISTS trg_check_term_lock_attendance_entry_delete ON app.attendance_entry;
DROP TRIGGER IF EXISTS trg_check_term_lock_attendance_entry ON app.attendance_entry;
DROP TRIGGER IF EXISTS trg_medical_exemption_guard ON app.attendance_medical_exemption;
DROP TRIGGER IF EXISTS trg_check_term_lock_medical_exemption ON app.attendance_medical_exemption;
DROP TRIGGER IF EXISTS trg_seed_attendance_threshold_default ON app.institution;
DROP TRIGGER IF EXISTS trg_attendance_threshold_department_guard ON app.attendance_threshold_policy;
DROP TRIGGER IF EXISTS trg_touch ON app.attendance_medical_exemption;
DROP TRIGGER IF EXISTS trg_touch ON app.attendance_threshold_policy;
DROP TABLE IF EXISTS app.attendance_threshold_policy CASCADE;
DROP TABLE IF EXISTS app.attendance_medical_exemption CASCADE;
DROP FUNCTION IF EXISTS app.fn_attendance_session_scope_guard();
DROP FUNCTION IF EXISTS app.fn_attendance_entry_scope_guard();
DROP FUNCTION IF EXISTS app.fn_attendance_term_lock_guard();
DROP FUNCTION IF EXISTS app.fn_attendance_scope_is_term_locked(uuid,uuid,uuid,uuid);
DROP FUNCTION IF EXISTS app.is_term_locked(uuid,uuid,uuid,uuid);
DROP FUNCTION IF EXISTS app.fn_medical_exemption_guard();
DROP FUNCTION IF EXISTS app.fn_seed_attendance_threshold_default();
DROP FUNCTION IF EXISTS app.fn_attendance_threshold_department_guard();
ALTER TABLE app.attendance_entry DROP CONSTRAINT IF EXISTS uq_attendance_entry_session_enrollment;
ALTER TABLE app.file_object DROP CONSTRAINT IF EXISTS ck_file_object_medical_exemption_type;
ALTER TABLE app.attendance_session DROP CONSTRAINT IF EXISTS ck_attendance_session_session_kind;
ALTER TABLE app.attendance_session ADD CONSTRAINT ck_attendance_session_session_kind CHECK (session_kind IN ('PERIOD','SCHOOL_FIRST_HALF','SCHOOL_SECOND_HALF'));
ALTER TABLE app.file_object DROP CONSTRAINT IF EXISTS ck_file_object_purpose;
ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_purpose CHECK (purpose IN (
 'MESSAGE_ATTACHMENT','ASSIGNMENT_SUBMISSION','STUDY_MATERIAL','STAFF_DOCUMENT','POLICY','DEPARTMENT_DOCUMENT',
 'ACCREDITATION','COMPLIANCE','LEAVE_DOCUMENT','HALL_TICKET','COMMUNITY_MEDIA','IMPORT_SOURCE','EXPORT',
 'PROFILE_PHOTO','BRANDING','ATTENDANCE_UPLOAD','MARKS_SOURCE','SYLLABUS','CERTIFICATE','CONSENT_FORM','SERVICE_RECORD'
));
COMMIT;