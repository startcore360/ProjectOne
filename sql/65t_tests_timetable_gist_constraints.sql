BEGIN;
DO $T6$ DECLARE v_forced boolean; v_teacher_def text; v_room_def text; BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_extension WHERE extname='btree_gist') THEN RAISE EXCEPTION 'DB-T6-01 btree_gist missing'; END IF;
 IF to_regclass('app.room') IS NULL OR to_regclass('app.timetable_slot') IS NULL THEN RAISE EXCEPTION 'DB-T6-02 timetable objects missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='timetable_slot' AND column_name='teacher_id') THEN RAISE EXCEPTION 'DB-T6-03 teacher_id missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='timetable_slot' AND column_name='room_id') THEN RAISE EXCEPTION 'DB-T6-04 room_id missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.timetable_slot'::regclass AND conname='ex_timetable_teacher_no_overlap' AND contype='x') THEN RAISE EXCEPTION 'DB-T6-05 teacher exclusion missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.timetable_slot'::regclass AND conname='ex_timetable_room_no_overlap' AND contype='x') THEN RAISE EXCEPTION 'DB-T6-06 room exclusion missing'; END IF;
 SELECT pg_get_constraintdef(oid) INTO v_teacher_def FROM pg_constraint WHERE conrelid='app.timetable_slot'::regclass AND conname='ex_timetable_teacher_no_overlap';
 SELECT pg_get_constraintdef(oid) INTO v_room_def FROM pg_constraint WHERE conrelid='app.timetable_slot'::regclass AND conname='ex_timetable_room_no_overlap';
 IF v_teacher_def NOT LIKE '%daterange%' OR v_teacher_def NOT LIKE '%tsrange%' THEN RAISE EXCEPTION 'DB-T6-07 teacher exclusion must intersect date and time ranges'; END IF;
 IF v_room_def NOT LIKE '%daterange%' OR v_room_def NOT LIKE '%tsrange%' THEN RAISE EXCEPTION 'DB-T6-08 room exclusion must intersect date and time ranges'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='meeting_link' AND column_name='access_token_hash') THEN RAISE EXCEPTION 'DB-T6-09 meeting token missing'; END IF;
 IF to_regclass('app.online_session_access_log') IS NULL THEN RAISE EXCEPTION 'DB-T6-10 access log missing'; END IF;
 SELECT relrowsecurity AND relforcerowsecurity INTO v_forced FROM pg_class WHERE oid='app.timetable_slot'::regclass;
 IF v_forced IS DISTINCT FROM true THEN RAISE EXCEPTION 'DB-T6-11 timetable RLS must remain forced'; END IF;
END $T6$;
ROLLBACK;