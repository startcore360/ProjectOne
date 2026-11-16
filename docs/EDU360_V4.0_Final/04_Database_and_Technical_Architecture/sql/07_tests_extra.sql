-- Extra checks run in a NEW connection after 04_tests.sql (settings never set in this session)
SET ROLE edu_app;
SELECT public.expect_value('DB-55','N-02 tenant setting never set in this connection: zero rows, no error', $q$SELECT count(*)::text FROM app.department$q$, '0');
SELECT set_config('app.tenant_id', 'a0000000-0000-0000-0000-000000000000', false);
SELECT public.expect_value('DB-56','PTD-I1 institution settings never set: institution-bound rows hidden (fail-closed)', $q$SELECT count(*)::text FROM app.department$q$, '0');
SELECT set_config('app.institution_scope', 'ALL', false);
SELECT public.expect_value('DB-57','PTD-I1 scope ALL shows the tenant''s rows', $q$SELECT (count(*) > 0)::text FROM app.department$q$, 'true');
SELECT set_config('app.tenant_id', 'b0000000-0000-0000-0000-000000000000', false);
SELECT public.expect_value('DB-58','K-002 switching tenant context hides tenant A rows', $q$SELECT count(*)::text FROM app.department WHERE tenant_id = 'a0000000-0000-0000-0000-000000000000'$q$, '0');
SELECT public.expect_fail('DB-59','SEC-04 tenant app role has no access to the platform schema', $q$UPDATE platform.tenant SET status = 'ACTIVE'$q$, 'permission denied');
RESET ROLE;
SELECT test_id, rule, outcome, detail FROM public.test_result WHERE test_id IN ('DB-55','DB-56','DB-57','DB-58','DB-59') ORDER BY id;
