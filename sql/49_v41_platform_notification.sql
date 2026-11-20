-- EDU 360 V4.1 — platform notification write-side foundation (E9-S5).
-- Separate from tenant app.notification. Additive and repeatable.
CREATE TABLE IF NOT EXISTS platform.platform_notification (
    platform_notification_id uuid NOT NULL,
    recipient_platform_user_id uuid NOT NULL,
    trigger_code varchar(80) NOT NULL,
    title varchar(200) NOT NULL,
    body varchar(2000),
    entity_type varchar(60),
    entity_id uuid,
    priority_class varchar(16) NOT NULL DEFAULT 'NORMAL',
    read_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_platform_notification PRIMARY KEY (platform_notification_id),
    CONSTRAINT fk_platform_notification_recipient FOREIGN KEY (recipient_platform_user_id)
        REFERENCES platform.platform_user(platform_user_id),
    CONSTRAINT ck_platform_notification_trigger CHECK (length(trim(trigger_code)) > 0),
    CONSTRAINT ck_platform_notification_title CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_platform_notification_priority CHECK (priority_class IN ('NORMAL','SECURITY','EMERGENCY')),
    CONSTRAINT ck_platform_notification_row_version CHECK (row_version >= 1),
    CONSTRAINT ck_platform_notification_updated CHECK (updated_at >= created_at)
);
CREATE INDEX IF NOT EXISTS ix_platform_notification_unread_recipient
    ON platform.platform_notification(recipient_platform_user_id, created_at DESC)
    WHERE read_at IS NULL;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'trg_platform_notification_touch'
          AND tgrelid = 'platform.platform_notification'::regclass
          AND NOT tgisinternal
    ) THEN
        CREATE TRIGGER trg_platform_notification_touch
            BEFORE UPDATE ON platform.platform_notification
            FOR EACH ROW EXECUTE FUNCTION platform.fn_touch();
    END IF;
END $$;
GRANT SELECT, INSERT, UPDATE ON platform.platform_notification TO edu_platform;
COMMENT ON TABLE platform.platform_notification IS
    'Platform-user notification write store; intentionally separate from tenant-scoped app.notification [E9-S5].';
COMMENT ON COLUMN platform.platform_notification.read_at IS
    'NULL until the recipient marks this platform notification as read.';
