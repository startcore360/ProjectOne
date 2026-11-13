-- B9: notification read-state schema contract.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'notification' AND column_name = 'read_at'
          AND data_type = 'timestamp with time zone'
    ) THEN
        RAISE EXCEPTION 'B9 FAIL: app.notification.read_at is missing or has the wrong type';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'app' AND tablename = 'notification'
          AND indexname = 'ix_notification_unread_recipient'
    ) THEN
        RAISE EXCEPTION 'B9 FAIL: unread-recipient index is missing';
    END IF;
END $$;
