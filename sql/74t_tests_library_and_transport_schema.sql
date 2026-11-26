DO $$
DECLARE t text;
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
 FOREACH t IN ARRAY ARRAY['library_catalog','book_copy','library_fine_policy','book_circulation','library_fine','library_reservation','vehicle','transport_route','route_stop','transport_allocation','report_definition','report_execution'] LOOP
  IF NOT (SELECT relrowsecurity FROM pg_class WHERE oid=format('app.%I',t)::regclass) THEN RAISE EXCEPTION 'RLS missing on app.%',t; END IF;
 END LOOP;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgname='trg_seed_library_fine_policy' AND NOT tgisinternal) THEN RAISE EXCEPTION 'new institution fine policy seed trigger missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.book_copy'::regclass AND conname='fk_book_copy_catalog_scope') THEN RAISE EXCEPTION 'book copy scope FK missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.transport_allocation'::regclass AND conname='fk_allocation_stop_scope') THEN RAISE EXCEPTION 'allocation stop scope FK missing'; END IF;
 IF NOT EXISTS (
   SELECT 1 FROM information_schema.columns WHERE table_schema='app'
    AND table_name='v_student_library_dues_flag' AND column_name='outstanding_fine_balance'
 ) THEN RAISE EXCEPTION 'T9 library dues balance contract missing'; END IF;
 IF position('fn_library_fine_amount' IN pg_get_viewdef('app.v_student_library_dues_flag'::regclass))=0
 THEN RAISE EXCEPTION 'T9 library dues view must include dynamic overdue accrual'; END IF;
END $$;
