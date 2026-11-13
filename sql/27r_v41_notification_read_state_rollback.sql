-- B9 rollback: remove notification read-state additions only.
DROP INDEX IF EXISTS app.ix_notification_unread_recipient;
ALTER TABLE app.notification DROP COLUMN IF EXISTS read_at;
