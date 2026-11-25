DO $$
BEGIN
 IF to_regclass('app.library_fine_policy') IS NULL OR to_regclass('app.v_student_library_dues_flag') IS NULL
 OR to_regclass('app.transport_allocation') IS NULL OR to_regclass('app.library_catalog') IS NULL
 OR to_regclass('app.vehicle') IS NULL OR to_regclass('app.report_definition') IS NULL
 OR to_regclass('app.report_execution') IS NULL THEN RAISE EXCEPTION 'T10-74 schema missing'; END IF;
 IF to_regprocedure('app.fn_library_fine_amount(uuid,uuid,date)') IS NULL THEN RAISE EXCEPTION 'library fine calculation function missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='library_fine_policy' AND column_name='working_days')
 THEN RAISE EXCEPTION 'institution working day configuration missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='library_fine_policy' AND column_name='day_basis')
 THEN RAISE EXCEPTION 'institution fine day basis missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgname='trg_transport_capacity_guard' AND NOT tgisinternal)
 THEN RAISE EXCEPTION 'transport capacity trigger missing'; END IF;
 IF NOT (SELECT relrowsecurity FROM pg_class WHERE oid='app.book_circulation'::regclass)
 THEN RAISE EXCEPTION 'book_circulation RLS missing'; END IF;
END $$;
