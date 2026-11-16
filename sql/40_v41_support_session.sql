-- EDU 360 V4.1 — read-only platform support sessions (E4-S1, E4-S2).
-- Apply after the V4.1 foundation migrations. Idempotent; rollback: 40r_v41_support_session_rollback.sql.

CREATE TABLE IF NOT EXISTS platform.support_session (
    support_session_id uuid NOT NULL,
    grant_id uuid NOT NULL,
    platform_user_id uuid NOT NULL,
    issued_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    ended_at timestamptz,
    end_reason text,
    CONSTRAINT pk_support_session PRIMARY KEY (support_session_id),
    CONSTRAINT fk_support_session_grant FOREIGN KEY (grant_id)
        REFERENCES platform.support_access_grant (support_access_grant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_support_session_platform_user FOREIGN KEY (platform_user_id)
        REFERENCES platform.platform_user (platform_user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_support_session_expiry CHECK (
        expires_at > issued_at AND expires_at <= issued_at + interval '1 hour'
    ),
    CONSTRAINT ck_support_session_end_reason CHECK (
        end_reason IS NULL OR end_reason IN ('ENDED', 'WITHDRAWN', 'EXPIRED')
    ),
    CONSTRAINT ck_support_session_end_state CHECK (
        (ended_at IS NULL AND end_reason IS NULL) OR
        (ended_at IS NOT NULL AND end_reason IS NOT NULL AND ended_at >= issued_at)
    )
);

COMMENT ON TABLE platform.support_session IS
    'Short-lived, ticket-bound, read-only support session. Session lifetime is at most one hour; the tenant grant may remain valid for up to 24 hours. [E4-S1]';
COMMENT ON COLUMN platform.support_session.grant_id IS 'Tenant-approved support_access_grant_id authorising this session.';
COMMENT ON COLUMN platform.support_session.platform_user_id IS 'Platform user who initiated the support session.';
COMMENT ON COLUMN platform.support_session.expires_at IS 'Session expiry; cannot exceed issued_at + 1 hour. The application also caps it at grant.expires_at.';
COMMENT ON COLUMN platform.support_session.end_reason IS 'ENDED by the operator, WITHDRAWN by the tenant, or EXPIRED at session expiry.';

CREATE INDEX IF NOT EXISTS ix_support_session_grant_active
    ON platform.support_session (grant_id, expires_at)
    WHERE ended_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_support_session_platform_user_active
    ON platform.support_session (platform_user_id, expires_at)
    WHERE ended_at IS NULL;

GRANT SELECT, INSERT, UPDATE ON platform.support_session TO edu_platform;

-- OD-15 is stored as a JSON setting. The setting key is lowercase to satisfy
-- ck_setting_definition_setting_key; category values remain the agreed uppercase codes.
INSERT INTO ref.setting_definition (
    setting_key, value_type, default_value, allowed_scope_levels, validation,
    owner_action_code, is_overridable, weakening_guard, source_ref
) VALUES (
    'support_ticket_categories',
    'JSON',
    '["LOGIN","ACCESS","DATA","BILLING","PERFORMANCE","BUG","OTHER"]'::jsonb,
    ARRAY['PLATFORM']::text[],
    jsonb_build_object('allowed_values', jsonb_build_array('LOGIN','ACCESS','DATA','BILLING','PERFORMANCE','BUG','OTHER')),
    NULL,
    false,
    NULL,
    'OD-15, E4-S2'
) ON CONFLICT (setting_key) DO NOTHING;
