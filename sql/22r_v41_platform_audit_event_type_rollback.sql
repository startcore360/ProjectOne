-- EDU 360 V4.1 — rollback for 22_v41_platform_audit_event_type.sql (B4).
-- Drops the event_type column; the operation column still describes every row.
ALTER TABLE platform.audit_outbox DROP CONSTRAINT IF EXISTS ck_audit_outbox_event_type;
ALTER TABLE platform.audit_outbox DROP COLUMN IF EXISTS event_type;
