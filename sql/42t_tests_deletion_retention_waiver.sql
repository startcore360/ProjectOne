-- Contract tests for migration 42 (E5-S2): PO retention waiver columns on platform.deletion_request.
-- Inserts throw-away rows to prove the check really rejects bad waivers; everything is rolled back.
\set ON_ERROR_STOP 1
BEGIN;
DO $$
DECLARE
 a uuid := '00000000-0000-0000-0000-000000004201';
 p uuid := '00000000-0000-0000-0000-000000004202';
 t uuid := '00000000-0000-0000-0000-000000004203';
 n integer;
 blocked boolean;
BEGIN
 SELECT count(*) INTO n FROM information_schema.columns
  WHERE table_schema = 'platform' AND table_name = 'deletion_request'
    AND column_name IN ('retention_waived_by', 'retention_waived_at', 'retention_waiver_reason');
 IF n <> 3 THEN RAISE EXCEPTION 'E5_S2_WAIVER_COLUMNS_MISSING: %', n; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'platform.deletion_request'::regclass
                 AND conname = 'ck_deletion_request_retention_waiver') THEN
  RAISE EXCEPTION 'E5_S2_WAIVER_CHECK_MISSING';
 END IF;

 INSERT INTO platform.plan(plan_id, plan_name, price_amount, status, created_by, updated_by)
  VALUES (p, 'E5-S2 migration test plan', 1000, 'ACTIVE', a, a);
 INSERT INTO platform.tenant(tenant_id, tenant_name, tenant_type, plan_id, state, isolation_mode, subdomain, created_by, updated_by)
  VALUES (t, 'E5-S2 migration test tenant', 'STANDALONE_SCHOOL', p, 'ACTIVE', 'SHARED', 'e5s2guard', a, a);

 -- A complete Product Owner waiver is accepted.
 INSERT INTO platform.deletion_request (deletion_request_id, tenant_id, origin, requested_by_user_id, status,
   retention_waived_by, retention_waived_at, retention_waiver_reason, created_by, updated_by)
  VALUES (gen_random_uuid(), t, 'PRODUCT_OWNER', a, 'REQUESTED', a, now(), 'Approved closure plan', a, a);

 -- No waiver at all is accepted.
 INSERT INTO platform.deletion_request (deletion_request_id, tenant_id, origin, requested_by_user_id, status, created_by, updated_by)
  VALUES (gen_random_uuid(), t, 'PRODUCT_OWNER', a, 'REQUESTED', a, a);

 -- A waiver without a reason is refused.
 blocked := false;
 BEGIN
  INSERT INTO platform.deletion_request (deletion_request_id, tenant_id, origin, requested_by_user_id, status,
    retention_waived_by, retention_waived_at, created_by, updated_by)
   VALUES (gen_random_uuid(), t, 'PRODUCT_OWNER', a, 'REQUESTED', a, now(), a, a);
 EXCEPTION WHEN check_violation THEN blocked := true;
 END;
 IF NOT blocked THEN RAISE EXCEPTION 'E5_S2_WAIVER_WITHOUT_REASON_ACCEPTED'; END IF;

 -- A whitespace-only reason is refused.
 blocked := false;
 BEGIN
  INSERT INTO platform.deletion_request (deletion_request_id, tenant_id, origin, requested_by_user_id, status,
    retention_waived_by, retention_waived_at, retention_waiver_reason, created_by, updated_by)
   VALUES (gen_random_uuid(), t, 'PRODUCT_OWNER', a, 'REQUESTED', a, now(), '   ', a, a);
 EXCEPTION WHEN check_violation THEN blocked := true;
 END;
 IF NOT blocked THEN RAISE EXCEPTION 'E5_S2_BLANK_WAIVER_REASON_ACCEPTED'; END IF;

 -- A reason longer than 900 characters is refused.
 blocked := false;
 BEGIN
  INSERT INTO platform.deletion_request (deletion_request_id, tenant_id, origin, requested_by_user_id, status,
    retention_waived_by, retention_waived_at, retention_waiver_reason, created_by, updated_by)
   VALUES (gen_random_uuid(), t, 'PRODUCT_OWNER', a, 'REQUESTED', a, now(), repeat('x', 901), a, a);
 EXCEPTION WHEN check_violation THEN blocked := true;
 END;
 IF NOT blocked THEN RAISE EXCEPTION 'E5_S2_LONG_WAIVER_REASON_ACCEPTED'; END IF;

 -- A waiver on a request that did not come from the Product Owner is refused.
 blocked := false;
 BEGIN
  INSERT INTO platform.deletion_request (deletion_request_id, tenant_id, origin, requested_by_user_id, status,
    retention_waived_by, retention_waived_at, retention_waiver_reason, created_by, updated_by)
   VALUES (gen_random_uuid(), t, 'TENANT_ADMIN', a, 'REQUESTED', a, now(), 'Not allowed', a, a);
 EXCEPTION WHEN check_violation THEN blocked := true;
 END;
 IF NOT blocked THEN RAISE EXCEPTION 'E5_S2_NON_PO_WAIVER_ACCEPTED'; END IF;
END $$;
ROLLBACK;
