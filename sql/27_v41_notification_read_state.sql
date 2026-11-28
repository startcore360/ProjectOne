-- B9: Persist notification read state without changing delivery or recipient semantics.
ALTER TABLE app.notification
    ADD COLUMN IF NOT EXISTS read_at timestamptz;

CREATE INDEX IF NOT EXISTS ix_notification_unread_recipient
    ON app.notification (tenant_id, recipient_user_id, created_at DESC)
    WHERE read_at IS NULL;
