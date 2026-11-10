-- EDU 360 V4.1 — rollback of 18_v41_user_sessions.sql (multi-device sessions, SES-01).
-- Restores the original R2-11 shape: one active device per (user, device_class).
-- Data handling, because the restored index and CHECK would otherwise reject existing rows:
--   * every active row except the most recently registered one per (user, device_class) is
--     revoked with reason NEW_LOGIN (exactly what R2-11 would have done at the time);
--   * EXPIRED reasons (unknown to the old CHECK) become USER.
-- The matching Keycloak sessions are NOT ended here; the app does that, not SQL.

UPDATE app.user_device d
   SET revoked_at = now(), revoked_reason = 'NEW_LOGIN', updated_by = d.user_account_id
 WHERE d.revoked_at IS NULL
   AND EXISTS (
     SELECT 1 FROM app.user_device n
      WHERE n.tenant_id = d.tenant_id AND n.user_account_id = d.user_account_id
        AND n.device_class = d.device_class AND n.revoked_at IS NULL
        AND (n.registered_at, n.user_device_id) > (d.registered_at, d.user_device_id));

UPDATE app.user_device SET revoked_reason = 'USER', updated_by = user_account_id
 WHERE revoked_reason = 'EXPIRED';

DROP INDEX IF EXISTS app.ux_user_device_browser;
DROP INDEX IF EXISTS app.ux_user_device_session;
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_device_1
  ON app.user_device (tenant_id, user_account_id, device_class) WHERE revoked_at IS NULL;

ALTER TABLE app.user_device DROP CONSTRAINT IF EXISTS ck_user_device_revoked_reason;
ALTER TABLE app.user_device ADD CONSTRAINT ck_user_device_revoked_reason
  CHECK (revoked_reason IN ('NEW_LOGIN', 'USER', 'ADMIN'));

ALTER TABLE app.user_device DROP CONSTRAINT IF EXISTS ck_user_device_keycloak_session_id;
ALTER TABLE app.user_device DROP CONSTRAINT IF EXISTS ck_user_device_browser_key_hash;
ALTER TABLE app.user_device DROP CONSTRAINT IF EXISTS ck_user_device_ip_address;
ALTER TABLE app.user_device DROP COLUMN IF EXISTS keycloak_session_id;
ALTER TABLE app.user_device DROP COLUMN IF EXISTS browser_key_hash;
ALTER TABLE app.user_device DROP COLUMN IF EXISTS ip_address;

DROP TABLE IF EXISTS platform.platform_user_device;
