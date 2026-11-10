-- EDU 360 V4.1 — tenant top-post authority and custom roles (TPA-01..TPA-04 and OD-62).
-- Apply after 15_v41_user_active_role.sql. Rollback: 16r_v41_tenant_top_post_authority_rollback.sql.

-- 1. Tenant Role Template
CREATE TABLE IF NOT EXISTS app.tenant_role_template (
  tenant_id uuid NOT NULL,
  tenant_role_template_id uuid NOT NULL,
  template_name text NOT NULL,
  description text,
  base_role_code text NOT NULL,
  owner_level text NOT NULL DEFAULT 'TENANT',
  is_standard boolean NOT NULL DEFAULT false,
  is_locked boolean NOT NULL DEFAULT false,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_tenant_role_template PRIMARY KEY (tenant_id, tenant_role_template_id),
  CONSTRAINT uq_tenant_role_template_name UNIQUE (tenant_id, template_name),
  CONSTRAINT ck_tenant_role_template_name CHECK (char_length(template_name) BETWEEN 2 AND 100),
  CONSTRAINT ck_tenant_role_template_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  CONSTRAINT ck_tenant_role_template_owner_level CHECK (owner_level IN ('TENANT', 'UNIVERSITY', 'COLLEGE', 'SCHOOL')),
  CONSTRAINT fk_tenant_role_template_tenant FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT,
  CONSTRAINT fk_tenant_role_template_base_role FOREIGN KEY (base_role_code) REFERENCES ref.standard_role (role_code)
);

CREATE INDEX IF NOT EXISTS ix_tenant_role_template_base_role ON app.tenant_role_template (base_role_code);

COMMENT ON TABLE app.tenant_role_template IS 'Starting suggestions for tenant custom roles [TPA-01..TPA-04].';

ALTER TABLE app.tenant_role_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.tenant_role_template FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE schemaname = 'app' AND tablename = 'tenant_role_template' AND policyname = 'p_tenant'
  ) THEN
    CREATE POLICY p_tenant ON app.tenant_role_template
      USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
      WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
  END IF;
END $$;

-- 2. Tenant Role Template Action
CREATE TABLE IF NOT EXISTS app.tenant_role_template_action (
  tenant_id uuid NOT NULL,
  tenant_role_template_action_id uuid NOT NULL,
  tenant_role_template_id uuid NOT NULL,
  action_code text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  CONSTRAINT pk_tenant_role_template_action PRIMARY KEY (tenant_id, tenant_role_template_action_id),
  CONSTRAINT uq_tenant_role_template_action UNIQUE (tenant_id, tenant_role_template_id, action_code),
  CONSTRAINT fk_tenant_role_template_action_template FOREIGN KEY (tenant_id, tenant_role_template_id) REFERENCES app.tenant_role_template (tenant_id, tenant_role_template_id) ON DELETE RESTRICT,
  CONSTRAINT fk_tenant_role_template_action_code FOREIGN KEY (action_code) REFERENCES ref.action_catalog (action_code)
);

CREATE INDEX IF NOT EXISTS ix_tenant_role_template_action_code ON app.tenant_role_template_action (tenant_id, action_code);
CREATE INDEX IF NOT EXISTS ix_tenant_role_template_action_action_code ON app.tenant_role_template_action (action_code);

ALTER TABLE app.tenant_role_template_action ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.tenant_role_template_action FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE schemaname = 'app' AND tablename = 'tenant_role_template_action' AND policyname = 'p_tenant'
  ) THEN
    CREATE POLICY p_tenant ON app.tenant_role_template_action
      USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
      WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
  END IF;
END $$;

-- 3. Enhance app.role_definition for custom role lifecycle and hierarchy
ALTER TABLE app.role_definition ADD COLUMN IF NOT EXISTS owner_level text DEFAULT 'TENANT';
ALTER TABLE app.role_definition ADD COLUMN IF NOT EXISTS owner_institution_id uuid;

ALTER TABLE app.role_definition DROP CONSTRAINT IF EXISTS ck_role_definition_owner_level;
ALTER TABLE app.role_definition ADD CONSTRAINT ck_role_definition_owner_level CHECK (owner_level IS NULL OR owner_level IN ('TENANT', 'UNIVERSITY', 'COLLEGE', 'SCHOOL'));

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_role_definition_owner_institution'
  ) THEN
    ALTER TABLE app.role_definition
      ADD CONSTRAINT fk_role_definition_owner_institution
      FOREIGN KEY (tenant_id, owner_institution_id) REFERENCES app.institution (tenant_id, institution_id);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_role_definition_owner_institution ON app.role_definition (tenant_id, owner_institution_id);

ALTER TABLE app.role_definition DROP CONSTRAINT IF EXISTS ck_role_definition_status;
ALTER TABLE app.role_definition ADD CONSTRAINT ck_role_definition_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'RETIRED', 'ARCHIVED'));

-- 4. Enhance app.role_action with access_mode (SHARED vs HANDED_OVER)
ALTER TABLE app.role_action ADD COLUMN IF NOT EXISTS access_mode text NOT NULL DEFAULT 'SHARED';
ALTER TABLE app.role_action DROP CONSTRAINT IF EXISTS ck_role_action_mode;
ALTER TABLE app.role_action ADD CONSTRAINT ck_role_action_mode CHECK (access_mode IN ('SHARED', 'HANDED_OVER'));

-- 5. Tenant Step-In table (TPA-02/TPA-04)
CREATE TABLE IF NOT EXISTS app.tenant_step_in (
  tenant_id uuid NOT NULL,
  step_in_id uuid NOT NULL,
  actor_user_id uuid NOT NULL,
  action_code text NOT NULL,
  reason text NOT NULL,
  target_role_definition_id uuid,
  started_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  CONSTRAINT pk_tenant_step_in PRIMARY KEY (tenant_id, step_in_id),
  CONSTRAINT fk_tenant_step_in_actor FOREIGN KEY (tenant_id, actor_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT,
  CONSTRAINT fk_tenant_step_in_action FOREIGN KEY (action_code) REFERENCES ref.action_catalog (action_code),
  CONSTRAINT fk_tenant_step_in_target_role FOREIGN KEY (tenant_id, target_role_definition_id) REFERENCES app.role_definition (tenant_id, role_definition_id),
  CONSTRAINT ck_tenant_step_in_reason CHECK (char_length(btrim(reason)) BETWEEN 5 AND 500),
  CONSTRAINT ck_tenant_step_in_window CHECK (expires_at > started_at AND expires_at <= started_at + interval '30 minutes')
);

COMMENT ON TABLE app.tenant_step_in IS 'Recorded time-limited step-in on a handed-over action [TPA-02, TPA-04]. Append-only.';
CREATE INDEX IF NOT EXISTS ix_tenant_step_in_actor_action ON app.tenant_step_in (tenant_id, actor_user_id, action_code, expires_at);
CREATE INDEX IF NOT EXISTS ix_tenant_step_in_action_code ON app.tenant_step_in (action_code);
CREATE INDEX IF NOT EXISTS ix_tenant_step_in_target_role ON app.tenant_step_in (tenant_id, target_role_definition_id);

ALTER TABLE app.tenant_step_in ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.tenant_step_in FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE schemaname = 'app' AND tablename = 'tenant_step_in' AND policyname = 'p_tenant'
  ) THEN
    CREATE POLICY p_tenant ON app.tenant_step_in
      USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
      WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger WHERE tgname = 'trg_append_only' AND tgrelid = 'app.tenant_step_in'::regclass
  ) THEN
    CREATE TRIGGER trg_append_only BEFORE UPDATE OR DELETE ON app.tenant_step_in FOR EACH ROW EXECUTE FUNCTION app.fn_append_only();
  END IF;
END $$;

GRANT SELECT, INSERT, UPDATE ON app.tenant_role_template TO edu_app;
GRANT SELECT, INSERT, DELETE ON app.tenant_role_template_action TO edu_app;
GRANT SELECT, INSERT ON app.tenant_step_in TO edu_app;
GRANT SELECT ON app.tenant_role_template TO edu_report;
GRANT SELECT ON app.tenant_role_template_action TO edu_report;
GRANT SELECT ON app.tenant_step_in TO edu_report;
