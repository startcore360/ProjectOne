DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='staff_document' AND column_name='qualification_title') THEN RAISE EXCEPTION 'T3-DB-59-01 qualification_title missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='staff_document' AND column_name='awarding_institution') THEN RAISE EXCEPTION 'T3-DB-59-02 awarding_institution missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='staff_document' AND column_name='qualification_level') THEN RAISE EXCEPTION 'T3-DB-59-03 qualification_level missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='app' AND indexname='ix_leave_request_staff_status_start') THEN RAISE EXCEPTION 'T3-DB-59-04 leave request index missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='app' AND indexname='ix_leave_balance_staff_year_type') THEN RAISE EXCEPTION 'T3-DB-59-05 leave balance index missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='app' AND indexname='uq_leave_balance_staff_type_year' AND indexdef ILIKE '%UNIQUE%') THEN RAISE EXCEPTION 'T3-DB-59-06 yearly leave balance uniqueness missing'; END IF;
END $$;