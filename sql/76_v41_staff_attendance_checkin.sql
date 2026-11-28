BEGIN;

-- V4.1 — Staff self-attendance check-in (selfie + liveness + geofencing, provisional approval).
-- Sub-task 1 of 4 for the staff self-attendance check-in feature. SQL layer only: no backend/
-- frontend wiring in this migration. 01_schema.sql remains immutable; new app.* tables and rules
-- follow the same shape (tenant_id/institution_id composite keys, trg_touch, RLS per 03_bindings.sql).

-- Per-institution geofence and approval configuration for staff self-check-in.
CREATE TABLE IF NOT EXISTS app.institution_attendance_location (
    tenant_id uuid NOT NULL,
    institution_attendance_location_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    latitude numeric(9,6) NOT NULL,
    longitude numeric(9,6) NOT NULL,
    radius_meters integer NOT NULL DEFAULT 200,
    gps_accuracy_threshold_meters integer NOT NULL DEFAULT 50,
    timezone text NOT NULL DEFAULT 'Asia/Kolkata',
    retention_days integer NOT NULL DEFAULT 180,
    approver_for_principal_role text,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_institution_attendance_location PRIMARY KEY (tenant_id, institution_attendance_location_id),
    CONSTRAINT ck_inst_attendance_loc_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_inst_attendance_loc_lng CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_inst_attendance_loc_radius CHECK (radius_meters > 0),
    CONSTRAINT ck_inst_attendance_loc_accuracy CHECK (gps_accuracy_threshold_meters > 0),
    CONSTRAINT ck_inst_attendance_loc_retention CHECK (retention_days > 0),
    CONSTRAINT ck_inst_attendance_loc_row_version CHECK (row_version >= 1),
    CONSTRAINT ck_inst_attendance_loc_upd CHECK (updated_at >= created_at),
    CONSTRAINT uq_inst_attendance_loc_tid UNIQUE (tenant_id, institution_attendance_location_id),
    CONSTRAINT uq_inst_attendance_loc_institution UNIQUE (tenant_id, institution_id)
);
DO $fk$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.institution_attendance_location'::regclass AND conname = 'fk_inst_attendance_loc_tenant_id') THEN
        ALTER TABLE app.institution_attendance_location
            ADD CONSTRAINT fk_inst_attendance_loc_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.institution_attendance_location'::regclass AND conname = 'fk_inst_attendance_loc_institution_id') THEN
        ALTER TABLE app.institution_attendance_location
            ADD CONSTRAINT fk_inst_attendance_loc_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
    END IF;
END $fk$;

-- Staff reference (enrolment) photo with recorded consent, used as the liveness baseline.
CREATE TABLE IF NOT EXISTS app.staff_reference_photo (
    tenant_id uuid NOT NULL,
    staff_reference_photo_id uuid NOT NULL,
    staff_user_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    photo_file_id uuid NOT NULL,
    uploaded_at timestamptz NOT NULL DEFAULT now(),
    consent_given_at timestamptz NOT NULL,
    consent_text_version text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_staff_reference_photo PRIMARY KEY (tenant_id, staff_reference_photo_id),
    CONSTRAINT ck_staff_reference_photo_consent_version CHECK (char_length(consent_text_version) BETWEEN 1 AND 30),
    CONSTRAINT ck_staff_reference_photo_row_version CHECK (row_version >= 1),
    CONSTRAINT ck_staff_reference_photo_upd CHECK (updated_at >= created_at),
    CONSTRAINT uq_staff_reference_photo_tid UNIQUE (tenant_id, staff_reference_photo_id)
);
DO $fk$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.staff_reference_photo'::regclass AND conname = 'fk_staff_reference_photo_tenant_id') THEN
        ALTER TABLE app.staff_reference_photo
            ADD CONSTRAINT fk_staff_reference_photo_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.staff_reference_photo'::regclass AND conname = 'fk_staff_reference_photo_staff_user_id') THEN
        ALTER TABLE app.staff_reference_photo
            ADD CONSTRAINT fk_staff_reference_photo_staff_user_id FOREIGN KEY (tenant_id, staff_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.staff_reference_photo'::regclass AND conname = 'fk_staff_reference_photo_institution_id') THEN
        ALTER TABLE app.staff_reference_photo
            ADD CONSTRAINT fk_staff_reference_photo_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.staff_reference_photo'::regclass AND conname = 'fk_staff_reference_photo_file_id') THEN
        ALTER TABLE app.staff_reference_photo
            ADD CONSTRAINT fk_staff_reference_photo_file_id FOREIGN KEY (tenant_id, photo_file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
    END IF;
END $fk$;

-- One self-check-in capture event. Capture facts (photo/geo/liveness) are immutable once written;
-- only the review/approval columns may change afterwards (see fn_checkin_capture_guard below).
CREATE TABLE IF NOT EXISTS app.staff_attendance_checkin (
    tenant_id uuid NOT NULL,
    staff_attendance_checkin_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    staff_user_id uuid NOT NULL,
    photo_file_id uuid NOT NULL,
    captured_at timestamptz NOT NULL DEFAULT now(),
    attendance_date date NOT NULL,
    latitude numeric(9,6) NOT NULL,
    longitude numeric(9,6) NOT NULL,
    gps_accuracy_meters numeric(7,2) NOT NULL,
    distance_from_institution_m numeric(10,2) NOT NULL,
    device_type text,
    liveness_confidence numeric(5,4) NOT NULL,
    status text NOT NULL DEFAULT 'PROVISIONAL',
    approval_request_id uuid,
    reviewed_at timestamptz,
    reviewed_by uuid,
    review_note text,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_staff_attendance_checkin PRIMARY KEY (tenant_id, staff_attendance_checkin_id),
    CONSTRAINT ck_staff_attendance_checkin_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_staff_attendance_checkin_lng CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_staff_attendance_checkin_accuracy CHECK (gps_accuracy_meters >= 0),
    CONSTRAINT ck_staff_attendance_checkin_distance CHECK (distance_from_institution_m >= 0),
    CONSTRAINT ck_staff_attendance_checkin_liveness CHECK (liveness_confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_staff_attendance_checkin_status CHECK (status IN ('PRESENT', 'PROVISIONAL', 'CHECK_IN_ONLY', 'REJECTED')),
    CONSTRAINT ck_staff_attendance_checkin_r1 CHECK (status <> 'REJECTED' OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CONSTRAINT ck_staff_attendance_checkin_row_version CHECK (row_version >= 1),
    CONSTRAINT ck_staff_attendance_checkin_upd CHECK (updated_at >= created_at),
    CONSTRAINT uq_staff_attendance_checkin_tid UNIQUE (tenant_id, staff_attendance_checkin_id)
);
DO $fk$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.staff_attendance_checkin'::regclass AND conname = 'fk_staff_attendance_checkin_tenant_id') THEN
        ALTER TABLE app.staff_attendance_checkin
            ADD CONSTRAINT fk_staff_attendance_checkin_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.staff_attendance_checkin'::regclass AND conname = 'fk_staff_attendance_checkin_institution_id') THEN
        ALTER TABLE app.staff_attendance_checkin
            ADD CONSTRAINT fk_staff_attendance_checkin_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.staff_attendance_checkin'::regclass AND conname = 'fk_staff_attendance_checkin_staff_user_id') THEN
        ALTER TABLE app.staff_attendance_checkin
            ADD CONSTRAINT fk_staff_attendance_checkin_staff_user_id FOREIGN KEY (tenant_id, staff_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.staff_attendance_checkin'::regclass AND conname = 'fk_staff_attendance_checkin_file_id') THEN
        ALTER TABLE app.staff_attendance_checkin
            ADD CONSTRAINT fk_staff_attendance_checkin_file_id FOREIGN KEY (tenant_id, photo_file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.staff_attendance_checkin'::regclass AND conname = 'fk_staff_attendance_checkin_approval_request_id') THEN
        ALTER TABLE app.staff_attendance_checkin
            ADD CONSTRAINT fk_staff_attendance_checkin_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.staff_attendance_checkin'::regclass AND conname = 'fk_staff_attendance_checkin_reviewed_by') THEN
        ALTER TABLE app.staff_attendance_checkin
            ADD CONSTRAINT fk_staff_attendance_checkin_reviewed_by FOREIGN KEY (tenant_id, reviewed_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
    END IF;
END $fk$;

-- One check-in per staff member per institution-local day. Any existing row for that date (any
-- status, including REJECTED) blocks another; hard-blocked attempts (geofence / GPS accuracy) never
-- write a row, so they do not use up the day. The service checks first and maps a race on this
-- index to 409; the index is the hard guard. It also covers fk_staff_attendance_checkin_staff_user_id.
CREATE UNIQUE INDEX IF NOT EXISTS ux_staff_attendance_checkin_staff_day ON app.staff_attendance_checkin (tenant_id, staff_user_id, attendance_date);
CREATE INDEX IF NOT EXISTS ix_staff_attendance_checkin_institution ON app.staff_attendance_checkin (tenant_id, institution_id);
CREATE INDEX IF NOT EXISTS ix_staff_attendance_checkin_status ON app.staff_attendance_checkin (tenant_id, status);
CREATE INDEX IF NOT EXISTS ix_staff_attendance_checkin_captured_at ON app.staff_attendance_checkin (tenant_id, captured_at);
CREATE INDEX IF NOT EXISTS ix_staff_reference_photo_staff ON app.staff_reference_photo (tenant_id, staff_user_id);

-- Capture facts are write-once: photo, geo, liveness and who/what captured it never change after
-- insert. Only status, the linked approval request and the review columns may be updated, mirroring
-- the audit-row pattern in app.fn_audit_guard (02_rules.sql).
CREATE OR REPLACE FUNCTION app.fn_checkin_capture_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'CHECKIN_IMMUTABLE: delete refused' USING ERRCODE = '42501';
  END IF;
  IF (to_jsonb(NEW) - 'status' - 'approval_request_id' - 'reviewed_at' - 'reviewed_by' - 'review_note' - 'updated_at' - 'updated_by' - 'row_version')
     <> (to_jsonb(OLD) - 'status' - 'approval_request_id' - 'reviewed_at' - 'reviewed_by' - 'review_note' - 'updated_at' - 'updated_by' - 'row_version') THEN
    RAISE EXCEPTION 'CHECKIN_IMMUTABLE: capture columns cannot change after insert' USING ERRCODE = '42501';
  END IF;
  RETURN NEW;
END$$;

DROP TRIGGER IF EXISTS trg_touch ON app.institution_attendance_location;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.institution_attendance_location FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
ALTER TABLE app.institution_attendance_location ENABLE ROW LEVEL SECURITY; ALTER TABLE app.institution_attendance_location FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_tenant ON app.institution_attendance_location;
CREATE POLICY p_tenant ON app.institution_attendance_location USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
DROP POLICY IF EXISTS p_institution ON app.institution_attendance_location;
CREATE POLICY p_institution ON app.institution_attendance_location AS RESTRICTIVE USING (institution_id IS NULL OR current_setting('app.institution_scope', true) = 'ALL' OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[])) WITH CHECK (institution_id IS NULL OR current_setting('app.institution_scope', true) = 'ALL' OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]));
GRANT SELECT, INSERT, UPDATE, DELETE ON app.institution_attendance_location TO edu_app;
GRANT SELECT ON app.institution_attendance_location TO edu_report;

DROP TRIGGER IF EXISTS trg_touch ON app.staff_reference_photo;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.staff_reference_photo FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
ALTER TABLE app.staff_reference_photo ENABLE ROW LEVEL SECURITY; ALTER TABLE app.staff_reference_photo FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_tenant ON app.staff_reference_photo;
CREATE POLICY p_tenant ON app.staff_reference_photo USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
DROP POLICY IF EXISTS p_institution ON app.staff_reference_photo;
CREATE POLICY p_institution ON app.staff_reference_photo AS RESTRICTIVE USING (institution_id IS NULL OR current_setting('app.institution_scope', true) = 'ALL' OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[])) WITH CHECK (institution_id IS NULL OR current_setting('app.institution_scope', true) = 'ALL' OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]));
GRANT SELECT, INSERT, UPDATE, DELETE ON app.staff_reference_photo TO edu_app;
GRANT SELECT ON app.staff_reference_photo TO edu_report;

DROP TRIGGER IF EXISTS trg_touch ON app.staff_attendance_checkin;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.staff_attendance_checkin FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
DROP TRIGGER IF EXISTS trg_capture_guard ON app.staff_attendance_checkin;
CREATE TRIGGER trg_capture_guard BEFORE UPDATE OR DELETE ON app.staff_attendance_checkin FOR EACH ROW EXECUTE FUNCTION app.fn_checkin_capture_guard();
ALTER TABLE app.staff_attendance_checkin ENABLE ROW LEVEL SECURITY; ALTER TABLE app.staff_attendance_checkin FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_tenant ON app.staff_attendance_checkin;
CREATE POLICY p_tenant ON app.staff_attendance_checkin USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
DROP POLICY IF EXISTS p_institution ON app.staff_attendance_checkin;
CREATE POLICY p_institution ON app.staff_attendance_checkin AS RESTRICTIVE USING (institution_id IS NULL OR current_setting('app.institution_scope', true) = 'ALL' OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[])) WITH CHECK (institution_id IS NULL OR current_setting('app.institution_scope', true) = 'ALL' OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]));
GRANT SELECT, INSERT, UPDATE, DELETE ON app.staff_attendance_checkin TO edu_app;
GRANT SELECT ON app.staff_attendance_checkin TO edu_report;

COMMENT ON TABLE app.institution_attendance_location IS 'Per-institution geofence centre, radius and GPS accuracy threshold, plus retention and the configurable approver role (approver_for_principal_role) for staff self-check-in.';
COMMENT ON TABLE app.staff_reference_photo IS 'Consented enrolment photo used as the liveness-match baseline for staff self-check-in.';
COMMENT ON TABLE app.staff_attendance_checkin IS 'One staff self-attendance capture event (selfie + liveness + geofencing). Capture columns are immutable after insert (fn_checkin_capture_guard); only status/approval/review columns may change.';
COMMENT ON COLUMN app.staff_attendance_checkin.status IS 'PRESENT = inside geofence and passed liveness; PROVISIONAL = pending approver review (e.g. outside geofence or low confidence); CHECK_IN_ONLY = liveness/geo not conclusive, recorded without an attendance effect; REJECTED = reviewer denied.';
COMMENT ON COLUMN app.staff_attendance_checkin.approval_request_id IS 'Set when status = PROVISIONAL and a review/approval workflow row (app.approval_request) is raised.';
COMMENT ON COLUMN app.staff_attendance_checkin.device_type IS 'Informational only (e.g. ANDROID, IOS, WEB); never used for a security decision.';
COMMENT ON COLUMN app.staff_attendance_checkin.attendance_date IS 'Institution-local calendar date of captured_at (institution_attendance_location.timezone), computed by the service. Capture column: write-once (fn_checkin_capture_guard). Unique per (tenant_id, staff_user_id).';

-- Selfie file purpose. ck_file_object_purpose is rebuilt by several migrations (63, 66, 72), so
-- this adds STAFF_ATTENDANCE_SELFIE to whatever list is current instead of restating it; 76r
-- removes exactly that value again. Guarded: a second apply is a no-op.
DO $purpose$
DECLARE v_def text;
BEGIN
    SELECT pg_get_constraintdef(oid) INTO v_def FROM pg_constraint
     WHERE conrelid = 'app.file_object'::regclass AND conname = 'ck_file_object_purpose';
    IF v_def IS NULL OR position('ARRAY[' IN v_def) = 0 THEN
        RAISE EXCEPTION 'CK_FILE_OBJECT_PURPOSE_UNEXPECTED: %', coalesce(v_def, '<missing>');
    END IF;
    IF position('''STAFF_ATTENDANCE_SELFIE''' IN v_def) = 0 THEN
        ALTER TABLE app.file_object DROP CONSTRAINT ck_file_object_purpose;
        EXECUTE 'ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_purpose '
            || replace(v_def, 'ARRAY[', 'ARRAY[''STAFF_ATTENDANCE_SELFIE''::text, ');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.file_object'::regclass AND conname = 'ck_file_object_staff_attendance_selfie_type') THEN
        ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_staff_attendance_selfie_type
            CHECK (purpose <> 'STAFF_ATTENDANCE_SELFIE' OR (content_type IN ('image/jpeg', 'image/png') AND size_bytes <= 5242880));
    END IF;
END $purpose$;

-- Staff self-attendance check-in is a tenant-scoped capability. Managing the geofence config is
-- limited to the Principals; submitting is open to every staff role; reviewing is limited to the
-- roles the router (StaffCheckinApproverResolver) can send a check-in to.
INSERT INTO ref.action_catalog (action_code, action_no, module_name, phase, description, is_approval_action, sensitive_class, changed_in) VALUES (
    'STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE', 291, 'Attendance', 1,
    'Configure the geofence, accuracy threshold and approver for staff self-attendance check-in',
    false, NULL, 'V4.1'
) ON CONFLICT (action_code) DO NOTHING;
INSERT INTO ref.action_catalog (action_code, action_no, module_name, phase, description, is_approval_action, sensitive_class, changed_in) VALUES (
    'STAFF_ATTENDANCE_CHECKIN_SUBMIT', 292, 'Attendance', 1,
    'Submit a selfie-and-location self-attendance check-in',
    false, 'Personal – Create / edit (own)', 'V4.1'
) ON CONFLICT (action_code) DO NOTHING;
INSERT INTO ref.action_catalog (action_code, action_no, module_name, phase, description, is_approval_action, sensitive_class, changed_in) VALUES (
    'STAFF_ATTENDANCE_CHECKIN_REVIEW', 293, 'Attendance', 1,
    'Review and decide a provisional staff self-attendance check-in',
    true, NULL, 'V4.1'
) ON CONFLICT (action_code) DO NOTHING;

-- Role grants, same shape as 72_v41_disciplinary_and_grievance_guard.sql: template rows numbered
-- after the current maximum (no hard-coded ids to collide with other migrations), only for roles
-- that exist in ref.standard_role, idempotent on (role_code, action_code).
--   SUBMIT: every staff role (ref.standard_role.is_staff, tenant side). Students, parents,
--           applicants, MANAGEMENT / GOVERNING_BODY_APPROVER (governance accounts, is_staff=false)
--           and platform roles are excluded.
--   REVIEW: both Principals (default approver), HEAD_OF_DEPARTMENT (first approver for their
--           department's staff) and the higher authorities a tenant can set as
--           approver_for_principal_role / the day-end escalation target: MANAGEMENT, and
--           GOVERNING_BODY_APPROVER as the trustee alternate (same pairing as PO-T04 in 70).
-- None of these actions is in a never-allowed class (20_v41_never_allowed_guard.sql); the
-- app.role_action backfill below still runs through trg_never_allowed, which would refuse one.
WITH pairs(role_code, action_code, limited_to) AS (
    VALUES
      ('PRINCIPAL_COLLEGE', 'STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE', 'Own institution'),
      ('SCHOOL_PRINCIPAL', 'STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE', 'Own institution'),
      ('PRINCIPAL_COLLEGE', 'STAFF_ATTENDANCE_CHECKIN_REVIEW', 'Own institution; never own check-in'),
      ('SCHOOL_PRINCIPAL', 'STAFF_ATTENDANCE_CHECKIN_REVIEW', 'Own institution; never own check-in'),
      ('HEAD_OF_DEPARTMENT', 'STAFF_ATTENDANCE_CHECKIN_REVIEW', 'Own department staff, when routed as the approver; never own check-in'),
      ('MANAGEMENT', 'STAFF_ATTENDANCE_CHECKIN_REVIEW', 'Own tenant; Principal check-ins when set as approver_for_principal_role, and day-end escalations'),
      ('GOVERNING_BODY_APPROVER', 'STAFF_ATTENDANCE_CHECKIN_REVIEW', 'Own tenant; Principal check-ins when set as approver_for_principal_role, and day-end escalations')
), submitters AS (
    SELECT role_code, 'STAFF_ATTENDANCE_CHECKIN_SUBMIT'::text AS action_code,
           'Own check-in only, at own assigned institution; one per institution-local day'::text AS limited_to
      FROM ref.standard_role
     WHERE is_staff AND role_level IN ('TENANT', 'UNIVERSITY', 'COLLEGE', 'SCHOOL')
), all_pairs AS (
    SELECT * FROM pairs UNION ALL SELECT * FROM submitters
), missing AS (
    SELECT p.* FROM all_pairs p
      JOIN ref.standard_role r ON r.role_code = p.role_code
     WHERE NOT EXISTS (
        SELECT 1 FROM ref.standard_role_action x
         WHERE x.role_code = p.role_code AND x.action_code = p.action_code
     )
), numbered AS (
    SELECT COALESCE((SELECT max(standard_role_action_id) FROM ref.standard_role_action), 0)
           + row_number() OVER (ORDER BY role_code, action_code) AS id,
           role_code, action_code, limited_to
      FROM missing
)
INSERT INTO ref.standard_role_action (standard_role_action_id, role_code, action_code, limited_to)
SELECT id, role_code, action_code, limited_to FROM numbered
ON CONFLICT (standard_role_action_id) DO NOTHING;

-- Bring already-provisioned STANDARD tenant role definitions up to date (pattern of 72). New
-- tenants copy the same template when their roles are provisioned.
INSERT INTO app.role_action (tenant_id, role_action_id, role_definition_id, action_code, created_by, updated_by)
SELECT rd.tenant_id, gen_random_uuid(), rd.role_definition_id, sra.action_code, rd.created_by, rd.created_by
  FROM app.role_definition rd
  JOIN ref.standard_role_action sra ON sra.role_code = rd.base_standard_role
 WHERE rd.role_origin = 'STANDARD'
   AND sra.action_code IN ('STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE', 'STAFF_ATTENDANCE_CHECKIN_SUBMIT', 'STAFF_ATTENDANCE_CHECKIN_REVIEW')
   AND NOT EXISTS (
        SELECT 1 FROM app.role_action existing
         WHERE existing.tenant_id = rd.tenant_id
           AND existing.role_definition_id = rd.role_definition_id
           AND existing.action_code = sra.action_code
   );

-- The approval process a PROVISIONAL check-in raises (app.approval_request.process_code has
-- fk_approval_request_process_code, so every PROVISIONAL submit needs this row). The route is
-- decided at runtime by StaffCheckinApproverResolver and the action by
-- ApprovalInboxService.resolveApprovalAction (STAFF_ATTENDANCE_CHECKIN_REVIEW); the chain steps
-- below document that route the same way 05_ref_data.sql documents every other chain.
-- allows_bulk = false: each decision needs the reviewer to look at that selfie.
INSERT INTO ref.approval_process (process_code, process_name, subject_type, allows_bulk, allows_delegation, source_ref)
VALUES ('STAFF_ATTENDANCE_REVIEW', 'Staff self-attendance check-in review', 'STAFF_ATTENDANCE_CHECKIN', false, true,
        'V4.1 staff self-check-in; D-002; V4-PD-01')
ON CONFLICT (process_code) DO NOTHING;
WITH steps(step_no, approver_rule) AS (
    VALUES
      (1::smallint, 'Requester''s HOD (named); Principal when the requester is an HOD or has no department; set approver (approver_for_principal_role) when the requester is a Principal, else BLOCKED (D-002)'),
      (2::smallint, 'Not reviewed by institution day end: the record becomes CHECK_IN_ONLY and escalates - HOD to Principal, Principal to the set approver')
), missing AS (
    SELECT s.* FROM steps s
     WHERE NOT EXISTS (SELECT 1 FROM ref.approval_chain_step c WHERE c.process_code = 'STAFF_ATTENDANCE_REVIEW' AND c.step_no = s.step_no)
), numbered AS (
    SELECT COALESCE((SELECT max(approval_chain_step_id) FROM ref.approval_chain_step), 0) + row_number() OVER (ORDER BY step_no) AS id,
           step_no, approver_rule
      FROM missing
)
INSERT INTO ref.approval_chain_step (approval_chain_step_id, process_code, step_no, approver_rule, action_code,
                                     is_optional, final_when_days_lte, skip_if_role_absent, forward_only)
SELECT id, 'STAFF_ATTENDANCE_REVIEW', step_no, approver_rule, 'STAFF_ATTENDANCE_CHECKIN_REVIEW', false, NULL, false, false
  FROM numbered
ON CONFLICT DO NOTHING;

COMMIT;
