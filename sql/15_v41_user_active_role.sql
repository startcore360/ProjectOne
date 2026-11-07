-- EDU 360 V4.1 — persistent active role selection per user session (ROLE_SWITCH / Rule HI-15).
-- Stores the user's chosen active role assignment across restarts and requests.
-- Apply after 14_v41_audit_foundation.sql. Rollback: 15r_v41_user_active_role_rollback.sql.

CREATE TABLE IF NOT EXISTS app.user_active_role (
  tenant_id uuid NOT NULL,
  user_account_id uuid NOT NULL,
  user_role_assignment_id uuid NOT NULL,
  switched_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pk_user_active_role PRIMARY KEY (tenant_id, user_account_id),
  CONSTRAINT fk_user_active_role_user FOREIGN KEY (tenant_id, user_account_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT,
  CONSTRAINT fk_user_active_role_assignment FOREIGN KEY (tenant_id, user_role_assignment_id) REFERENCES app.user_role_assignment (tenant_id, user_role_assignment_id) ON DELETE RESTRICT
);

COMMENT ON TABLE app.user_active_role IS 'Persisted active role selection per user session [ROLE_SWITCH, Rule HI-15, PO-3].';
COMMENT ON COLUMN app.user_active_role.user_role_assignment_id IS 'The active role assignment currently selected by the user.';

CREATE INDEX IF NOT EXISTS ix_user_active_role_assignment ON app.user_active_role (tenant_id, user_role_assignment_id);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger WHERE tgname = 'trg_touch' AND tgrelid = 'app.user_active_role'::regclass
  ) THEN
    CREATE TRIGGER trg_touch BEFORE UPDATE ON app.user_active_role FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
  END IF;
END $$;

ALTER TABLE app.user_active_role ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.user_active_role FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE schemaname = 'app' AND tablename = 'user_active_role' AND policyname = 'p_tenant'
  ) THEN
    CREATE POLICY p_tenant ON app.user_active_role
      USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
      WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
  END IF;
END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON app.user_active_role TO edu_app;
GRANT SELECT ON app.user_active_role TO edu_report;
