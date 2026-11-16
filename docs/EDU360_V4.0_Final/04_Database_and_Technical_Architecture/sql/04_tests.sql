-- EDU 360 V3.3 executable database acceptance tests (subset that can run without the application).
-- Seeds two tenants as the superuser, then runs positive and negative checks as edu_app with RLS forced.
\set ON_ERROR_STOP 1
SET client_min_messages = warning;

CREATE TABLE public.test_result (id serial, test_id text, rule text, outcome text, detail text);
GRANT INSERT, SELECT ON public.test_result TO edu_app, edu_retention;
GRANT USAGE ON SEQUENCE public.test_result_id_seq TO edu_app, edu_retention;

CREATE OR REPLACE FUNCTION public.expect_fail(p_id text, p_rule text, p_sql text, p_pattern text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  BEGIN
    EXECUTE p_sql;
    SET CONSTRAINTS ALL IMMEDIATE;
    INSERT INTO public.test_result(test_id, rule, outcome, detail) VALUES (p_id, p_rule, 'FAIL', 'statement succeeded but should have been refused');
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM ~* p_pattern THEN
      INSERT INTO public.test_result(test_id, rule, outcome, detail) VALUES (p_id, p_rule, 'PASS', left(SQLERRM, 160));
    ELSE
      INSERT INTO public.test_result(test_id, rule, outcome, detail) VALUES (p_id, p_rule, 'FAIL', 'unexpected error: ' || left(SQLERRM, 160));
    END IF;
  END;
END$$;
CREATE OR REPLACE FUNCTION public.expect_ok(p_id text, p_rule text, p_sql text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  BEGIN
    EXECUTE p_sql;
    SET CONSTRAINTS ALL IMMEDIATE;
    INSERT INTO public.test_result(test_id, rule, outcome, detail) VALUES (p_id, p_rule, 'PASS', 'accepted');
  EXCEPTION WHEN OTHERS THEN
    INSERT INTO public.test_result(test_id, rule, outcome, detail) VALUES (p_id, p_rule, 'FAIL', left(SQLERRM, 160));
  END;
END$$;
CREATE OR REPLACE FUNCTION public.expect_value(p_id text, p_rule text, p_sql text, p_expected text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE v text;
BEGIN
  EXECUTE p_sql INTO v;
  INSERT INTO public.test_result(test_id, rule, outcome, detail)
  VALUES (p_id, p_rule, CASE WHEN v IS NOT DISTINCT FROM p_expected THEN 'PASS' ELSE 'FAIL' END, 'got ' || coalesce(v, 'NULL') || ', expected ' || p_expected);
END$$;
GRANT EXECUTE ON FUNCTION public.expect_fail(text,text,text,text), public.expect_ok(text,text,text), public.expect_value(text,text,text,text) TO edu_app, edu_retention;

-- ------------------------------------------------------------------ seed (superuser; bypasses RLS)
INSERT INTO ref.approval_process VALUES ('TEST_CHAIN','Test chain','TEST',true,true,'test'), ('MERIT_LIST','Merit list','MERIT_LIST',true,false,'WF-37');
INSERT INTO ref.idempotency_scope VALUES ('PAYMENT_START','startFeePayment','REQUIRED');
INSERT INTO app.tenant_local (tenant_id, tenant_type) VALUES ('a0000000-0000-0000-0000-000000000000','STANDALONE_COLLEGE'), ('b0000000-0000-0000-0000-000000000000','STANDALONE_COLLEGE');

\set M 'created_by, updated_by'
\set V '''00000000-0000-0000-0000-0000000000aa'',''00000000-0000-0000-0000-0000000000aa'''
INSERT INTO app.user_account (tenant_id, user_account_id, user_type, display_name, email, status, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000101','STAFF','Teacher A','t1@a.in','ACTIVE', :V),
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000102','STAFF','HOD A','h1@a.in','ACTIVE', :V),
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000103','GOVERNANCE','GBA A','g1@a.in','ACTIVE', :V),
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000104','STAFF','Accountant A','acc@a.in','ACTIVE', :V),
 ('b0000000-0000-0000-0000-000000000000','b0000000-0000-0000-0000-000000000101','STAFF','Teacher B','t1@b.in','ACTIVE', :V);
INSERT INTO app.institution (tenant_id, institution_id, institution_type, institution_code, institution_name, status, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000201','COLLEGE','ENG01','College A1','DRAFT', :V),
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000202','COLLEGE','ENG02','College A2','DRAFT', :V),
 ('b0000000-0000-0000-0000-000000000000','b0000000-0000-0000-0000-000000000201','COLLEGE','ENG01','College B1','DRAFT', :V);
INSERT INTO app.academic_session (tenant_id, academic_session_id, institution_id, session_label, start_date, end_date, academic_year, status, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000301','a0000000-0000-0000-0000-000000000201','2026-27','2026-06-01','2027-05-31',2026,'ACTIVE', :V);
INSERT INTO app.department (tenant_id, department_id, institution_id, department_code, department_name, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000401','a0000000-0000-0000-0000-000000000201','CSE','CSE A1', :V),
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000402','a0000000-0000-0000-0000-000000000202','CSE','CSE A2', :V);
INSERT INTO app.program (tenant_id, program_id, institution_id, department_id, program_code, program_name, duration_years, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000501','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000401','BTECH','B.Tech',4, :V);
INSERT INTO app.program_year (tenant_id, program_year_id, institution_id, program_id, year_number, label, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000502','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000501',1,'Year 1', :V);
INSERT INTO app.semester (tenant_id, semester_id, institution_id, program_year_id, semester_number, label, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000503','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000502',1,'Sem 1', :V);
INSERT INTO app.section (tenant_id, section_id, institution_id, academic_session_id, semester_id, section_name, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000504','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000301','a0000000-0000-0000-0000-000000000503','A', :V);
INSERT INTO app.subject (tenant_id, subject_id, institution_id, subject_code, subject_name, credits, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000601','a0000000-0000-0000-0000-000000000201','CS101','Programming',4, :V);
INSERT INTO app.course_offering (tenant_id, course_offering_id, institution_id, subject_id, academic_session_id, semester_id, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000602','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000601','a0000000-0000-0000-0000-000000000301','a0000000-0000-0000-0000-000000000503', :V);
INSERT INTO app.student_profile (tenant_id, student_profile_id, institution_id, student_code, full_name, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000701','a0000000-0000-0000-0000-000000000201','ENG26-00001','Student One', :V);
INSERT INTO app.student_enrollment (tenant_id, enrollment_id, student_profile_id, institution_id, academic_session_id, section_id, started_on, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000702','a0000000-0000-0000-0000-000000000701','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000301','a0000000-0000-0000-0000-000000000504','2026-06-01', :V);
INSERT INTO app.student_fee_account (tenant_id, student_fee_account_id, student_profile_id, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000703','a0000000-0000-0000-0000-000000000701', :V);
-- finance seed: policy, structure (DRAFT), lines, installments
INSERT INTO app.fee_head (tenant_id, fee_head_id, fee_head_code, fee_head_name, category, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000801','TUITION','Tuition','ACADEMIC', :V);
INSERT INTO app.fee_policy (tenant_id, fee_policy_id, owner_kind, institution_id, version_no, status, published_by, published_at, effective_from, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000802','PRINCIPAL','a0000000-0000-0000-0000-000000000201',1,'PUBLISHED','a0000000-0000-0000-0000-000000000102',now(),'2026-04-01', :V);
INSERT INTO app.fee_head_rule (tenant_id, fee_head_rule_id, fee_policy_id, fee_head_id, min_amount, max_amount, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000803','a0000000-0000-0000-0000-000000000802','a0000000-0000-0000-0000-000000000801',1000,50000, :V);
INSERT INTO app.fee_structure (tenant_id, fee_structure_id, institution_id, academic_session_id, program_year_id, fee_policy_id, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000804','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000301','a0000000-0000-0000-0000-000000000502','a0000000-0000-0000-0000-000000000802', :V);
INSERT INTO app.fee_structure_line (tenant_id, fee_structure_line_id, institution_id, fee_structure_id, fee_head_id, amount, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000805','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000804','a0000000-0000-0000-0000-000000000801',40000, :V);
INSERT INTO app.fee_structure_installment (tenant_id, installment_template_id, institution_id, fee_structure_id, installment_no, due_date, amount, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000806','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000804',1,'2026-07-10',20000, :V),
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000816','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000804',2,'2026-11-10',20000, :V);
INSERT INTO app.approval_request (tenant_id, approval_request_id, institution_id, process_code, subject_type, subject_id, requested_by_user_id, requester_active_role, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000a01','a0000000-0000-0000-0000-000000000201','TEST_CHAIN','TEST','a0000000-0000-0000-0000-000000000804','a0000000-0000-0000-0000-000000000101','PROFESSOR', :V),
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000a05','a0000000-0000-0000-0000-000000000201','TEST_CHAIN','FEE_STRUCTURE','a0000000-0000-0000-0000-000000000804','a0000000-0000-0000-0000-000000000104','COLLEGE_ACCOUNTANT', :V);
INSERT INTO app.approval_step (tenant_id, approval_step_id, approval_request_id, step_no, approver_role_code, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000a02','a0000000-0000-0000-0000-000000000a01',1,'HEAD_OF_DEPARTMENT', :V),
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000a03','a0000000-0000-0000-0000-000000000a01',2,'PRINCIPAL_COLLEGE', :V);
INSERT INTO app.financial_period_lock (tenant_id, period_lock_id, institution_id, period_month, lock_state, locked_by, locked_at, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000d20','a0000000-0000-0000-0000-000000000201','2026-08-01','LOCKED','a0000000-0000-0000-0000-000000000104',now(), :V);
INSERT INTO app.receipt_sequence (tenant_id, receipt_sequence_id, institution_id, financial_year, prefix, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000d10','a0000000-0000-0000-0000-000000000201',2026,'A1/', :V);
INSERT INTO app.audit_outbox (tenant_id, audit_event_id, actor_kind, actor_user_id, active_role_code, operation, entity_type, outcome, correlation_id, payload_hash, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000e02','USER','a0000000-0000-0000-0000-000000000101','PROFESSOR','UPDATE','TEST','SUCCESS','c-1',repeat('0',64), :V);
INSERT INTO app.student_note (tenant_id, student_note_id, student_profile_id, institution_id, note_type, body, added_by_user_id, added_active_role, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000e01','a0000000-0000-0000-0000-000000000701','a0000000-0000-0000-0000-000000000201','DISCIPLINE','Late to lab','a0000000-0000-0000-0000-000000000101','PROFESSOR', :V);
-- tenant B data used for isolation tests
INSERT INTO app.department (tenant_id, department_id, institution_id, department_code, department_name, :M) VALUES
 ('b0000000-0000-0000-0000-000000000000','b0000000-0000-0000-0000-000000000401','b0000000-0000-0000-0000-000000000201','CSE','CSE B1', :V);

-- ------------------------------------------------------------------ tests run as the application role
SET ROLE edu_app;
SELECT set_config('app.tenant_id', '', false);
-- ST-26 / AC-I2: no tenant context → zero rows (fail-closed)
SELECT public.expect_value('DB-01','SEC-03 fail-closed RLS (no tenant context)', $q$SELECT count(*)::text FROM app.department$q$, '0');
SELECT set_config('app.tenant_id', 'a0000000-0000-0000-0000-000000000000', false);
SELECT set_config('app.institution_scope', 'ALL', false);
-- ST-01: tenant A sees only its own rows
SELECT public.expect_value('DB-02','K-002 tenant RLS: tenant A sees only A rows', $q$SELECT count(*)::text FROM app.department$q$, '2');
-- ST-03 / T-02: write with another tenant's id rejected by WITH CHECK
SELECT public.expect_fail('DB-03','K-002 WITH CHECK blocks writing a row for tenant B', $q$INSERT INTO app.department (tenant_id, department_id, institution_id, department_code, department_name, created_by, updated_by) VALUES ('b0000000-0000-0000-0000-000000000000', gen_random_uuid(), 'b0000000-0000-0000-0000-000000000201','X1','x','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'row-level security');
-- K-001: tenant A row pointing to tenant B parent → FK error
SELECT public.expect_fail('DB-04','K-001 composite FK blocks cross-tenant parent', $q$INSERT INTO app.program (tenant_id, program_id, institution_id, department_id, program_code, program_name, duration_years, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(), 'a0000000-0000-0000-0000-000000000201','b0000000-0000-0000-0000-000000000401','P2','Prog two',3,'00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'foreign key');
-- NEW-03: institution pinning — a program of institution A2 cannot hang off a department of A1
SELECT public.expect_fail('DB-05','NEW-03 composite FK pins institution', $q$INSERT INTO app.program (tenant_id, program_id, institution_id, department_id, program_code, program_name, duration_years, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(), 'a0000000-0000-0000-0000-000000000202','a0000000-0000-0000-0000-000000000401','P3','Prog three',3,'00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'foreign key');
-- PTD-I1 (proposed): restrictive institution policy
SELECT set_config('app.institution_scope', '', false);
SELECT set_config('app.institution_ids', 'a0000000-0000-0000-0000-000000000201', false);
SELECT public.expect_value('DB-06','PTD-I1 institution RLS limits rows to assigned institutions', $q$SELECT count(*)::text FROM app.department$q$, '1');
SELECT set_config('app.institution_scope', 'ALL', false);
-- C7: go-live gate
SELECT public.expect_fail('DB-07','NEW-01 go-live gate: no Principal leave approver', $q$UPDATE app.institution SET status='ACTIVE' WHERE institution_id='a0000000-0000-0000-0000-000000000201'$q$, 'GO_LIVE_BLOCKED');
SELECT public.expect_fail('DB-08','NEW-04 GBA approver must be a governance account [C6]', $q$INSERT INTO app.leave_approver_setting (tenant_id, leave_approver_setting_id, subject_role, institution_id, approver_kind, approver_user_id, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'PRINCIPAL','a0000000-0000-0000-0000-000000000201','GOVERNING_BODY_APPROVER','a0000000-0000-0000-0000-000000000101','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'APPROVER_KIND_MISMATCH');
SELECT public.expect_ok('DB-09','NEW-01 approver set (GBA)', $q$INSERT INTO app.leave_approver_setting (tenant_id, leave_approver_setting_id, subject_role, institution_id, approver_kind, approver_user_id, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', 'a0000000-0000-0000-0000-000000000f01','PRINCIPAL','a0000000-0000-0000-0000-000000000201','GOVERNING_BODY_APPROVER','a0000000-0000-0000-0000-000000000103','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$);
SELECT public.expect_ok('DB-10','NEW-01 activation allowed once approver set', $q$UPDATE app.institution SET status='ACTIVE' WHERE institution_id='a0000000-0000-0000-0000-000000000201'$q$);
SELECT public.expect_value('DB-11','NEW-01 go_live_blockers lists institution A2 only', $q$SELECT string_agg(blocker,',') FROM app.go_live_blockers('a0000000-0000-0000-0000-000000000000')$q$, 'PRINCIPAL_LEAVE_APPROVER_MISSING');
-- approval engine
SELECT public.expect_fail('DB-12','K-015 no self-approval', $q$UPDATE app.approval_step SET decision='APPROVED', acted_by_user_id='a0000000-0000-0000-0000-000000000101', acted_active_role='HOD', decided_at=now() WHERE approval_step_id='a0000000-0000-0000-0000-000000000a02'$q$, 'SELF_APPROVAL');
SELECT public.expect_fail('DB-13','K-016 reject needs a remark', $q$UPDATE app.approval_step SET decision='REJECTED', acted_by_user_id='a0000000-0000-0000-0000-000000000102', acted_active_role='HOD', decided_at=now() WHERE approval_step_id='a0000000-0000-0000-0000-000000000a02'$q$, 'check constraint');
SELECT public.expect_ok('DB-14','approve step 1', $q$UPDATE app.approval_step SET decision='APPROVED', acted_by_user_id='a0000000-0000-0000-0000-000000000102', acted_active_role='HOD', decided_at=now() WHERE approval_step_id='a0000000-0000-0000-0000-000000000a02'$q$);
SELECT public.expect_fail('DB-15','K-014 one actor cannot decide two steps', $q$UPDATE app.approval_step SET decision='APPROVED', acted_by_user_id='a0000000-0000-0000-0000-000000000102', acted_active_role='PRINCIPAL', decided_at=now() WHERE approval_step_id='a0000000-0000-0000-0000-000000000a03'$q$, 'duplicate key');
SELECT public.expect_fail('DB-16','K-017 decided step is immutable', $q$UPDATE app.approval_step SET remark='changed' WHERE approval_step_id='a0000000-0000-0000-0000-000000000a02'$q$, 'APPROVAL_IMMUTABLE');
SELECT public.expect_fail('DB-17','K-018 conditional update on PENDING (second decider updates 0 rows)', $q$DO $d$ DECLARE n int; BEGIN UPDATE app.approval_step SET decision='APPROVED' WHERE approval_step_id='a0000000-0000-0000-0000-000000000a02' AND decision='PENDING'; GET DIAGNOSTICS n = ROW_COUNT; IF n = 0 THEN RAISE EXCEPTION 'ALREADY_ACTIONED'; END IF; END $d$$q$, 'ALREADY_ACTIONED');
-- delegation
SELECT public.expect_ok('DB-18','delegation HOD -> other', $q$INSERT INTO app.delegation (tenant_id, delegation_id, delegator_user_id, delegate_user_id, delegation_basis, starts_at, ends_at, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000f10','a0000000-0000-0000-0000-000000000102','a0000000-0000-0000-0000-000000000104','SAME_OR_HIGHER', now(), now() + interval '5 days','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$);
SELECT public.expect_fail('DB-19','K-013 a delegate cannot delegate again [AP-08]', $q$INSERT INTO app.delegation (tenant_id, delegation_id, delegator_user_id, delegate_user_id, delegation_basis, starts_at, ends_at, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000104','a0000000-0000-0000-0000-000000000101','SAME_OR_HIGHER', now(), now() + interval '2 days','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'REDELEGATION');
SELECT public.expect_fail('DB-20','NEW-05 merit lists cannot be delegated [WF-37]', $q$INSERT INTO app.delegation (tenant_id, delegation_id, delegator_user_id, delegate_user_id, delegation_basis, process_code, starts_at, ends_at, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000101','a0000000-0000-0000-0000-000000000103','SAME_OR_HIGHER','MERIT_LIST', now(), now() + interval '2 days','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'DELEGATION_NOT_ALLOWED');
-- fee structure (finding 36)
SELECT public.expect_fail('DB-21','K-041 out-of-range line needs an approved exception', $q$UPDATE app.fee_structure_line SET amount = 60000 WHERE fee_structure_line_id='a0000000-0000-0000-0000-000000000805'$q$, 'FEE_OUT_OF_POLICY_RANGE');
SELECT public.expect_ok('DB-22','structure submitted (installments = lines)', $q$UPDATE app.fee_structure SET status='PENDING_APPROVAL', approval_request_id='a0000000-0000-0000-0000-000000000a05' WHERE fee_structure_id='a0000000-0000-0000-0000-000000000804'$q$);
SELECT public.expect_fail('DB-23','NEW-02 no line edit after DRAFT (finding 36)', $q$UPDATE app.fee_structure_line SET amount = 41000 WHERE fee_structure_line_id='a0000000-0000-0000-0000-000000000805'$q$, 'FEE_STRUCTURE_LOCKED');
SELECT public.expect_ok('DB-24','Principal approval -> LIVE', $q$UPDATE app.fee_structure SET status='LIVE' WHERE fee_structure_id='a0000000-0000-0000-0000-000000000804'$q$);
SELECT public.expect_fail('DB-25','NEW-02 LIVE structure content immutable', $q$UPDATE app.fee_structure SET applies_from_date='2026-07-01' WHERE fee_structure_id='a0000000-0000-0000-0000-000000000804'$q$, 'FEE_STRUCTURE_LOCKED');
SELECT public.expect_fail('DB-26','NEW-02 LIVE -> DRAFT forbidden', $q$UPDATE app.fee_structure SET status='DRAFT' WHERE fee_structure_id='a0000000-0000-0000-0000-000000000804'$q$, 'STATE_TRANSITION_NOT_ALLOWED');
-- student installment + payments
SELECT public.expect_ok('DB-27','fee assignment and installment', $q$
  INSERT INTO app.fee_assignment (tenant_id, fee_assignment_id, institution_id, student_fee_account_id, enrollment_id, fee_structure_id, effective_from, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000807','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000703','a0000000-0000-0000-0000-000000000702','a0000000-0000-0000-0000-000000000804','2026-06-01','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa');
  INSERT INTO app.student_installment (tenant_id, student_installment_id, institution_id, student_fee_account_id, fee_assignment_id, installment_no, due_date, billed_amount, created_by, updated_by) VALUES
   ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000808','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000703','a0000000-0000-0000-0000-000000000807',1,'2026-07-10',20000,'00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa'),
   ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000809','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000703','a0000000-0000-0000-0000-000000000807',2,'2026-11-10',20000,'00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa');
  INSERT INTO app.payment (tenant_id, payment_id, institution_id, student_fee_account_id, method, quoted_amount, request_key, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000901','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000703','GATEWAY',20000,'key-0000001','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$);
SELECT public.expect_fail('DB-28','NEW-06 one live online payment per account (PAYMENT_IN_PROGRESS)', $q$INSERT INTO app.payment (tenant_id, payment_id, institution_id, student_fee_account_id, method, quoted_amount, request_key, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000703','GATEWAY',20000,'key-0000002','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'duplicate key');
SELECT public.expect_fail('DB-29','K-044 same request key ignored (unique)', $q$INSERT INTO app.payment (tenant_id, payment_id, institution_id, student_fee_account_id, method, quoted_amount, request_key, status, amount, confirmed_via, marked_by_user_id, paid_at, allocated_amount, credit_added_amount, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000703','CASH',20000,'key-0000001','PAID',20000,'OFFLINE_ENTRY','a0000000-0000-0000-0000-000000000104',now(),20000,0,'00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'duplicate key');
SELECT public.expect_fail('DB-30','K-045 partial payment allocation refused [FE-04]', $q$INSERT INTO app.payment_allocation (tenant_id, payment_allocation_id, institution_id, payment_id, student_installment_id, allocated_amount, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000901','a0000000-0000-0000-0000-000000000808',15000,'00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'PARTIAL_PAYMENT_NOT_ALLOWED');
SELECT public.expect_fail('DB-31','NEW-07 PAID needs amount = allocated + credit', $q$UPDATE app.payment SET status='PAID', amount=25000, confirmed_via='MANUAL_REFERENCE', manual_gateway_reference='UTR1234', marked_by_user_id='a0000000-0000-0000-0000-000000000104', paid_at=now(), allocated_amount=20000, credit_added_amount=0 WHERE payment_id='a0000000-0000-0000-0000-000000000901'$q$, 'check constraint');
SELECT public.expect_ok('DB-32','PO-A1 confirm: 25000 received, installment 1 settled, 5000 credit', $q$
  INSERT INTO app.payment_allocation (tenant_id, payment_allocation_id, institution_id, payment_id, student_installment_id, allocated_amount, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000902','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000901','a0000000-0000-0000-0000-000000000808',20000,'00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa');
  UPDATE app.student_installment SET status='PAID', settled_by='PAYMENT', paid_at=now() WHERE student_installment_id='a0000000-0000-0000-0000-000000000808';
  UPDATE app.payment SET status='PAID', amount=25000, confirmed_via='MANUAL_REFERENCE', manual_gateway_reference='UTR1234', marked_by_user_id='a0000000-0000-0000-0000-000000000104', paid_at=now(), allocated_amount=20000, credit_added_amount=5000, amount_mismatch=true WHERE payment_id='a0000000-0000-0000-0000-000000000901';
  INSERT INTO app.ledger_entry (tenant_id, ledger_entry_id, institution_id, student_fee_account_id, academic_session_id, financial_year, posting_date, entry_type, direction, amount, student_installment_id, payment_id, created_by, updated_by) VALUES
   ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000d01','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000703','a0000000-0000-0000-0000-000000000301',0,'2026-10-20','PAYMENT','CREDIT',20000,'a0000000-0000-0000-0000-000000000808','a0000000-0000-0000-0000-000000000901','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa'),
   ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000d02','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000703','a0000000-0000-0000-0000-000000000301',0,'2026-10-20','CREDIT_ADDED','CREDIT',5000,NULL,'a0000000-0000-0000-0000-000000000901','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$);
SELECT public.expect_value('DB-33','PTD-34 credit balance = 5000 after overpayment', $q$SELECT credit_balance::text FROM app.v_student_fee_balance WHERE student_fee_account_id='a0000000-0000-0000-0000-000000000703'$q$, '5000.00');
SELECT public.expect_value('DB-34','K-042 financial year derived by trigger (Oct 2026 -> 2026)', $q$SELECT financial_year::text FROM app.ledger_entry WHERE ledger_entry_id='a0000000-0000-0000-0000-000000000d01'$q$, '2026');
SELECT public.expect_fail('DB-35','K-045 installment settled once', $q$INSERT INTO app.payment_allocation (tenant_id, payment_allocation_id, institution_id, payment_id, student_installment_id, allocated_amount, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000901','a0000000-0000-0000-0000-000000000808',20000,'00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'INSTALLMENT_ALREADY_PAID|duplicate key');
SELECT public.expect_fail('DB-36','K-042 ledger append-only (UPDATE)', $q$UPDATE app.ledger_entry SET amount = 1 WHERE ledger_entry_id='a0000000-0000-0000-0000-000000000d01'$q$, 'APPEND_ONLY|permission denied');
SELECT public.expect_fail('DB-37','K-042 ledger append-only (DELETE)', $q$DELETE FROM app.ledger_entry WHERE ledger_entry_id='a0000000-0000-0000-0000-000000000d01'$q$, 'APPEND_ONLY|permission denied');
SELECT public.expect_fail('DB-38','K-043 no posting into a locked month', $q$INSERT INTO app.ledger_entry (tenant_id, ledger_entry_id, institution_id, student_fee_account_id, academic_session_id, financial_year, posting_date, entry_type, direction, amount, fee_head_id, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000703','a0000000-0000-0000-0000-000000000301',0,'2026-08-15','CHARGE','DEBIT',100,'a0000000-0000-0000-0000-000000000801','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'PERIOD_LOCKED');
SELECT public.expect_fail('DB-39','K-042 no REFUND entry type [R2-21]', $q$INSERT INTO app.ledger_entry (tenant_id, ledger_entry_id, institution_id, student_fee_account_id, academic_session_id, financial_year, posting_date, entry_type, direction, amount, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000703','a0000000-0000-0000-0000-000000000301',0,'2026-10-20','REFUND','DEBIT',100,'00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'check constraint');
-- receipts
SELECT public.expect_ok('DB-40','receipt issued with gap-free number', $q$INSERT INTO app.receipt (tenant_id, receipt_id, institution_id, payment_id, financial_year, receipt_number, receipt_label, amount, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000d11','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000901',2026, app.next_receipt_number('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000201',2026::smallint),'A1/1',25000,'00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$);
SELECT public.expect_fail('DB-41','K-048 rolled-back receipt leaves no gap (number re-used)', $q$DO $d$ BEGIN PERFORM app.next_receipt_number('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000201',2026::smallint); RAISE EXCEPTION 'ROLLBACK_SIMULATED'; END $d$$q$, 'ROLLBACK_SIMULATED');
SELECT public.expect_value('DB-42','K-048 next number after rollback is 2', $q$SELECT (last_number + 1)::text FROM app.receipt_sequence WHERE receipt_sequence_id='a0000000-0000-0000-0000-000000000d10'$q$, '2');
SELECT public.expect_fail('DB-43','K-047 receipt never edited', $q$UPDATE app.receipt SET amount = 1 WHERE receipt_id='a0000000-0000-0000-0000-000000000d11'$q$, 'RECEIPT_IMMUTABLE');
SELECT public.expect_fail('DB-44','K-047 second ISSUED receipt for the same payment', $q$INSERT INTO app.receipt (tenant_id, receipt_id, institution_id, payment_id, financial_year, receipt_number, receipt_label, amount, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000901',2026, 99,'A1/99',25000,'00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'duplicate key');
-- attendance and term lock
SELECT public.expect_ok('DB-45','attendance session + entry', $q$
  INSERT INTO app.attendance_session (tenant_id, attendance_session_id, institution_id, section_id, session_kind, attendance_date, academic_year, marked_by_user_id, marked_as, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000c01','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000504','PERIOD','2026-10-21',2026,'a0000000-0000-0000-0000-000000000101','ASSIGNED','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa');
  INSERT INTO app.attendance_entry (tenant_id, attendance_entry_id, institution_id, attendance_session_id, enrollment_id, academic_year, status, recorded_by_user_id, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000c02','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000c01','a0000000-0000-0000-0000-000000000702',2026,'ABSENT','a0000000-0000-0000-0000-000000000101','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$);
SELECT public.expect_fail('DB-46','K-024 one session per slot per day', $q$INSERT INTO app.attendance_session (tenant_id, attendance_session_id, institution_id, section_id, session_kind, attendance_date, academic_year, marked_by_user_id, marked_as, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000504','PERIOD','2026-10-21',2026,'a0000000-0000-0000-0000-000000000101','ASSIGNED','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'duplicate key');
SELECT public.expect_fail('DB-47','K-026 entry status mirrors session (NOT_HELD)', $q$UPDATE app.attendance_entry SET status='NOT_HELD' WHERE attendance_entry_id='a0000000-0000-0000-0000-000000000c02'$q$, 'NOT_HELD_MISMATCH');
SELECT public.expect_ok('DB-48','Principal locks the term', $q$INSERT INTO app.term_lock (tenant_id, term_lock_id, institution_id, academic_session_id, lock_state, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000c03','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000301','LOCKED','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$);
SELECT public.expect_fail('DB-49','K-027 no attendance change while term locked', $q$UPDATE app.attendance_entry SET status='PRESENT' WHERE attendance_entry_id='a0000000-0000-0000-0000-000000000c02'$q$, 'TERM_LOCKED');
SELECT public.expect_fail('DB-50','K-029 unlock > 3 days refused', $q$INSERT INTO app.term_lock_event (tenant_id, term_lock_event_id, institution_id, term_lock_id, event_type, actor_user_id, reason, unlock_until, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000c03','UNLOCK','a0000000-0000-0000-0000-000000000102','fix', now() + interval '4 days','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'check constraint');
-- notes and audit immutability
SELECT public.expect_fail('DB-51','K-053 note cannot be edited [MD-19]', $q$UPDATE app.student_note SET body='x' WHERE student_note_id='a0000000-0000-0000-0000-000000000e01'$q$, 'NOTE_IMMUTABLE|permission denied');
SELECT public.expect_fail('DB-52','K-054 app role cannot delete a note', $q$DELETE FROM app.student_note WHERE student_note_id='a0000000-0000-0000-0000-000000000e01'$q$, 'NOTE_IMMUTABLE|permission denied');
SELECT public.expect_fail('DB-53','K-053 audit rows immutable for the app role', $q$UPDATE app.audit_outbox SET outcome='DENIED' WHERE audit_event_id='a0000000-0000-0000-0000-000000000e02'$q$, 'AUDIT_IMMUTABLE|permission denied');
-- leave overlap
SELECT public.expect_value('DB-54','ref data: rows visible to app role', $q$SELECT count(*)::text FROM ref.approval_process$q$, '2');
RESET ROLE;
SELECT outcome, count(*) FROM public.test_result GROUP BY outcome ORDER BY outcome;
SELECT test_id, rule, outcome, detail FROM public.test_result ORDER BY id;
