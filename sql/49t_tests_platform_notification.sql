-- Contract tests for migration 49. Run after 49_v41_platform_notification.sql.
\set ON_ERROR_STOP 1
DO $$
BEGIN
    IF to_regclass('platform.platform_notification') IS NULL THEN
        RAISE EXCEPTION 'PLATFORM_NOTIFICATION_TABLE_MISSING';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='platform' AND table_name='platform_notification'
          AND column_name='recipient_platform_user_id' AND data_type='uuid'
    ) THEN RAISE EXCEPTION 'PLATFORM_NOTIFICATION_RECIPIENT_MISSING'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='platform' AND table_name='platform_notification'
          AND column_name='read_at' AND data_type='timestamp with time zone'
    ) THEN RAISE EXCEPTION 'PLATFORM_NOTIFICATION_READ_STATE_MISSING'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname='platform' AND indexname='ix_platform_notification_unread_recipient'
    ) THEN RAISE EXCEPTION 'PLATFORM_NOTIFICATION_UNREAD_INDEX_MISSING'; END IF;
    IF NOT has_table_privilege('edu_platform','platform.platform_notification','INSERT') THEN
        RAISE EXCEPTION 'EDU_PLATFORM_NOTIFICATION_INSERT_GRANT_MISSING';
    END IF;
    IF has_table_privilege('edu_app','platform.platform_notification','INSERT') THEN
        RAISE EXCEPTION 'EDU_APP_MUST_NOT_WRITE_PLATFORM_NOTIFICATIONS';
    END IF;
END $$;
