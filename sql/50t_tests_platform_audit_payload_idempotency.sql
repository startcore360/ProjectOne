-- Contract tests for migration 50.
\set ON_ERROR_STOP 1
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='platform' AND table_name='audit_outbox'
          AND column_name='payload' AND data_type='jsonb'
    ) THEN RAISE EXCEPTION 'PLATFORM_AUDIT_PAYLOAD_JSONB_MISSING'; END IF;
    IF to_regclass('platform.idempotency_key') IS NULL THEN
        RAISE EXCEPTION 'PLATFORM_IDEMPOTENCY_TABLE_MISSING';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid='platform.idempotency_key'::regclass
          AND conname='uq_platform_idempotency_key_scope_caller_key'
    ) THEN RAISE EXCEPTION 'PLATFORM_IDEMPOTENCY_UNIQUE_SCOPE_MISSING'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid='platform.idempotency_key'::regclass
          AND conname='fk_platform_idempotency_caller'
    ) THEN RAISE EXCEPTION 'PLATFORM_IDEMPOTENCY_CALLER_FK_MISSING'; END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='platform' AND table_name='idempotency_key'
          AND column_name IN ('expires_at','purge_after','retention_until')
    ) THEN RAISE EXCEPTION 'OD39_EXPIRY_MUST_REMAIN_UNDEFINED'; END IF;
    IF NOT has_table_privilege('edu_platform','platform.idempotency_key','INSERT') THEN
        RAISE EXCEPTION 'EDU_PLATFORM_IDEMPOTENCY_INSERT_GRANT_MISSING';
    END IF;
    IF has_table_privilege('edu_app','platform.idempotency_key','INSERT') THEN
        RAISE EXCEPTION 'EDU_APP_MUST_NOT_WRITE_PLATFORM_IDEMPOTENCY';
    END IF;
END $$;
