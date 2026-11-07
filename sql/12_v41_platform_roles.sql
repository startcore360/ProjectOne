-- EDU 360 V4.1 — editable platform roles and templates (TPA-01..TPA-04 and OD-62,
-- docs/GOVERNANCE_DECISION_LOG.md, 2026-11-07). Apply after 10_v40_rolldown.sql.
-- Template rows: 12d_v41_platform_role_templates.sql. Rollback: 12r_v41_platform_roles_rollback.sql.
--
-- The six standard platform roles stay (ck_platform_user_platform_role). A custom role starts from
-- a template copy, and a person holds at most one. platform_user.platform_role keeps naming the
-- standard role it was based on. A custom role's actions are never inherited from a template after
-- creation: editing a template changes only what later roles start from (OD-62).

CREATE TABLE platform.platform_role_template (
  platform_role_template_id uuid NOT NULL,
  template_name text NOT NULL,
  description text,
  base_platform_role text NOT NULL,
  is_standard boolean NOT NULL DEFAULT false,
  is_locked boolean NOT NULL DEFAULT false,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_platform_role_template PRIMARY KEY (platform_role_template_id),
  CONSTRAINT uq_platform_role_template_name UNIQUE (template_name),
  CONSTRAINT ck_platform_role_template_name CHECK (char_length(template_name) BETWEEN 2 AND 100),
  CONSTRAINT ck_platform_role_template_description CHECK (char_length(description) <= 500),
  CONSTRAINT ck_platform_role_template_base CHECK (base_platform_role IN ('PLATFORM_ADMIN', 'BILLING_MANAGER', 'SUPPORT_MANAGER', 'RELEASE_MANAGER', 'DATA_PROTECTION_OFFICER')),
  CONSTRAINT ck_platform_role_template_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  CONSTRAINT ck_platform_role_template_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_platform_role_template_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.platform_role_template IS 'Starting suggestions for platform custom roles. Choosing one copies its actions; it never grants access (D3 CR-07).';
COMMENT ON COLUMN platform.platform_role_template.is_standard IS 'Seeded from ref.standard_role_action; can be reset to those rows.';
COMMENT ON COLUMN platform.platform_role_template.is_locked IS 'Read-only reference. The Data Protection Officer template is locked because its approvals are never delegated (D1 §7).';

CREATE TABLE platform.platform_role_template_action (
  platform_role_template_action_id uuid NOT NULL,
  platform_role_template_id uuid NOT NULL,
  action_code text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  CONSTRAINT pk_platform_role_template_action PRIMARY KEY (platform_role_template_action_id),
  CONSTRAINT uq_platform_role_template_action UNIQUE (platform_role_template_id, action_code),
  CONSTRAINT fk_platform_role_template_action_template FOREIGN KEY (platform_role_template_id) REFERENCES platform.platform_role_template (platform_role_template_id),
  CONSTRAINT fk_platform_role_template_action_code FOREIGN KEY (action_code) REFERENCES ref.action_catalog (action_code)
);
COMMENT ON TABLE platform.platform_role_template_action IS 'Actions a template suggests.';
CREATE INDEX ix_platform_role_template_action_code ON platform.platform_role_template_action (action_code);

CREATE TABLE platform.platform_role (
  platform_role_id uuid NOT NULL,
  role_name text NOT NULL,
  base_platform_role text NOT NULL,
  source_template_id uuid,
  status text NOT NULL DEFAULT 'DRAFT',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_platform_role PRIMARY KEY (platform_role_id),
  CONSTRAINT uq_platform_role_name UNIQUE (role_name),
  CONSTRAINT ck_platform_role_name CHECK (char_length(role_name) BETWEEN 2 AND 100),
  CONSTRAINT ck_platform_role_base CHECK (base_platform_role IN ('PLATFORM_ADMIN', 'BILLING_MANAGER', 'SUPPORT_MANAGER', 'RELEASE_MANAGER')),
  CONSTRAINT ck_platform_role_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),
  CONSTRAINT ck_platform_role_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_platform_role_upd CHECK (updated_at >= created_at),
  CONSTRAINT fk_platform_role_source_template FOREIGN KEY (source_template_id) REFERENCES platform.platform_role_template (platform_role_template_id)
);
COMMENT ON TABLE platform.platform_role IS 'A custom platform role. Lifecycle per Addendum rule 16; never hard-deleted (rule 17).';
COMMENT ON COLUMN platform.platform_role.base_platform_role IS 'The standard role this work is based on. Product Owner and Data Protection Officer are excluded: neither can be customised.';
CREATE INDEX ix_platform_role_source_template ON platform.platform_role (source_template_id);

CREATE TABLE platform.platform_role_action (
  platform_role_action_id uuid NOT NULL,
  platform_role_id uuid NOT NULL,
  action_code text NOT NULL,
  access_mode text NOT NULL DEFAULT 'SHARED',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  CONSTRAINT pk_platform_role_action PRIMARY KEY (platform_role_action_id),
  CONSTRAINT uq_platform_role_action UNIQUE (platform_role_id, action_code),
  CONSTRAINT fk_platform_role_action_role FOREIGN KEY (platform_role_id) REFERENCES platform.platform_role (platform_role_id),
  CONSTRAINT fk_platform_role_action_code FOREIGN KEY (action_code) REFERENCES ref.action_catalog (action_code),
  CONSTRAINT ck_platform_role_action_mode CHECK (access_mode IN ('SHARED', 'HANDED_OVER')),
  -- Data protection approvals stay with a named Data Protection Officer (D1 §7, D2 action 198). The
  -- Product Owner's own powers to add staff and to confirm a deletion can't be handed on, so nobody
  -- can raise their own access or take both steps of a deletion through a custom role.
  CONSTRAINT ck_platform_role_action_reserved CHECK (action_code NOT IN ('TENANT_EXPORT_APPROVE', 'TENANT_DELETION_APPROVE', 'RETENTION_CHANGE_APPROVE', 'APPROVAL_DELEGATE', 'PLATFORM_USER_MANAGE', 'TENANT_DELETION_CONFIRM'))
);
COMMENT ON TABLE platform.platform_role_action IS 'Actions held by a custom role.';
COMMENT ON COLUMN platform.platform_role_action.access_mode IS 'SHARED: the Product Owner can also act. HANDED_OVER: while an active person holds the role, the Product Owner acts only by stepping in (TPA-02).';
CREATE INDEX ix_platform_role_action_code ON platform.platform_role_action (action_code);

ALTER TABLE platform.platform_user ADD COLUMN platform_role_id uuid;
ALTER TABLE platform.platform_user ADD CONSTRAINT fk_platform_user_platform_role FOREIGN KEY (platform_role_id) REFERENCES platform.platform_role (platform_role_id);
ALTER TABLE platform.platform_user ADD CONSTRAINT ck_platform_user_custom_role CHECK (platform_role_id IS NULL OR platform_role NOT IN ('PRODUCT_OWNER', 'DATA_PROTECTION_OFFICER'));
COMMENT ON COLUMN platform.platform_user.platform_role_id IS 'The custom role held, if any. NULL means the standard rights of platform_role apply.';
CREATE INDEX ix_platform_user_platform_role_id ON platform.platform_user (platform_role_id);

CREATE TABLE platform.platform_step_in (
  platform_step_in_id uuid NOT NULL,
  actor_user_id uuid NOT NULL,
  action_code text NOT NULL,
  reason text NOT NULL,
  started_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  CONSTRAINT pk_platform_step_in PRIMARY KEY (platform_step_in_id),
  CONSTRAINT fk_platform_step_in_actor FOREIGN KEY (actor_user_id) REFERENCES platform.platform_user (platform_user_id),
  CONSTRAINT fk_platform_step_in_action FOREIGN KEY (action_code) REFERENCES ref.action_catalog (action_code),
  CONSTRAINT ck_platform_step_in_reason CHECK (char_length(btrim(reason)) BETWEEN 5 AND 500),
  CONSTRAINT ck_platform_step_in_window CHECK (expires_at > started_at AND expires_at <= started_at + interval '60 minutes')
);
COMMENT ON TABLE platform.platform_step_in IS 'A recorded, time-limited step-in on a handed-over action (TPA-02). Append-only.';
CREATE INDEX ix_platform_step_in_actor_action ON platform.platform_step_in (actor_user_id, action_code, expires_at);
CREATE INDEX ix_platform_step_in_action ON platform.platform_step_in (action_code);

CREATE TRIGGER trg_touch BEFORE UPDATE ON platform.platform_role_template FOR EACH ROW EXECUTE FUNCTION platform.fn_touch();
CREATE TRIGGER trg_touch BEFORE UPDATE ON platform.platform_role FOR EACH ROW EXECUTE FUNCTION platform.fn_touch();
CREATE TRIGGER trg_append_only BEFORE UPDATE OR DELETE ON platform.platform_step_in FOR EACH ROW EXECUTE FUNCTION platform.fn_append_only();

GRANT SELECT, INSERT, UPDATE ON platform.platform_role_template TO edu_platform;
GRANT SELECT, INSERT, DELETE ON platform.platform_role_template_action TO edu_platform;
GRANT SELECT, INSERT, UPDATE ON platform.platform_role TO edu_platform;
GRANT SELECT, INSERT, DELETE ON platform.platform_role_action TO edu_platform;
GRANT SELECT, INSERT ON platform.platform_step_in TO edu_platform;
