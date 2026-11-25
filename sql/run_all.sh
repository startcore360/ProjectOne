#!/usr/bin/env bash
# EDU 360 V4.0 — build and verify the database in two throw-away PostgreSQL databases (never a production database).
# Usage: PSQL="psql -h localhost -U postgres" ./run_all.sh   (needs PostgreSQL 15+; validated on 16.15)
set -euo pipefail
PSQL=${PSQL:-psql}; D=$(cd "$(dirname "$0")" && pwd); OUT=${OUT:-$D/run_results.txt}
run(){ $PSQL -q -v ON_ERROR_STOP=1 -d "$1" -f "$D/$2"; }
: > "$OUT"
echo "# EDU 360 V4.0 database build and test run — $(date -u +%FT%TZ)" >> "$OUT"
$PSQL -d postgres -qc "DROP DATABASE IF EXISTS edu360_v4_test WITH (FORCE)" -qc "CREATE DATABASE edu360_v4_test"
$PSQL -d postgres -qc "DROP DATABASE IF EXISTS edu360_v4_ref WITH (FORCE)"  -qc "CREATE DATABASE edu360_v4_ref"
# A. acceptance tests (tests seed their own minimal reference rows)
for f in 01_schema.sql 02_rules.sql 03_bindings.sql 03b_fk_indexes.sql 08_v40_rules.sql 10_v40_rolldown.sql 12_v41_platform_roles.sql 14_v41_audit_foundation.sql 15_v41_user_active_role.sql 16_v41_tenant_top_post_authority.sql 18_v41_user_sessions.sql 20_v41_never_allowed_guard.sql 22_v41_platform_audit_event_type.sql 24_v41_sign_in_security.sql 04_tests.sql; do run edu360_v4_test $f > /dev/null; done
$PSQL -q -d edu360_v4_test -f "$D/25t_seed_sprint2_refs.sql" > /dev/null  # Minimal reference fixture for Sprint 2 migration checks
run edu360_v4_test 25_sprint2_foundations.sql > /dev/null
run edu360_v4_test 26_v41_student_login_switch_guard.sql > /dev/null
run edu360_v4_test 27_v41_notification_read_state.sql > /dev/null
run edu360_v4_test 28_v41_study_material_view.sql > /dev/null
run edu360_v4_test 29_v41_institution_code_formats.sql > /dev/null
run edu360_v4_test 30_v41_exam_day_event_type.sql > /dev/null
run edu360_v4_test 32_v41_staff_login_aliases.sql > /dev/null
run edu360_v4_test 40_v41_support_session.sql > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/40t_tests_support_session.sql" > /dev/null  # E4 support-session storage and OD-15 categories
run edu360_v4_test 36_v41_plan_commercial.sql > /dev/null
run edu360_v4_test 38_v41_invoice_approval_and_renewal.sql > /dev/null
run edu360_v4_test 39_v41_billing_settings.sql > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/38t_tests_invoice_approval_and_renewal.sql" > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/39t_tests_billing_settings.sql" > /dev/null
run edu360_v4_test 49_v41_platform_notification.sql > /dev/null
run edu360_v4_test 50_v41_platform_audit_payload_idempotency.sql > /dev/null
run edu360_v4_test 51_v41_mfa_recovery.sql > /dev/null  # MFA-01 two-step login recovery
run edu360_v4_test 52_v41_tenant_branding_logo.sql > /dev/null  # E1-S4 tenant logo storage
run edu360_v4_test 53_v41_platform_approval_delegation.sql > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/53t_tests_platform_approval_delegation.sql" > /dev/null
run edu360_v4_test 44_v41_release_scheduling.sql > /dev/null  # E7-S1 release scheduling
$PSQL -q -d edu360_v4_test -f "$D/44t_tests_release_scheduling.sql" > /dev/null
run edu360_v4_test 45_v41_usage_rollup.sql > /dev/null  # E6-S1 usage rollup
$PSQL -q -d edu360_v4_test -f "$D/45t_tests_usage_rollup.sql" > /dev/null
run edu360_v4_test 54_v41_subscription_commercial_engine.sql > /dev/null
run edu360_v4_test 54t_tests_subscription_commercial_engine.sql > /dev/null
run edu360_v4_test 55_v41_term_lock_engine.sql > /dev/null
run edu360_v4_test 56_v41_rolldown_management_revocation.sql > /dev/null
run edu360_v4_test 57_v41_academic_hierarchy_constraints.sql > /dev/null
run edu360_v4_test 58_v41_department_program_section_schema.sql > /dev/null
run edu360_v4_test 61_v41_student_profile_and_guardians.sql > /dev/null
run edu360_v4_test 62_v41_student_notes_and_legal_holds.sql > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/61t_tests_student_profile_guardians.sql" > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/62t_tests_student_notes_legal_holds.sql" > /dev/null
run edu360_v4_test 63_v41_admission_cycles_and_applications.sql > /dev/null
run edu360_v4_test 64_v41_merit_list_and_seat_allocation.sql > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/57_58_migration_smoke_tests.sql" > /dev/null
run edu360_v4_test 59_v41_staff_qualification_and_leave_indexes.sql > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/59t_tests_staff_qualification_and_leave.sql" > /dev/null
run edu360_v4_test 60_v41_leave_delegation.sql > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/60t_tests_leave_delegation.sql" > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/57_58_migration_smoke_tests.sql" > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/55t_tests_term_lock_engine.sql" > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/56t_tests_rolldown_management_revocation.sql" > /dev/null
run edu360_v4_test 42_v41_deletion_retention_waiver.sql > /dev/null  # E5-S2 deletion retention waiver
$PSQL -q -d edu360_v4_test -f "$D/42t_tests_deletion_retention_waiver.sql" > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/52t_tests_tenant_branding_logo.sql" > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/36t_tests_plan_commercial.sql" > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/49t_tests_platform_notification.sql" > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/50t_tests_platform_audit_payload_idempotency.sql" > /dev/null
$PSQL -q -d edu360_v4_test -f "$D/07_tests_extra.sql" > /dev/null      # new connection: settings never set (DB-55..59)
$PSQL -q -d edu360_v4_test -f "$D/09_tests_v40.sql"  > /dev/null      # V4.0 tests DB-60..79
$PSQL -q -d edu360_v4_test -f "$D/11_tests_rolldown.sql" > /dev/null  # V4.0 role roll-down tests DB-80..97
$PSQL -q -d edu360_v4_test -f "$D/13_tests_platform_roles.sql" > /dev/null  # V4.1 platform role tests DB-98..112
$PSQL -q -d edu360_v4_test -f "$D/17_tests_tenant_top_post.sql" > /dev/null  # V4.1 tenant top post tests DB-113..125
$PSQL -q -d edu360_v4_test -f "$D/19_tests_user_sessions.sql" > /dev/null  # V4.1 multi-device session tests DB-126..137 (SES-01)
$PSQL -q -d edu360_v4_test -f "$D/21_tests_audit_guard_v41.sql" > /dev/null  # V4.1 B6 tests DB-150..201 (Q-01 tenant audit, Q-04 never-allowed, Q-05 files 14-16)
$PSQL -q -d edu360_v4_test -f "$D/22t_tests_platform_b4.sql" > /dev/null  # V4.1 B4 tests DB-210..212 (file 22)
$PSQL -q -d edu360_v4_test -f "$D/25t_tests_sprint2_foundations.sql" > /dev/null  # Sprint 2 Chunk 1 SQL checks
$PSQL -q -d edu360_v4_test -f "$D/26t_tests_student_login_switch_guard.sql" > /dev/null  # Sprint 2 A8 login-switch guard
$PSQL -q -d edu360_v4_test -f "$D/27t_tests_notification_read_state.sql" > /dev/null  # B9 notification read-state checks
$PSQL -q -d edu360_v4_test -f "$D/28t_tests_study_material_view.sql" > /dev/null  # B5 student/guardian material access action
$PSQL -q -d edu360_v4_test -f "$D/29t_tests_institution_code_formats.sql" > /dev/null  # B3.0 code-format storage
$PSQL -q -d edu360_v4_test -f "$D/30t_tests_exam_day_event_type.sql" > /dev/null  # DEC-23 typed exam days
$PSQL -q -d edu360_v4_test -f "$D/32t_tests_staff_login_aliases.sql" > /dev/null  # Verified secondary staff login aliases
$PSQL -At -d edu360_v4_test -c "SELECT test_id||' | '||outcome||' | '||rule||' | '||detail FROM public.test_result ORDER BY id" >> "$OUT"
$PSQL -At -d edu360_v4_test -c "SELECT 'SUMMARY | '||outcome||' | '||count(*) FROM public.test_result GROUP BY outcome" >> "$OUT"
# B. full V3.3 reference data and count checks (AC-S5), plus idempotent second load
for f in 01_schema.sql 02_rules.sql 03_bindings.sql 03b_fk_indexes.sql 08_v40_rules.sql 10_v40_rolldown.sql 05_ref_data.sql; do run edu360_v4_ref $f > /dev/null; done
$PSQL -At -d edu360_v4_ref -f "$D/06_ref_checks.sql" | sed 's/^/REFCHECK | /' >> "$OUT"   # V3.3 baseline counts
run edu360_v4_ref 10d_v40_rolldown_ref.sql > /dev/null
run edu360_v4_ref 29_v41_institution_code_formats.sql > /dev/null
run edu360_v4_ref 31_v41_staff_record_view_grants.sql > /dev/null
run edu360_v4_ref 32_v41_staff_login_aliases.sql > /dev/null
run edu360_v4_ref 33_v41_study_material_leadership_grants.sql > /dev/null
run edu360_v4_ref 40_v41_support_session.sql > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/40t_tests_support_session.sql" > /dev/null
run edu360_v4_ref 41_v41_support_readable.sql > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/41t_tests_support_readable.sql" > /dev/null
run edu360_v4_ref 49_v41_platform_notification.sql > /dev/null
run edu360_v4_ref 50_v41_platform_audit_payload_idempotency.sql > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/49t_tests_platform_notification.sql" > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/50t_tests_platform_audit_payload_idempotency.sql" > /dev/null
run edu360_v4_ref 36_v41_plan_commercial.sql > /dev/null
run edu360_v4_ref 54_v41_subscription_commercial_engine.sql > /dev/null
run edu360_v4_ref 55_v41_term_lock_engine.sql > /dev/null
run edu360_v4_ref 56_v41_rolldown_management_revocation.sql > /dev/null
run edu360_v4_ref 57_v41_academic_hierarchy_constraints.sql > /dev/null
run edu360_v4_ref 58_v41_department_program_section_schema.sql > /dev/null
run edu360_v4_ref 61_v41_student_profile_and_guardians.sql > /dev/null
run edu360_v4_ref 62_v41_student_notes_and_legal_holds.sql > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/61t_tests_student_profile_guardians.sql" > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/62t_tests_student_notes_legal_holds.sql" > /dev/null
run edu360_v4_ref 63_v41_admission_cycles_and_applications.sql > /dev/null
run edu360_v4_ref 64_v41_merit_list_and_seat_allocation.sql > /dev/null
run edu360_v4_ref 65_v41_landing_cms_and_enquiries.sql > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/65t_tests_landing_cms_and_enquiries.sql" > /dev/null
run edu360_v4_ref 59_v41_staff_qualification_and_leave_indexes.sql > /dev/null
run edu360_v4_ref 60_v41_leave_delegation.sql > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/59t_tests_staff_qualification_and_leave.sql" > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/60t_tests_leave_delegation.sql" > /dev/null
run edu360_v4_ref 37_v41_feature_catalog_and_provisioning.sql > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/37t_tests_feature_catalog_and_provisioning.sql" > /dev/null
run edu360_v4_ref 38_v41_invoice_approval_and_renewal.sql > /dev/null
run edu360_v4_ref 39_v41_billing_settings.sql > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/38t_tests_invoice_approval_and_renewal.sql" > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/39t_tests_billing_settings.sql" > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/36t_tests_plan_commercial.sql" > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/33t_tests_study_material_leadership_grants.sql" > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/34t_tests_staff_record_view_hr_grants.sql" > /dev/null                                          # V4.0 additions
$PSQL -At -d edu360_v4_ref >> "$OUT" <<'SQL'
SELECT 'REFCHECK | V4.0 actions (279 + 2 roll-down + 1 MFA reset + 1 RD-G8 revocation)|'||count(*)||'|283' FROM ref.action_catalog;
SELECT 'REFCHECK | role-action pairs (1851 + 12 roll-down + 3 HR + 4 leadership)|'||count(*)||'|1870' FROM ref.standard_role_action;
SELECT 'REFCHECK | roll-down default rows|'||count(*)||'|182' FROM ref.role_rolldown_default;
SELECT 'REFCHECK | roles with a roll-down|'||count(DISTINCT missing_role_code)||'|52' FROM ref.role_rolldown_default;
SELECT 'REFCHECK | roll-down stand-ins that are personal / platform / GBA roles|'||count(*)||'|0' FROM ref.role_rolldown_default d JOIN ref.standard_role r ON r.role_code = d.stand_in_role_code WHERE r.role_level IN ('PLATFORM','EXTERNAL') OR d.stand_in_role_code IN ('GOVERNING_BODY_APPROVER','STUDENT_COLLEGE','PARENT_COLLEGE','STUDENT_SCHOOL','PARENT_SCHOOL');
SQL
CNT="SELECT sum((xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', schemaname, tablename), false, true, '')))[1]::text::bigint) FROM pg_tables WHERE schemaname='ref'"
before=$($PSQL -At -d edu360_v4_ref -c "$CNT")
run edu360_v4_ref 05_ref_data.sql > /dev/null; run edu360_v4_ref 10d_v40_rolldown_ref.sql > /dev/null
after=$($PSQL -At -d edu360_v4_ref -c "$CNT")
echo "REFCHECK | reference rows after first load | $before" >> "$OUT"
echo "REFCHECK | second load of 05_ref_data.sql + 10d inserted rows | $((after-before)) | 0" >> "$OUT"
run edu360_v4_ref 25_sprint2_foundations.sql > /dev/null  # Sprint 2 foundation defaults and role grants
run edu360_v4_ref 30_v41_exam_day_event_type.sql > /dev/null  # DEC-23 typed exam days
run edu360_v4_ref 31_v41_staff_record_view_grants.sql > /dev/null  # STAFF_RECORD_VIEW for HR record managers
# C. catalogue counts
$PSQL -At -d edu360_v4_ref >> "$OUT" <<'SQL'
SELECT 'CATALOGUE | parent tables | '||count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE relkind IN ('r','p') AND NOT relispartition AND nspname IN ('app','platform','ref');
SELECT 'CATALOGUE | partitions | '||count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE relispartition AND nspname IN ('app','platform','ref');
SELECT 'CATALOGUE | columns on parent tables | '||count(*) FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE a.attnum>0 AND NOT a.attisdropped AND relkind IN ('r','p') AND NOT relispartition AND nspname IN ('app','platform','ref');
SELECT 'CATALOGUE | declared foreign keys (conparentid = 0) | '||count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE contype='f' AND conparentid=0 AND nspname IN ('app','platform','ref');
SELECT 'CATALOGUE | all foreign-key rows incl. partition clones | '||count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE contype='f' AND nspname IN ('app','platform','ref');
SELECT 'CATALOGUE | check constraints | '||count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE contype='c' AND nspname IN ('app','platform','ref');
SELECT 'CATALOGUE | RLS policies | '||count(*) FROM pg_policies WHERE schemaname IN ('app','platform','ref');
SELECT 'CATALOGUE | user triggers | '||count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE NOT tgisinternal AND nspname IN ('app','platform','ref');
SELECT 'CATALOGUE | app tables with RLS forced | '||count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE relforcerowsecurity AND nspname='app';
SELECT 'CATALOGUE | FKs without a covering index (AC-S4) | '||count(*) FROM pg_constraint c WHERE contype='f' AND conparentid=0 AND connamespace::regnamespace::text IN ('app','platform','ref') AND NOT EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid=c.conrelid AND (i.indkey::int2[])[0:array_length(c.conkey,1)-1] @> c.conkey AND (i.indkey::int2[])[0:array_length(c.conkey,1)-1] <@ c.conkey);
SELECT 'CATALOGUE | FKs with CASCADE / SET NULL (AC-R2) | '||count(*) FROM pg_constraint WHERE contype='f' AND confdeltype IN ('c','n','d') AND connamespace::regnamespace::text IN ('app','platform','ref');
SELECT 'CATALOGUE | edu_ roles with superuser or bypassrls (AC-I3) | '||count(*) FROM pg_roles WHERE rolname LIKE 'edu\_%' AND (rolsuper OR rolbypassrls);
SQL
# C. V4.1 platform roles on the full reference data, plus an idempotent second load of the templates
for f in 12_v41_platform_roles.sql 12d_v41_platform_role_templates.sql 14_v41_audit_foundation.sql 15_v41_user_active_role.sql 16_v41_tenant_top_post_authority.sql 18_v41_user_sessions.sql 20_v41_never_allowed_guard.sql 22_v41_platform_audit_event_type.sql 24_v41_sign_in_security.sql 24d_v41_action_module_map.sql 26_v41_student_login_switch_guard.sql 27_v41_notification_read_state.sql 28_v41_study_material_view.sql; do run edu360_v4_ref $f > /dev/null; done
$PSQL -At -d edu360_v4_ref -c "SELECT 'REFCHECK | platform role templates | '||count(*)||' | 5' FROM platform.platform_role_template" >> "$OUT"
$PSQL -At -d edu360_v4_ref -c "SELECT 'REFCHECK | platform template actions with a reserved action outside the locked template | '||count(*)||' | 0' FROM platform.platform_role_template_action a JOIN platform.platform_role_template t USING (platform_role_template_id) WHERE NOT t.is_locked AND a.action_code IN ('TENANT_EXPORT_APPROVE','TENANT_DELETION_APPROVE','RETENTION_CHANGE_APPROVE','APPROVAL_DELEGATE','PLATFORM_USER_MANAGE','TENANT_DELETION_CONFIRM')" >> "$OUT"
before=$($PSQL -At -d edu360_v4_ref -c "SELECT count(*) FROM platform.platform_role_template_action")
run edu360_v4_ref 12d_v41_platform_role_templates.sql > /dev/null
after=$($PSQL -At -d edu360_v4_ref -c "SELECT count(*) FROM platform.platform_role_template_action")
echo "REFCHECK | second load of 12d inserted rows | $((after-before)) | 0" >> "$OUT"
# Q-04: the never-allowed guard must not contradict the register (05_ref_data.sql).
$PSQL -At -d edu360_v4_ref -c "SELECT 'REFCHECK | tenant-level standard roles holding a DATA_PROTECTION action | '||count(*)||' | 0' FROM ref.standard_role_action s JOIN ref.standard_role r USING (role_code) WHERE r.role_level <> 'PLATFORM' AND app.fn_never_allowed_class(s.action_code) = 'DATA_PROTECTION'" >> "$OUT"
run edu360_v4_ref 51_v41_mfa_recovery.sql > /dev/null  # MFA-01: needs 05 grants and the 20 never-allowed function
$PSQL -q -d edu360_v4_ref -f "$D/51t_tests_mfa_recovery.sql" > /dev/null
run edu360_v4_ref 52_v41_tenant_branding_logo.sql > /dev/null  # E1-S4 tenant logo storage
run edu360_v4_ref 53_v41_platform_approval_delegation.sql > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/53t_tests_platform_approval_delegation.sql" > /dev/null
run edu360_v4_ref 44_v41_release_scheduling.sql > /dev/null  # E7-S1 release scheduling
$PSQL -q -d edu360_v4_ref -f "$D/44t_tests_release_scheduling.sql" > /dev/null
run edu360_v4_ref 45_v41_usage_rollup.sql > /dev/null  # E6-S1 usage rollup
$PSQL -q -d edu360_v4_ref -f "$D/45t_tests_usage_rollup.sql" > /dev/null
run edu360_v4_ref 42_v41_deletion_retention_waiver.sql > /dev/null  # E5-S2 deletion retention waiver
$PSQL -q -d edu360_v4_ref -f "$D/42t_tests_deletion_retention_waiver.sql" > /dev/null
$PSQL -q -d edu360_v4_ref -f "$D/52t_tests_tenant_branding_logo.sql" > /dev/null
# D. Q-03 / Q-06: V4.1 migrations are idempotent and their rollbacks round-trip (third throw-away database).
$PSQL -d postgres -qc "DROP DATABASE IF EXISTS edu360_v4_rb WITH (FORCE)" -qc "CREATE DATABASE edu360_v4_rb"
V41="14_v41_audit_foundation.sql 15_v41_user_active_role.sql 16_v41_tenant_top_post_authority.sql 18_v41_user_sessions.sql 20_v41_never_allowed_guard.sql 22_v41_platform_audit_event_type.sql 25_sprint2_foundations.sql 26_v41_student_login_switch_guard.sql 27_v41_notification_read_state.sql 28_v41_study_material_view.sql 29_v41_institution_code_formats.sql 30_v41_exam_day_event_type.sql 31_v41_staff_record_view_grants.sql 32_v41_staff_login_aliases.sql 33_v41_study_material_leadership_grants.sql 36_v41_plan_commercial.sql 37_v41_feature_catalog_and_provisioning.sql 38_v41_invoice_approval_and_renewal.sql 39_v41_billing_settings.sql 40_v41_support_session.sql 41_v41_support_readable.sql 49_v41_platform_notification.sql 50_v41_platform_audit_payload_idempotency.sql 51_v41_mfa_recovery.sql 52_v41_tenant_branding_logo.sql 44_v41_release_scheduling.sql 45_v41_usage_rollup.sql 42_v41_deletion_retention_waiver.sql 53_v41_platform_approval_delegation.sql 55_v41_term_lock_engine.sql 56_v41_rolldown_management_revocation.sql 57_v41_academic_hierarchy_constraints.sql 58_v41_department_program_section_schema.sql 59_v41_staff_qualification_and_leave_indexes.sql 60_v41_leave_delegation.sql 61_v41_student_profile_and_guardians.sql 62_v41_student_notes_and_legal_holds.sql 63_v41_admission_cycles_and_applications.sql 64_v41_merit_list_and_seat_allocation.sql 65_v41_landing_cms_and_enquiries.sql 73_v41_notices_and_communications.sql 74_v41_library_and_transport_schema.sql"
for f in 01_schema.sql 02_rules.sql 03_bindings.sql 03b_fk_indexes.sql 08_v40_rules.sql 10_v40_rolldown.sql 12_v41_platform_roles.sql 05_ref_data.sql $V41; do run edu360_v4_rb $f > /dev/null; done
for f in $V41; do run edu360_v4_rb $f > /dev/null; done                      # second apply: idempotent
$PSQL -q -v ON_ERROR_STOP=1 -d edu360_v4_rb -f "$D/73t_tests_notices_and_communications.sql" > /dev/null
$PSQL -q -v ON_ERROR_STOP=1 -d edu360_v4_rb -f "$D/74t_tests_library_and_transport_schema.sql" > /dev/null
echo "RBCHECK | apply V4.1 files twice | OK" >> "$OUT"
# Step-in evidence must stop the 16 rollback (DB-A12) until the operator confirms the export.
$PSQL -q -v ON_ERROR_STOP=1 -d edu360_v4_rb > /dev/null <<'SQL'
INSERT INTO app.tenant_local (tenant_id, tenant_type) VALUES ('00000000-0000-0000-0000-00000000c601', 'STANDALONE_SCHOOL');
INSERT INTO app.user_account (tenant_id, user_account_id, user_type, display_name, email, status, created_by, updated_by)
VALUES ('00000000-0000-0000-0000-00000000c601', '00000000-0000-0000-0000-00000000c611', 'STAFF', 'Rollback Tester', 'rb@example.test', 'ACTIVE', '00000000-0000-0000-0000-00000000c611', '00000000-0000-0000-0000-00000000c611');
INSERT INTO ref.action_catalog (action_code, action_no, module_name, phase, description) VALUES ('B6_RB_ACTION', 950, 'Academic', 1, 'Rollback test action');
INSERT INTO app.tenant_step_in (tenant_id, step_in_id, actor_user_id, action_code, reason, expires_at)
VALUES ('00000000-0000-0000-0000-00000000c601', '00000000-0000-0000-0000-00000000c621', '00000000-0000-0000-0000-00000000c611', 'B6_RB_ACTION', 'Rollback evidence row', now() + interval '5 minutes');
SQL
for f in 74r_v41_library_and_transport_schema_rollback.sql 73r_v41_notices_and_communications_rollback.sql 65r_v41_landing_cms_and_enquiries_rollback.sql 60r_v41_leave_delegation_rollback.sql 59_v41_staff_qualification_and_leave_rollback.sql 58_rollback.sql 57_rollback.sql 56r_v41_rolldown_management_revocation_rollback.sql 55r_v41_term_lock_engine_rollback.sql 53r_v41_platform_approval_delegation_rollback.sql 42r_v41_deletion_retention_waiver_rollback.sql 44r_v41_release_scheduling_rollback.sql 52r_v41_tenant_branding_logo_rollback.sql 51r_v41_mfa_recovery_rollback.sql 50r_v41_platform_audit_payload_idempotency_rollback.sql 49r_v41_platform_notification_rollback.sql 45r_v41_usage_rollup_rollback.sql 41r_v41_support_readable_rollback.sql 40r_v41_support_session_rollback.sql 39r_v41_billing_settings_rollback.sql 38r_v41_invoice_approval_and_renewal_rollback.sql 37r_v41_feature_catalog_and_provisioning_rollback.sql 36r_v41_plan_commercial_rollback.sql 33r_v41_study_material_leadership_grants_rollback.sql 32r_v41_staff_login_aliases_rollback.sql 31r_v41_staff_record_view_grants_rollback.sql 30r_v41_exam_day_event_type_rollback.sql 29r_v41_institution_code_formats_rollback.sql 28r_v41_study_material_view_rollback.sql 27r_v41_notification_read_state_rollback.sql 26r_v41_student_login_switch_guard_rollback.sql 25r_sprint2_foundations_rollback.sql 22r_v41_platform_audit_event_type_rollback.sql 20r_v41_never_allowed_guard_rollback.sql 18r_v41_user_sessions_rollback.sql; do run edu360_v4_rb $f > /dev/null; done
if run edu360_v4_rb 16r_v41_tenant_top_post_authority_rollback.sql > /dev/null 2>&1; then r=MISMATCH; else r=OK; fi
echo "RBCHECK | 16r refuses while step-in rows exist (DB-A12) | $r" >> "$OUT"
$PSQL -q -v ON_ERROR_STOP=1 -d edu360_v4_rb -c "SET edu360.rollback_discard_step_ins = 'on'" -f "$D/16r_v41_tenant_top_post_authority_rollback.sql" > /dev/null
for f in 15r_v41_user_active_role_rollback.sql 14r_v41_audit_foundation_rollback.sql; do run edu360_v4_rb $f > /dev/null; done
$PSQL -At -d edu360_v4_rb -c "SELECT 'RBCHECK | rolled back: V4.1 objects left | '||CASE WHEN count(*) = 0 THEN 'OK' ELSE 'MISMATCH '||string_agg(o, ',') END FROM (VALUES (to_regclass('app.tenant_step_in')::text), (to_regclass('app.tenant_role_template')::text), (to_regclass('app.user_active_role')::text), (to_regprocedure('app.fn_never_allowed_class(text)')::text), (to_regprocedure('public.fn_audit_logs_guard()')::text), (to_regclass('platform.support_session')::text), ((SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='ref' AND table_name='action_catalog' AND column_name='support_readable') THEN 'ref.action_catalog.support_readable' END))) v(o) WHERE o IS NOT NULL" >> "$OUT"
$PSQL -At -d edu360_v4_rb -c "SELECT 'RBCHECK | rolled back: role_definition status CHECK restored | '||CASE WHEN pg_get_constraintdef(oid) LIKE '%RETIRED%' AND pg_get_constraintdef(oid) NOT LIKE '%DRAFT%' THEN 'OK' ELSE 'MISMATCH' END FROM pg_constraint WHERE conname = 'ck_role_definition_status'" >> "$OUT"
$PSQL -At -d edu360_v4_rb -c "SELECT 'RBCHECK | migration 37 rollback | '||CASE WHEN NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='platform' AND table_name='tenant_provision_step' AND column_name IN ('started_at','finished_at')) AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='ref' AND table_name='feature_catalog' AND column_name='provisional') AND position('P09_RETENTION' IN (SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='platform.tenant_provision_step'::regclass AND conname='ck_tenant_provision_step_step_code'))=0 AND NOT EXISTS (SELECT 1 FROM ref.feature_catalog WHERE feature_code IN ('USERS_ACCESS_BULK_UPLOAD','PLATFORM_BACKUP_STATUS')) THEN 'OK' ELSE 'MISMATCH' END" >> "$OUT"
for f in $V41; do run edu360_v4_rb $f > /dev/null; done                      # re-apply after rollback
$PSQL -At -d edu360_v4_rb -c "SELECT 'RBCHECK | re-applied: V4.1 objects present | '||CASE WHEN count(*) = 7 THEN 'OK' ELSE 'MISMATCH '||count(*) END FROM (VALUES (to_regclass('app.tenant_step_in')::text), (to_regclass('app.tenant_role_template')::text), (to_regclass('app.user_active_role')::text), (to_regprocedure('app.fn_never_allowed_class(text)')::text), (to_regprocedure('public.fn_audit_logs_guard()')::text), (to_regclass('platform.support_session')::text), ((SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='ref' AND table_name='action_catalog' AND column_name='support_readable') THEN 'ref.action_catalog.support_readable' END))) v(o) WHERE o IS NOT NULL" >> "$OUT"
$PSQL -d postgres -qc "DROP DATABASE IF EXISTS edu360_v4_rb WITH (FORCE)"
grep -q "SUMMARY | FAIL" "$OUT" && { echo "TESTS FAILED — see $OUT"; cat "$OUT"; exit 1; } || true
grep -q "^RBCHECK | .* | MISMATCH" "$OUT" && { echo "ROLLBACK CHECKS FAILED — see $OUT"; cat "$OUT"; exit 1; } || echo "All tests passed — see $OUT"
