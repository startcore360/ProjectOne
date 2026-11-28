DO $$
BEGIN
 IF to_regclass('app.leave_delegation') IS NULL THEN RAISE EXCEPTION 'T3-DB-60-01 leave_delegation missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='app' AND table_name='leave_request' AND column_name='designated_delegate_staff_profile_id') THEN RAISE EXCEPTION 'T3-DB-60-06 designated leave cover field missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid='app.leave_delegation'::regclass AND relrowsecurity AND relforcerowsecurity) THEN RAISE EXCEPTION 'T3-DB-60-02 leave delegation RLS missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='leave_delegation' AND policyname='p_tenant') THEN RAISE EXCEPTION 'T3-DB-60-03 tenant policy missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='app.leave_delegation'::regclass AND tgname='trg_leave_delegation_guard' AND NOT tgisinternal) THEN RAISE EXCEPTION 'T3-DB-60-04 guard missing'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.leave_delegation'::regclass AND conname='fk_leave_delegation_request') THEN RAISE EXCEPTION 'T3-DB-60-05 request FK missing'; END IF;
END $$;