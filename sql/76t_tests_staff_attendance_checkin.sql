BEGIN;

-- Self-contained fixtures: tenant A and tenant B, one institution and one staff user each, plus a
-- file_object row to satisfy the photo FK. Everything here is rolled back at the end of the script.
INSERT INTO app.tenant_local (tenant_id, tenant_type) VALUES
    ('c6000000-0000-0000-0000-00000000a000', 'STANDALONE_COLLEGE'),
    ('c6000000-0000-0000-0000-00000000b000', 'STANDALONE_COLLEGE');

INSERT INTO app.institution (tenant_id, institution_id, institution_type, institution_code, institution_name, created_by, updated_by) VALUES
    ('c6000000-0000-0000-0000-00000000a000', 'c6000000-0000-0000-0000-00000000a201', 'COLLEGE', 'C6A', 'Checkin College A', '00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000aa'),
    ('c6000000-0000-0000-0000-00000000b000', 'c6000000-0000-0000-0000-00000000b201', 'COLLEGE', 'C6B', 'Checkin College B', '00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000aa');

INSERT INTO app.user_account (tenant_id, user_account_id, user_type, display_name, created_by, updated_by) VALUES
    ('c6000000-0000-0000-0000-00000000a000', 'c6000000-0000-0000-0000-00000000a101', 'STAFF', 'Checkin Staff A', '00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000aa'),
    ('c6000000-0000-0000-0000-00000000b000', 'c6000000-0000-0000-0000-00000000b101', 'STAFF', 'Checkin Staff B', '00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000aa');

-- The fixture selfies use the new STAFF_ATTENDANCE_SELFIE purpose: inserting them proves
-- ck_file_object_purpose accepts it.
INSERT INTO app.file_object (tenant_id, file_id, institution_id, purpose, storage_key, original_filename, content_type, size_bytes, sha256, uploaded_by, created_by, updated_by) VALUES
    ('c6000000-0000-0000-0000-00000000a000', 'c6000000-0000-0000-0000-00000000a301', 'c6000000-0000-0000-0000-00000000a201', 'STAFF_ATTENDANCE_SELFIE', 'checkin/a301.jpg', 'a301.jpg', 'image/jpeg', 1024, repeat('0', 64), 'c6000000-0000-0000-0000-00000000a101', '00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000aa'),
    ('c6000000-0000-0000-0000-00000000a000', 'c6000000-0000-0000-0000-00000000a302', 'c6000000-0000-0000-0000-00000000a201', 'STAFF_ATTENDANCE_SELFIE', 'checkin/a302.png', 'a302.png', 'image/png', 1024, repeat('0', 64), 'c6000000-0000-0000-0000-00000000a101', '00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000aa'),
    ('c6000000-0000-0000-0000-00000000b000', 'c6000000-0000-0000-0000-00000000b301', 'c6000000-0000-0000-0000-00000000b201', 'STAFF_ATTENDANCE_SELFIE', 'checkin/b301.jpg', 'b301.jpg', 'image/jpeg', 1024, repeat('0', 64), 'c6000000-0000-0000-0000-00000000b101', '00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000aa');

INSERT INTO app.staff_attendance_checkin (
    tenant_id, staff_attendance_checkin_id, institution_id, staff_user_id, photo_file_id, attendance_date,
    latitude, longitude, gps_accuracy_meters, distance_from_institution_m, liveness_confidence,
    status, created_by, updated_by
) VALUES
    ('c6000000-0000-0000-0000-00000000a000', 'c6000000-0000-0000-0000-00000000a401', 'c6000000-0000-0000-0000-00000000a201', 'c6000000-0000-0000-0000-00000000a101', 'c6000000-0000-0000-0000-00000000a301', DATE '2026-11-02',
     12.971600, 77.594600, 12.5, 30.0, 0.9800, 'PRESENT', 'c6000000-0000-0000-0000-00000000a101', 'c6000000-0000-0000-0000-00000000a101'),
    ('c6000000-0000-0000-0000-00000000b000', 'c6000000-0000-0000-0000-00000000b401', 'c6000000-0000-0000-0000-00000000b201', 'c6000000-0000-0000-0000-00000000b101', 'c6000000-0000-0000-0000-00000000b301', DATE '2026-11-02',
     12.971600, 77.594600, 12.5, 30.0, 0.9800, 'PRESENT', 'c6000000-0000-0000-0000-00000000b101', 'c6000000-0000-0000-0000-00000000b101');

-- ---------------------------------------------------------------- reference data (migration 76)
DO $$
DECLARE v_banned integer;
BEGIN
    -- The approval process a PROVISIONAL check-in raises (fk_approval_request_process_code).
    IF NOT EXISTS (SELECT 1 FROM ref.approval_process
                    WHERE process_code = 'STAFF_ATTENDANCE_REVIEW' AND subject_type = 'STAFF_ATTENDANCE_CHECKIN') THEN
        RAISE EXCEPTION 'APPROVAL_PROCESS_GAP: ref.approval_process STAFF_ATTENDANCE_REVIEW / STAFF_ATTENDANCE_CHECKIN missing';
    END IF;
    IF (SELECT count(*) FROM ref.approval_chain_step
         WHERE process_code = 'STAFF_ATTENDANCE_REVIEW' AND action_code = 'STAFF_ATTENDANCE_CHECKIN_REVIEW') <> 2
       OR EXISTS (SELECT 1 FROM ref.approval_chain_step
                   WHERE process_code = 'STAFF_ATTENDANCE_REVIEW' AND action_code <> 'STAFF_ATTENDANCE_CHECKIN_REVIEW') THEN
        RAISE EXCEPTION 'APPROVAL_CHAIN_GAP: STAFF_ATTENDANCE_REVIEW must have exactly 2 steps, both on STAFF_ATTENDANCE_CHECKIN_REVIEW';
    END IF;

    -- SUBMIT: every tenant-side staff role present in the catalogue, and nobody else.
    IF EXISTS (SELECT 1 FROM ref.standard_role r
                WHERE r.is_staff AND r.role_level IN ('TENANT', 'UNIVERSITY', 'COLLEGE', 'SCHOOL')
                  AND NOT EXISTS (SELECT 1 FROM ref.standard_role_action a
                                   WHERE a.role_code = r.role_code AND a.action_code = 'STAFF_ATTENDANCE_CHECKIN_SUBMIT')) THEN
        RAISE EXCEPTION 'GRANT_GAP: a staff role lacks STAFF_ATTENDANCE_CHECKIN_SUBMIT';
    END IF;
    IF EXISTS (SELECT 1 FROM ref.standard_role_action a JOIN ref.standard_role r USING (role_code)
                WHERE a.action_code = 'STAFF_ATTENDANCE_CHECKIN_SUBMIT'
                  AND (NOT r.is_staff OR r.role_level NOT IN ('TENANT', 'UNIVERSITY', 'COLLEGE', 'SCHOOL'))) THEN
        RAISE EXCEPTION 'GRANT_LEAK: a student / parent / applicant / governance / platform role holds STAFF_ATTENDANCE_CHECKIN_SUBMIT';
    END IF;

    -- REVIEW: exactly the routable approvers (those present in the catalogue).
    IF EXISTS (SELECT 1 FROM ref.standard_role r
                WHERE r.role_code IN ('PRINCIPAL_COLLEGE', 'SCHOOL_PRINCIPAL', 'HEAD_OF_DEPARTMENT', 'MANAGEMENT', 'GOVERNING_BODY_APPROVER')
                  AND NOT EXISTS (SELECT 1 FROM ref.standard_role_action a
                                   WHERE a.role_code = r.role_code AND a.action_code = 'STAFF_ATTENDANCE_CHECKIN_REVIEW')) THEN
        RAISE EXCEPTION 'GRANT_GAP: a routable approver role lacks STAFF_ATTENDANCE_CHECKIN_REVIEW';
    END IF;
    IF EXISTS (SELECT 1 FROM ref.standard_role_action
                WHERE action_code = 'STAFF_ATTENDANCE_CHECKIN_REVIEW'
                  AND role_code NOT IN ('PRINCIPAL_COLLEGE', 'SCHOOL_PRINCIPAL', 'HEAD_OF_DEPARTMENT', 'MANAGEMENT', 'GOVERNING_BODY_APPROVER')) THEN
        RAISE EXCEPTION 'GRANT_LEAK: STAFF_ATTENDANCE_CHECKIN_REVIEW granted outside the routable approvers';
    END IF;
    IF EXISTS (SELECT 1 FROM ref.standard_role_action
                WHERE action_code = 'STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE'
                  AND role_code NOT IN ('PRINCIPAL_COLLEGE', 'SCHOOL_PRINCIPAL')) THEN
        RAISE EXCEPTION 'GRANT_LEAK: STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE granted outside the Principals';
    END IF;

    -- Backfill: no already-provisioned STANDARD role definition is missing a templated grant.
    IF EXISTS (SELECT 1 FROM app.role_definition rd
                 JOIN ref.standard_role_action sra ON sra.role_code = rd.base_standard_role
                WHERE rd.role_origin = 'STANDARD'
                  AND sra.action_code IN ('STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE', 'STAFF_ATTENDANCE_CHECKIN_SUBMIT', 'STAFF_ATTENDANCE_CHECKIN_REVIEW')
                  AND NOT EXISTS (SELECT 1 FROM app.role_action ra
                                   WHERE ra.tenant_id = rd.tenant_id AND ra.role_definition_id = rd.role_definition_id
                                     AND ra.action_code = sra.action_code)) THEN
        RAISE EXCEPTION 'BACKFILL_GAP: a STANDARD tenant role definition lacks a staff check-in grant';
    END IF;

    -- Never-allowed guard (20): none of the three actions may be in a banned class. Checked
    -- dynamically because this file also runs in a database where 20 is applied later.
    IF to_regprocedure('app.fn_never_allowed_class(text)') IS NOT NULL THEN
        EXECUTE $q$SELECT count(*)::int FROM ref.action_catalog
                    WHERE action_code IN ('STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE', 'STAFF_ATTENDANCE_CHECKIN_SUBMIT', 'STAFF_ATTENDANCE_CHECKIN_REVIEW')
                      AND app.fn_never_allowed_class(action_code) IS NOT NULL$q$ INTO v_banned;
        IF v_banned > 0 THEN
            RAISE EXCEPTION 'NEVER_ALLOWED_GAP: a staff check-in action is in a never-allowed class';
        END IF;
    END IF;
END $$;

-- A PROVISIONAL check-in's approval request satisfies fk_approval_request_process_code.
INSERT INTO app.approval_request (tenant_id, approval_request_id, institution_id, process_code, subject_type, subject_id,
                                  requested_by_user_id, requester_active_role, status, created_by, updated_by)
VALUES ('c6000000-0000-0000-0000-00000000a000', 'c6000000-0000-0000-0000-00000000a601', 'c6000000-0000-0000-0000-00000000a201',
        'STAFF_ATTENDANCE_REVIEW', 'STAFF_ATTENDANCE_CHECKIN', 'c6000000-0000-0000-0000-00000000a401',
        'c6000000-0000-0000-0000-00000000a101', 'ASSISTANT_PROFESSOR', 'PENDING',
        'c6000000-0000-0000-0000-00000000a101', 'c6000000-0000-0000-0000-00000000a101');

-- File purpose: a selfie must be JPEG/PNG (ck_file_object_staff_attendance_selfie_type).
DO $$
BEGIN
    BEGIN
        INSERT INTO app.file_object (tenant_id, file_id, institution_id, purpose, storage_key, original_filename, content_type, size_bytes, sha256, uploaded_by, created_by, updated_by)
        VALUES ('c6000000-0000-0000-0000-00000000a000', 'c6000000-0000-0000-0000-00000000a399', 'c6000000-0000-0000-0000-00000000a201', 'STAFF_ATTENDANCE_SELFIE', 'checkin/a399.pdf', 'a399.pdf', 'application/pdf', 1024, repeat('0', 64), 'c6000000-0000-0000-0000-00000000a101', '00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000aa');
        RAISE EXCEPTION 'FILE_PURPOSE_GAP: a PDF was accepted as a staff attendance selfie';
    EXCEPTION WHEN check_violation THEN
        NULL; -- expected
    END;
END $$;

-- One check-in per staff member per day: a second row for the same date is refused by the index
-- (whatever its status), a row for another date is accepted.
DO $$
BEGIN
    BEGIN
        INSERT INTO app.staff_attendance_checkin (
            tenant_id, staff_attendance_checkin_id, institution_id, staff_user_id, photo_file_id, attendance_date,
            latitude, longitude, gps_accuracy_meters, distance_from_institution_m, liveness_confidence,
            status, created_by, updated_by
        ) VALUES (
            'c6000000-0000-0000-0000-00000000a000', 'c6000000-0000-0000-0000-00000000a404', 'c6000000-0000-0000-0000-00000000a201', 'c6000000-0000-0000-0000-00000000a101', 'c6000000-0000-0000-0000-00000000a302', DATE '2026-11-02',
            12.9716, 77.5946, 12.5, 30.0, 0.30, 'PROVISIONAL', 'c6000000-0000-0000-0000-00000000a101', 'c6000000-0000-0000-0000-00000000a101'
        );
        RAISE EXCEPTION 'ONE_PER_DAY_GAP: a second check-in for the same staff member and date was accepted';
    EXCEPTION WHEN unique_violation THEN
        NULL; -- expected
    END;
END $$;

INSERT INTO app.staff_attendance_checkin (
    tenant_id, staff_attendance_checkin_id, institution_id, staff_user_id, photo_file_id, attendance_date,
    latitude, longitude, gps_accuracy_meters, distance_from_institution_m, liveness_confidence,
    status, created_by, updated_by
) VALUES (
    'c6000000-0000-0000-0000-00000000a000', 'c6000000-0000-0000-0000-00000000a405', 'c6000000-0000-0000-0000-00000000a201', 'c6000000-0000-0000-0000-00000000a101', 'c6000000-0000-0000-0000-00000000a302', DATE '2026-11-03',
    12.9716, 77.5946, 12.5, 30.0, 0.98, 'PRESENT', 'c6000000-0000-0000-0000-00000000a101', 'c6000000-0000-0000-0000-00000000a101'
);

-- attendance_date is a capture column: write-once.
DO $$
BEGIN
    BEGIN
        UPDATE app.staff_attendance_checkin SET attendance_date = DATE '2026-11-04'
         WHERE staff_attendance_checkin_id = 'c6000000-0000-0000-0000-00000000a405';
        RAISE EXCEPTION 'CHECKIN_IMMUTABLE_GAP: attendance_date was changed after insert';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE 'CHECKIN_IMMUTABLE:%' THEN
            RAISE;
        END IF;
    END;
END $$;

-- RLS: run as the application role, scoped to tenant A only.
SET ROLE edu_app;
SELECT set_config('app.tenant_id', 'c6000000-0000-0000-0000-00000000a000', false);
SELECT set_config('app.institution_scope', 'ALL', false);

DO $$
DECLARE v_count integer;
BEGIN
    SELECT count(*) INTO v_count FROM app.staff_attendance_checkin;
    IF v_count <> 2 THEN
        RAISE EXCEPTION 'RLS_TENANT_ISOLATION: expected 2 rows visible to tenant A, got %', v_count;
    END IF;

    SELECT count(*) INTO v_count FROM app.staff_attendance_checkin WHERE tenant_id = 'c6000000-0000-0000-0000-00000000b000';
    IF v_count <> 0 THEN
        RAISE EXCEPTION 'RLS_TENANT_ISOLATION: tenant A role could read a tenant B row';
    END IF;
END $$;

RESET ROLE;
SELECT set_config('app.tenant_id', '', false);

-- Fail-closed: no tenant context at all hides every row, even to the application role.
SET ROLE edu_app;
SELECT set_config('app.tenant_id', '', false);
DO $$
DECLARE v_count integer;
BEGIN
    SELECT count(*) INTO v_count FROM app.staff_attendance_checkin;
    IF v_count <> 0 THEN
        RAISE EXCEPTION 'RLS_FAIL_CLOSED: expected 0 rows with no tenant context, got %', v_count;
    END IF;
END $$;
RESET ROLE;

-- Constraint checks (as table owner, so we see the real constraint violation message). Each uses
-- its own attendance_date so the one-per-day index cannot mask the constraint under test.
DO $$
BEGIN
    BEGIN
        INSERT INTO app.staff_attendance_checkin (
            tenant_id, staff_attendance_checkin_id, institution_id, staff_user_id, photo_file_id, attendance_date,
            latitude, longitude, gps_accuracy_meters, distance_from_institution_m, liveness_confidence,
            status, created_by, updated_by
        ) VALUES (
            'c6000000-0000-0000-0000-00000000a000', 'c6000000-0000-0000-0000-00000000a402', 'c6000000-0000-0000-0000-00000000a201', 'c6000000-0000-0000-0000-00000000a101', 'c6000000-0000-0000-0000-00000000a301', DATE '2026-11-10',
            12.9716, 77.5946, 12.5, 30.0, 0.98, 'NOT_A_REAL_STATUS', 'c6000000-0000-0000-0000-00000000a101', 'c6000000-0000-0000-0000-00000000a101'
        );
        RAISE EXCEPTION 'CHECK_CONSTRAINT_GAP: an invalid status value was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL; -- expected
    END;
END $$;

DO $$
BEGIN
    BEGIN
        INSERT INTO app.staff_attendance_checkin (
            tenant_id, staff_attendance_checkin_id, institution_id, staff_user_id, photo_file_id, attendance_date,
            latitude, longitude, gps_accuracy_meters, distance_from_institution_m, liveness_confidence,
            status, created_by, updated_by
        ) VALUES (
            'c6000000-0000-0000-0000-00000000a000', 'c6000000-0000-0000-0000-00000000a403', 'c6000000-0000-0000-0000-00000000a201', 'c6000000-0000-0000-0000-00000000a101', NULL, DATE '2026-11-11',
            12.9716, 77.5946, 12.5, 30.0, 0.98, 'PRESENT', 'c6000000-0000-0000-0000-00000000a101', 'c6000000-0000-0000-0000-00000000a101'
        );
        RAISE EXCEPTION 'NOT_NULL_GAP: photo_file_id accepted a NULL value';
    EXCEPTION WHEN not_null_violation THEN
        NULL; -- expected
    END;
END $$;

-- Immutability: capture columns cannot change once written, but status/review columns can.
DO $$
BEGIN
    BEGIN
        UPDATE app.staff_attendance_checkin SET latitude = 0 WHERE staff_attendance_checkin_id = 'c6000000-0000-0000-0000-00000000a401';
        RAISE EXCEPTION 'CHECKIN_IMMUTABLE_GAP: a capture column was changed after insert';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE 'CHECKIN_IMMUTABLE%' THEN
            RAISE;
        END IF;
    END;
END $$;

UPDATE app.staff_attendance_checkin
   SET status = 'PROVISIONAL', review_note = 'needs a second look'
 WHERE staff_attendance_checkin_id = 'c6000000-0000-0000-0000-00000000a401';

DO $$
DECLARE v_status text;
BEGIN
    SELECT status INTO v_status FROM app.staff_attendance_checkin WHERE staff_attendance_checkin_id = 'c6000000-0000-0000-0000-00000000a401';
    IF v_status <> 'PROVISIONAL' THEN
        RAISE EXCEPTION 'CHECKIN_REVIEW_UPDATE_GAP: status column could not be updated after insert';
    END IF;
END $$;

-- Location config defaults and range checks.
DO $$
BEGIN
    BEGIN
        INSERT INTO app.institution_attendance_location (
            tenant_id, institution_attendance_location_id, institution_id, latitude, longitude,
            created_by, updated_by
        ) VALUES (
            'c6000000-0000-0000-0000-00000000a000', 'c6000000-0000-0000-0000-00000000a501', 'c6000000-0000-0000-0000-00000000a201', 200, 77.5946,
            'c6000000-0000-0000-0000-00000000a101', 'c6000000-0000-0000-0000-00000000a101'
        );
        RAISE EXCEPTION 'CHECK_CONSTRAINT_GAP: an out-of-range latitude was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL; -- expected
    END;
END $$;

INSERT INTO app.institution_attendance_location (
    tenant_id, institution_attendance_location_id, institution_id, latitude, longitude,
    created_by, updated_by
) VALUES (
    'c6000000-0000-0000-0000-00000000a000', 'c6000000-0000-0000-0000-00000000a502', 'c6000000-0000-0000-0000-00000000a201', 12.9716, 77.5946,
    'c6000000-0000-0000-0000-00000000a101', 'c6000000-0000-0000-0000-00000000a101'
);

DO $$
DECLARE v_radius integer; v_accuracy integer; v_retention integer;
BEGIN
    SELECT radius_meters, gps_accuracy_threshold_meters, retention_days INTO v_radius, v_accuracy, v_retention
    FROM app.institution_attendance_location WHERE institution_attendance_location_id = 'c6000000-0000-0000-0000-00000000a502';
    IF v_radius <> 200 OR v_accuracy <> 50 OR v_retention <> 180 THEN
        RAISE EXCEPTION 'DEFAULTS_GAP: institution_attendance_location defaults changed unexpectedly (radius=%, accuracy=%, retention=%)', v_radius, v_accuracy, v_retention;
    END IF;
END $$;

ROLLBACK;
