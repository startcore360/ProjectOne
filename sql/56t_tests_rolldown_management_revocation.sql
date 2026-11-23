-- 56t_tests_rolldown_management_revocation_tests.sql
DO $$
BEGIN
    IF to_regclass('app.gba_rolldown_revocation') IS NULL THEN RAISE EXCEPTION 'RD-G8 FAIL: revocation ledger missing'; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='app.user_role_assignment'::regclass AND tgname='trg_revoke_gba_rolldown' AND NOT tgisinternal) THEN
        RAISE EXCEPTION 'RD-G8 FAIL: Management assignment trigger missing';
    END IF;
    IF position('GOVERNING_BODY_APPROVER' in pg_get_functiondef('app.fn_rolldown_override_guard()'::regprocedure)) = 0 THEN
        RAISE EXCEPTION 'RD-G8 FAIL: override guard missing GBA restriction';
    END IF;
    IF position('1..6' in pg_get_functiondef('app.fn_rolldown_resolve(uuid,text,text)'::regprocedure)) = 0 THEN
        RAISE EXCEPTION 'DB-80..97 FAIL: six-hop resolver contract missing';
    END IF;
    IF position('gba_rolldown_revocation' in pg_get_functiondef('app.fn_rolldown_resolve(uuid,text,text)'::regprocedure)) = 0 THEN
        RAISE EXCEPTION 'RD-G8 FAIL: resolver must ignore revoked GBA overrides';
    END IF;
    IF EXISTS (SELECT 1 FROM app.role_rolldown_override o
                JOIN app.gba_rolldown_revocation r USING (tenant_id)
               WHERE o.status='ACTIVE' AND o.set_by_role='GOVERNING_BODY_APPROVER') THEN
        RAISE EXCEPTION 'RD-G8 FAIL: active GBA override remains after permanent revocation';
    END IF;
END $$;
