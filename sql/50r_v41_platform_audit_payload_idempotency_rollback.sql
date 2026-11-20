-- Rollback migration 50 only when no stored data would be discarded.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM platform.idempotency_key) THEN
        RAISE EXCEPTION 'PLATFORM_IDEMPOTENCY_ROWS_EXIST: preserve idempotency rows before rollback';
    END IF;
    IF EXISTS (SELECT 1 FROM platform.audit_outbox WHERE payload IS NOT NULL) THEN
        RAISE EXCEPTION 'PLATFORM_AUDIT_PAYLOADS_EXIST: preserve audit payloads before rollback';
    END IF;
END $$;
DROP INDEX IF EXISTS platform.ix_platform_idempotency_status;
DROP TABLE platform.idempotency_key;
ALTER TABLE platform.audit_outbox DROP COLUMN payload;
