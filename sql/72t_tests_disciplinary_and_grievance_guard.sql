DO $$
DECLARE t text; def text;
BEGIN
 IF EXISTS (
  SELECT 1 FROM (VALUES
   ('ID_CARD_ISSUE',284),('ID_CARD_REPLACE',285),('ID_CARD_TEMPLATE_MANAGE',286),('ID_CARD_REVOKE',287),
   ('CERTIFICATE_TEMPLATE_MANAGE',288),('CERTIFICATE_REVOKE',289),
   ('DISCIPLINARY_INCIDENT_VIEW',290),('DISCIPLINARY_INCIDENT_MANAGE',291),
   ('GRIEVANCE_CASE_SUBMIT',292),('GRIEVANCE_CASE_VIEW',293),('GRIEVANCE_CASE_ASSIGN',294),
   ('GRIEVANCE_HEARING_MANAGE',295),('GRIEVANCE_CASE_RESOLVE',296),('GRIEVANCE_EVIDENCE_VIEW',297),
   ('GRIEVANCE_EVIDENCE_UPLOAD',298),('ID_CARD_REPLACEMENT_REQUEST',299),('DEPARTMENT_EQUIPMENT_CLEARANCE_MANAGE',300)
  ) AS expected(code,action_no)
  LEFT JOIN ref.action_catalog a ON a.action_code=expected.code AND a.action_no=expected.action_no
  WHERE a.action_code IS NULL
 ) THEN RAISE EXCEPTION 'T9_DB_72 action codes or numbers do not match the catalog extension'; END IF;
 IF (SELECT count(*) FROM ref.action_catalog WHERE action_no BETWEEN 284 AND 300)<>17 THEN
  RAISE EXCEPTION 'T9_DB_72 expected exactly 17 action numbers 284-300';
 END IF;
 IF EXISTS(SELECT 1 FROM ref.action_catalog WHERE action_no BETWEEN 284 AND 300 GROUP BY action_no HAVING count(*)>1) THEN
  RAISE EXCEPTION 'T9_DB_72 action number collision';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM ref.action_catalog WHERE action_code='ID_CARD_REPLACE' AND is_approval_action=true) THEN
  RAISE EXCEPTION 'T9_DB_72 ID_CARD_REPLACE must be an approval action';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM ref.standard_role_action WHERE role_code='STUDENT_COLLEGE' AND action_code='ID_CARD_REPLACEMENT_REQUEST')
  OR NOT EXISTS(SELECT 1 FROM ref.standard_role_action WHERE role_code='STUDENT_SCHOOL' AND action_code='ID_CARD_REPLACEMENT_REQUEST')
  OR NOT EXISTS(SELECT 1 FROM ref.standard_role_action WHERE role_code='PROFESSOR' AND action_code='ID_CARD_REPLACEMENT_REQUEST') THEN
  RAISE EXCEPTION 'T9_DB_72 self-service ID card replacement request grants missing';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM ref.action_catalog WHERE action_no=283 AND action_code='LANDING_CMS_MANAGE') THEN
  RAISE EXCEPTION 'T9_DB_72 must preserve seeded action number 283';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM ref.standard_role WHERE role_code='INTERNAL_COMPLAINTS_COMMITTEE' AND role_level='COLLEGE') THEN
  RAISE EXCEPTION 'T9_DB_72 institution-level ICC standard role missing';
 END IF;
 IF EXISTS(SELECT 1 FROM ref.standard_role_action sra
  WHERE sra.action_code IN ('GRIEVANCE_CASE_VIEW','GRIEVANCE_CASE_ASSIGN','GRIEVANCE_HEARING_MANAGE','GRIEVANCE_CASE_RESOLVE','GRIEVANCE_EVIDENCE_VIEW')
   AND sra.role_code NOT IN ('INTERNAL_COMPLAINTS_COMMITTEE','PRINCIPAL_COLLEGE','SCHOOL_PRINCIPAL')) THEN
  RAISE EXCEPTION 'T9_DB_72 confidential grievance action granted outside ICC/Principal';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM ref.action_catalog WHERE action_no=300 AND action_code='DEPARTMENT_EQUIPMENT_CLEARANCE_MANAGE') THEN
  RAISE EXCEPTION 'T9_DB_72 department equipment clearance action missing';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM ref.standard_role_action WHERE role_code='HEAD_OF_DEPARTMENT' AND action_code='DEPARTMENT_EQUIPMENT_CLEARANCE_MANAGE') THEN
  RAISE EXCEPTION 'T9_DB_72 HOD department equipment clearance grant missing';
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
 FOREACH t IN ARRAY ARRAY['disciplinary_incident','disciplinary_evidence','disciplinary_action','grievance_case','grievance_evidence','grievance_action'] LOOP
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
 IF def NOT LIKE '%scope_type=''INSTITUTION''%' THEN RAISE EXCEPTION 'T9_DB_72 ICC/Principal must hold institution-scoped active role'; END IF;
 IF def NOT LIKE '%role_origin=''STANDARD''%' OR def NOT LIKE '%base_standard_role=rd.role_code%' OR def NOT LIKE '%is_sensitive_role=true%' THEN
  RAISE EXCEPTION 'T9_DB_72 requires an active sensitive standard-role definition, not a custom role code';
 END IF;
 IF to_regprocedure('app.fn_complaint_guard()') IS NULL THEN RAISE EXCEPTION 'T9_DB_72 original complaint state-transition trigger function was replaced'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='app.grievance_action'::regclass AND tgname='trg_append_only' AND NOT tgisinternal) THEN RAISE EXCEPTION 'T9_DB_72 grievance action ledger is not append-only'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='grievance_evidence' AND policyname='p_grievance_confidentiality' AND permissive='RESTRICTIVE' AND cmd='SELECT') THEN RAISE EXCEPTION 'T9_DB_72 evidence confidentiality policy missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='grievance_action' AND policyname='p_grievance_confidentiality' AND permissive='RESTRICTIVE' AND cmd='SELECT') THEN RAISE EXCEPTION 'T9_DB_72 action confidentiality policy missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='grievance_case' AND policyname='p_grievance_confidentiality_update' AND permissive='RESTRICTIVE' AND cmd='UPDATE') THEN RAISE EXCEPTION 'T9_DB_72 grievance update confidentiality policy missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='grievance_evidence' AND policyname='p_grievance_evidence_insert' AND permissive='RESTRICTIVE' AND cmd='INSERT') THEN RAISE EXCEPTION 'T9_DB_72 evidence insert confidentiality policy missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='grievance_action' AND policyname='p_grievance_action_insert' AND permissive='RESTRICTIVE' AND cmd='INSERT') THEN RAISE EXCEPTION 'T9_DB_72 action insert confidentiality policy missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='disciplinary_incident' AND policyname='p_disciplinary_institution' AND permissive='RESTRICTIVE') THEN RAISE EXCEPTION 'T9_DB_72 disciplinary institution isolation policy missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='disciplinary_evidence' AND policyname='p_disciplinary_evidence_insert' AND permissive='RESTRICTIVE' AND cmd='INSERT') THEN RAISE EXCEPTION 'T9_DB_72 disciplinary evidence insert policy missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='app' AND tablename='disciplinary_action' AND policyname='p_disciplinary_action_insert' AND permissive='RESTRICTIVE' AND cmd='INSERT') THEN RAISE EXCEPTION 'T9_DB_72 disciplinary action insert policy missing'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='app.disciplinary_action'::regclass AND tgname='trg_disc_action_append_only' AND NOT tgisinternal) THEN RAISE EXCEPTION 'T9_DB_72 disciplinary action ledger must be append-only'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='app.file_object'::regclass AND conname='ck_file_object_purpose'
  AND pg_get_constraintdef(oid) LIKE '%DISCIPLINARY_EVIDENCE%' AND pg_get_constraintdef(oid) LIKE '%GRIEVANCE_EVIDENCE%') THEN
  RAISE EXCEPTION 'T9_DB_72 file purpose catalog must include both T9 evidence types';
 END IF;

 IF EXISTS(SELECT 1 FROM ref.standard_role_action WHERE action_code='GRIEVANCE_EVIDENCE_UPLOAD'
   AND role_code NOT IN ('INTERNAL_COMPLAINTS_COMMITTEE','PRINCIPAL_COLLEGE','SCHOOL_PRINCIPAL')) THEN
  RAISE EXCEPTION 'T9_DB_72 evidence upload action granted outside ICC/Principal';
 END IF;
 IF app.fn_complaint_guard('00000000-0000-0000-0000-000000000001'::uuid,'00000000-0000-0000-0000-000000000002'::uuid,'00000000-0000-0000-0000-000000000003'::uuid) THEN RAISE EXCEPTION 'T9_DB_72 predicate must deny when tenant/user GUCs are absent'; END IF;
END $$;
