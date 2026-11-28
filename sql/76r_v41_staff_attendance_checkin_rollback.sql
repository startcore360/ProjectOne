BEGIN;

-- Refuse rather than fail half-way on a foreign key: approval history raised by check-ins, and
-- selfie files, must be dealt with (exported / removed) before this rollback is run.
DO $guard$
BEGIN
    IF EXISTS (SELECT 1 FROM app.approval_request WHERE process_code = 'STAFF_ATTENDANCE_REVIEW') THEN
        RAISE EXCEPTION 'ROLLBACK_76_REFUSED: app.approval_request rows for STAFF_ATTENDANCE_REVIEW exist';
    END IF;
    IF EXISTS (SELECT 1 FROM app.file_object WHERE purpose = 'STAFF_ATTENDANCE_SELFIE') THEN
        RAISE EXCEPTION 'ROLLBACK_76_REFUSED: app.file_object rows with purpose STAFF_ATTENDANCE_SELFIE exist';
    END IF;
END $guard$;

DROP TABLE IF EXISTS app.staff_attendance_checkin CASCADE;
DROP TABLE IF EXISTS app.staff_reference_photo CASCADE;
DROP TABLE IF EXISTS app.institution_attendance_location CASCADE;

DROP FUNCTION IF EXISTS app.fn_checkin_capture_guard();

-- Restore ck_file_object_purpose to exactly the list it had before 76: remove only the value 76 added.
ALTER TABLE app.file_object DROP CONSTRAINT IF EXISTS ck_file_object_staff_attendance_selfie_type;
DO $purpose$
DECLARE v_def text; v_restored text;
BEGIN
    SELECT pg_get_constraintdef(oid) INTO v_def FROM pg_constraint
     WHERE conrelid = 'app.file_object'::regclass AND conname = 'ck_file_object_purpose';
    IF v_def IS NOT NULL AND position('''STAFF_ATTENDANCE_SELFIE''' IN v_def) > 0 THEN
        v_restored := replace(replace(v_def, '''STAFF_ATTENDANCE_SELFIE''::text, ', ''), ', ''STAFF_ATTENDANCE_SELFIE''::text', '');
        IF position('''STAFF_ATTENDANCE_SELFIE''' IN v_restored) > 0 THEN
            RAISE EXCEPTION 'CK_FILE_OBJECT_PURPOSE_UNEXPECTED: %', v_def;
        END IF;
        ALTER TABLE app.file_object DROP CONSTRAINT ck_file_object_purpose;
        EXECUTE 'ALTER TABLE app.file_object ADD CONSTRAINT ck_file_object_purpose ' || v_restored;
    END IF;
END $purpose$;

DELETE FROM ref.approval_chain_step WHERE process_code = 'STAFF_ATTENDANCE_REVIEW';
DELETE FROM ref.approval_process WHERE process_code = 'STAFF_ATTENDANCE_REVIEW';

DELETE FROM app.role_action WHERE action_code IN (
    'STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE', 'STAFF_ATTENDANCE_CHECKIN_SUBMIT', 'STAFF_ATTENDANCE_CHECKIN_REVIEW'
);
DELETE FROM ref.standard_role_action WHERE action_code IN (
    'STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE', 'STAFF_ATTENDANCE_CHECKIN_SUBMIT', 'STAFF_ATTENDANCE_CHECKIN_REVIEW'
);
DELETE FROM ref.action_catalog WHERE action_code IN (
    'STAFF_ATTENDANCE_CHECKIN_LOCATION_MANAGE', 'STAFF_ATTENDANCE_CHECKIN_SUBMIT', 'STAFF_ATTENDANCE_CHECKIN_REVIEW'
);

COMMIT;
