#!/usr/bin/env bash
# EDU 360 V4.0 — build and verify the database in two throw-away PostgreSQL databases (never a production database).
# Usage: PSQL="psql -h localhost -U postgres" ./run_all.sh   (needs PostgreSQL 15+; validated on 16.15)
set -euo pipefail
PSQL=${PSQL:-psql}; D=$(cd "$(dirname "$0")" && pwd); OUT=${OUT:-$D/run_results.txt}
run(){ $PSQL -q -v ON_ERROR_STOP=1 -d "$1" -f "$D/$2"; }
: > "$OUT"
echo "# EDU 360 V4.0 database build and test run — $(date -u +%FT%TZ)" >> "$OUT"
$PSQL -d postgres -qc "DROP DATABASE IF EXISTS edu360_v4_test" -qc "CREATE DATABASE edu360_v4_test"
$PSQL -d postgres -qc "DROP DATABASE IF EXISTS edu360_v4_ref"  -qc "CREATE DATABASE edu360_v4_ref"
# A. acceptance tests (tests seed their own minimal reference rows)
for f in 01_schema.sql 02_rules.sql 03_bindings.sql 03b_fk_indexes.sql 08_v40_rules.sql 10_v40_rolldown.sql 12_v41_platform_roles.sql 14_v41_audit_foundation.sql 15_v41_user_active_role.sql 16_v41_tenant_top_post_authority.sql 04_tests.sql; do run edu360_v4_test $f > /dev/null; done
$PSQL -q -d edu360_v4_test -f "$D/07_tests_extra.sql" > /dev/null      # new connection: settings never set (DB-55..59)
$PSQL -q -d edu360_v4_test -f "$D/09_tests_v40.sql"  > /dev/null      # V4.0 tests DB-60..79
$PSQL -q -d edu360_v4_test -f "$D/11_tests_rolldown.sql" > /dev/null  # V4.0 role roll-down tests DB-80..97
$PSQL -q -d edu360_v4_test -f "$D/13_tests_platform_roles.sql" > /dev/null  # V4.1 platform role tests DB-98..112
$PSQL -At -d edu360_v4_test -c "SELECT test_id||' | '||outcome||' | '||rule||' | '||detail FROM public.test_result ORDER BY id" >> "$OUT"
$PSQL -At -d edu360_v4_test -c "SELECT 'SUMMARY | '||outcome||' | '||count(*) FROM public.test_result GROUP BY outcome" >> "$OUT"
# B. full V3.3 reference data and count checks (AC-S5), plus idempotent second load
for f in 01_schema.sql 02_rules.sql 03_bindings.sql 03b_fk_indexes.sql 08_v40_rules.sql 10_v40_rolldown.sql 05_ref_data.sql; do run edu360_v4_ref $f > /dev/null; done
$PSQL -At -d edu360_v4_ref -f "$D/06_ref_checks.sql" | sed 's/^/REFCHECK | /' >> "$OUT"   # V3.3 baseline counts
run edu360_v4_ref 10d_v40_rolldown_ref.sql > /dev/null                                          # V4.0 additions
$PSQL -At -d edu360_v4_ref >> "$OUT" <<'SQL'
SELECT 'REFCHECK | V4.0 actions (278 + 2 roll-down)|'||count(*)||'|280' FROM ref.action_catalog;
SELECT 'REFCHECK | V4.0 role-action pairs (1851 + 12)|'||count(*)||'|1863' FROM ref.standard_role_action;
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
for f in 12_v41_platform_roles.sql 12d_v41_platform_role_templates.sql; do run edu360_v4_ref $f > /dev/null; done
$PSQL -At -d edu360_v4_ref -c "SELECT 'REFCHECK | platform role templates | '||count(*)||' | 5' FROM platform.platform_role_template" >> "$OUT"
$PSQL -At -d edu360_v4_ref -c "SELECT 'REFCHECK | platform template actions with a reserved action outside the locked template | '||count(*)||' | 0' FROM platform.platform_role_template_action a JOIN platform.platform_role_template t USING (platform_role_template_id) WHERE NOT t.is_locked AND a.action_code IN ('TENANT_EXPORT_APPROVE','TENANT_DELETION_APPROVE','RETENTION_CHANGE_APPROVE','APPROVAL_DELEGATE','PLATFORM_USER_MANAGE','TENANT_DELETION_CONFIRM')" >> "$OUT"
before=$($PSQL -At -d edu360_v4_ref -c "SELECT count(*) FROM platform.platform_role_template_action")
run edu360_v4_ref 12d_v41_platform_role_templates.sql > /dev/null
after=$($PSQL -At -d edu360_v4_ref -c "SELECT count(*) FROM platform.platform_role_template_action")
echo "REFCHECK | second load of 12d inserted rows | $((after-before)) | 0" >> "$OUT"
grep -q "SUMMARY | FAIL" "$OUT" && { echo "TESTS FAILED — see $OUT"; exit 1; } || echo "All tests passed — see $OUT"
