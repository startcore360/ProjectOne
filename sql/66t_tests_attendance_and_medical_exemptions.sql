BEGIN;
DO $$
DECLARE v_forced boolean; v_status text; v_default text; v_ops smallint; v_session_kind text;
BEGIN
 IF to_regclass('app.attendance_session') IS NULL OR to_regclass('app.attendance_entry') IS NULL THEN RAISE EXCEPTION 'DB-T6-10 canonical attendance tables missing'; END IF;
 IF to_regclass('app.attendance_medical_exemption') IS NULL OR to_regclass('app.attendance_threshold_policy') IS NULL THEN RAISE EXCEPTION 'DB-T6-11 medical/threshold tables missing'; END IF;
 SELECT pg_get_constraintdef(oid) INTO v_status FROM pg_constraint
  WHERE conrelid='app.attendance_entry'::regclass AND conname='ck_attendance_entry_status';
 IF v_status IS NULL OR v_status NOT LIKE '%PRESENT%ABSENT%ON_APPROVED_LEAVE%NOT_HELD%' THEN RAISE EXCEPTION 'DB-T6-12 canonical attendance statuses missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='app.attendance_session'::regclass AND tgname='trg_attendance_session_scope_guard' AND NOT tgisinternal) THEN RAISE EXCEPTION 'DB-T6-13 session scope/term-lock guard missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='app.attendance_entry'::regclass AND tgname='trg_attendance_entry_scope_guard' AND NOT tgisinternal) THEN RAISE EXCEPTION 'DB-T6-14 entry enrollment guard missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_proc WHERE oid=to_regprocedure('app.is_term_locked(uuid,uuid,uuid,uuid)')) THEN RAISE EXCEPTION 'DB-T6-15 section/semester term-lock adapter missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_proc WHERE oid=to_regprocedure('app.fn_attendance_scope_is_term_locked(uuid,uuid,uuid,uuid)')) THEN RAISE EXCEPTION 'DB-T6-16 attendance scope resolver missing'; END IF;
 SELECT pg_get_constraintdef(oid) INTO v_session_kind FROM pg_constraint WHERE conrelid='app.attendance_session'::regclass AND conname='ck_attendance_session_session_kind';
 IF v_session_kind NOT LIKE '%DAILY%' OR v_session_kind NOT LIKE '%PERIOD%' OR v_session_kind LIKE '%LATE%' OR v_session_kind LIKE '%EXCUSED%' THEN RAISE EXCEPTION 'DB-T6-17 daily/period attendance kinds must be canonical'; END IF;
 SELECT tgtype INTO v_ops FROM pg_trigger WHERE tgrelid='app.attendance_session'::regclass AND tgname='trg_check_term_lock_attendance' AND NOT tgisinternal;
 IF v_ops IS NULL OR (v_ops & 28)<>28 THEN RAISE EXCEPTION 'DB-T6-18 attendance session must be guarded for INSERT/UPDATE/DELETE'; END IF;
 SELECT tgtype INTO v_ops FROM pg_trigger WHERE tgrelid='app.attendance_entry'::regclass AND tgname='trg_check_term_lock_attendance_entry' AND NOT tgisinternal;
 IF v_ops IS NULL OR (v_ops & 28)<>28 THEN RAISE EXCEPTION 'DB-T6-19 attendance entry must be guarded for INSERT/UPDATE/DELETE'; END IF;
 SELECT tgtype INTO v_ops FROM pg_trigger WHERE tgrelid='app.attendance_medical_exemption'::regclass AND tgname='trg_check_term_lock_medical_exemption' AND NOT tgisinternal;
 IF v_ops IS NULL OR (v_ops & 28)<>28 THEN RAISE EXCEPTION 'DB-T6-20 medical exemption must be guarded for INSERT/UPDATE/DELETE'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='app.attendance_medical_exemption'::regclass AND tgname='trg_medical_exemption_guard' AND NOT tgisinternal) THEN RAISE EXCEPTION 'DB-T6-21 medical exemption guard missing'; END IF;
 IF to_regclass('app.v_attendance_percentage') IS NULL OR to_regprocedure('app.fn_attendance_exam_eligibility(uuid,uuid,uuid)') IS NULL THEN RAISE EXCEPTION 'DB-T6-22 attendance calculation/eligibility missing'; END IF;
 SELECT relrowsecurity AND relforcerowsecurity INTO v_forced FROM pg_class WHERE oid='app.attendance_medical_exemption'::regclass;
 IF v_forced IS DISTINCT FROM true THEN RAISE EXCEPTION 'DB-T6-23 medical exemption RLS must be forced'; END IF;
 SELECT column_default INTO v_default FROM information_schema.columns
  WHERE table_schema='app' AND table_name='attendance_threshold_policy' AND column_name='minimum_attendance_percentage';
 IF v_default IS NULL OR v_default NOT LIKE '%75%' THEN RAISE EXCEPTION 'DB-T6-24 PO-T02 default threshold must be 75 and configurable'; END IF;
END $$;
ROLLBACK;