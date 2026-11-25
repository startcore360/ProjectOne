BEGIN;
DROP TABLE IF EXISTS app.online_session_access_log CASCADE;
DROP TABLE IF EXISTS app.online_session CASCADE;
DROP TABLE IF EXISTS app.timetable_slot CASCADE;
DROP TABLE IF EXISTS app.room CASCADE;
DROP FUNCTION IF EXISTS app.fn_timetable_slot_scope_guard();
COMMIT;