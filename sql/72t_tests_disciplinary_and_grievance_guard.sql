DO $$
DECLARE t text; def text;
BEGIN
 FOREACH t IN ARRAY ARRAY['disciplinary_incident','grievance_case','grievance_evidence','grievance_action'] LOOP
  IF to_regclass('app.'||t) IS NULL THEN RAISE EXCEPTION 'T9_DB_72 missing app.%',t; END IF;
  IF NOT (SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid=to_regclass('app.'||t)) THEN RAISE EXCEPTION 'T9_DB_72 RLS not forced: %',t; END IF;
 END LOOP;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='grievance_case' AND policyname='p_grievance_confidentiality' AND permissive='RESTRICTIVE' AND cmd='SELECT') THEN RAISE EXCEPTION 'T9_DB_72 restrictive PO-T05 policy missing'; END IF;
 IF to_regprocedure('app.fn_complaint_guard(uuid,uuid,uuid)') IS NULL THEN RAISE EXCEPTION 'T9_DB_72 PO-T05 predicate missing'; END IF;
 SELECT pg_get_functiondef('app.fn_complaint_guard(uuid,uuid,uuid)'::regprocedure) INTO def;
 IF def NOT LIKE '%INTERNAL_COMPLAINTS_COMMITTEE%' OR def NOT LIKE '%PRINCIPAL_COLLEGE%' OR def NOT LIKE '%SCHOOL_PRINCIPAL%' THEN RAISE EXCEPTION 'T9_DB_72 ICC/Principal allowlist incomplete'; END IF;
 IF def LIKE '%TENANT_DPO%' OR def LIKE '%GRIEVANCE_REDACTED_COMMITTEE%' THEN RAISE EXCEPTION 'T9_DB_72 forbidden role present'; END IF;
 IF def NOT LIKE '%user_active_role%' THEN RAISE EXCEPTION 'T9_DB_72 predicate must honor the single active role'; END IF;
 IF def NOT LIKE '%hod_staff_assignment_id%' THEN RAISE EXCEPTION 'T9_DB_72 immediate HOD exclusion missing'; END IF;
 IF to_regprocedure('app.fn_complaint_guard()') IS NULL THEN RAISE EXCEPTION 'T9_DB_72 original complaint state-transition trigger function was replaced'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='app.grievance_action'::regclass AND tgname='trg_append_only' AND NOT tgisinternal) THEN RAISE EXCEPTION 'T9_DB_72 grievance action ledger is not append-only'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='grievance_evidence' AND policyname='p_grievance_confidentiality' AND permissive='RESTRICTIVE' AND cmd='SELECT') THEN RAISE EXCEPTION 'T9_DB_72 evidence confidentiality policy missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='grievance_action' AND policyname='p_grievance_confidentiality' AND permissive='RESTRICTIVE' AND cmd='SELECT') THEN RAISE EXCEPTION 'T9_DB_72 action confidentiality policy missing'; END IF;
END $$;
