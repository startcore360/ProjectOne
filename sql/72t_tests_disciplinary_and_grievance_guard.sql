DO $$
DECLARE t text; def text;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM ref.standard_role WHERE role_code='INTERNAL_COMPLAINTS_COMMITTEE' AND role_level='COLLEGE') THEN
  RAISE EXCEPTION 'T9_DB_72 institution-level ICC standard role missing';
 END IF;
 IF EXISTS(SELECT 1 FROM ref.standard_role_action sra
  WHERE sra.action_code IN ('GRIEVANCE_CASE_VIEW','GRIEVANCE_CASE_ASSIGN','GRIEVANCE_HEARING_MANAGE','GRIEVANCE_CASE_RESOLVE','GRIEVANCE_EVIDENCE_VIEW')
   AND sra.role_code NOT IN ('INTERNAL_COMPLAINTS_COMMITTEE','PRINCIPAL_COLLEGE','SCHOOL_PRINCIPAL')) THEN
  RAISE EXCEPTION 'T9_DB_72 confidential grievance action granted outside ICC/Principal';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM ref.standard_role_action WHERE role_code='INTERNAL_COMPLAINTS_COMMITTEE' AND action_code='GRIEVANCE_CASE_VIEW') THEN
  RAISE EXCEPTION 'T9_DB_72 ICC grievance view grant missing';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM ref.standard_role_action WHERE role_code='PRINCIPAL_COLLEGE' AND action_code='GRIEVANCE_CASE_VIEW') OR
    NOT EXISTS(SELECT 1 FROM ref.standard_role_action WHERE role_code='SCHOOL_PRINCIPAL' AND action_code='GRIEVANCE_CASE_VIEW') THEN
  RAISE EXCEPTION 'T9_DB_72 Principal grievance view grants missing';
 END IF;
 IF EXISTS(SELECT 1 FROM ref.standard_role_action WHERE role_code IN ('COLLEGE_ADMIN','SCHOOL_ADMIN','HEAD_OF_DEPARTMENT') AND
   action_code IN ('GRIEVANCE_CASE_VIEW','GRIEVANCE_CASE_ASSIGN','GRIEVANCE_HEARING_MANAGE','GRIEVANCE_CASE_RESOLVE','GRIEVANCE_EVIDENCE_VIEW')) THEN
  RAISE EXCEPTION 'T9_DB_72 generic admin/HOD must not receive confidential grievance permissions';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM ref.standard_role_action WHERE role_code='COLLEGE_ADMIN' AND action_code='COMPLAINT_ASSIGN') OR
    NOT EXISTS(SELECT 1 FROM ref.standard_role_action WHERE role_code='HEAD_OF_DEPARTMENT' AND action_code='COMPLAINT_RESOLVE') THEN
  RAISE EXCEPTION 'T9_DB_72 ordinary complaint grants were unexpectedly modified';
 END IF;
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
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='grievance_case' AND policyname='p_grievance_confidentiality_update' AND permissive='RESTRICTIVE' AND cmd='UPDATE') THEN RAISE EXCEPTION 'T9_DB_72 grievance update confidentiality policy missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='grievance_evidence' AND policyname='p_grievance_evidence_insert' AND permissive='RESTRICTIVE' AND cmd='INSERT') THEN RAISE EXCEPTION 'T9_DB_72 evidence insert confidentiality policy missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='grievance_action' AND policyname='p_grievance_action_insert' AND permissive='RESTRICTIVE' AND cmd='INSERT') THEN RAISE EXCEPTION 'T9_DB_72 action insert confidentiality policy missing'; END IF;
 IF EXISTS(SELECT 1 FROM ref.standard_role_action WHERE action_code='GRIEVANCE_EVIDENCE_UPLOAD'
   AND role_code NOT IN ('INTERNAL_COMPLAINTS_COMMITTEE','PRINCIPAL_COLLEGE','SCHOOL_PRINCIPAL')) THEN
  RAISE EXCEPTION 'T9_DB_72 evidence upload action granted outside ICC/Principal';
 END IF;
 IF app.fn_complaint_guard('00000000-0000-0000-0000-000000000001'::uuid,'00000000-0000-0000-0000-000000000002'::uuid,'00000000-0000-0000-0000-000000000003'::uuid) THEN RAISE EXCEPTION 'T9_DB_72 predicate must deny when tenant/user GUCs are absent'; END IF;
END $$;
