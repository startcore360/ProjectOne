DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='staff_document' AND column_name='qualification_title') THEN RAISE EXCEPTION 'T3-DB-59-01 qualification_title missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='staff_document' AND column_name='awarding_institution') THEN RAISE EXCEPTION 'T3-DB-59-02 awarding_institution missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='staff_document' AND column_name='qualification_level') THEN RAISE EXCEPTION 'T3-DB-59-03 qualification_level missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='app' AND indexname='ix_leave_request_staff_status_start') THEN RAISE EXCEPTION 'T3-DB-59-04 leave request index missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='app' AND indexname='ix_leave_balance_staff_year_type') THEN RAISE EXCEPTION 'T3-DB-59-05 leave balance index missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='app' AND indexname='uq_leave_balance_staff_type_year' AND indexdef ILIKE '%UNIQUE%') THEN RAISE EXCEPTION 'T3-DB-59-06 yearly leave balance uniqueness missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='app' AND indexname='uq_leave_type_institution_code' AND indexdef ILIKE '%UNIQUE%') THEN RAISE EXCEPTION 'T3-DB-59-07 leave type uniqueness missing'; END IF;

 IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='appraisal_cycle') THEN RAISE EXCEPTION 'T3-M13-01 appraisal_cycle missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='appraisal_submission') THEN RAISE EXCEPTION 'T3-M13-02 appraisal_submission missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='appraisal_review') THEN RAISE EXCEPTION 'T3-M13-03 appraisal_review missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname='appraisal_cycle' AND relrowsecurity AND relforcerowsecurity) THEN RAISE EXCEPTION 'T3-M13-04 appraisal cycle RLS missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname='appraisal_submission' AND relrowsecurity AND relforcerowsecurity) THEN RAISE EXCEPTION 'T3-M13-05 appraisal submission RLS missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname='appraisal_review' AND relrowsecurity AND relforcerowsecurity) THEN RAISE EXCEPTION 'T3-M13-06 appraisal review RLS missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM ref.action_catalog WHERE action_code='STAFF_APPRAISAL_CYCLE_MANAGE') THEN RAISE EXCEPTION 'T3-M13-07 appraisal action catalogue missing'; END IF;

END $;