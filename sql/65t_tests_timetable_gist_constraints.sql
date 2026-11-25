BEGIN;
DO $$
DECLARE v_forced boolean;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_extension WHERE extname='btree_gist') THEN RAISE EXCEPTION 'DB-T6-01 btree_gist missing'; END IF;
 IF to_regclass('app.room') IS NULL OR to_regclass('app.timetable_slot') IS NULL THEN RAISE EXCEPTION 'DB-T6-02 timetable tables missing'; END IF;
 IF to_regclass('app.online_session') IS NULL OR to_regclass('app.online_session_access_log') IS NULL THEN RAISE EXCEPTION 'DB-T6-03 online session tables missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.timetable_slot'::regclass AND conname='ex_timetable_teacher_no_overlap' AND contype='x') THEN RAISE EXCEPTION 'DB-T6-04 teacher GiST exclusion missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.timetable_slot'::regclass AND conname='ex_timetable_room_no_overlap' AND contype='x') THEN RAISE EXCEPTION 'DB-T6-05 room GiST exclusion missing'; END IF;
 SELECT relrowsecurity AND relforcerowsecurity INTO v_forced FROM pg_class WHERE oid='app.timetable_slot'::regclass;
 IF v_forced IS DISTINCT FROM true THEN RAISE EXCEPTION 'DB-T6-06 timetable RLS must be forced'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='app.online_session_access_log'::regclass AND tgname='trg_online_session_access_append_only' AND NOT tgisinternal) THEN RAISE EXCEPTION 'DB-T6-07 online access log must be append-only'; END IF;
END $$;
ROLLBACK;