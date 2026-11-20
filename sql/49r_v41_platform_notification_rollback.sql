-- Rollback migration 49 only when no notification data would be discarded.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM platform.platform_notification) THEN
        RAISE EXCEPTION 'PLATFORM_NOTIFICATION_ROWS_EXIST: preserve or explicitly clear notification rows before rollback';
    END IF;
END $$;
DROP TABLE platform.platform_notification;
