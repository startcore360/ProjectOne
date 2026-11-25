DO $$
BEGIN
 IF to_regclass('app.library_fine_policy') IS NULL OR to_regclass('app.v_student_library_dues_flag') IS NULL
 OR to_regclass('app.transport_allocation') IS NULL THEN RAISE EXCEPTION 'T10-74 schema missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='library_fine_policy' AND column_name='day_basis')
 THEN RAISE EXCEPTION 'per-institution fine day basis missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgname='trg_transport_capacity_guard' AND NOT tgisinternal)
 THEN RAISE EXCEPTION 'transport capacity trigger missing'; END IF;
 IF NOT (SELECT relrowsecurity FROM pg_class WHERE oid='app.book_circulation'::regclass)
 THEN RAISE EXCEPTION 'book_circulation RLS missing'; END IF;
END $$;
