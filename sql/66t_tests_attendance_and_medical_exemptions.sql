BEGIN;
DO $$
DECLARE v_forced boolean; v_default numeric; v_function regprocedure;
BEGIN
 IF to_regclass('app.attendance_session') IS NULL OR to_regclass('app.attendance_entry') IS NULL OR to_regclass('app.attendance_medical_exemption') IS NULL THEN RAISE EXCEPTION 'DB-T6-10 attendance tables missing'; END IF;
 IF to_regclass('app.attendance_threshold_policy') IS NULL THEN RAISE EXCEPTION 'DB-T6-11 PO-T02 threshold policy missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.attendance_entry'::regclass AND conname='ck_attendance_entry_status' AND pg_get_constraintdef(oid) LIKE '%PRESENT%ABSENT%ON_APPROVED_LEAVE%NOT_HELD%') THEN RAISE EXCEPTION 'DB-T6-12 canonical attendance statuses missing'; END IF;
 IF EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.attendance_entry'::regclass AND pg_get_constraintdef(oid) ~ 'LATE|EXCUSED') THEN RAISE EXCEPTION 'DB-T6-13 legacy attendance status must not exist'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='app.attendance_session'::regclass AND tgname='trg_check_term_lock_attendance' AND NOT tgisinternal) THEN RAISE EXCEPTION 'DB-T6-14 attendance session term-lock trigger missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='app.attendance_entry'::regclass AND tgname='trg_check_term_lock_attendance_entry' AND NOT tgisinternal) THEN RAISE EXCEPTION 'DB-T6-15 attendance entry term-lock trigger missing'; END IF;
 SELECT relrowsecurity AND relforcerowsecurity INTO v_forced FROM pg_class WHERE oid='app.attendance_entry'::regclass;
 IF v_forced IS DISTINCT FROM true THEN RAISE EXCEPTION 'DB-T6-16 attendance RLS must be forced'; END IF;
 IF to_regprocedure('app.fn_attendance_exam_eligibility(uuid,uuid,uuid)') IS NULL THEN RAISE EXCEPTION 'DB-T6-17 exam eligibility gate missing'; END IF;
 SELECT minimum_attendance_percentage INTO v_default FROM app.attendance_threshold_policy WHERE department_id IS NULL ORDER BY created_at LIMIT 1;
 IF v_default IS NOT NULL AND v_default<>75.00 THEN RAISE EXCEPTION 'DB-T6-18 seeded institution default should be 75.00'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='app.attendance_medical_exemption'::regclass AND tgname='trg_medical_exemption_guard' AND NOT tgisinternal) THEN RAISE EXCEPTION 'DB-T6-19 medical certificate guard missing'; END IF;
END $$;
ROLLBACK;