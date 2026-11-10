-- EDU 360 V4.1 — multi-device sessions (AUTH_DEVICES_MANAGE, "see own active devices and log
-- them out"). Apply after 16_v41_tenant_top_post_authority.sql. Idempotent: safe to re-run.
-- Rollback: 18r_v41_user_sessions_rollback.sql. Tests: 19_tests_user_sessions.sql (DB-126..137).
--
-- DELIBERATE DEVIATION from R2-11 / WF-35 ("one phone and one computer per user"), decided by
-- the product owner on 2026-11-09 (docs/GOVERNANCE_DECISION_LOG.md, SES-01):
--   * a user may stay signed in on any number of devices at once;
--   * signing in again from the SAME browser replaces that browser's previous session.
-- So the old "one active row per (user, device_class)" index becomes "one active row per
-- (user, browser)". Keycloak stays the authority on which sessions are alive; these rows only
-- add what Keycloak cannot know with a server-side password grant (device class, a readable
-- label, the caller's real IP, and which browser it was).

-- ---------------------------------------------------------------------------------------------
-- Tenant side: app.user_device (already in 01_schema.sql, RLS + edu_app grants in 03_bindings).
-- ---------------------------------------------------------------------------------------------
ALTER TABLE app.user_device ADD COLUMN IF NOT EXISTS keycloak_session_id text;
ALTER TABLE app.user_device ADD COLUMN IF NOT EXISTS browser_key_hash text;
ALTER TABLE app.user_device ADD COLUMN IF NOT EXISTS ip_address text;

COMMENT ON COLUMN app.user_device.keycloak_session_id IS 'Keycloak user-session id ("sid") this device row belongs to; used to sign the device out. [SES-01]';
COMMENT ON COLUMN app.user_device.browser_key_hash IS 'SHA-256 (hex) of the random per-browser id the client sends at login; never the raw id. One active row per browser. [SES-01]';
COMMENT ON COLUMN app.user_device.ip_address IS 'Caller IP at sign-in, as seen by the API (Keycloak only sees the API server). [SES-01]';

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_user_device_keycloak_session_id') THEN
    ALTER TABLE app.user_device ADD CONSTRAINT ck_user_device_keycloak_session_id
      CHECK (keycloak_session_id IS NULL OR char_length(keycloak_session_id) BETWEEN 1 AND 255);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_user_device_browser_key_hash') THEN
    ALTER TABLE app.user_device ADD CONSTRAINT ck_user_device_browser_key_hash
      CHECK (browser_key_hash IS NULL OR browser_key_hash ~ '^[0-9a-f]{64}$');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_user_device_ip_address') THEN
    ALTER TABLE app.user_device ADD CONSTRAINT ck_user_device_ip_address
      CHECK (ip_address IS NULL OR char_length(ip_address) BETWEEN 2 AND 45);
  END IF;
END $$;

-- EXPIRED: Keycloak ended the session itself (idle/max lifetime), noticed on the next listing.
ALTER TABLE app.user_device DROP CONSTRAINT IF EXISTS ck_user_device_revoked_reason;
ALTER TABLE app.user_device ADD CONSTRAINT ck_user_device_revoked_reason
  CHECK (revoked_reason IN ('NEW_LOGIN', 'USER', 'ADMIN', 'EXPIRED'));

DROP INDEX IF EXISTS app.ux_user_device_1;
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_device_browser
  ON app.user_device (tenant_id, user_account_id, browser_key_hash)
  WHERE revoked_at IS NULL AND browser_key_hash IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_device_session
  ON app.user_device (tenant_id, keycloak_session_id)
  WHERE keycloak_session_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- Platform side: platform accounts get the same device list (they have no app.* identity).
-- Written only through edu_platform, like every other platform.* table.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS platform.platform_user_device (
  platform_user_device_id uuid NOT NULL,
  platform_user_id uuid NOT NULL,
  device_class text NOT NULL,
  device_label text,
  keycloak_session_id text NOT NULL,
  browser_key_hash text,
  ip_address text,
  registered_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz,
  revoked_reason text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_platform_user_device PRIMARY KEY (platform_user_device_id),
  CONSTRAINT fk_platform_user_device_user FOREIGN KEY (platform_user_id)
    REFERENCES platform.platform_user (platform_user_id) ON DELETE RESTRICT,
  CONSTRAINT ck_platform_user_device_device_class CHECK (device_class IN ('PHONE', 'COMPUTER')),
  CONSTRAINT ck_platform_user_device_device_label CHECK (device_label IS NULL OR char_length(device_label) BETWEEN 1 AND 100),
  CONSTRAINT ck_platform_user_device_keycloak_session_id CHECK (char_length(keycloak_session_id) BETWEEN 1 AND 255),
  CONSTRAINT ck_platform_user_device_browser_key_hash CHECK (browser_key_hash IS NULL OR browser_key_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT ck_platform_user_device_ip_address CHECK (ip_address IS NULL OR char_length(ip_address) BETWEEN 2 AND 45),
  CONSTRAINT ck_platform_user_device_revoked_reason CHECK (revoked_reason IN ('NEW_LOGIN', 'USER', 'ADMIN', 'EXPIRED')),
  CONSTRAINT ck_platform_user_device_r1 CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL)),
  CONSTRAINT ck_platform_user_device_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_platform_user_device_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.platform_user_device IS 'Signed-in devices of platform staff; any number at once, one active row per browser. [AUTH_DEVICES_MANAGE, SES-01]';

CREATE UNIQUE INDEX IF NOT EXISTS ux_platform_user_device_browser
  ON platform.platform_user_device (platform_user_id, browser_key_hash)
  WHERE revoked_at IS NULL AND browser_key_hash IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_platform_user_device_session
  ON platform.platform_user_device (keycloak_session_id);
CREATE INDEX IF NOT EXISTS ix_platform_user_device_user
  ON platform.platform_user_device (platform_user_id);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger WHERE tgname = 'trg_touch' AND tgrelid = 'platform.platform_user_device'::regclass
  ) THEN
    CREATE TRIGGER trg_touch BEFORE UPDATE ON platform.platform_user_device
      FOR EACH ROW EXECUTE FUNCTION platform.fn_touch();
  END IF;
END $$;

GRANT SELECT, INSERT, UPDATE ON platform.platform_user_device TO edu_platform;
