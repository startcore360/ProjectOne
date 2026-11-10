-- EDU 360 V4.1 — batch B4 (L-01, L-07): platform audit events carry a real event type.
-- platform.audit_outbox had only a coarse "operation" (CREATE, UPDATE, ...). Tenant deletion, retention
-- and support steps need their own names (TENANT_DELETION_REQUESTED, ...), so this adds event_type.
-- Idempotent; no data is changed. Rollback: 22r_v41_platform_audit_event_type_rollback.sql.

ALTER TABLE platform.audit_outbox ADD COLUMN IF NOT EXISTS event_type text;
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_audit_outbox_event_type'
                   AND conrelid = 'platform.audit_outbox'::regclass) THEN
    ALTER TABLE platform.audit_outbox ADD CONSTRAINT ck_audit_outbox_event_type
      CHECK (event_type IS NULL OR char_length(event_type) BETWEEN 1 AND 60);
  END IF;
END $$;
COMMENT ON COLUMN platform.audit_outbox.event_type IS 'Named event, e.g. TENANT_DELETION_APPROVED. NULL only on rows written before V4.1 B4. Length 1-60. [L-01, L-07]';
