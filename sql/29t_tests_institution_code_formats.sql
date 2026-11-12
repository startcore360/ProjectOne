SELECT public.expect_ok('S2-ID-DB-01','staff code sequence is tenant isolated and institution unique',$q$
 DO $d$ DECLARE n int; BEGIN
  IF to_regclass('app.staff_code_sequence') IS NULL THEN RAISE EXCEPTION 'STAFF_SEQUENCE_TABLE_MISSING'; END IF;
  SELECT count(*) INTO n FROM pg_indexes
   WHERE schemaname='app' AND indexname='ux_staff_code_sequence_institution';
  IF n<>1 THEN RAISE EXCEPTION 'STAFF_SEQUENCE_UNIQUE_INDEX_MISSING'; END IF;
  IF NOT (SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='app.staff_code_sequence'::regclass)
   THEN RAISE EXCEPTION 'STAFF_SEQUENCE_RLS_NOT_FORCED'; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='staff_code_sequence' AND policyname='p_tenant')
   THEN RAISE EXCEPTION 'STAFF_SEQUENCE_TENANT_POLICY_MISSING'; END IF;
 END $d$
$q$);

SELECT public.expect_ok('S2-ID-DB-02','student code sequence has one row per tenant institution',$q$
 DO $d$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='app' AND indexname='ux_student_code_sequence_institution')
   THEN RAISE EXCEPTION 'STUDENT_SEQUENCE_UNIQUE_INDEX_MISSING'; END IF;
 END $d$
$q$);

SELECT public.expect_ok('S2-ID-DB-03','non-transactional per-institution sequence helpers are installed',$q$
 DO $d$ BEGIN
  IF to_regprocedure('app.ensure_institution_code_sequence(uuid,uuid,text)') IS NULL
   OR to_regprocedure('app.next_institution_code_value(uuid,uuid,text)') IS NULL
   OR to_regprocedure('app.institution_code_format_locked(uuid,uuid,text)') IS NULL
  THEN RAISE EXCEPTION 'INSTITUTION_CODE_SEQUENCE_FUNCTIONS_MISSING'; END IF;
 END $d$
$q$);
