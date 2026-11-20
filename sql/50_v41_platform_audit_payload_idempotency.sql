-- EDU 360 V4.1 — platform audit payload and platform-side idempotency (E6-S4, E8-S5).
-- OD-39 is deliberately open: this migration defines no expiry or cleanup policy.
-- Additive and repeatable.
ALTER TABLE platform.audit_outbox ADD COLUMN IF NOT EXISTS payload jsonb;
COMMENT ON COLUMN platform.audit_outbox.payload IS
    'Masked old_values/new_values audit change payload. password, secret, token, nationalId and salary values must be masked [E6-S4].';
CREATE TABLE IF NOT EXISTS platform.idempotency_key (
    platform_idempotency_key_id uuid NOT NULL,
    scope varchar(100) NOT NULL,
    request_key varchar(100) NOT NULL,
    caller_user_id uuid NOT NULL,
    request_hash char(64) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'IN_PROGRESS',
    response_status smallint,
    response_body jsonb,
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_platform_idempotency_key PRIMARY KEY (platform_idempotency_key_id),
    CONSTRAINT uq_platform_idempotency_key_scope_caller_key UNIQUE (scope, caller_user_id, request_key),
    CONSTRAINT fk_platform_idempotency_caller FOREIGN KEY (caller_user_id)
        REFERENCES platform.platform_user(platform_user_id),
    CONSTRAINT ck_platform_idempotency_scope CHECK (length(trim(scope)) > 0),
    CONSTRAINT ck_platform_idempotency_request_key CHECK (length(trim(request_key)) BETWEEN 8 AND 100),
    CONSTRAINT ck_platform_idempotency_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_platform_idempotency_status CHECK (status IN ('IN_PROGRESS','COMPLETED')),
    CONSTRAINT ck_platform_idempotency_response_status CHECK (response_status IS NULL OR response_status BETWEEN 100 AND 599),
    CONSTRAINT ck_platform_idempotency_completed CHECK (
        (status = 'IN_PROGRESS' AND completed_at IS NULL AND response_status IS NULL)
        OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND response_status IS NOT NULL)
    ),
    CONSTRAINT ck_platform_idempotency_row_version CHECK (row_version >= 1),
    CONSTRAINT ck_platform_idempotency_updated CHECK (updated_at >= created_at)
);
CREATE INDEX IF NOT EXISTS ix_platform_idempotency_status
    ON platform.idempotency_key(status, created_at);
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'trg_platform_idempotency_touch'
          AND tgrelid = 'platform.idempotency_key'::regclass
          AND NOT tgisinternal
    ) THEN
        CREATE TRIGGER trg_platform_idempotency_touch
            BEFORE UPDATE ON platform.idempotency_key
            FOR EACH ROW EXECUTE FUNCTION platform.fn_touch();
    END IF;
END $$;
GRANT SELECT, INSERT, UPDATE, DELETE ON platform.idempotency_key TO edu_platform;
REVOKE ALL ON platform.idempotency_key FROM edu_app;
COMMENT ON TABLE platform.idempotency_key IS
    'Platform command replay store scoped by operation, caller and Idempotency-Key. OD-39 expiry/cleanup is unresolved.';
COMMENT ON COLUMN platform.idempotency_key.request_hash IS
    'SHA-256 of the canonical request body; reuse with a different body conflicts.';
COMMENT ON COLUMN platform.idempotency_key.response_body IS
    'Stored JSON response used for idempotent replay.';
