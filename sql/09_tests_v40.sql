-- EDU 360 V4.0 — additional executable database acceptance tests DB-60 … DB-79.
-- Run AFTER 04_tests.sql and 07_tests_extra.sql in the same database (re-uses their seed for tenant A and public.expect_*).
-- Purpose: exercise constraint-catalogue rules that V3.3 recorded as "Implemented, Not executed" (finding V4-F-15)
-- and the V4.0 rule hardening in 08_v40_rules.sql (V4-DB-01). Every test runs as edu_app with RLS forced.
\set ON_ERROR_STOP 1
SET client_min_messages = warning;

-- ------------------------------------------------------------------ extra seed (superuser)
\set M 'created_by, updated_by'
\set V '''00000000-0000-0000-0000-0000000000aa'',''00000000-0000-0000-0000-0000000000aa'''
INSERT INTO ref.standard_role (role_code, role_name, role_level, introduced_in_version, requires_two_step_login, is_staff) VALUES
 ('PRINCIPAL_COLLEGE','Principal (College)','COLLEGE','≤3.2',true,true), ('REGISTRAR','Registrar','UNIVERSITY','≤3.2',true,true)
 ON CONFLICT (role_code) DO NOTHING;   -- present already when 05_ref_data.sql was loaded
INSERT INTO app.user_account (tenant_id, user_account_id, user_type, display_name, email, status, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000105','STAFF','Principal A','pr@a.in','ACTIVE', :V),
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000106','STAFF','Plain staff A','ps@a.in','ACTIVE', :V);
INSERT INTO app.role_definition (tenant_id, role_definition_id, role_code, role_name, role_origin, role_level, base_standard_role, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000001001','PRINCIPAL_COLLEGE','Principal (College)','STANDARD','COLLEGE','PRINCIPAL_COLLEGE', :V);
INSERT INTO app.user_role_assignment (tenant_id, user_role_assignment_id, user_account_id, role_definition_id, scope_type, institution_id, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000001002','a0000000-0000-0000-0000-000000000105','a0000000-0000-0000-0000-000000001001','INSTITUTION','a0000000-0000-0000-0000-000000000202', :V);
INSERT INTO app.institution (tenant_id, institution_id, institution_type, institution_code, institution_name, status, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000000203','SCHOOL','SCH01','School A3','DRAFT', :V);
INSERT INTO app.staff_profile (tenant_id, staff_profile_id, staff_code, full_name, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000001101','STF-0001','Teacher A', :V);
INSERT INTO app.leave_type (tenant_id, leave_type_id, institution_id, audience, leave_code, leave_name, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000001102','a0000000-0000-0000-0000-000000000201','STAFF','CASUAL','Casual leave', :V);
INSERT INTO app.grading_scheme_version (tenant_id, grading_scheme_version_id, scheme_name, version_no, effective_from, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000001201','Ten-point',1,'2026-06-01', :V);
INSERT INTO app.grade_band (tenant_id, grade_band_id, grading_scheme_version_id, lower_percent, grade_label, grade_points, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000001202','a0000000-0000-0000-0000-000000001201',90,'O',10, :V);
INSERT INTO app.complaint (tenant_id, complaint_id, institution_id, raised_by_user_id, description, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000001301','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000101','Projector broken', :V);
INSERT INTO ref.notification_template (trigger_code, title_template, default_channels, priority_class) VALUES ('TEST','Test',ARRAY['IN_APP'],'NORMAL') ON CONFLICT DO NOTHING;
INSERT INTO app.notification (tenant_id, notification_id, recipient_user_id, trigger_code, title, :M) VALUES
 ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000001401','a0000000-0000-0000-0000-000000000101','TEST','Hello', :V);

SET ROLE edu_app;
SELECT set_config('app.tenant_id', 'a0000000-0000-0000-0000-000000000000', false);
SELECT set_config('app.institution_scope', 'ALL', false);

-- K-022 unique student_code per tenant
SELECT public.expect_fail('DB-60','K-022 student code unique per tenant', $q$INSERT INTO app.student_profile (tenant_id, student_profile_id, institution_id, student_code, full_name, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000201','ENG26-00001','Duplicate','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'duplicate key');
-- K-008 unique staff email per tenant
SELECT public.expect_fail('DB-61','K-008 staff email unique per tenant', $q$INSERT INTO app.user_account (tenant_id, user_account_id, user_type, display_name, email, status, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'STAFF','Copy','t1@a.in','ACTIVE','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'duplicate key');
-- K-019 a department can only hang off a COLLEGE institution
SELECT public.expect_fail('DB-62','K-019 department cannot belong to a school', $q$INSERT INTO app.department (tenant_id, department_id, institution_id, department_code, department_name, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000203','SCI','Science','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'foreign key|check constraint|violates');
-- K-031 no overlapping full-day leave for one person
SELECT public.expect_ok('DB-63','leave request (pending, full day)', $q$INSERT INTO app.leave_request (tenant_id, leave_request_id, institution_id, requested_by_user_id, for_staff_profile_id, leave_type_id, unit, start_date, end_date, reason, days_counted, process_code, status, submitted_at, approval_request_id, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000001103','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000101','a0000000-0000-0000-0000-000000001101','a0000000-0000-0000-0000-000000001102','FULL_DAY','2026-11-02','2026-11-04','Family function',3,'TEST_CHAIN','PENDING', now(), 'a0000000-0000-0000-0000-000000000a01','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$);
SELECT public.expect_fail('DB-64','K-031 overlapping full-day leave refused [LV-05]', $q$INSERT INTO app.leave_request (tenant_id, leave_request_id, institution_id, requested_by_user_id, for_staff_profile_id, leave_type_id, unit, start_date, end_date, reason, days_counted, process_code, status, submitted_at, approval_request_id, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000101','a0000000-0000-0000-0000-000000001101','a0000000-0000-0000-0000-000000001102','FULL_DAY','2026-11-04','2026-11-05','Overlap',2,'TEST_CHAIN','PENDING', now(), 'a0000000-0000-0000-0000-000000000a01','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'conflicting key|exclusion|check constraint');
-- K-030 exactly one subject (student or staff)
SELECT public.expect_fail('DB-65','K-030 leave needs exactly one subject', $q$INSERT INTO app.leave_request (tenant_id, leave_request_id, institution_id, requested_by_user_id, leave_type_id, unit, start_date, end_date, reason, days_counted, process_code, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000101','a0000000-0000-0000-0000-000000001102','FULL_DAY','2026-12-01','2026-12-01','No subject',1,'TEST_CHAIN','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'ck_leave_request_r1');
-- K-033 grade bands frozen once the scheme version leaves DRAFT
SELECT public.expect_ok('DB-66','grading scheme version activated', $q$UPDATE app.grading_scheme_version SET status='ACTIVE' WHERE grading_scheme_version_id='a0000000-0000-0000-0000-000000001201'$q$);
SELECT public.expect_fail('DB-67','K-033 band of an ACTIVE scheme cannot change [PQ-38]', $q$UPDATE app.grade_band SET grade_points = 9 WHERE grade_band_id='a0000000-0000-0000-0000-000000001202'$q$, 'GRADING_SCHEME_FROZEN');
SELECT public.expect_fail('DB-68','K-033 no new band on an ACTIVE scheme', $q$INSERT INTO app.grade_band (tenant_id, grade_band_id, grading_scheme_version_id, lower_percent, grade_label, grade_points, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000001201',80,'A+',9,'00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'GRADING_SCHEME_FROZEN');
-- K-028 correction lines frozen once the request leaves DRAFT (the term is unlocked first so only K-028 can refuse)
RESET ROLE;
UPDATE app.term_lock SET lock_state='TEMP_UNLOCKED', unlock_until=now() + interval '1 day' WHERE term_lock_id='a0000000-0000-0000-0000-000000000c03';
SET ROLE edu_app;
SELECT public.expect_ok('DB-69','correction request (DRAFT) with one line', $q$
  INSERT INTO app.attendance_correction_request (tenant_id, attendance_correction_request_id, institution_id, attendance_session_id, requested_by_user_id, reason, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000001501','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000000c01','a0000000-0000-0000-0000-000000000101','Marked absent by mistake','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa');
  INSERT INTO app.attendance_correction (tenant_id, attendance_correction_id, institution_id, attendance_correction_request_id, attendance_entry_id, academic_year, old_status, new_status, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000','a0000000-0000-0000-0000-000000001502','a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000001501','a0000000-0000-0000-0000-000000000c02',2026,'ABSENT','PRESENT','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$);
SELECT public.expect_fail('DB-70','K-028 old status must differ from new', $q$INSERT INTO app.attendance_correction (tenant_id, attendance_correction_id, institution_id, attendance_correction_request_id, attendance_entry_id, academic_year, old_status, new_status, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000201','a0000000-0000-0000-0000-000000001501','a0000000-0000-0000-0000-000000000c02',2026,'ABSENT','ABSENT','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'ck_attendance_correction_r1');
SELECT public.expect_ok('DB-71','correction request submitted (REQUESTED, approval linked)', $q$UPDATE app.attendance_correction_request SET status='REQUESTED', approval_request_id='a0000000-0000-0000-0000-000000000a01' WHERE attendance_correction_request_id='a0000000-0000-0000-0000-000000001501'$q$);
SELECT public.expect_fail('DB-72','K-028 correction lines frozen after DRAFT [PQ-33]', $q$UPDATE app.attendance_correction SET new_status='ON_APPROVED_LEAVE' WHERE attendance_correction_id='a0000000-0000-0000-0000-000000001502'$q$, 'CORRECTION_LOCKED');
-- K-049 a period lock is always the first day of the month
SELECT public.expect_fail('DB-73','K-049 period month must start on day 1', $q$INSERT INTO app.financial_period_lock (tenant_id, period_lock_id, institution_id, period_month, lock_state, locked_by, locked_at, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000000201','2026-09-15','LOCKED','a0000000-0000-0000-0000-000000000104', now(),'00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'ck_financial_period_lock_period_month');
-- K-052 WhatsApp is not a Phase 1 channel
SELECT public.expect_fail('DB-74','K-052 WhatsApp delivery channel refused [CM-07]', $q$INSERT INTO app.notification_delivery (tenant_id, delivery_id, notification_id, channel, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'a0000000-0000-0000-0000-000000001401','WHATSAPP','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'ck_notification_delivery_channel');
-- K-055 a RESOLVED complaint is final
SELECT public.expect_ok('DB-75','complaint resolved with a note', $q$UPDATE app.complaint SET status='RESOLVED', resolved_at=now(), resolution_note='Replaced' WHERE complaint_id='a0000000-0000-0000-0000-000000001301'$q$);
SELECT public.expect_fail('DB-76','K-055 a resolved complaint is never reopened [CP-04]', $q$UPDATE app.complaint SET status='OPEN', resolved_at=NULL, resolution_note=NULL WHERE complaint_id='a0000000-0000-0000-0000-000000001301'$q$, 'COMPLAINT_FINAL');
-- V4-DB-01 leave-approver setting hardening (08_v40_rules.sql)
SELECT public.expect_fail('DB-77','V4-DB-01 the Principal is never their own leave approver [D-002]', $q$INSERT INTO app.leave_approver_setting (tenant_id, leave_approver_setting_id, subject_role, institution_id, approver_kind, approver_user_id, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'PRINCIPAL','a0000000-0000-0000-0000-000000000202','REGISTRAR','a0000000-0000-0000-0000-000000000105','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'APPROVER_IS_SUBJECT');
SELECT public.expect_fail('DB-78','V4-DB-01 Registrar kind needs an active Registrar assignment [A30]', $q$INSERT INTO app.leave_approver_setting (tenant_id, leave_approver_setting_id, subject_role, institution_id, approver_kind, approver_user_id, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'PRINCIPAL','a0000000-0000-0000-0000-000000000202','REGISTRAR','a0000000-0000-0000-0000-000000000106','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$, 'APPROVER_ROLE_MISSING');
SELECT public.expect_ok('DB-79','V4-DB-01 governance account still accepted for institution A2 (regression of DB-09)', $q$INSERT INTO app.leave_approver_setting (tenant_id, leave_approver_setting_id, subject_role, institution_id, approver_kind, approver_user_id, created_by, updated_by) VALUES ('a0000000-0000-0000-0000-000000000000', gen_random_uuid(),'PRINCIPAL','a0000000-0000-0000-0000-000000000202','GOVERNING_BODY_APPROVER','a0000000-0000-0000-0000-000000000103','00000000-0000-0000-0000-0000000000aa','00000000-0000-0000-0000-0000000000aa')$q$);
RESET ROLE;
SELECT outcome, count(*) FROM public.test_result WHERE test_id >= 'DB-60' GROUP BY outcome ORDER BY outcome;
SELECT test_id, rule, outcome, detail FROM public.test_result WHERE test_id >= 'DB-60' ORDER BY id;
