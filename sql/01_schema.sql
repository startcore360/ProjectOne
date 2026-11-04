-- EDU 360 Version 3.3 baseline schema (generated from model/*.py). Validated on PostgreSQL 16; major version is OD-01.
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE SCHEMA IF NOT EXISTS ref;
CREATE SCHEMA IF NOT EXISTS platform;
CREATE SCHEMA IF NOT EXISTS app;
DO $$BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='edu_app') THEN CREATE ROLE edu_app LOGIN NOSUPERUSER NOBYPASSRLS; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='edu_report') THEN CREATE ROLE edu_report LOGIN NOSUPERUSER NOBYPASSRLS; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='edu_retention') THEN CREATE ROLE edu_retention LOGIN NOSUPERUSER NOBYPASSRLS; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='edu_shipper') THEN CREATE ROLE edu_shipper LOGIN NOSUPERUSER NOBYPASSRLS; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='edu_platform') THEN CREATE ROLE edu_platform LOGIN NOSUPERUSER NOBYPASSRLS; END IF;
END$$;
CREATE TABLE ref.module_catalog (
  module_code text NOT NULL,
  module_name text NOT NULL,
  phase smallint NOT NULL,
  is_entitlement boolean NOT NULL DEFAULT true,
  CONSTRAINT pk_module_catalog PRIMARY KEY (module_code),
  CONSTRAINT ck_module_catalog_module_code CHECK (module_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
  CONSTRAINT ck_module_catalog_module_name CHECK (char_length(module_name) BETWEEN 2 AND 100),
  CONSTRAINT ck_module_catalog_phase CHECK (phase BETWEEN 1 AND 3)
);
COMMENT ON TABLE ref.module_catalog IS 'The 29 product modules and their phase; drives entitlements.';
COMMENT ON COLUMN ref.module_catalog.module_code IS 'Module key, e.g. FEES. [§8]';
COMMENT ON COLUMN ref.module_catalog.module_name IS 'Display name. Length 2–100.';
COMMENT ON COLUMN ref.module_catalog.phase IS 'Release phase. [OB-02]';
COMMENT ON COLUMN ref.module_catalog.is_entitlement IS 'Can be switched per plan / tenant. [PQ-01]';
CREATE TABLE ref.feature_catalog (
  feature_code text NOT NULL,
  module_code text NOT NULL,
  description text NOT NULL,
  CONSTRAINT pk_feature_catalog PRIMARY KEY (feature_code),
  CONSTRAINT ck_feature_catalog_feature_code CHECK (feature_code ~ '^[A-Z][A-Z0-9_]{1,59}$'),
  CONSTRAINT ck_feature_catalog_description CHECK (char_length(description) BETWEEN 1 AND 300)
);
COMMENT ON TABLE ref.feature_catalog IS 'Features inside a module that can be toggled (list not in the sources: OD-43a).';
COMMENT ON COLUMN ref.feature_catalog.feature_code IS 'Feature key.';
COMMENT ON COLUMN ref.feature_catalog.module_code IS 'Owning module.';
COMMENT ON COLUMN ref.feature_catalog.description IS 'What the feature is. Length 1–300.';
CREATE TABLE ref.module_dependency (
  module_dependency_id integer NOT NULL,
  module_code text NOT NULL,
  requires_module_code text NOT NULL,
  kind text NOT NULL,
  CONSTRAINT pk_module_dependency PRIMARY KEY (module_dependency_id),
  CONSTRAINT ck_module_dependency_kind CHECK (kind IN ('REQUIRED', 'OPTIONAL')),
  CONSTRAINT ck_module_dependency_r1 CHECK (module_code <> requires_module_code)
);
COMMENT ON TABLE ref.module_dependency IS 'Declared required / optional module dependencies (PQ-73). Rows pending OD-43.';
COMMENT ON COLUMN ref.module_dependency.module_dependency_id IS 'Surrogate key.';
COMMENT ON COLUMN ref.module_dependency.module_code IS 'Dependent module.';
COMMENT ON COLUMN ref.module_dependency.requires_module_code IS 'Module it depends on.';
COMMENT ON COLUMN ref.module_dependency.kind IS 'Required blocks unsafe disabling; optional fails gracefully. [PQ-73]';
CREATE TABLE ref.feature_area (
  feature_area_code text NOT NULL,
  feature_area_name text NOT NULL,
  display_order smallint NOT NULL,
  CONSTRAINT pk_feature_area PRIMARY KEY (feature_area_code),
  CONSTRAINT ck_feature_area_feature_area_name CHECK (char_length(feature_area_name) BETWEEN 1 AND 60),
  CONSTRAINT ck_feature_area_display_order CHECK (display_order BETWEEN 1 AND 28)
);
COMMENT ON TABLE ref.feature_area IS 'The 28 permission-matrix columns (Tenant … Integrations).';
COMMENT ON COLUMN ref.feature_area.feature_area_code IS 'Area key, e.g. HR_PAYROLL.';
COMMENT ON COLUMN ref.feature_area.feature_area_name IS 'Matrix column label. Length 1–60.';
COMMENT ON COLUMN ref.feature_area.display_order IS 'Column order.';
CREATE TABLE ref.standard_role (
  role_code text NOT NULL,
  role_name text NOT NULL,
  role_level text NOT NULL,
  introduced_in_version text NOT NULL,
  requires_two_step_login boolean NOT NULL DEFAULT false,
  is_staff boolean NOT NULL DEFAULT true,
  CONSTRAINT pk_standard_role PRIMARY KEY (role_code),
  CONSTRAINT ck_standard_role_role_code CHECK (role_code ~ '^[A-Z][A-Z0-9_]{2,59}$'),
  CONSTRAINT ck_standard_role_role_name CHECK (char_length(role_name) BETWEEN 2 AND 100),
  CONSTRAINT ck_standard_role_role_level CHECK (role_level IN ('PLATFORM', 'TENANT', 'UNIVERSITY', 'COLLEGE', 'SCHOOL', 'EXTERNAL'))
);
COMMENT ON TABLE ref.standard_role IS 'All 71 standard roles of Permission Matrix V3.3 (6 platform + 65 tenant-side).';
COMMENT ON COLUMN ref.standard_role.role_code IS 'Role key, e.g. GOVERNING_BODY_APPROVER.';
COMMENT ON COLUMN ref.standard_role.role_name IS 'Display name as in the matrix. Length 2–100.';
COMMENT ON COLUMN ref.standard_role.role_level IS 'Matrix level.';
COMMENT ON COLUMN ref.standard_role.introduced_in_version IS 'Version that added the role (e.g. 3.3 for the Governing Body Approver). [C6, C13]';
COMMENT ON COLUMN ref.standard_role.requires_two_step_login IS 'Two-step login required [NF-09, F-14, C6]. [NF-09, F-14, C6]';
COMMENT ON COLUMN ref.standard_role.is_staff IS 'Counts as "tenant staff" for staff self-service groups. False for Management and the Governing Body Approver [C9], students, parents and applicants. [C9]';
CREATE TABLE ref.standard_role_action (
  standard_role_action_id integer NOT NULL,
  role_code text NOT NULL,
  action_code text NOT NULL,
  limited_to text,
  CONSTRAINT pk_standard_role_action PRIMARY KEY (standard_role_action_id),
  CONSTRAINT ck_standard_role_action_limited_to CHECK (char_length(limited_to) BETWEEN 0 AND 2000)
);
COMMENT ON TABLE ref.standard_role_action IS 'Role-to-action template from Action List V3.3 column "Who can do it" (1 row per pair).';
COMMENT ON COLUMN ref.standard_role_action.standard_role_action_id IS 'Surrogate key.';
COMMENT ON COLUMN ref.standard_role_action.role_code IS 'Role.';
COMMENT ON COLUMN ref.standard_role_action.action_code IS 'Action.';
COMMENT ON COLUMN ref.standard_role_action.limited_to IS '"Limited to" text of the Action List (scope rule enforced by the service). Length 0–2000.';
CREATE TABLE ref.permission_matrix_cell (
  permission_matrix_cell_id integer NOT NULL,
  role_code text NOT NULL,
  feature_area_code text NOT NULL,
  letter text NOT NULL,
  scope_note text,
  CONSTRAINT pk_permission_matrix_cell PRIMARY KEY (permission_matrix_cell_id),
  CONSTRAINT ck_permission_matrix_cell_letter CHECK (letter ~ '^(F|M|C|A|V|R|N)(\+(F|M|C|A|V|R))*$'),
  CONSTRAINT ck_permission_matrix_cell_scope_note CHECK (char_length(scope_note) BETWEEN 0 AND 500)
);
COMMENT ON TABLE ref.permission_matrix_cell IS 'Matrix V3.3 letters (71 × 28 = 1,988 cells) for the RBAC mapping check.';
COMMENT ON COLUMN ref.permission_matrix_cell.permission_matrix_cell_id IS 'Surrogate key.';
COMMENT ON COLUMN ref.permission_matrix_cell.role_code IS 'Role.';
COMMENT ON COLUMN ref.permission_matrix_cell.feature_area_code IS 'Area.';
COMMENT ON COLUMN ref.permission_matrix_cell.letter IS 'Letter(s), e.g. V+A. [RL-03]';
COMMENT ON COLUMN ref.permission_matrix_cell.scope_note IS 'Scoped-letter meaning (Matrix "Scoped Letters" sheet). Length 0–500.';
CREATE TABLE ref.action_catalog (
  action_code text NOT NULL,
  action_no smallint NOT NULL,
  module_name text NOT NULL,
  phase smallint NOT NULL,
  description text NOT NULL,
  is_approval_action boolean NOT NULL DEFAULT false,
  sensitive_class text,
  changed_in text,
  CONSTRAINT pk_action_catalog PRIMARY KEY (action_code),
  CONSTRAINT ck_action_catalog_action_code CHECK (action_code ~ '^[A-Z][A-Z0-9_]+$'),
  CONSTRAINT ck_action_catalog_action_no CHECK (action_no BETWEEN 1 AND 999),
  CONSTRAINT uq_action_catalog_action_no UNIQUE (action_no),
  CONSTRAINT ck_action_catalog_module_name CHECK (char_length(module_name) BETWEEN 2 AND 60),
  CONSTRAINT ck_action_catalog_phase CHECK (phase BETWEEN 1 AND 3),
  CONSTRAINT ck_action_catalog_description CHECK (char_length(description) BETWEEN 1 AND 300),
  CONSTRAINT ck_action_catalog_sensitive_class CHECK (char_length(sensitive_class) BETWEEN 1 AND 200),
  CONSTRAINT ck_action_catalog_changed_in CHECK (char_length(changed_in) BETWEEN 0 AND 20)
);
COMMENT ON TABLE ref.action_catalog IS 'All 278 actions of Action List V3.3 (234 Phase 1, 32 Phase 2, 12 Phase 3).';
COMMENT ON COLUMN ref.action_catalog.action_code IS 'Action key.';
COMMENT ON COLUMN ref.action_catalog.action_no IS 'Action List row number (#).';
COMMENT ON COLUMN ref.action_catalog.module_name IS 'Action List module. Length 2–60.';
COMMENT ON COLUMN ref.action_catalog.phase IS 'Phase.';
COMMENT ON COLUMN ref.action_catalog.description IS '"What it does". Length 1–300.';
COMMENT ON COLUMN ref.action_catalog.is_approval_action IS 'True for chain-step actions; act only at the step the role holds [Rule 1]. [§26.1]';
COMMENT ON COLUMN ref.action_catalog.sensitive_class IS 'Action List "Sensitive data" value; non-NULL on 101 actions in V3.3 [PQ-66]. Length 1–200. [PQ-66, V3.3 letter gaps]';
COMMENT ON COLUMN ref.action_catalog.changed_in IS 'Last version that changed the action (3.0/3.1/3.2/3.3). Length 0–20.';
CREATE TABLE ref.approval_process (
  process_code text NOT NULL,
  process_name text NOT NULL,
  subject_type text NOT NULL,
  allows_bulk boolean NOT NULL DEFAULT true,
  allows_delegation boolean NOT NULL DEFAULT true,
  source_ref text NOT NULL,
  CONSTRAINT pk_approval_process PRIMARY KEY (process_code),
  CONSTRAINT ck_approval_process_process_name CHECK (char_length(process_name) BETWEEN 2 AND 150),
  CONSTRAINT ck_approval_process_subject_type CHECK (char_length(subject_type) BETWEEN 2 AND 60),
  CONSTRAINT ck_approval_process_source_ref CHECK (char_length(source_ref) BETWEEN 1 AND 200)
);
COMMENT ON TABLE ref.approval_process IS 'Approval chains of master document §7.1 (V3.3).';
COMMENT ON COLUMN ref.approval_process.process_code IS 'Chain key, e.g. FEE_STRUCTURE.';
COMMENT ON COLUMN ref.approval_process.process_name IS 'Name. Length 2–150.';
COMMENT ON COLUMN ref.approval_process.subject_type IS 'Entity type the request is about (matches approval_request.subject_type). Length 2–60.';
COMMENT ON COLUMN ref.approval_process.allows_bulk IS 'False for chains whose approvals may never be bulk-approved. [AP-25, C12]';
COMMENT ON COLUMN ref.approval_process.allows_delegation IS 'False where delegation is forbidden (merit lists, WF-37). [WF-37, C12]';
COMMENT ON COLUMN ref.approval_process.source_ref IS 'Decision IDs. Length 1–200.';
CREATE TABLE ref.approval_chain_step (
  approval_chain_step_id integer NOT NULL,
  process_code text NOT NULL,
  step_no smallint NOT NULL,
  approver_rule text NOT NULL,
  action_code text NOT NULL,
  is_optional boolean NOT NULL DEFAULT false,
  final_when_days_lte smallint,
  skip_if_role_absent boolean NOT NULL DEFAULT false,
  forward_only boolean NOT NULL DEFAULT false,
  CONSTRAINT pk_approval_chain_step PRIMARY KEY (approval_chain_step_id),
  CONSTRAINT ck_approval_chain_step_step_no CHECK (step_no BETWEEN 1 AND 10),
  CONSTRAINT ck_approval_chain_step_approver_rule CHECK (char_length(approver_rule) BETWEEN 2 AND 300),
  CONSTRAINT ck_approval_chain_step_final_when_days_lte CHECK (final_when_days_lte IS NULL OR final_when_days_lte >= 0)
);
COMMENT ON TABLE ref.approval_chain_step IS 'Ordered steps of each chain.';
COMMENT ON COLUMN ref.approval_chain_step.approval_chain_step_id IS 'Surrogate key.';
COMMENT ON COLUMN ref.approval_chain_step.process_code IS 'Chain.';
COMMENT ON COLUMN ref.approval_chain_step.step_no IS 'Order.';
COMMENT ON COLUMN ref.approval_chain_step.approver_rule IS 'Approver role(s) or rule (e.g. "set approver: Management, else Governing Body Approver"). Length 2–300.';
COMMENT ON COLUMN ref.approval_chain_step.action_code IS 'Action the approver must hold at this step.';
COMMENT ON COLUMN ref.approval_chain_step.is_optional IS 'Step only when the institution switches it on (e.g. exam-schedule approval [Q9]).';
COMMENT ON COLUMN ref.approval_chain_step.final_when_days_lte IS 'Length-based final approver (leave). [AP-11..13]';
COMMENT ON COLUMN ref.approval_chain_step.skip_if_role_absent IS 'Skip when the role does not exist in the institution (e.g. no Management) [R2-03].';
COMMENT ON COLUMN ref.approval_chain_step.forward_only IS 'Step may only forward with a remark, not reject (mentor for leave > 2 days) [F-11]. [F-11]';
CREATE TABLE ref.notification_template (
  trigger_code text NOT NULL,
  title_template text NOT NULL,
  default_channels text[] NOT NULL,
  priority_class text NOT NULL,
  is_optional boolean NOT NULL DEFAULT false,
  CONSTRAINT pk_notification_template PRIMARY KEY (trigger_code),
  CONSTRAINT ck_notification_template_title_template CHECK (char_length(title_template) BETWEEN 1 AND 200),
  CONSTRAINT ck_notification_template_priority_class CHECK (priority_class IN ('NORMAL', 'SECURITY', 'EMERGENCY'))
);
COMMENT ON TABLE ref.notification_template IS 'Notification triggers of §15 with default channels.';
COMMENT ON COLUMN ref.notification_template.trigger_code IS 'Trigger key.';
COMMENT ON COLUMN ref.notification_template.title_template IS 'Heading (the only text sent by SMS [CM-15]). Length 1–200.';
COMMENT ON COLUMN ref.notification_template.default_channels IS 'Subset of IN_APP, PUSH, SMS, EMAIL (no WHATSAPP in v1). [PQ-54]';
COMMENT ON COLUMN ref.notification_template.priority_class IS 'Quiet-hours behaviour. [CM-02, PQ-57]';
COMMENT ON COLUMN ref.notification_template.is_optional IS 'Institution may tick it on/off [CM-16]; mandatory ones never suppressed [PQ-57].';
CREATE TABLE ref.report_catalog (
  report_code text NOT NULL,
  report_name text NOT NULL,
  module_code text NOT NULL,
  formats text[] NOT NULL,
  required_action_code text NOT NULL,
  sensitive_class text,
  CONSTRAINT pk_report_catalog PRIMARY KEY (report_code),
  CONSTRAINT ck_report_catalog_report_name CHECK (char_length(report_name) BETWEEN 2 AND 150),
  CONSTRAINT ck_report_catalog_sensitive_class CHECK (char_length(sensitive_class) BETWEEN 1 AND 100)
);
COMMENT ON TABLE ref.report_catalog IS 'Fixed reports and statutory exports.';
COMMENT ON COLUMN ref.report_catalog.report_code IS 'Report key.';
COMMENT ON COLUMN ref.report_catalog.report_name IS 'Name. Length 2–150.';
COMMENT ON COLUMN ref.report_catalog.module_code IS 'Module.';
COMMENT ON COLUMN ref.report_catalog.formats IS 'PDF, XLSX, CSV (CSV only where a template needs it, OD-48).';
COMMENT ON COLUMN ref.report_catalog.required_action_code IS 'Action needed to run it (REPORTS_VIEW, LEDGER_VIEW, STATUTORY_EXPORT…).';
COMMENT ON COLUMN ref.report_catalog.sensitive_class IS 'Sensitivity of the output. Length 1–100.';
CREATE TABLE ref.job_type (
  job_type text NOT NULL,
  max_attempts smallint NOT NULL,
  requires_authorization_recheck boolean NOT NULL DEFAULT false,
  is_idempotent boolean NOT NULL DEFAULT true,
  CONSTRAINT pk_job_type PRIMARY KEY (job_type),
  CONSTRAINT ck_job_type_max_attempts CHECK (max_attempts BETWEEN 1 AND 50)
);
COMMENT ON TABLE ref.job_type IS 'Background job types.';
COMMENT ON COLUMN ref.job_type.job_type IS 'Job key.';
COMMENT ON COLUMN ref.job_type.max_attempts IS 'Retry ceiling (value OD-38). (OD-38)';
COMMENT ON COLUMN ref.job_type.requires_authorization_recheck IS 'Re-check requester rights at run time [PQ-60].';
COMMENT ON COLUMN ref.job_type.is_idempotent IS 'Safe to re-run.';
CREATE TABLE ref.safe_file_type (
  safe_file_type_id integer NOT NULL,
  purpose text NOT NULL,
  extension text NOT NULL,
  mime_type text NOT NULL,
  max_size_bytes bigint NOT NULL,
  CONSTRAINT pk_safe_file_type PRIMARY KEY (safe_file_type_id),
  CONSTRAINT ck_safe_file_type_purpose CHECK (char_length(purpose) BETWEEN 2 AND 40),
  CONSTRAINT ck_safe_file_type_extension CHECK (char_length(extension) BETWEEN 1 AND 10),
  CONSTRAINT ck_safe_file_type_mime_type CHECK (char_length(mime_type) BETWEEN 3 AND 100),
  CONSTRAINT ck_safe_file_type_max_size_bytes CHECK (max_size_bytes > 0)
);
COMMENT ON TABLE ref.safe_file_type IS 'Allowed upload types and limits per purpose (only message limits are in the sources: OD-35).';
COMMENT ON COLUMN ref.safe_file_type.safe_file_type_id IS 'Surrogate key.';
COMMENT ON COLUMN ref.safe_file_type.purpose IS 'file_object.purpose value. Length 2–40.';
COMMENT ON COLUMN ref.safe_file_type.extension IS 'e.g. pdf. Length 1–10.';
COMMENT ON COLUMN ref.safe_file_type.mime_type IS 'e.g. application/pdf. Length 3–100.';
COMMENT ON COLUMN ref.safe_file_type.max_size_bytes IS 'Limit; 10 MB for message attachments [CM-06] (byte definition OD-36). (OD-35, OD-36)';
CREATE TABLE ref.setting_definition (
  setting_key text NOT NULL,
  value_type text NOT NULL,
  default_value jsonb NOT NULL,
  allowed_scope_levels text[] NOT NULL,
  validation jsonb NOT NULL DEFAULT '{}'::jsonb,
  owner_action_code text,
  is_overridable boolean NOT NULL DEFAULT true,
  weakening_guard text,
  source_ref text NOT NULL,
  CONSTRAINT pk_setting_definition PRIMARY KEY (setting_key),
  CONSTRAINT ck_setting_definition_setting_key CHECK (setting_key ~ '^[a-z][a-z0-9_.]+$'),
  CONSTRAINT ck_setting_definition_value_type CHECK (value_type IN ('BOOLEAN', 'INTEGER', 'NUMERIC', 'TEXT', 'JSON')),
  CONSTRAINT ck_setting_definition_weakening_guard CHECK (char_length(weakening_guard) BETWEEN 1 AND 60),
  CONSTRAINT ck_setting_definition_source_ref CHECK (char_length(source_ref) BETWEEN 1 AND 200)
);
COMMENT ON TABLE ref.setting_definition IS 'Every configurable setting, its default, allowed levels and owner action (PTD-38, precedence OD-08).';
COMMENT ON COLUMN ref.setting_definition.setting_key IS 'Key, e.g. risk.normal_min_percent.';
COMMENT ON COLUMN ref.setting_definition.value_type IS 'Type.';
COMMENT ON COLUMN ref.setting_definition.default_value IS 'Platform default (JSON null when the sources give none).';
COMMENT ON COLUMN ref.setting_definition.allowed_scope_levels IS 'PLATFORM, TENANT, INSTITUTION.';
COMMENT ON COLUMN ref.setting_definition.validation IS 'min / max / allowed values.';
COMMENT ON COLUMN ref.setting_definition.owner_action_code IS 'Action needed to change it; NULL = fixed.';
COMMENT ON COLUMN ref.setting_definition.is_overridable IS 'False = fixed platform value (quiet hours, 3-day unlock).';
COMMENT ON COLUMN ref.setting_definition.weakening_guard IS 'Rule name preventing security weakening [PQ-74]. Length 1–60.';
COMMENT ON COLUMN ref.setting_definition.source_ref IS 'Decision IDs. Length 1–200.';
CREATE TABLE ref.idempotency_scope (
  scope text NOT NULL,
  operations text NOT NULL,
  key_class text NOT NULL,
  CONSTRAINT pk_idempotency_scope PRIMARY KEY (scope),
  CONSTRAINT ck_idempotency_scope_scope CHECK (scope ~ '^[A-Z][A-Z_]+$'),
  CONSTRAINT ck_idempotency_scope_operations CHECK (char_length(operations) BETWEEN 1 AND 500),
  CONSTRAINT ck_idempotency_scope_key_class CHECK (key_class IN ('REQUIRED', 'SUPPORTED'))
);
COMMENT ON TABLE ref.idempotency_scope IS 'One list of idempotency scopes shared by the API and the database (replaces the 6-value CHECK; fixes finding 40).';
COMMENT ON COLUMN ref.idempotency_scope.scope IS 'Scope key.';
COMMENT ON COLUMN ref.idempotency_scope.operations IS 'operationIds that use it. Length 1–500.';
COMMENT ON COLUMN ref.idempotency_scope.key_class IS 'Whether the Idempotency-Key header is mandatory.';
CREATE TABLE ref.data_class (
  data_class_code text NOT NULL,
  description text NOT NULL,
  fixed_rule text,
  deletion_allowed boolean NOT NULL DEFAULT false,
  CONSTRAINT pk_data_class PRIMARY KEY (data_class_code),
  CONSTRAINT ck_data_class_description CHECK (char_length(description) BETWEEN 1 AND 300)
);
COMMENT ON TABLE ref.data_class IS 'Retention data classes; durations are OD-06 (open item 20).';
COMMENT ON COLUMN ref.data_class.data_class_code IS 'Class key, e.g. STUDENT_NOTE.';
COMMENT ON COLUMN ref.data_class.description IS 'What the class covers. Length 1–300.';
COMMENT ON COLUMN ref.data_class.fixed_rule IS 'Rule stated by the sources, e.g. "1 year after leaving unless on hold" [MD-22]; NULL when open.';
COMMENT ON COLUMN ref.data_class.deletion_allowed IS 'True only where the sources allow physical deletion (notes [MD-22], tenant deletion [CL-07]).';
CREATE TABLE ref.reference_load (
  reference_load_id bigint NOT NULL,
  source_file text NOT NULL,
  checksum_sha256 char(64) NOT NULL,
  row_counts jsonb NOT NULL,
  loaded_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pk_reference_load PRIMARY KEY (reference_load_id),
  CONSTRAINT ck_reference_load_source_file CHECK (char_length(source_file) BETWEEN 1 AND 300)
);
COMMENT ON TABLE ref.reference_load IS 'Record of each reference-data load with checksums (PTD-53).';
COMMENT ON COLUMN ref.reference_load.reference_load_id IS 'Surrogate key.';
COMMENT ON COLUMN ref.reference_load.source_file IS 'Loaded file. Length 1–300.';
COMMENT ON COLUMN ref.reference_load.checksum_sha256 IS 'File checksum.';
COMMENT ON COLUMN ref.reference_load.row_counts IS 'Rows per table.';
COMMENT ON COLUMN ref.reference_load.loaded_at IS 'When loaded.';
CREATE TABLE platform.platform_user (
  platform_user_id uuid NOT NULL,
  email text NOT NULL,
  full_name text NOT NULL,
  platform_role text NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  mfa_required boolean NOT NULL DEFAULT true,
  identity_subject text NOT NULL,
  last_login_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_platform_user PRIMARY KEY (platform_user_id),
  CONSTRAINT ck_platform_user_email CHECK (email = lower(email) AND email ~ '^[^@\s]+@[^@\s]+$'),
  CONSTRAINT uq_platform_user_email UNIQUE (email),
  CONSTRAINT ck_platform_user_full_name CHECK (char_length(full_name) BETWEEN 1 AND 150),
  CONSTRAINT ck_platform_user_platform_role CHECK (platform_role IN ('PRODUCT_OWNER', 'PLATFORM_ADMIN', 'BILLING_MANAGER', 'SUPPORT_MANAGER', 'RELEASE_MANAGER', 'DATA_PROTECTION_OFFICER')),
  CONSTRAINT ck_platform_user_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT ck_platform_user_mfa_required CHECK (mfa_required),
  CONSTRAINT ck_platform_user_identity_subject CHECK (char_length(identity_subject) BETWEEN 1 AND 255),
  CONSTRAINT uq_platform_user_identity_subject UNIQUE (identity_subject),
  CONSTRAINT ck_platform_user_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_platform_user_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.platform_user IS 'Platform staff accounts (Product Owner and five platform roles).';
COMMENT ON COLUMN platform.platform_user.platform_user_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.platform_user.email IS 'Lower-case login email. Length 3–254.';
COMMENT ON COLUMN platform.platform_user.full_name IS 'Name. Length 1–150.';
COMMENT ON COLUMN platform.platform_user.platform_role IS 'Platform role. [§6.6]';
COMMENT ON COLUMN platform.platform_user.status IS 'Created / deactivated by the Product Owner (PLATFORM_USER_MANAGE). [F-12]';
COMMENT ON COLUMN platform.platform_user.mfa_required IS 'Two-step login always required [NF-09]. [NF-09]';
COMMENT ON COLUMN platform.platform_user.identity_subject IS 'Identity-provider subject (OD-10). Length 1–255. (OD-10)';
COMMENT ON COLUMN platform.platform_user.last_login_at IS 'Last login.';
COMMENT ON COLUMN platform.platform_user.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.platform_user.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.platform_user.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.platform_user.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.platform_user.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.plan (
  plan_id uuid NOT NULL,
  plan_name text NOT NULL,
  pricing_type text NOT NULL DEFAULT 'PER_STUDENT_YEAR',
  price_amount numeric(14,2) NOT NULL,
  currency_code char(3) NOT NULL DEFAULT 'INR',
  user_limit integer,
  storage_limit_gb integer,
  sms_limit integer,
  api_limit integer,
  max_institutions integer,
  status text NOT NULL DEFAULT 'DRAFT',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_plan PRIMARY KEY (plan_id),
  CONSTRAINT ck_plan_plan_name CHECK (char_length(plan_name) BETWEEN 3 AND 100),
  CONSTRAINT uq_plan_plan_name UNIQUE (plan_name),
  CONSTRAINT ck_plan_pricing_type CHECK (pricing_type IN ('PER_STUDENT_YEAR', 'FLAT', 'PER_MODULE')),
  CONSTRAINT ck_plan_price_amount CHECK (price_amount >= 0),
  CONSTRAINT ck_plan_currency_code CHECK (currency_code = 'INR'),
  CONSTRAINT ck_plan_user_limit CHECK (user_limit IS NULL OR user_limit > 0),
  CONSTRAINT ck_plan_storage_limit_gb CHECK (storage_limit_gb IS NULL OR storage_limit_gb > 0),
  CONSTRAINT ck_plan_sms_limit CHECK (sms_limit IS NULL OR sms_limit > 0),
  CONSTRAINT ck_plan_api_limit CHECK (api_limit IS NULL OR api_limit > 0),
  CONSTRAINT ck_plan_max_institutions CHECK (max_institutions IS NULL OR max_institutions > 0),
  CONSTRAINT ck_plan_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
  CONSTRAINT ck_plan_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_plan_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.plan IS 'Subscription plan.';
COMMENT ON COLUMN platform.plan.plan_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.plan.plan_name IS 'Name. Length 3–100.';
COMMENT ON COLUMN platform.plan.pricing_type IS 'Pricing type [MK-03]. [MK-03]';
COMMENT ON COLUMN platform.plan.price_amount IS 'Price; only the Product Owner changes it (PLAN_MANAGE "not prices" for Platform Admin [F-4]). Money, INR in v1 (PTD-06). [F-4, MD-34]';
COMMENT ON COLUMN platform.plan.currency_code IS 'ISO currency. INR only in v1; column kept so other countries can be added later (PR-01). [PTD-06, PR-01]';
COMMENT ON COLUMN platform.plan.user_limit IS 'Users; warn at 90 %, block at 100 % [WF-01]. NULL = not set (PTD-19). [WF-01]';
COMMENT ON COLUMN platform.plan.storage_limit_gb IS 'Storage limit.';
COMMENT ON COLUMN platform.plan.sms_limit IS 'SMS limit per period.';
COMMENT ON COLUMN platform.plan.api_limit IS 'API call limit.';
COMMENT ON COLUMN platform.plan.max_institutions IS 'Plan limits the number of institutions [WF-14]. [WF-14]';
COMMENT ON COLUMN platform.plan.status IS 'A retired plan keeps its tenants.';
COMMENT ON COLUMN platform.plan.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.plan.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.plan.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.plan.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.plan.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.plan_entitlement (
  plan_entitlement_id uuid NOT NULL,
  plan_id uuid NOT NULL,
  module_code text NOT NULL,
  feature_code text,
  enabled boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_plan_entitlement PRIMARY KEY (plan_entitlement_id),
  CONSTRAINT ck_plan_entitlement_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_plan_entitlement_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.plan_entitlement IS 'Modules / features included in a plan.';
COMMENT ON COLUMN platform.plan_entitlement.plan_entitlement_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.plan_entitlement.plan_id IS 'Plan.';
COMMENT ON COLUMN platform.plan_entitlement.module_code IS 'Module.';
COMMENT ON COLUMN platform.plan_entitlement.feature_code IS 'Feature; NULL = whole module.';
COMMENT ON COLUMN platform.plan_entitlement.enabled IS 'Included.';
COMMENT ON COLUMN platform.plan_entitlement.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.plan_entitlement.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.plan_entitlement.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.plan_entitlement.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.plan_entitlement.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.tenant (
  tenant_id uuid NOT NULL,
  tenant_name text NOT NULL,
  tenant_type text NOT NULL,
  plan_id uuid NOT NULL,
  state text NOT NULL DEFAULT 'CREATED',
  isolation_mode text NOT NULL DEFAULT 'ISOLATED',
  db_connection_ref text,
  subdomain text NOT NULL,
  custom_domain text,
  time_zone text NOT NULL DEFAULT 'Asia/Kolkata',
  data_region text NOT NULL DEFAULT 'IN',
  trial_ends_at timestamptz,
  user_limit integer,
  storage_limit_gb integer,
  sms_limit integer,
  api_limit integer,
  branding_logo_file_ref uuid,
  branding_colors jsonb,
  keycloak_realm text,
  analytics_opt_out boolean NOT NULL DEFAULT false,
  activated_at timestamptz,
  suspended_at timestamptz,
  archived_at timestamptz,
  deleted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_tenant PRIMARY KEY (tenant_id),
  CONSTRAINT ck_tenant_tenant_name CHECK (char_length(tenant_name) BETWEEN 2 AND 200),
  CONSTRAINT ck_tenant_tenant_type CHECK (tenant_type IN ('UNIVERSITY_GROUP', 'STANDALONE_COLLEGE', 'STANDALONE_SCHOOL')),
  CONSTRAINT ck_tenant_state CHECK (state IN ('CREATED', 'TRIAL', 'ACTIVE', 'SUSPENDED', 'ARCHIVED', 'DELETED')),
  CONSTRAINT ck_tenant_isolation_mode CHECK (isolation_mode IN ('ISOLATED', 'SHARED')),
  CONSTRAINT ck_tenant_db_connection_ref CHECK (char_length(db_connection_ref) BETWEEN 1 AND 300),
  CONSTRAINT ck_tenant_subdomain CHECK (subdomain ~ '^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$'),
  CONSTRAINT uq_tenant_subdomain UNIQUE (subdomain),
  CONSTRAINT ck_tenant_custom_domain CHECK (char_length(custom_domain) BETWEEN 3 AND 253),
  CONSTRAINT uq_tenant_custom_domain UNIQUE (custom_domain),
  CONSTRAINT ck_tenant_data_region CHECK (data_region = 'IN'),
  CONSTRAINT ck_tenant_user_limit CHECK (user_limit IS NULL OR user_limit > 0),
  CONSTRAINT ck_tenant_storage_limit_gb CHECK (storage_limit_gb IS NULL OR storage_limit_gb > 0),
  CONSTRAINT ck_tenant_sms_limit CHECK (sms_limit IS NULL OR sms_limit > 0),
  CONSTRAINT ck_tenant_api_limit CHECK (api_limit IS NULL OR api_limit > 0),
  CONSTRAINT ck_tenant_keycloak_realm CHECK (char_length(keycloak_realm) BETWEEN 1 AND 100),
  CONSTRAINT uq_tenant_keycloak_realm UNIQUE (keycloak_realm),
  CONSTRAINT ck_tenant_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_tenant_r1 CHECK (state <> 'TRIAL' OR trial_ends_at IS NOT NULL),
  CONSTRAINT ck_tenant_r2 CHECK (isolation_mode <> 'ISOLATED' OR state = 'DELETED' OR db_connection_ref IS NOT NULL),
  CONSTRAINT ck_tenant_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.tenant IS 'Tenant registry: the paying customer and its database route.';
COMMENT ON COLUMN platform.tenant.tenant_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.tenant.tenant_name IS 'Name. Length 2–200.';
COMMENT ON COLUMN platform.tenant.tenant_type IS 'Tenant kind [PQ-08]. [PQ-08]';
COMMENT ON COLUMN platform.tenant.plan_id IS 'Plan.';
COMMENT ON COLUMN platform.tenant.state IS 'Lifecycle [CL-06]; transitions §4.26.1. [CL-06]';
COMMENT ON COLUMN platform.tenant.isolation_mode IS 'Database per tenant or shared with RLS [TE-01, PQ-70]. [PQ-70]';
COMMENT ON COLUMN platform.tenant.db_connection_ref IS 'Vault reference, never a credential. Required when ISOLATED; NULL after DELETED. Length 1–300.';
COMMENT ON COLUMN platform.tenant.subdomain IS 'Tenant subdomain [TE-14]. [TE-14]';
COMMENT ON COLUMN platform.tenant.custom_domain IS 'Higher plans only [R2-14]. Length 3–253.';
COMMENT ON COLUMN platform.tenant.time_zone IS 'IANA zone [TE-15]. [TE-15]';
COMMENT ON COLUMN platform.tenant.data_region IS 'India only [TE-06]. [TE-06]';
COMMENT ON COLUMN platform.tenant.trial_ends_at IS 'Trial end, 30 days [MK-05]; read-only after it until paid [R2-24]. [MK-05, R2-24]';
COMMENT ON COLUMN platform.tenant.user_limit IS 'Tenant override within plan; NULL = inherit. [WF-06]';
COMMENT ON COLUMN platform.tenant.storage_limit_gb IS 'Override.';
COMMENT ON COLUMN platform.tenant.sms_limit IS 'Override.';
COMMENT ON COLUMN platform.tenant.api_limit IS 'Override.';
COMMENT ON COLUMN platform.tenant.branding_logo_file_ref IS 'Logo (all plans) [R2-14]; tenant-side file id, no FK (PTD-18).';
COMMENT ON COLUMN platform.tenant.branding_colors IS 'Higher plans only; keys validated by the API.';
COMMENT ON COLUMN platform.tenant.keycloak_realm IS 'Realm (model OD-10). Length 1–100. (OD-10)';
COMMENT ON COLUMN platform.tenant.analytics_opt_out IS 'Excluded from anonymous platform analytics [PV-07, F-13]. [PV-07, F-13]';
COMMENT ON COLUMN platform.tenant.activated_at IS 'Set on ACTIVE.';
COMMENT ON COLUMN platform.tenant.suspended_at IS 'Set on SUSPENDED.';
COMMENT ON COLUMN platform.tenant.archived_at IS 'Set on ARCHIVED.';
COMMENT ON COLUMN platform.tenant.deleted_at IS 'Set on DELETED.';
COMMENT ON COLUMN platform.tenant.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.tenant.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.tenant.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.tenant.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.tenant.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.tenant_entitlement (
  tenant_entitlement_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  module_code text NOT NULL,
  feature_code text,
  enabled boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_tenant_entitlement PRIMARY KEY (tenant_entitlement_id),
  CONSTRAINT ck_tenant_entitlement_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_tenant_entitlement_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.tenant_entitlement IS 'Tenant override of plan entitlements.';
COMMENT ON COLUMN platform.tenant_entitlement.tenant_entitlement_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.tenant_entitlement.tenant_id IS 'Tenant.';
COMMENT ON COLUMN platform.tenant_entitlement.module_code IS 'Module.';
COMMENT ON COLUMN platform.tenant_entitlement.feature_code IS 'Feature; NULL = module.';
COMMENT ON COLUMN platform.tenant_entitlement.enabled IS 'Effective value = override else plan. Never selects a different app version [PQ-05].';
COMMENT ON COLUMN platform.tenant_entitlement.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.tenant_entitlement.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.tenant_entitlement.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.tenant_entitlement.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.tenant_entitlement.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.tenant_retention_policy (
  tenant_retention_policy_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  data_class_code text NOT NULL,
  retention_months integer,
  effective_from timestamptz NOT NULL DEFAULT now(),
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_tenant_retention_policy PRIMARY KEY (tenant_retention_policy_id),
  CONSTRAINT ck_tenant_retention_policy_retention_months CHECK (retention_months IS NULL OR retention_months > 0),
  CONSTRAINT ck_tenant_retention_policy_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
  CONSTRAINT ck_tenant_retention_policy_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_tenant_retention_policy_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.tenant_retention_policy IS 'Per-tenant retention durations per data class, set at creation [CL-08, WF-02, AU-02]; values OD-06.';
COMMENT ON COLUMN platform.tenant_retention_policy.tenant_retention_policy_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.tenant_retention_policy.tenant_id IS 'Tenant.';
COMMENT ON COLUMN platform.tenant_retention_policy.data_class_code IS 'Data class.';
COMMENT ON COLUMN platform.tenant_retention_policy.retention_months IS 'Duration; NULL until OD-06 decides. (OD-06)';
COMMENT ON COLUMN platform.tenant_retention_policy.effective_from IS 'Version start.';
COMMENT ON COLUMN platform.tenant_retention_policy.status IS 'A change creates a new ACTIVE row only after DPO approval (retention_change_request).';
COMMENT ON COLUMN platform.tenant_retention_policy.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.tenant_retention_policy.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.tenant_retention_policy.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.tenant_retention_policy.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.tenant_retention_policy.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.retention_change_request (
  retention_change_request_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  data_class_code text NOT NULL,
  current_months integer,
  requested_months integer NOT NULL,
  reason text NOT NULL,
  requested_by uuid NOT NULL,
  status text NOT NULL DEFAULT 'REQUESTED',
  decided_by uuid,
  decision_remark text,
  decided_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_retention_change_request PRIMARY KEY (retention_change_request_id),
  CONSTRAINT ck_retention_change_request_current_months CHECK (current_months IS NULL OR current_months > 0),
  CONSTRAINT ck_retention_change_request_requested_months CHECK (requested_months > 0),
  CONSTRAINT ck_retention_change_request_reason CHECK (char_length(reason) BETWEEN 1 AND 1000),
  CONSTRAINT ck_retention_change_request_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED')),
  CONSTRAINT ck_retention_change_request_decision_remark CHECK (char_length(decision_remark) BETWEEN 1 AND 1000),
  CONSTRAINT ck_retention_change_request_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_retention_change_request_r1 CHECK (status = 'REQUESTED' OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
  CONSTRAINT ck_retention_change_request_r2 CHECK (status <> 'REJECTED' OR decision_remark IS NOT NULL),
  CONSTRAINT ck_retention_change_request_r3 CHECK (decided_by IS NULL OR decided_by <> requested_by),
  CONSTRAINT ck_retention_change_request_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.retention_change_request IS 'Request to change a tenant retention duration; approved by the DPO (RETENTION_CHANGE_APPROVE). Fixes finding 25.';
COMMENT ON COLUMN platform.retention_change_request.retention_change_request_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.retention_change_request.tenant_id IS 'Tenant.';
COMMENT ON COLUMN platform.retention_change_request.data_class_code IS 'Class.';
COMMENT ON COLUMN platform.retention_change_request.current_months IS 'Value at request.';
COMMENT ON COLUMN platform.retention_change_request.requested_months IS 'Proposed value.';
COMMENT ON COLUMN platform.retention_change_request.reason IS 'Why. Length 1–1000.';
COMMENT ON COLUMN platform.retention_change_request.requested_by IS 'Product Owner (sets retention per tenant [CL-08]); proposed requester, OD-130. (OD-130)';
COMMENT ON COLUMN platform.retention_change_request.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN platform.retention_change_request.decided_by IS 'DPO.';
COMMENT ON COLUMN platform.retention_change_request.decision_remark IS 'Required on reject [AP-24]. Length 1–1000.';
COMMENT ON COLUMN platform.retention_change_request.decided_at IS 'Decision time.';
COMMENT ON COLUMN platform.retention_change_request.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.retention_change_request.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.retention_change_request.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.retention_change_request.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.retention_change_request.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.invoice (
  invoice_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  invoice_number text NOT NULL,
  period_start date NOT NULL,
  period_end date NOT NULL,
  subtotal_amount numeric(14,2) NOT NULL,
  discount_amount numeric(14,2) NOT NULL DEFAULT 0,
  discount_applied_by uuid,
  total_amount numeric(14,2) NOT NULL,
  currency_code char(3) NOT NULL DEFAULT 'INR',
  status text NOT NULL DEFAULT 'DRAFT',
  sent_at timestamptz,
  paid_at timestamptz,
  payment_mode text,
  payment_reference text,
  overdue_flagged_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_invoice PRIMARY KEY (invoice_id),
  CONSTRAINT ck_invoice_invoice_number CHECK (char_length(invoice_number) BETWEEN 1 AND 40),
  CONSTRAINT uq_invoice_invoice_number UNIQUE (invoice_number),
  CONSTRAINT ck_invoice_subtotal_amount CHECK (subtotal_amount >= 0),
  CONSTRAINT ck_invoice_discount_amount CHECK (discount_amount >= 0),
  CONSTRAINT ck_invoice_total_amount CHECK (total_amount >= 0),
  CONSTRAINT ck_invoice_currency_code CHECK (currency_code = 'INR'),
  CONSTRAINT ck_invoice_status CHECK (status IN ('DRAFT', 'APPROVED', 'SENT', 'PAID')),
  CONSTRAINT ck_invoice_payment_mode CHECK (payment_mode IN ('GATEWAY', 'OFFLINE')),
  CONSTRAINT ck_invoice_payment_reference CHECK (char_length(payment_reference) BETWEEN 1 AND 100),
  CONSTRAINT ck_invoice_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_invoice_r1 CHECK (period_end >= period_start),
  CONSTRAINT ck_invoice_r2 CHECK (discount_amount <= subtotal_amount),
  CONSTRAINT ck_invoice_r3 CHECK (total_amount = subtotal_amount - discount_amount),
  CONSTRAINT ck_invoice_r4 CHECK (discount_amount = 0 OR discount_applied_by IS NOT NULL),
  CONSTRAINT ck_invoice_r5 CHECK (status <> 'PAID' OR (paid_at IS NOT NULL AND payment_mode IS NOT NULL AND payment_reference IS NOT NULL)),
  CONSTRAINT ck_invoice_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.invoice IS 'Platform invoice to a tenant (no tax, refund or credit-note columns in v1).';
COMMENT ON COLUMN platform.invoice.invoice_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.invoice.tenant_id IS 'Tenant.';
COMMENT ON COLUMN platform.invoice.invoice_number IS 'Number. Length 1–40.';
COMMENT ON COLUMN platform.invoice.period_start IS 'Period start.';
COMMENT ON COLUMN platform.invoice.period_end IS 'Period end.';
COMMENT ON COLUMN platform.invoice.subtotal_amount IS 'Sum of lines. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN platform.invoice.discount_amount IS 'Only the Product Owner gives discounts [MD-34]. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN platform.invoice.discount_applied_by IS 'Required when discount > 0.';
COMMENT ON COLUMN platform.invoice.total_amount IS '= subtotal − discount. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN platform.invoice.currency_code IS 'ISO currency. INR only in v1; column kept so other countries can be added later (PR-01). [PTD-06, PR-01]';
COMMENT ON COLUMN platform.invoice.status IS 'PTD-20.';
COMMENT ON COLUMN platform.invoice.sent_at IS 'Sent.';
COMMENT ON COLUMN platform.invoice.paid_at IS 'Paid.';
COMMENT ON COLUMN platform.invoice.payment_mode IS 'Required when PAID.';
COMMENT ON COLUMN platform.invoice.payment_reference IS 'Required when PAID. Length 1–100.';
COMMENT ON COLUMN platform.invoice.overdue_flagged_at IS 'Billing Manager flags for suspension review (OVERDUE_FLAG).';
COMMENT ON COLUMN platform.invoice.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.invoice.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.invoice.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.invoice.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.invoice.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.invoice_line (
  invoice_line_id uuid NOT NULL,
  invoice_id uuid NOT NULL,
  line_type text NOT NULL,
  description text NOT NULL,
  quantity numeric(14,2) NOT NULL,
  unit_amount numeric(14,2) NOT NULL,
  line_amount numeric(14,2) NOT NULL,
  tenant_usage_monthly_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_invoice_line PRIMARY KEY (invoice_line_id),
  CONSTRAINT ck_invoice_line_line_type CHECK (line_type IN ('PLAN', 'SMS', 'STORAGE', 'SETUP', 'OTHER')),
  CONSTRAINT ck_invoice_line_description CHECK (char_length(description) BETWEEN 1 AND 300),
  CONSTRAINT ck_invoice_line_quantity CHECK (quantity >= 0),
  CONSTRAINT ck_invoice_line_unit_amount CHECK (unit_amount >= 0),
  CONSTRAINT ck_invoice_line_line_amount CHECK (line_amount >= 0),
  CONSTRAINT ck_invoice_line_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_invoice_line_r1 CHECK (line_amount = round(quantity * unit_amount, 2)),
  CONSTRAINT ck_invoice_line_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.invoice_line IS 'Invoice line (plan, SMS per message, storage) [MK-07, MK-10].';
COMMENT ON COLUMN platform.invoice_line.invoice_line_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.invoice_line.invoice_id IS 'Invoice; lines change only while the invoice is DRAFT (trigger).';
COMMENT ON COLUMN platform.invoice_line.line_type IS 'Line kind.';
COMMENT ON COLUMN platform.invoice_line.description IS 'Text. Length 1–300.';
COMMENT ON COLUMN platform.invoice_line.quantity IS 'Quantity (e.g. SMS count from tenant_usage_monthly).';
COMMENT ON COLUMN platform.invoice_line.unit_amount IS 'Unit price. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN platform.invoice_line.line_amount IS '= round(quantity × unit, 2). Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN platform.invoice_line.tenant_usage_monthly_id IS 'Usage row billed (SMS / storage lines).';
COMMENT ON COLUMN platform.invoice_line.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.invoice_line.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.invoice_line.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.invoice_line.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.invoice_line.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.tenant_usage_monthly (
  tenant_usage_monthly_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  usage_month date NOT NULL,
  metric_code text NOT NULL,
  quantity numeric(14,2) NOT NULL,
  computed_at timestamptz NOT NULL DEFAULT now(),
  is_final boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_tenant_usage_monthly PRIMARY KEY (tenant_usage_monthly_id),
  CONSTRAINT ck_tenant_usage_monthly_usage_month CHECK (usage_month = date_trunc('month', usage_month)::date),
  CONSTRAINT ck_tenant_usage_monthly_metric_code CHECK (metric_code IN ('SMS_SENT', 'STORAGE_GB_PEAK', 'ACTIVE_USERS_PEAK', 'API_CALLS')),
  CONSTRAINT ck_tenant_usage_monthly_quantity CHECK (quantity >= 0),
  CONSTRAINT ck_tenant_usage_monthly_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_tenant_usage_monthly_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.tenant_usage_monthly IS 'Per-tenant monthly usage used for billing (SMS charged per message; storage). Fixes findings 13 / 34.';
COMMENT ON COLUMN platform.tenant_usage_monthly.tenant_usage_monthly_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.tenant_usage_monthly.tenant_id IS 'Tenant.';
COMMENT ON COLUMN platform.tenant_usage_monthly.usage_month IS 'First day of month.';
COMMENT ON COLUMN platform.tenant_usage_monthly.metric_code IS 'Metric.';
COMMENT ON COLUMN platform.tenant_usage_monthly.quantity IS 'Value, aggregated from tenant delivery attempts / storage.';
COMMENT ON COLUMN platform.tenant_usage_monthly.computed_at IS 'Aggregation time.';
COMMENT ON COLUMN platform.tenant_usage_monthly.is_final IS 'Frozen once invoiced.';
COMMENT ON COLUMN platform.tenant_usage_monthly.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.tenant_usage_monthly.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.tenant_usage_monthly.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.tenant_usage_monthly.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.tenant_usage_monthly.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.usage_metric_daily (
  usage_metric_daily_id uuid NOT NULL,
  usage_date date NOT NULL,
  metric_code text NOT NULL,
  value bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_usage_metric_daily PRIMARY KEY (usage_metric_daily_id),
  CONSTRAINT ck_usage_metric_daily_metric_code CHECK (metric_code IN ('LOGINS', 'ATTENDANCE_MARKED', 'FEES_PAID_ONLINE')),
  CONSTRAINT ck_usage_metric_daily_value CHECK (value >= 0),
  CONSTRAINT ck_usage_metric_daily_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_usage_metric_daily_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.usage_metric_daily IS 'Anonymous combined platform usage counts (logins, attendance marked, fees paid online); opted-out tenants excluded [R2-13, PV-07].';
COMMENT ON COLUMN platform.usage_metric_daily.usage_metric_daily_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.usage_metric_daily.usage_date IS 'Day.';
COMMENT ON COLUMN platform.usage_metric_daily.metric_code IS 'Counter.';
COMMENT ON COLUMN platform.usage_metric_daily.value IS 'Count, no tenant id.';
COMMENT ON COLUMN platform.usage_metric_daily.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.usage_metric_daily.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.usage_metric_daily.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.usage_metric_daily.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.usage_metric_daily.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.support_ticket (
  support_ticket_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  ticket_number text NOT NULL,
  raised_by_user_id uuid NOT NULL,
  raised_by_name text NOT NULL,
  subject text NOT NULL,
  description text NOT NULL,
  priority text NOT NULL DEFAULT 'MEDIUM',
  category text,
  status text NOT NULL DEFAULT 'NEW',
  assigned_to uuid,
  escalated_at timestamptz,
  closed_at timestamptz,
  resolution_note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_support_ticket PRIMARY KEY (support_ticket_id),
  CONSTRAINT ck_support_ticket_ticket_number CHECK (char_length(ticket_number) BETWEEN 1 AND 30),
  CONSTRAINT uq_support_ticket_ticket_number UNIQUE (ticket_number),
  CONSTRAINT ck_support_ticket_raised_by_name CHECK (char_length(raised_by_name) BETWEEN 1 AND 150),
  CONSTRAINT ck_support_ticket_subject CHECK (char_length(subject) BETWEEN 1 AND 200),
  CONSTRAINT ck_support_ticket_description CHECK (char_length(description) BETWEEN 1 AND 10000),
  CONSTRAINT ck_support_ticket_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
  CONSTRAINT ck_support_ticket_category CHECK (char_length(category) BETWEEN 1 AND 60),
  CONSTRAINT ck_support_ticket_status CHECK (status IN ('NEW', 'TRIAGED', 'ASSIGNED', 'ESCALATED', 'RESOLVED', 'CLOSED')),
  CONSTRAINT ck_support_ticket_resolution_note CHECK (char_length(resolution_note) BETWEEN 1 AND 5000),
  CONSTRAINT ck_support_ticket_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_support_ticket_r1 CHECK (status NOT IN ('RESOLVED','CLOSED') OR resolution_note IS NOT NULL),
  CONSTRAINT ck_support_ticket_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.support_ticket IS 'Support ticket (raised by any user [WF-10]); no SLA fields in v1 [R2-27].';
COMMENT ON COLUMN platform.support_ticket.support_ticket_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.support_ticket.tenant_id IS 'Tenant.';
COMMENT ON COLUMN platform.support_ticket.ticket_number IS 'Number. Length 1–30.';
COMMENT ON COLUMN platform.support_ticket.raised_by_user_id IS 'Tenant-side user_account_id (no FK, PTD-18).';
COMMENT ON COLUMN platform.support_ticket.raised_by_name IS 'Snapshot. Length 1–150.';
COMMENT ON COLUMN platform.support_ticket.subject IS 'Subject. Length 1–200.';
COMMENT ON COLUMN platform.support_ticket.description IS 'Description. Length 1–10000.';
COMMENT ON COLUMN platform.support_ticket.priority IS 'Priority.';
COMMENT ON COLUMN platform.support_ticket.category IS 'Values OD-15. Length 1–60. (OD-15)';
COMMENT ON COLUMN platform.support_ticket.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN platform.support_ticket.assigned_to IS 'Agent.';
COMMENT ON COLUMN platform.support_ticket.escalated_at IS 'Escalation.';
COMMENT ON COLUMN platform.support_ticket.closed_at IS 'Closure.';
COMMENT ON COLUMN platform.support_ticket.resolution_note IS 'Required when RESOLVED or CLOSED. Length 1–5000.';
COMMENT ON COLUMN platform.support_ticket.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.support_ticket.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.support_ticket.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.support_ticket.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.support_ticket.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.support_access_grant (
  support_access_grant_id uuid NOT NULL,
  ticket_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  granted_by_user_id uuid NOT NULL,
  institution_id uuid,
  granted_modules text[],
  granted_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  withdrawn_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_support_access_grant PRIMARY KEY (support_access_grant_id),
  CONSTRAINT ck_support_access_grant_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_support_access_grant_r1 CHECK (expires_at <= granted_at + interval '24 hours'),
  CONSTRAINT ck_support_access_grant_r2 CHECK (expires_at > granted_at),
  CONSTRAINT ck_support_access_grant_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.support_access_grant IS 'Tenant-granted, read-only, ticket-bound access for 24 hours [WF-11, PQ-21].';
COMMENT ON COLUMN platform.support_access_grant.support_access_grant_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.support_access_grant.ticket_id IS 'Ticket (one grant serves one ticket).';
COMMENT ON COLUMN platform.support_access_grant.tenant_id IS 'Must equal the ticket tenant (trigger).';
COMMENT ON COLUMN platform.support_access_grant.granted_by_user_id IS 'Tenant Admin (SUPPORT_ACCESS_GRANT).';
COMMENT ON COLUMN platform.support_access_grant.institution_id IS 'Optional institution limit chosen by the Admin (proposed, OD-79). (OD-79)';
COMMENT ON COLUMN platform.support_access_grant.granted_modules IS 'Optional module limit chosen by the Admin; NULL = whole tenant read-only (proposed, OD-79). (OD-79)';
COMMENT ON COLUMN platform.support_access_grant.granted_at IS 'Grant time.';
COMMENT ON COLUMN platform.support_access_grant.expires_at IS '≤ granted_at + 24 h.';
COMMENT ON COLUMN platform.support_access_grant.withdrawn_at IS 'Tenant may withdraw at any time.';
COMMENT ON COLUMN platform.support_access_grant.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.support_access_grant.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.support_access_grant.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.support_access_grant.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.support_access_grant.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.release (
  release_id uuid NOT NULL,
  version_label text NOT NULL,
  release_notes text NOT NULL,
  is_hotfix boolean NOT NULL DEFAULT false,
  status text NOT NULL DEFAULT 'UPLOADED',
  deployed_at timestamptz,
  rolled_back_at timestamptz,
  schema_version text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_release PRIMARY KEY (release_id),
  CONSTRAINT ck_release_version_label CHECK (char_length(version_label) BETWEEN 1 AND 40),
  CONSTRAINT uq_release_version_label UNIQUE (version_label),
  CONSTRAINT ck_release_release_notes CHECK (char_length(release_notes) BETWEEN 1 AND 20000),
  CONSTRAINT ck_release_status CHECK (status IN ('UPLOADED', 'STAGING_TESTED', 'DEPLOYED', 'ROLLED_BACK')),
  CONSTRAINT ck_release_schema_version CHECK (char_length(schema_version) BETWEEN 1 AND 60),
  CONSTRAINT ck_release_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_release_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.release IS 'Release record; one version for all tenants, app-only rollback [R2-26].';
COMMENT ON COLUMN platform.release.release_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.release.version_label IS 'Version. Length 1–40.';
COMMENT ON COLUMN platform.release.release_notes IS 'Notes. Length 1–20000.';
COMMENT ON COLUMN platform.release.is_hotfix IS 'Hotfix.';
COMMENT ON COLUMN platform.release.status IS 'Lifecycle status; transitions in §4.26. [R2-15]';
COMMENT ON COLUMN platform.release.deployed_at IS 'Deploy.';
COMMENT ON COLUMN platform.release.rolled_back_at IS 'Rollback.';
COMMENT ON COLUMN platform.release.schema_version IS 'Highest migration needed. Length 1–60.';
COMMENT ON COLUMN platform.release.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.release.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.release.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.release.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.release.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.export_request (
  export_request_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  requested_by_user_id uuid NOT NULL,
  requested_by_kind text NOT NULL,
  status text NOT NULL DEFAULT 'REQUESTED',
  decided_by uuid,
  decision_remark text,
  file_ref uuid,
  download_expires_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_export_request PRIMARY KEY (export_request_id),
  CONSTRAINT ck_export_request_requested_by_kind CHECK (requested_by_kind IN ('TENANT_ADMIN', 'PRODUCT_OWNER')),
  CONSTRAINT ck_export_request_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'GENERATING', 'READY', 'EXPIRED', 'FAILED')),
  CONSTRAINT ck_export_request_decision_remark CHECK (char_length(decision_remark) BETWEEN 1 AND 1000),
  CONSTRAINT ck_export_request_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_export_request_r1 CHECK (status <> 'REJECTED' OR decision_remark IS NOT NULL),
  CONSTRAINT ck_export_request_r2 CHECK (status <> 'READY' OR (file_ref IS NOT NULL AND download_expires_at IS NOT NULL)),
  CONSTRAINT ck_export_request_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.export_request IS 'Full-tenant export, approved by the DPO [R2-25, CL-09].';
COMMENT ON COLUMN platform.export_request.export_request_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.export_request.tenant_id IS 'Tenant.';
COMMENT ON COLUMN platform.export_request.requested_by_user_id IS 'Tenant Admin user id or platform_user id.';
COMMENT ON COLUMN platform.export_request.requested_by_kind IS 'Requester kind.';
COMMENT ON COLUMN platform.export_request.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN platform.export_request.decided_by IS 'DPO.';
COMMENT ON COLUMN platform.export_request.decision_remark IS 'Required on reject. Length 1–1000.';
COMMENT ON COLUMN platform.export_request.file_ref IS 'ZIP plus data dictionary.';
COMMENT ON COLUMN platform.export_request.download_expires_at IS '24 h after READY [CL-09].';
COMMENT ON COLUMN platform.export_request.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.export_request.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.export_request.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.export_request.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.export_request.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.deletion_request (
  deletion_request_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  origin text NOT NULL,
  requested_by_user_id uuid,
  status text NOT NULL DEFAULT 'REQUESTED',
  po_confirmed_by uuid,
  po_confirmed_at timestamptz,
  export_request_id uuid,
  retention_ends_at date,
  dpo_decided_by uuid,
  dpo_decided_at timestamptz,
  dpo_remark text,
  deleted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_deletion_request PRIMARY KEY (deletion_request_id),
  CONSTRAINT ck_deletion_request_origin CHECK (origin IN ('TENANT_ADMIN', 'PRODUCT_OWNER', 'SYSTEM_RETENTION_END')),
  CONSTRAINT ck_deletion_request_status CHECK (status IN ('REQUESTED', 'PO_CONFIRMED', 'EXPORT_DONE', 'PENDING_DPO', 'APPROVED', 'REJECTED', 'DELETED')),
  CONSTRAINT ck_deletion_request_dpo_remark CHECK (char_length(dpo_remark) BETWEEN 1 AND 1000),
  CONSTRAINT ck_deletion_request_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_deletion_request_r1 CHECK (origin = 'SYSTEM_RETENTION_END' OR requested_by_user_id IS NOT NULL),
  CONSTRAINT ck_deletion_request_r2 CHECK (status IN ('REQUESTED','REJECTED') OR po_confirmed_by IS NOT NULL),
  CONSTRAINT ck_deletion_request_r3 CHECK (status NOT IN ('PENDING_DPO','APPROVED','DELETED') OR export_request_id IS NOT NULL),
  CONSTRAINT ck_deletion_request_r4 CHECK (dpo_decided_by IS NULL OR dpo_decided_by <> po_confirmed_by),
  CONSTRAINT ck_deletion_request_r5 CHECK (status <> 'REJECTED' OR dpo_remark IS NOT NULL),
  CONSTRAINT ck_deletion_request_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.deletion_request IS 'Tenant deletion chain: request → PO confirm → export → retention met → DPO approves [AP-23, CL-07].';
COMMENT ON COLUMN platform.deletion_request.deletion_request_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.deletion_request.tenant_id IS 'Tenant.';
COMMENT ON COLUMN platform.deletion_request.origin IS 'Who started it.';
COMMENT ON COLUMN platform.deletion_request.requested_by_user_id IS 'NULL for SYSTEM_RETENTION_END.';
COMMENT ON COLUMN platform.deletion_request.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN platform.deletion_request.po_confirmed_by IS 'Product Owner (TENANT_DELETION_CONFIRM).';
COMMENT ON COLUMN platform.deletion_request.po_confirmed_at IS 'Confirm time.';
COMMENT ON COLUMN platform.deletion_request.export_request_id IS 'Required before PENDING_DPO.';
COMMENT ON COLUMN platform.deletion_request.retention_ends_at IS 'Must be ≤ today before PENDING_DPO; duration OD-06. (OD-06)';
COMMENT ON COLUMN platform.deletion_request.dpo_decided_by IS 'DPO (TENANT_DELETION_APPROVE).';
COMMENT ON COLUMN platform.deletion_request.dpo_decided_at IS 'Decision.';
COMMENT ON COLUMN platform.deletion_request.dpo_remark IS 'Required on reject. Length 1–1000.';
COMMENT ON COLUMN platform.deletion_request.deleted_at IS 'Deletion time.';
COMMENT ON COLUMN platform.deletion_request.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.deletion_request.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.deletion_request.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.deletion_request.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.deletion_request.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.deletion_certificate (
  deletion_certificate_id uuid NOT NULL,
  deletion_request_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  tenant_name text NOT NULL,
  deleted_at timestamptz NOT NULL,
  approved_by uuid NOT NULL,
  summary jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_deletion_certificate PRIMARY KEY (deletion_certificate_id),
  CONSTRAINT uq_deletion_certificate_deletion_request_id UNIQUE (deletion_request_id),
  CONSTRAINT ck_deletion_certificate_tenant_name CHECK (char_length(tenant_name) BETWEEN 1 AND 200),
  CONSTRAINT ck_deletion_certificate_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_deletion_certificate_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.deletion_certificate IS 'Immutable proof of tenant deletion [CL-10].';
COMMENT ON COLUMN platform.deletion_certificate.deletion_certificate_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.deletion_certificate.deletion_request_id IS 'Request.';
COMMENT ON COLUMN platform.deletion_certificate.tenant_id IS 'Tenant (row kept after deletion).';
COMMENT ON COLUMN platform.deletion_certificate.tenant_name IS 'Snapshot. Length 1–200.';
COMMENT ON COLUMN platform.deletion_certificate.deleted_at IS 'When deleted.';
COMMENT ON COLUMN platform.deletion_certificate.approved_by IS 'DPO.';
COMMENT ON COLUMN platform.deletion_certificate.summary IS 'What was deleted (counts per class).';
COMMENT ON COLUMN platform.deletion_certificate.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.deletion_certificate.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.deletion_certificate.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.deletion_certificate.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.deletion_certificate.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.integration_type (
  integration_type_id uuid NOT NULL,
  integration_type_code text NOT NULL,
  description text NOT NULL,
  status text NOT NULL DEFAULT 'ALLOWED',
  allowed_providers text[] NOT NULL DEFAULT '{}',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_integration_type PRIMARY KEY (integration_type_id),
  CONSTRAINT uq_integration_type_integration_type_code UNIQUE (integration_type_code),
  CONSTRAINT ck_integration_type_description CHECK (char_length(description) BETWEEN 1 AND 300),
  CONSTRAINT ck_integration_type_status CHECK (status IN ('ALLOWED', 'DISALLOWED')),
  CONSTRAINT ck_integration_type_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_integration_type_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.integration_type IS 'Allowed integration types only; never tenant keys [MX-02].';
COMMENT ON COLUMN platform.integration_type.integration_type_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.integration_type.integration_type_code IS 'Key, e.g. SMS_DLT.';
COMMENT ON COLUMN platform.integration_type.description IS 'Text. Length 1–300.';
COMMENT ON COLUMN platform.integration_type.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN platform.integration_type.allowed_providers IS 'Providers allowed.';
COMMENT ON COLUMN platform.integration_type.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.integration_type.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.integration_type.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.integration_type.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.integration_type.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.tenant_backup_status (
  tenant_backup_status_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  last_daily_backup_at timestamptz,
  last_backup_outcome text,
  earliest_restorable_at timestamptz,
  latest_restorable_at timestamptz,
  last_restore_test_at timestamptz,
  last_restore_test_outcome text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_tenant_backup_status PRIMARY KEY (tenant_backup_status_id),
  CONSTRAINT uq_tenant_backup_status_tenant_id UNIQUE (tenant_id),
  CONSTRAINT ck_tenant_backup_status_last_backup_outcome CHECK (last_backup_outcome IN ('SUCCESS', 'FAILED')),
  CONSTRAINT ck_tenant_backup_status_last_restore_test_outcome CHECK (last_restore_test_outcome IN ('PASSED', 'FAILED')),
  CONSTRAINT ck_tenant_backup_status_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_tenant_backup_status_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.tenant_backup_status IS 'Backup / PITR status per tenant; IT staff see status only [WF-33].';
COMMENT ON COLUMN platform.tenant_backup_status.tenant_backup_status_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.tenant_backup_status.tenant_id IS 'Tenant.';
COMMENT ON COLUMN platform.tenant_backup_status.last_daily_backup_at IS 'Last daily backup.';
COMMENT ON COLUMN platform.tenant_backup_status.last_backup_outcome IS 'Outcome.';
COMMENT ON COLUMN platform.tenant_backup_status.earliest_restorable_at IS 'PITR window start.';
COMMENT ON COLUMN platform.tenant_backup_status.latest_restorable_at IS 'PITR window end; RPO monitor = now() − this (≤ 15 min target) [NF-10]. [NF-10]';
COMMENT ON COLUMN platform.tenant_backup_status.last_restore_test_at IS 'Last restore test.';
COMMENT ON COLUMN platform.tenant_backup_status.last_restore_test_outcome IS 'Outcome.';
COMMENT ON COLUMN platform.tenant_backup_status.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.tenant_backup_status.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.tenant_backup_status.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.tenant_backup_status.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.tenant_backup_status.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.setting_value (
  setting_value_id uuid NOT NULL,
  setting_key text NOT NULL,
  value jsonb NOT NULL,
  version_no integer NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  change_reason text,
  restored_from_version_no integer,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_setting_value PRIMARY KEY (setting_value_id),
  CONSTRAINT ck_setting_value_version_no CHECK (version_no >= 1),
  CONSTRAINT ck_setting_value_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
  CONSTRAINT ck_setting_value_change_reason CHECK (char_length(change_reason) BETWEEN 1 AND 500),
  CONSTRAINT ck_setting_value_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_setting_value_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.setting_value IS 'Platform defaults and the Product Owner security policy (SECURITY_POLICY_SET).';
COMMENT ON COLUMN platform.setting_value.setting_value_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.setting_value.setting_key IS 'Setting.';
COMMENT ON COLUMN platform.setting_value.value IS 'Validated value; weakening guard enforced [PQ-74].';
COMMENT ON COLUMN platform.setting_value.version_no IS 'Version.';
COMMENT ON COLUMN platform.setting_value.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN platform.setting_value.change_reason IS 'Reason. Length 1–500.';
COMMENT ON COLUMN platform.setting_value.restored_from_version_no IS 'Rollback = new version copying an old one.';
COMMENT ON COLUMN platform.setting_value.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.setting_value.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.setting_value.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.setting_value.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.setting_value.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.audit_outbox (
  audit_outbox_id uuid NOT NULL,
  tenant_id uuid,
  occurred_at timestamptz NOT NULL DEFAULT now(),
  actor_kind text NOT NULL,
  actor_user_id uuid,
  operation text NOT NULL,
  action_code text,
  entity_type text NOT NULL,
  entity_id uuid NOT NULL,
  outcome text NOT NULL,
  old_values jsonb,
  new_values jsonb,
  correlation_id text NOT NULL,
  ship_status text NOT NULL DEFAULT 'PENDING',
  shipped_at timestamptz,
  store_object_key text,
  payload_hash char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_audit_outbox PRIMARY KEY (audit_outbox_id),
  CONSTRAINT ck_audit_outbox_actor_kind CHECK (actor_kind IN ('PLATFORM_USER', 'SYSTEM')),
  CONSTRAINT ck_audit_outbox_operation CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE', 'APPROVE', 'REJECT', 'LOGIN', 'EXPORT', 'DOWNLOAD', 'DENIED', 'VIEW_SENSITIVE', 'CONFIG_CHANGE')),
  CONSTRAINT ck_audit_outbox_entity_type CHECK (char_length(entity_type) BETWEEN 1 AND 60),
  CONSTRAINT ck_audit_outbox_outcome CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILED')),
  CONSTRAINT ck_audit_outbox_correlation_id CHECK (char_length(correlation_id) BETWEEN 1 AND 100),
  CONSTRAINT ck_audit_outbox_ship_status CHECK (ship_status IN ('PENDING', 'SHIPPED', 'FAILED')),
  CONSTRAINT ck_audit_outbox_store_object_key CHECK (char_length(store_object_key) BETWEEN 1 AND 500),
  CONSTRAINT ck_audit_outbox_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_audit_outbox_r1 CHECK (actor_kind = 'SYSTEM' OR actor_user_id IS NOT NULL),
  CONSTRAINT ck_audit_outbox_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.audit_outbox IS 'Platform-action audit events awaiting shipment to the write-once store [WF-03, AU-06].';
COMMENT ON COLUMN platform.audit_outbox.audit_outbox_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.audit_outbox.tenant_id IS 'Tenant concerned (lifecycle events).';
COMMENT ON COLUMN platform.audit_outbox.occurred_at IS 'Event time.';
COMMENT ON COLUMN platform.audit_outbox.actor_kind IS 'Actor kind.';
COMMENT ON COLUMN platform.audit_outbox.actor_user_id IS 'Actor; NULL only for SYSTEM.';
COMMENT ON COLUMN platform.audit_outbox.operation IS 'Operation.';
COMMENT ON COLUMN platform.audit_outbox.action_code IS 'Action.';
COMMENT ON COLUMN platform.audit_outbox.entity_type IS 'Entity. Length 1–60.';
COMMENT ON COLUMN platform.audit_outbox.entity_id IS 'Entity id.';
COMMENT ON COLUMN platform.audit_outbox.outcome IS 'Outcome.';
COMMENT ON COLUMN platform.audit_outbox.old_values IS 'Masked [AU-04].';
COMMENT ON COLUMN platform.audit_outbox.new_values IS 'Masked [AU-04].';
COMMENT ON COLUMN platform.audit_outbox.correlation_id IS 'Request id. Length 1–100.';
COMMENT ON COLUMN platform.audit_outbox.ship_status IS 'Shipper state.';
COMMENT ON COLUMN platform.audit_outbox.shipped_at IS 'Ack time.';
COMMENT ON COLUMN platform.audit_outbox.store_object_key IS 'Store key. Length 1–500.';
COMMENT ON COLUMN platform.audit_outbox.payload_hash IS 'SHA-256 of canonical event (method OD-09). (OD-09)';
COMMENT ON COLUMN platform.audit_outbox.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.audit_outbox.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.audit_outbox.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.audit_outbox.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.audit_outbox.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.tenant_provision_step (
  tenant_provision_step_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  step_code text NOT NULL,
  status text NOT NULL DEFAULT 'PENDING',
  attempts smallint NOT NULL DEFAULT 0,
  last_error_code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_tenant_provision_step PRIMARY KEY (tenant_provision_step_id),
  CONSTRAINT ck_tenant_provision_step_step_code CHECK (step_code IN ('P01_REGISTRY', 'P02_DATABASE', 'P03_MIGRATIONS', 'P04_ROLES_SEED', 'P05_ENTITLEMENTS', 'P06_IDENTITY_REALM', 'P07_FIRST_ADMIN', 'P08_TRIAL')),
  CONSTRAINT ck_tenant_provision_step_status CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED', 'COMPENSATED')),
  CONSTRAINT ck_tenant_provision_step_attempts CHECK (attempts >= 0),
  CONSTRAINT ck_tenant_provision_step_last_error_code CHECK (char_length(last_error_code) BETWEEN 1 AND 60),
  CONSTRAINT ck_tenant_provision_step_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_tenant_provision_step_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.tenant_provision_step IS 'Resumable provisioning steps with compensation (PTD-52).';
COMMENT ON COLUMN platform.tenant_provision_step.tenant_provision_step_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.tenant_provision_step.tenant_id IS 'Tenant.';
COMMENT ON COLUMN platform.tenant_provision_step.step_code IS 'Step.';
COMMENT ON COLUMN platform.tenant_provision_step.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN platform.tenant_provision_step.attempts IS 'Attempts.';
COMMENT ON COLUMN platform.tenant_provision_step.last_error_code IS 'Safe error code. Length 1–60.';
COMMENT ON COLUMN platform.tenant_provision_step.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.tenant_provision_step.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.tenant_provision_step.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.tenant_provision_step.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.tenant_provision_step.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.schema_release (
  schema_release_id uuid NOT NULL,
  release_id uuid NOT NULL,
  schema_version text NOT NULL,
  migration_files text[] NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_schema_release PRIMARY KEY (schema_release_id),
  CONSTRAINT ck_schema_release_schema_version CHECK (char_length(schema_version) BETWEEN 1 AND 60),
  CONSTRAINT ck_schema_release_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_schema_release_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.schema_release IS 'Schema version required by an application release (PTD-50).';
COMMENT ON COLUMN platform.schema_release.schema_release_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.schema_release.release_id IS 'Release.';
COMMENT ON COLUMN platform.schema_release.schema_version IS 'Version. Length 1–60.';
COMMENT ON COLUMN platform.schema_release.migration_files IS 'Ordered files with checksums.';
COMMENT ON COLUMN platform.schema_release.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.schema_release.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.schema_release.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.schema_release.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.schema_release.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.tenant_migration_state (
  tenant_migration_state_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  schema_version text NOT NULL,
  status text NOT NULL DEFAULT 'PENDING',
  attempts smallint NOT NULL DEFAULT 0,
  last_error_code text,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_tenant_migration_state PRIMARY KEY (tenant_migration_state_id),
  CONSTRAINT uq_tenant_migration_state_tenant_id UNIQUE (tenant_id),
  CONSTRAINT ck_tenant_migration_state_schema_version CHECK (char_length(schema_version) BETWEEN 1 AND 60),
  CONSTRAINT ck_tenant_migration_state_status CHECK (status IN ('PENDING', 'APPLYING', 'APPLIED', 'FAILED')),
  CONSTRAINT ck_tenant_migration_state_attempts CHECK (attempts >= 0),
  CONSTRAINT ck_tenant_migration_state_last_error_code CHECK (char_length(last_error_code) BETWEEN 1 AND 60),
  CONSTRAINT ck_tenant_migration_state_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_tenant_migration_state_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.tenant_migration_state IS 'Per-tenant migration progress; partial failures detected (PTD-50, PQ-72).';
COMMENT ON COLUMN platform.tenant_migration_state.tenant_migration_state_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.tenant_migration_state.tenant_id IS 'Tenant.';
COMMENT ON COLUMN platform.tenant_migration_state.schema_version IS 'Current version. Length 1–60.';
COMMENT ON COLUMN platform.tenant_migration_state.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN platform.tenant_migration_state.attempts IS 'Attempts.';
COMMENT ON COLUMN platform.tenant_migration_state.last_error_code IS 'Error. Length 1–60.';
COMMENT ON COLUMN platform.tenant_migration_state.started_at IS 'Start.';
COMMENT ON COLUMN platform.tenant_migration_state.finished_at IS 'End.';
COMMENT ON COLUMN platform.tenant_migration_state.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.tenant_migration_state.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.tenant_migration_state.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.tenant_migration_state.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.tenant_migration_state.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.migration_run (
  migration_run_id uuid NOT NULL,
  release_id uuid NOT NULL,
  tenant_id uuid,
  file_name text NOT NULL,
  checksum_sha256 char(64) NOT NULL,
  started_at timestamptz NOT NULL,
  finished_at timestamptz,
  outcome text NOT NULL,
  rows_touched bigint,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_migration_run PRIMARY KEY (migration_run_id),
  CONSTRAINT ck_migration_run_file_name CHECK (char_length(file_name) BETWEEN 1 AND 300),
  CONSTRAINT ck_migration_run_outcome CHECK (outcome IN ('SUCCEEDED', 'FAILED', 'SKIPPED')),
  CONSTRAINT ck_migration_run_rows_touched CHECK (rows_touched IS NULL OR rows_touched >= 0),
  CONSTRAINT ck_migration_run_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_migration_run_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.migration_run IS 'One row per migration file per tenant run (PTD-50).';
COMMENT ON COLUMN platform.migration_run.migration_run_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.migration_run.release_id IS 'Release.';
COMMENT ON COLUMN platform.migration_run.tenant_id IS 'Tenant (NULL for shared-database run).';
COMMENT ON COLUMN platform.migration_run.file_name IS 'Migration file. Length 1–300.';
COMMENT ON COLUMN platform.migration_run.checksum_sha256 IS 'Checksum.';
COMMENT ON COLUMN platform.migration_run.started_at IS 'Start.';
COMMENT ON COLUMN platform.migration_run.finished_at IS 'End.';
COMMENT ON COLUMN platform.migration_run.outcome IS 'Result.';
COMMENT ON COLUMN platform.migration_run.rows_touched IS 'Rows.';
COMMENT ON COLUMN platform.migration_run.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.migration_run.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.migration_run.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.migration_run.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.migration_run.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.tenant_relocation (
  tenant_relocation_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  from_mode text NOT NULL,
  to_mode text NOT NULL,
  step text NOT NULL,
  write_freeze_started_at timestamptz,
  verification jsonb,
  last_error_code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_tenant_relocation PRIMARY KEY (tenant_relocation_id),
  CONSTRAINT ck_tenant_relocation_from_mode CHECK (from_mode IN ('SHARED')),
  CONSTRAINT ck_tenant_relocation_to_mode CHECK (to_mode IN ('ISOLATED')),
  CONSTRAINT ck_tenant_relocation_step CHECK (step IN ('PLANNED', 'TARGET_CREATED', 'SCHEMA_MIGRATED', 'WRITE_FREEZE', 'COPIED', 'VERIFIED', 'CUTOVER', 'SOURCE_PURGED', 'ABORTED')),
  CONSTRAINT ck_tenant_relocation_last_error_code CHECK (char_length(last_error_code) BETWEEN 1 AND 60),
  CONSTRAINT ck_tenant_relocation_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_tenant_relocation_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.tenant_relocation IS 'Shared → isolated database move, step by step (proposed procedure; OD-11). Fixes finding 20 structurally.';
COMMENT ON COLUMN platform.tenant_relocation.tenant_relocation_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.tenant_relocation.tenant_id IS 'Tenant.';
COMMENT ON COLUMN platform.tenant_relocation.from_mode IS 'Source mode.';
COMMENT ON COLUMN platform.tenant_relocation.to_mode IS 'Target mode.';
COMMENT ON COLUMN platform.tenant_relocation.step IS 'Current step.';
COMMENT ON COLUMN platform.tenant_relocation.write_freeze_started_at IS 'Tenant read-only window start.';
COMMENT ON COLUMN platform.tenant_relocation.verification IS 'Per-table row counts and checksums, source vs target.';
COMMENT ON COLUMN platform.tenant_relocation.last_error_code IS 'Error. Length 1–60.';
COMMENT ON COLUMN platform.tenant_relocation.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.tenant_relocation.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.tenant_relocation.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.tenant_relocation.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.tenant_relocation.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.payment_route (
  payment_route_id uuid NOT NULL,
  provider text NOT NULL,
  gateway_order_id text NOT NULL,
  tenant_id uuid NOT NULL,
  payment_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_payment_route PRIMARY KEY (payment_route_id),
  CONSTRAINT ck_payment_route_provider CHECK (provider IN ('RAZORPAY', 'STRIPE', 'PAYU')),
  CONSTRAINT ck_payment_route_gateway_order_id CHECK (char_length(gateway_order_id) BETWEEN 1 AND 100),
  CONSTRAINT ck_payment_route_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_payment_route_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.payment_route IS 'Routes a gateway webhook (central endpoint) to the tenant and payment; written when the order is created. Missing from Doc 1 although Doc 2 depends on it (X-4, finding 39).';
COMMENT ON COLUMN platform.payment_route.payment_route_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.payment_route.provider IS 'Gateway (Razorpay default [FE-30]). [FE-30]';
COMMENT ON COLUMN platform.payment_route.gateway_order_id IS 'Provider order id. Length 1–100.';
COMMENT ON COLUMN platform.payment_route.tenant_id IS 'Tenant.';
COMMENT ON COLUMN platform.payment_route.payment_id IS 'Tenant-side payment id (no FK, PTD-18).';
COMMENT ON COLUMN platform.payment_route.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.payment_route.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.payment_route.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.payment_route.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.payment_route.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.payment_unmatched_event (
  payment_unmatched_event_id uuid NOT NULL,
  provider text NOT NULL,
  provider_event_id text NOT NULL,
  signature_verified boolean NOT NULL DEFAULT false,
  payload jsonb NOT NULL,
  received_at timestamptz NOT NULL DEFAULT now(),
  status text NOT NULL DEFAULT 'UNMATCHED',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_payment_unmatched_event PRIMARY KEY (payment_unmatched_event_id),
  CONSTRAINT ck_payment_unmatched_event_provider CHECK (provider IN ('RAZORPAY', 'STRIPE', 'PAYU')),
  CONSTRAINT ck_payment_unmatched_event_provider_event_id CHECK (char_length(provider_event_id) BETWEEN 1 AND 200),
  CONSTRAINT ck_payment_unmatched_event_status CHECK (status IN ('UNMATCHED', 'REPLAYED', 'DISCARDED')),
  CONSTRAINT ck_payment_unmatched_event_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_payment_unmatched_event_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.payment_unmatched_event IS 'Webhook that arrived before its route existed or could not be matched; replayed by reconciliation [PQ-49].';
COMMENT ON COLUMN platform.payment_unmatched_event.payment_unmatched_event_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.payment_unmatched_event.provider IS 'Gateway.';
COMMENT ON COLUMN platform.payment_unmatched_event.provider_event_id IS 'Provider event id. Length 1–200.';
COMMENT ON COLUMN platform.payment_unmatched_event.signature_verified IS 'Signature result.';
COMMENT ON COLUMN platform.payment_unmatched_event.payload IS 'Masked payload (no card data).';
COMMENT ON COLUMN platform.payment_unmatched_event.received_at IS 'Receipt.';
COMMENT ON COLUMN platform.payment_unmatched_event.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN platform.payment_unmatched_event.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.payment_unmatched_event.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.payment_unmatched_event.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.payment_unmatched_event.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.payment_unmatched_event.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE platform.security_incident (
  security_incident_id uuid NOT NULL,
  tenant_id uuid,
  title text NOT NULL,
  detected_at timestamptz NOT NULL,
  severity text,
  owner_user_id uuid NOT NULL,
  status text NOT NULL DEFAULT 'DETECTED',
  external_notification_required boolean NOT NULL DEFAULT false,
  external_notified_at timestamptz,
  evidence_ref text,
  closure_note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_security_incident PRIMARY KEY (security_incident_id),
  CONSTRAINT ck_security_incident_title CHECK (char_length(title) BETWEEN 1 AND 200),
  CONSTRAINT ck_security_incident_severity CHECK (char_length(severity) BETWEEN 1 AND 20),
  CONSTRAINT ck_security_incident_status CHECK (status IN ('DETECTED', 'CONTAINED', 'RECOVERED', 'CLOSED')),
  CONSTRAINT ck_security_incident_evidence_ref CHECK (char_length(evidence_ref) BETWEEN 1 AND 500),
  CONSTRAINT ck_security_incident_closure_note CHECK (char_length(closure_note) BETWEEN 1 AND 5000),
  CONSTRAINT ck_security_incident_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_security_incident_r1 CHECK (status <> 'CLOSED' OR closure_note IS NOT NULL),
  CONSTRAINT ck_security_incident_upd CHECK (updated_at >= created_at)
);
COMMENT ON TABLE platform.security_incident IS 'Incident record: detection, owner, containment, notification, closure [PQ-69]. Severity levels and deadlines OD-14 / open item 18.';
COMMENT ON COLUMN platform.security_incident.security_incident_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN platform.security_incident.tenant_id IS 'Affected tenant; NULL = platform-wide.';
COMMENT ON COLUMN platform.security_incident.title IS 'Title. Length 1–200.';
COMMENT ON COLUMN platform.security_incident.detected_at IS 'Detection.';
COMMENT ON COLUMN platform.security_incident.severity IS 'Values OD-14 (open item 18). Length 1–20. (OD-14)';
COMMENT ON COLUMN platform.security_incident.owner_user_id IS 'Owner.';
COMMENT ON COLUMN platform.security_incident.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN platform.security_incident.external_notification_required IS 'Legally driven; decided per incident.';
COMMENT ON COLUMN platform.security_incident.external_notified_at IS 'When notified (deadline OD-14).';
COMMENT ON COLUMN platform.security_incident.evidence_ref IS 'Evidence location (preserved). Length 1–500.';
COMMENT ON COLUMN platform.security_incident.closure_note IS 'Required when CLOSED. Length 1–5000.';
COMMENT ON COLUMN platform.security_incident.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN platform.security_incident.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN platform.security_incident.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN platform.security_incident.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN platform.security_incident.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.tenant_local (
  tenant_id uuid NOT NULL,
  tenant_type text NOT NULL,
  time_zone text NOT NULL DEFAULT 'Asia/Kolkata',
  provisioned_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pk_tenant_local PRIMARY KEY (tenant_id),
  CONSTRAINT ck_tenant_local_tenant_type CHECK (tenant_type IN ('UNIVERSITY_GROUP', 'STANDALONE_COLLEGE', 'STANDALONE_SCHOOL'))
);
COMMENT ON TABLE app.tenant_local IS 'Local anchor row for every tenant held in this database (one row in isolated mode, many in shared mode). Gives tenant_id a real FK target; Doc 1 pointed tenant_id at platform.tenant, which lives in another database (defect D1-01).';
COMMENT ON COLUMN app.tenant_local.tenant_id IS '= platform.tenant.tenant_id (copied at provisioning step P02). [PQ-70]';
COMMENT ON COLUMN app.tenant_local.tenant_type IS 'Copy of the registry value, used by mode-dependent rules (publisher, fee-policy owner).';
COMMENT ON COLUMN app.tenant_local.time_zone IS 'Tenant time zone for business dates and quiet hours [TE-15].';
COMMENT ON COLUMN app.tenant_local.provisioned_at IS 'Provisioning time.';
CREATE TABLE app.institution (
  tenant_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  institution_type text NOT NULL,
  institution_code text NOT NULL,
  institution_name text NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT',
  external_university_mode boolean NOT NULL DEFAULT false,
  under_university boolean NOT NULL DEFAULT false,
  address_line text,
  city text,
  state_name text,
  postal_code text,
  principal_invite_email text,
  activated_at timestamptz,
  blocked_at timestamptz,
  archived_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_institution PRIMARY KEY (tenant_id, institution_id),
  CONSTRAINT ck_institution_institution_type CHECK (institution_type IN ('COLLEGE', 'SCHOOL')),
  CONSTRAINT ck_institution_institution_code CHECK (institution_code ~ '^[A-Z0-9_-]{2,20}$'),
  CONSTRAINT ck_institution_institution_name CHECK (char_length(institution_name) BETWEEN 2 AND 200),
  CONSTRAINT ck_institution_status CHECK (status IN ('DRAFT', 'ACTIVE', 'BLOCKED', 'ARCHIVED')),
  CONSTRAINT ck_institution_address_line CHECK (char_length(address_line) BETWEEN 1 AND 300),
  CONSTRAINT ck_institution_city CHECK (char_length(city) BETWEEN 1 AND 100),
  CONSTRAINT ck_institution_state_name CHECK (char_length(state_name) BETWEEN 1 AND 100),
  CONSTRAINT ck_institution_postal_code CHECK (char_length(postal_code) BETWEEN 3 AND 12),
  CONSTRAINT ck_institution_principal_invite_email CHECK (char_length(principal_invite_email) BETWEEN 3 AND 254),
  CONSTRAINT ck_institution_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_institution_r1 CHECK (institution_type = 'COLLEGE' OR NOT external_university_mode),
  CONSTRAINT ck_institution_r2 CHECK (status <> 'ACTIVE' OR activated_at IS NOT NULL),
  CONSTRAINT ck_institution_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_institution_tid UNIQUE (tenant_id, institution_id)
);
COMMENT ON TABLE app.institution IS 'College or school inside a tenant.';
COMMENT ON COLUMN app.institution.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.institution.institution_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.institution.institution_type IS 'Kind.';
COMMENT ON COLUMN app.institution.institution_code IS 'Code, unique per tenant.';
COMMENT ON COLUMN app.institution.institution_name IS 'Name, unique per tenant. Length 2–200.';
COMMENT ON COLUMN app.institution.status IS 'DRAFT → ACTIVE by Vice-Chancellor approval [WF-16]; activation refused until the Principal''s-leave approver is set (trigger, C7). Block needs VC approval [WF-15]. [WF-15, WF-16, C7]';
COMMENT ON COLUMN app.institution.external_university_mode IS 'College whose university is not on EDU 360: enters external marks; College Admin publishes after Principal approval [HI-10]. [HI-10]';
COMMENT ON COLUMN app.institution.under_university IS 'True when the institution belongs to a university tenant (enables the D-002 approver option Registrar / VC) [C6]. [C6]';
COMMENT ON COLUMN app.institution.address_line IS 'Address. Length 1–300.';
COMMENT ON COLUMN app.institution.city IS 'City. Length 1–100.';
COMMENT ON COLUMN app.institution.state_name IS 'State. Length 1–100.';
COMMENT ON COLUMN app.institution.postal_code IS 'PIN code. Length 3–12.';
COMMENT ON COLUMN app.institution.principal_invite_email IS 'Principal invitation; cleared once the account is active. Length 3–254.';
COMMENT ON COLUMN app.institution.activated_at IS 'Set on ACTIVE.';
COMMENT ON COLUMN app.institution.blocked_at IS 'Set on BLOCKED.';
COMMENT ON COLUMN app.institution.archived_at IS 'Set on ARCHIVED.';
COMMENT ON COLUMN app.institution.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.institution.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.institution.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.institution.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.institution.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.leave_approver_setting (
  tenant_id uuid NOT NULL,
  leave_approver_setting_id uuid NOT NULL,
  subject_role text NOT NULL,
  institution_id uuid,
  approver_kind text NOT NULL,
  approver_user_id uuid NOT NULL,
  effective_from timestamptz NOT NULL DEFAULT now(),
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_leave_approver_setting PRIMARY KEY (tenant_id, leave_approver_setting_id),
  CONSTRAINT ck_leave_approver_setting_subject_role CHECK (subject_role IN ('PRINCIPAL', 'VICE_CHANCELLOR', 'DATA_PROTECTION_CONTACT')),
  CONSTRAINT ck_leave_approver_setting_approver_kind CHECK (approver_kind IN ('MANAGEMENT', 'GOVERNING_BODY_APPROVER', 'REGISTRAR', 'VICE_CHANCELLOR')),
  CONSTRAINT ck_leave_approver_setting_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
  CONSTRAINT ck_leave_approver_setting_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_leave_approver_setting_r1 CHECK ((subject_role = 'PRINCIPAL') = (institution_id IS NOT NULL)),
  CONSTRAINT ck_leave_approver_setting_r2 CHECK (subject_role = 'PRINCIPAL' OR approver_kind IN ('MANAGEMENT','GOVERNING_BODY_APPROVER')),
  CONSTRAINT ck_leave_approver_setting_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_leave_approver_setting_tid UNIQUE (tenant_id, leave_approver_setting_id)
);
COMMENT ON TABLE app.leave_approver_setting IS 'Who approves the Principal''s, the Vice-Chancellor''s or a dedicated Data Protection Contact''s own leave (PRINCIPAL_LEAVE_APPROVER_SET, VC_LEAVE_APPROVER_SET). Versioned; required before go-live [D-002, C6, C7].';
COMMENT ON COLUMN app.leave_approver_setting.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.leave_approver_setting.leave_approver_setting_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.leave_approver_setting.subject_role IS 'Whose leave.';
COMMENT ON COLUMN app.leave_approver_setting.institution_id IS 'Institution for PRINCIPAL; NULL for VICE_CHANCELLOR and DATA_PROTECTION_CONTACT (tenant level). [§6.1]';
COMMENT ON COLUMN app.leave_approver_setting.approver_kind IS 'Management where it exists, otherwise the Governing Body Approver; REGISTRAR / VICE_CHANCELLOR only for a Principal of an institution under a university [C6]. [C6]';
COMMENT ON COLUMN app.leave_approver_setting.approver_user_id IS 'The named approver; never the subject (trigger).';
COMMENT ON COLUMN app.leave_approver_setting.effective_from IS 'Version start.';
COMMENT ON COLUMN app.leave_approver_setting.status IS 'One ACTIVE row per subject; a change supersedes and notifies Management / GBA [answer 6].';
COMMENT ON COLUMN app.leave_approver_setting.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.leave_approver_setting.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.leave_approver_setting.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.leave_approver_setting.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.leave_approver_setting.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.academic_session (
  tenant_id uuid NOT NULL,
  academic_session_id uuid NOT NULL,
  institution_id uuid,
  session_label text NOT NULL,
  start_date date NOT NULL,
  end_date date NOT NULL,
  academic_year smallint NOT NULL,
  status text NOT NULL DEFAULT 'PLANNED',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_academic_session PRIMARY KEY (tenant_id, academic_session_id),
  CONSTRAINT ck_academic_session_session_label CHECK (char_length(session_label) BETWEEN 4 AND 20),
  CONSTRAINT ck_academic_session_academic_year CHECK (academic_year BETWEEN 2000 AND 2200),
  CONSTRAINT ck_academic_session_status CHECK (status IN ('PLANNED', 'ACTIVE', 'CLOSED')),
  CONSTRAINT ck_academic_session_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_academic_session_r1 CHECK (end_date > start_date),
  CONSTRAINT ck_academic_session_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_academic_session_tid UNIQUE (tenant_id, academic_session_id)
);
COMMENT ON TABLE app.academic_session IS 'Academic year; master set by the university, own session for a standalone institution [§7.12, PQ-11].';
COMMENT ON COLUMN app.academic_session.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.academic_session.academic_session_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.academic_session.institution_id IS 'NULL = tenant-wide master session; set = institution session. [§6.1]';
COMMENT ON COLUMN app.academic_session.session_label IS 'e.g. 2026-27. Length 4–20.';
COMMENT ON COLUMN app.academic_session.start_date IS 'Start; fee structures lock here [FE-02]. [FE-02]';
COMMENT ON COLUMN app.academic_session.end_date IS 'End.';
COMMENT ON COLUMN app.academic_session.academic_year IS 'Start year; partition key copied onto attendance rows (PTD-28).';
COMMENT ON COLUMN app.academic_session.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.academic_session.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.academic_session.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.academic_session.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.academic_session.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.academic_session.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.academic_calendar_event (
  tenant_id uuid NOT NULL,
  academic_calendar_event_id uuid NOT NULL,
  academic_session_id uuid NOT NULL,
  institution_id uuid,
  source_level text NOT NULL,
  event_type text NOT NULL,
  title text NOT NULL,
  start_date date NOT NULL,
  end_date date NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_academic_calendar_event PRIMARY KEY (tenant_id, academic_calendar_event_id),
  CONSTRAINT ck_academic_calendar_event_source_level CHECK (source_level IN ('UNIVERSITY', 'INSTITUTION')),
  CONSTRAINT ck_academic_calendar_event_event_type CHECK (event_type IN ('SEMESTER_DATES', 'UNIVERSITY_HOLIDAY', 'LOCAL_HOLIDAY', 'LOCAL_EVENT')),
  CONSTRAINT ck_academic_calendar_event_title CHECK (char_length(title) BETWEEN 1 AND 200),
  CONSTRAINT ck_academic_calendar_event_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_academic_calendar_event_r1 CHECK (end_date >= start_date),
  CONSTRAINT ck_academic_calendar_event_r2 CHECK (source_level <> 'UNIVERSITY' OR (institution_id IS NULL AND event_type IN ('SEMESTER_DATES','UNIVERSITY_HOLIDAY'))),
  CONSTRAINT ck_academic_calendar_event_r3 CHECK (source_level <> 'INSTITUTION' OR (institution_id IS NOT NULL AND event_type IN ('LOCAL_HOLIDAY','LOCAL_EVENT'))),
  CONSTRAINT ck_academic_calendar_event_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_academic_calendar_event_tid UNIQUE (tenant_id, academic_calendar_event_id)
);
COMMENT ON TABLE app.academic_calendar_event IS 'Semester dates, holidays and events [CL-01..03].';
COMMENT ON COLUMN app.academic_calendar_event.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.academic_calendar_event.academic_calendar_event_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.academic_calendar_event.academic_session_id IS 'Session.';
COMMENT ON COLUMN app.academic_calendar_event.institution_id IS 'NULL for university-level rows. [§6.1]';
COMMENT ON COLUMN app.academic_calendar_event.source_level IS 'Who set it.';
COMMENT ON COLUMN app.academic_calendar_event.event_type IS 'Kind.';
COMMENT ON COLUMN app.academic_calendar_event.title IS 'Title. Length 1–200.';
COMMENT ON COLUMN app.academic_calendar_event.start_date IS 'Start.';
COMMENT ON COLUMN app.academic_calendar_event.end_date IS 'End.';
COMMENT ON COLUMN app.academic_calendar_event.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.academic_calendar_event.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.academic_calendar_event.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.academic_calendar_event.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.academic_calendar_event.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.weekly_off_rule (
  tenant_id uuid NOT NULL,
  weekly_off_rule_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  day_of_week smallint NOT NULL,
  occurrence text NOT NULL DEFAULT 'ALL',
  effective_from date NOT NULL,
  effective_to date,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_weekly_off_rule PRIMARY KEY (tenant_id, weekly_off_rule_id),
  CONSTRAINT ck_weekly_off_rule_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
  CONSTRAINT ck_weekly_off_rule_occurrence CHECK (occurrence IN ('ALL', 'ODD', 'EVEN')),
  CONSTRAINT ck_weekly_off_rule_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_weekly_off_rule_r1 CHECK (effective_to IS NULL OR effective_to >= effective_from),
  CONSTRAINT ck_weekly_off_rule_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_weekly_off_rule_tid UNIQUE (tenant_id, weekly_off_rule_id),
  CONSTRAINT uq_weekly_off_rule_tiid UNIQUE (tenant_id, institution_id, weekly_off_rule_id)
);
COMMENT ON TABLE app.weekly_off_rule IS 'Weekly-off pattern, e.g. alternate Saturdays [CL-03].';
COMMENT ON COLUMN app.weekly_off_rule.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.weekly_off_rule.weekly_off_rule_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.weekly_off_rule.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.weekly_off_rule.day_of_week IS 'ISO day, Monday = 1.';
COMMENT ON COLUMN app.weekly_off_rule.occurrence IS 'Which weeks.';
COMMENT ON COLUMN app.weekly_off_rule.effective_from IS 'Start.';
COMMENT ON COLUMN app.weekly_off_rule.effective_to IS 'End; NULL while open.';
COMMENT ON COLUMN app.weekly_off_rule.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.weekly_off_rule.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.weekly_off_rule.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.weekly_off_rule.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.weekly_off_rule.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.department (
  tenant_id uuid NOT NULL,
  department_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  institution_type text NOT NULL DEFAULT 'COLLEGE',
  department_code text NOT NULL,
  department_name text NOT NULL,
  hod_staff_assignment_id uuid,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_department PRIMARY KEY (tenant_id, department_id),
  CONSTRAINT ck_department_institution_type CHECK (institution_type = 'COLLEGE'),
  CONSTRAINT ck_department_department_code CHECK (department_code ~ '^[A-Z0-9_-]{2,20}$'),
  CONSTRAINT ck_department_department_name CHECK (char_length(department_name) BETWEEN 2 AND 150),
  CONSTRAINT ck_department_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  CONSTRAINT ck_department_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_department_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_department_tid UNIQUE (tenant_id, department_id),
  CONSTRAINT uq_department_tiid UNIQUE (tenant_id, institution_id, department_id)
);
COMMENT ON TABLE app.department IS 'Department (colleges only; schools have none in v1 [HI-12]).';
COMMENT ON COLUMN app.department.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.department.department_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.department.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.department.institution_type IS 'Pins the parent to a college via composite FK (PTD-21).';
COMMENT ON COLUMN app.department.department_code IS 'Code.';
COMMENT ON COLUMN app.department.department_name IS 'Name. Length 2–150.';
COMMENT ON COLUMN app.department.hod_staff_assignment_id IS 'HOD; must be faculty of this department [WF-20] (service + trigger). [WF-20, HOD_ASSIGN]';
COMMENT ON COLUMN app.department.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.department.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.department.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.department.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.department.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.department.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.program (
  tenant_id uuid NOT NULL,
  program_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  department_id uuid NOT NULL,
  program_code text NOT NULL,
  program_name text NOT NULL,
  duration_years smallint NOT NULL,
  semesters_per_year smallint NOT NULL DEFAULT 2,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_program PRIMARY KEY (tenant_id, program_id),
  CONSTRAINT ck_program_program_code CHECK (program_code ~ '^[A-Z0-9_.-]{2,20}$'),
  CONSTRAINT ck_program_program_name CHECK (char_length(program_name) BETWEEN 2 AND 200),
  CONSTRAINT ck_program_duration_years CHECK (duration_years BETWEEN 1 AND 10),
  CONSTRAINT ck_program_semesters_per_year CHECK (semesters_per_year BETWEEN 1 AND 6),
  CONSTRAINT ck_program_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  CONSTRAINT ck_program_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_program_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_program_tid UNIQUE (tenant_id, program_id),
  CONSTRAINT uq_program_tiid UNIQUE (tenant_id, institution_id, program_id)
);
COMMENT ON TABLE app.program IS 'Degree program [HI-01].';
COMMENT ON COLUMN app.program.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.program.program_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.program.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.program.department_id IS 'Department.';
COMMENT ON COLUMN app.program.program_code IS 'Code, unique per institution.';
COMMENT ON COLUMN app.program.program_name IS 'e.g. B.Sc. Physics. Length 2–200.';
COMMENT ON COLUMN app.program.duration_years IS 'Years (bounds PTD-22).';
COMMENT ON COLUMN app.program.semesters_per_year IS 'Configurable [HI-01] (bounds PTD-22).';
COMMENT ON COLUMN app.program.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.program.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.program.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.program.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.program.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.program.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.program_rule_set (
  tenant_id uuid NOT NULL,
  program_rule_set_id uuid NOT NULL,
  institution_id uuid,
  program_id uuid,
  version_no integer NOT NULL,
  rules jsonb NOT NULL,
  effective_from date NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_program_rule_set PRIMARY KEY (tenant_id, program_rule_set_id),
  CONSTRAINT ck_program_rule_set_version_no CHECK (version_no >= 1),
  CONSTRAINT ck_program_rule_set_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
  CONSTRAINT ck_program_rule_set_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_program_rule_set_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_program_rule_set_tid UNIQUE (tenant_id, program_rule_set_id)
);
COMMENT ON TABLE app.program_rule_set IS 'Versioned program and curriculum rules set by the Registrar (PROGRAM_RULES_SET). The rule fields are not defined by the sources, so content is a validated JSON document whose schema is OD-69. Fixes findings 7 / 24 structurally.';
COMMENT ON COLUMN app.program_rule_set.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.program_rule_set.program_rule_set_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.program_rule_set.institution_id IS 'NULL = university-wide rule set. [§6.1]';
COMMENT ON COLUMN app.program_rule_set.program_id IS 'NULL = applies to all programs in scope.';
COMMENT ON COLUMN app.program_rule_set.version_no IS 'Version; historical results keep the version used [PQ-38, PQ-43].';
COMMENT ON COLUMN app.program_rule_set.rules IS 'Rule document; schema OD-69 (e.g. credit totals, elective limits). (OD-69)';
COMMENT ON COLUMN app.program_rule_set.effective_from IS 'Start.';
COMMENT ON COLUMN app.program_rule_set.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.program_rule_set.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.program_rule_set.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.program_rule_set.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.program_rule_set.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.program_rule_set.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.program_year (
  tenant_id uuid NOT NULL,
  program_year_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  program_id uuid NOT NULL,
  year_number smallint NOT NULL,
  label text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_program_year PRIMARY KEY (tenant_id, program_year_id),
  CONSTRAINT ck_program_year_year_number CHECK (year_number BETWEEN 1 AND 10),
  CONSTRAINT ck_program_year_label CHECK (char_length(label) BETWEEN 1 AND 30),
  CONSTRAINT ck_program_year_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_program_year_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_program_year_tid UNIQUE (tenant_id, program_year_id),
  CONSTRAINT uq_program_year_tiid UNIQUE (tenant_id, institution_id, program_year_id)
);
COMMENT ON TABLE app.program_year IS 'Year of a program.';
COMMENT ON COLUMN app.program_year.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.program_year.program_year_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.program_year.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.program_year.program_id IS 'Program.';
COMMENT ON COLUMN app.program_year.year_number IS '≤ program.duration_years (trigger).';
COMMENT ON COLUMN app.program_year.label IS 'e.g. Year 1. Length 1–30.';
COMMENT ON COLUMN app.program_year.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.program_year.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.program_year.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.program_year.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.program_year.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.semester (
  tenant_id uuid NOT NULL,
  semester_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  program_year_id uuid NOT NULL,
  semester_number smallint NOT NULL,
  label text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_semester PRIMARY KEY (tenant_id, semester_id),
  CONSTRAINT ck_semester_semester_number CHECK (semester_number BETWEEN 1 AND 60),
  CONSTRAINT ck_semester_label CHECK (char_length(label) BETWEEN 1 AND 30),
  CONSTRAINT ck_semester_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_semester_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_semester_tid UNIQUE (tenant_id, semester_id),
  CONSTRAINT uq_semester_tiid UNIQUE (tenant_id, institution_id, semester_id)
);
COMMENT ON TABLE app.semester IS 'Semester within a program year.';
COMMENT ON COLUMN app.semester.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.semester.semester_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.semester.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.semester.program_year_id IS 'Year.';
COMMENT ON COLUMN app.semester.semester_number IS 'Counted across the program; per-year count ≤ semesters_per_year (trigger).';
COMMENT ON COLUMN app.semester.label IS 'e.g. Semester 3. Length 1–30.';
COMMENT ON COLUMN app.semester.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.semester.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.semester.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.semester.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.semester.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.school_class (
  tenant_id uuid NOT NULL,
  class_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  institution_type text NOT NULL DEFAULT 'SCHOOL',
  class_name text NOT NULL,
  display_order smallint NOT NULL DEFAULT 0,
  student_login_enabled boolean NOT NULL DEFAULT false,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_school_class PRIMARY KEY (tenant_id, class_id),
  CONSTRAINT ck_school_class_institution_type CHECK (institution_type = 'SCHOOL'),
  CONSTRAINT ck_school_class_class_name CHECK (char_length(class_name) BETWEEN 1 AND 30),
  CONSTRAINT ck_school_class_display_order CHECK (display_order >= 0),
  CONSTRAINT ck_school_class_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  CONSTRAINT ck_school_class_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_school_class_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_school_class_tid UNIQUE (tenant_id, class_id),
  CONSTRAINT uq_school_class_tiid UNIQUE (tenant_id, institution_id, class_id)
);
COMMENT ON TABLE app.school_class IS 'Class of a school (schools only).';
COMMENT ON COLUMN app.school_class.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.school_class.class_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.school_class.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.school_class.institution_type IS 'Pins parent to a school (PTD-21).';
COMMENT ON COLUMN app.school_class.class_name IS 'e.g. 9. Length 1–30.';
COMMENT ON COLUMN app.school_class.display_order IS 'Order.';
COMMENT ON COLUMN app.school_class.student_login_enabled IS 'View-only login for chosen classes (STUDENT_LOGIN_SWITCH) [PR-04]. [PR-04]';
COMMENT ON COLUMN app.school_class.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.school_class.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.school_class.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.school_class.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.school_class.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.school_class.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.section (
  tenant_id uuid NOT NULL,
  section_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  academic_session_id uuid NOT NULL,
  semester_id uuid,
  class_id uuid,
  section_name text NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_section PRIMARY KEY (tenant_id, section_id),
  CONSTRAINT ck_section_section_name CHECK (char_length(section_name) BETWEEN 1 AND 20),
  CONSTRAINT ck_section_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  CONSTRAINT ck_section_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_section_r1 CHECK (num_nonnulls(semester_id, class_id) = 1),
  CONSTRAINT ck_section_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_section_tid UNIQUE (tenant_id, section_id),
  CONSTRAINT uq_section_tiid UNIQUE (tenant_id, institution_id, section_id)
);
COMMENT ON TABLE app.section IS 'Section of a semester (college) or class (school), held per session so historical grouping is kept [PQ-12, PQ-61].';
COMMENT ON COLUMN app.section.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.section.section_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.section.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.section.academic_session_id IS 'Session.';
COMMENT ON COLUMN app.section.semester_id IS 'College only.';
COMMENT ON COLUMN app.section.class_id IS 'School only.';
COMMENT ON COLUMN app.section.section_name IS 'e.g. A. Length 1–20.';
COMMENT ON COLUMN app.section.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.section.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.section.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.section.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.section.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.section.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.subject (
  tenant_id uuid NOT NULL,
  subject_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  subject_code text NOT NULL,
  subject_name text NOT NULL,
  credits numeric(4,1),
  is_elective boolean NOT NULL DEFAULT false,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_subject PRIMARY KEY (tenant_id, subject_id),
  CONSTRAINT ck_subject_subject_code CHECK (subject_code ~ '^[A-Z0-9_.-]{2,20}$'),
  CONSTRAINT ck_subject_subject_name CHECK (char_length(subject_name) BETWEEN 2 AND 200),
  CONSTRAINT ck_subject_credits CHECK (credits IS NULL OR credits >= 0),
  CONSTRAINT ck_subject_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  CONSTRAINT ck_subject_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_subject_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_subject_tid UNIQUE (tenant_id, subject_id),
  CONSTRAINT uq_subject_tiid UNIQUE (tenant_id, institution_id, subject_id)
);
COMMENT ON TABLE app.subject IS 'Subject offered by an institution.';
COMMENT ON COLUMN app.subject.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.subject.subject_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.subject.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.subject.subject_code IS 'Code.';
COMMENT ON COLUMN app.subject.subject_name IS 'Name. Length 2–200.';
COMMENT ON COLUMN app.subject.credits IS 'Used by GPA / CGPA [§20].';
COMMENT ON COLUMN app.subject.is_elective IS 'Elective.';
COMMENT ON COLUMN app.subject.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.subject.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.subject.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.subject.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.subject.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.subject.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.syllabus (
  tenant_id uuid NOT NULL,
  syllabus_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  subject_id uuid NOT NULL,
  academic_session_id uuid,
  version_no integer NOT NULL,
  content_text text,
  file_id uuid,
  status text NOT NULL DEFAULT 'DRAFT',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_syllabus PRIMARY KEY (tenant_id, syllabus_id),
  CONSTRAINT ck_syllabus_version_no CHECK (version_no >= 1),
  CONSTRAINT ck_syllabus_content_text CHECK (char_length(content_text) BETWEEN 1 AND 50000),
  CONSTRAINT ck_syllabus_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
  CONSTRAINT ck_syllabus_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_syllabus_r1 CHECK (content_text IS NOT NULL OR file_id IS NOT NULL),
  CONSTRAINT ck_syllabus_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_syllabus_tid UNIQUE (tenant_id, syllabus_id),
  CONSTRAINT uq_syllabus_tiid UNIQUE (tenant_id, institution_id, syllabus_id)
);
COMMENT ON TABLE app.syllabus IS 'Syllabus of a subject (SYLLABUS_MANAGE: HOD and Department Coordinator for own department; Vice-Principal (School) and School Admin; SYLLABUS_VIEW for students, parents, Controller, QA and Accreditation roles). Doc 1 has no syllabus entity although V3.2 F-7 added SYLLABUS_VIEW (defect D1-14). Field list is minimal and proposed (OD-131).';
COMMENT ON COLUMN app.syllabus.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.syllabus.syllabus_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.syllabus.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.syllabus.subject_id IS 'Subject.';
COMMENT ON COLUMN app.syllabus.academic_session_id IS 'Session it applies to; NULL = until replaced.';
COMMENT ON COLUMN app.syllabus.version_no IS 'Version; every change is logged [answer 7].';
COMMENT ON COLUMN app.syllabus.content_text IS 'Syllabus text. Length 1–50000. (OD-131)';
COMMENT ON COLUMN app.syllabus.file_id IS 'Optional document.';
COMMENT ON COLUMN app.syllabus.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.syllabus.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.syllabus.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.syllabus.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.syllabus.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.syllabus.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.course_offering (
  tenant_id uuid NOT NULL,
  course_offering_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  subject_id uuid NOT NULL,
  academic_session_id uuid NOT NULL,
  semester_id uuid,
  class_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_course_offering PRIMARY KEY (tenant_id, course_offering_id),
  CONSTRAINT ck_course_offering_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_course_offering_r1 CHECK (num_nonnulls(semester_id, class_id) = 1),
  CONSTRAINT ck_course_offering_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_course_offering_tid UNIQUE (tenant_id, course_offering_id),
  CONSTRAINT uq_course_offering_tiid UNIQUE (tenant_id, institution_id, course_offering_id)
);
COMMENT ON TABLE app.course_offering IS 'A subject offered in a session for a semester or class (§16.5 CourseOffering).';
COMMENT ON COLUMN app.course_offering.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.course_offering.course_offering_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.course_offering.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.course_offering.subject_id IS 'Subject.';
COMMENT ON COLUMN app.course_offering.academic_session_id IS 'Session.';
COMMENT ON COLUMN app.course_offering.semester_id IS 'College.';
COMMENT ON COLUMN app.course_offering.class_id IS 'School.';
COMMENT ON COLUMN app.course_offering.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.course_offering.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.course_offering.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.course_offering.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.course_offering.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.subject_group (
  tenant_id uuid NOT NULL,
  subject_group_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  course_offering_id uuid NOT NULL,
  group_name text NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_subject_group PRIMARY KEY (tenant_id, subject_group_id),
  CONSTRAINT ck_subject_group_group_name CHECK (char_length(group_name) BETWEEN 1 AND 100),
  CONSTRAINT ck_subject_group_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  CONSTRAINT ck_subject_group_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_subject_group_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_subject_group_tid UNIQUE (tenant_id, subject_group_id),
  CONSTRAINT uq_subject_group_tiid UNIQUE (tenant_id, institution_id, subject_group_id)
);
COMMENT ON TABLE app.subject_group IS 'Elective group mixing students from several sections [HI-05].';
COMMENT ON COLUMN app.subject_group.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.subject_group.subject_group_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.subject_group.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.subject_group.course_offering_id IS 'Offering.';
COMMENT ON COLUMN app.subject_group.group_name IS 'e.g. Elective A. Length 1–100.';
COMMENT ON COLUMN app.subject_group.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.subject_group.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.subject_group.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.subject_group.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.subject_group.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.subject_group.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.subject_group_member (
  tenant_id uuid NOT NULL,
  subject_group_member_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  subject_group_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_subject_group_member PRIMARY KEY (tenant_id, subject_group_member_id),
  CONSTRAINT ck_subject_group_member_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_subject_group_member_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_subject_group_member_tid UNIQUE (tenant_id, subject_group_member_id),
  CONSTRAINT uq_subject_group_member_tiid UNIQUE (tenant_id, institution_id, subject_group_member_id)
);
COMMENT ON TABLE app.subject_group_member IS 'Enrolment in an elective group.';
COMMENT ON COLUMN app.subject_group_member.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.subject_group_member.subject_group_member_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.subject_group_member.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.subject_group_member.subject_group_id IS 'Group.';
COMMENT ON COLUMN app.subject_group_member.enrollment_id IS 'Enrolment; same session as the group offering (trigger).';
COMMENT ON COLUMN app.subject_group_member.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.subject_group_member.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.subject_group_member.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.subject_group_member.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.subject_group_member.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.user_account (
  tenant_id uuid NOT NULL,
  user_account_id uuid NOT NULL,
  user_type text NOT NULL,
  display_name text NOT NULL,
  email text,
  mobile text,
  status text NOT NULL DEFAULT 'PENDING_ACTIVATION',
  credential_expires_at timestamptz,
  last_login_at timestamptz,
  deactivated_at timestamptz,
  deactivated_by uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_user_account PRIMARY KEY (tenant_id, user_account_id),
  CONSTRAINT ck_user_account_user_type CHECK (user_type IN ('STAFF', 'STUDENT', 'GUARDIAN', 'APPLICANT', 'GOVERNANCE', 'SYSTEM')),
  CONSTRAINT ck_user_account_display_name CHECK (char_length(display_name) BETWEEN 1 AND 150),
  CONSTRAINT ck_user_account_email CHECK (email IS NULL OR (email = lower(email) AND email ~ '^[^@\s]+@[^@\s]+$')),
  CONSTRAINT ck_user_account_mobile CHECK (mobile IS NULL OR mobile ~ '^\+[1-9][0-9]{7,14}$'),
  CONSTRAINT ck_user_account_status CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'DEACTIVATED')),
  CONSTRAINT ck_user_account_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_user_account_r1 CHECK (credential_expires_at IS NULL OR user_type = 'STUDENT'),
  CONSTRAINT ck_user_account_r2 CHECK ((deactivated_at IS NULL) = (deactivated_by IS NULL)),
  CONSTRAINT ck_user_account_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_user_account_tid UNIQUE (tenant_id, user_account_id)
);
COMMENT ON TABLE app.user_account IS 'Login identity only; passwords, codes, lockout and two-step login live in the identity provider [§16.1, NF-07].';
COMMENT ON COLUMN app.user_account.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.user_account.user_account_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.user_account.user_type IS 'STAFF; STUDENT; GUARDIAN; GOVERNANCE = Management or Governing Body Approver (not staff [C9]); APPLICANT reserved (Phase 2); SYSTEM = job user (PTD-23). [C6, C9]';
COMMENT ON COLUMN app.user_account.display_name IS 'Name shown. Length 1–150.';
COMMENT ON COLUMN app.user_account.email IS 'Lower-case; staff and governance log in with it. Length 3–254.';
COMMENT ON COLUMN app.user_account.mobile IS 'E.164; parents are matched by mobile [WF-21]; same number may sit on a staff account [HI-08].';
COMMENT ON COLUMN app.user_account.status IS 'Deactivation moves pending approvals to the new holder and warns the Admin [WF-26].';
COMMENT ON COLUMN app.user_account.credential_expires_at IS 'Printed first-login password expiry, 7 days, students only [R2-35]. [R2-35]';
COMMENT ON COLUMN app.user_account.last_login_at IS 'Last login.';
COMMENT ON COLUMN app.user_account.deactivated_at IS 'Set with deactivated_by.';
COMMENT ON COLUMN app.user_account.deactivated_by IS 'Actor.';
COMMENT ON COLUMN app.user_account.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.user_account.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.user_account.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.user_account.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.user_account.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.user_identity_link (
  tenant_id uuid NOT NULL,
  user_identity_link_id uuid NOT NULL,
  user_account_id uuid NOT NULL,
  identity_realm text NOT NULL,
  identity_subject text NOT NULL,
  username_kind text NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_user_identity_link PRIMARY KEY (tenant_id, user_identity_link_id),
  CONSTRAINT ck_user_identity_link_identity_realm CHECK (char_length(identity_realm) BETWEEN 1 AND 100),
  CONSTRAINT ck_user_identity_link_identity_subject CHECK (char_length(identity_subject) BETWEEN 1 AND 255),
  CONSTRAINT ck_user_identity_link_username_kind CHECK (username_kind IN ('EMAIL', 'STUDENT_CODE', 'MOBILE')),
  CONSTRAINT ck_user_identity_link_status CHECK (status IN ('ACTIVE', 'REVOKED')),
  CONSTRAINT ck_user_identity_link_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_user_identity_link_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_user_identity_link_tid UNIQUE (tenant_id, user_identity_link_id)
);
COMMENT ON TABLE app.user_identity_link IS 'Link between a user account and the identity-provider subject (Keycloak). Realm / username model is OD-10; this table makes the link explicit and unique. Fixes finding 19 structurally.';
COMMENT ON COLUMN app.user_identity_link.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.user_identity_link.user_identity_link_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.user_identity_link.user_account_id IS 'Account.';
COMMENT ON COLUMN app.user_identity_link.identity_realm IS 'Realm (realm per tenant per §16.12; OD-10). Length 1–100. (OD-10)';
COMMENT ON COLUMN app.user_identity_link.identity_subject IS 'Provider subject (sub claim). Length 1–255.';
COMMENT ON COLUMN app.user_identity_link.username_kind IS 'Staff log in with email, students with their Student ID, parents with mobile [§8.1]. [§8.1]';
COMMENT ON COLUMN app.user_identity_link.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.user_identity_link.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.user_identity_link.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.user_identity_link.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.user_identity_link.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.user_identity_link.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.user_device (
  tenant_id uuid NOT NULL,
  user_device_id uuid NOT NULL,
  user_account_id uuid NOT NULL,
  device_class text NOT NULL,
  device_label text,
  registered_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  remembered_until timestamptz,
  revoked_at timestamptz,
  revoked_reason text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_user_device PRIMARY KEY (tenant_id, user_device_id),
  CONSTRAINT ck_user_device_device_class CHECK (device_class IN ('PHONE', 'COMPUTER')),
  CONSTRAINT ck_user_device_device_label CHECK (char_length(device_label) BETWEEN 1 AND 100),
  CONSTRAINT ck_user_device_revoked_reason CHECK (revoked_reason IN ('NEW_LOGIN', 'USER', 'ADMIN')),
  CONSTRAINT ck_user_device_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_user_device_r1 CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL)),
  CONSTRAINT ck_user_device_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_user_device_tid UNIQUE (tenant_id, user_device_id)
);
COMMENT ON TABLE app.user_device IS 'Active devices: one phone and one computer per user; parents and students remembered 60 days [R2-11, WF-35].';
COMMENT ON COLUMN app.user_device.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.user_device.user_device_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.user_device.user_account_id IS 'User.';
COMMENT ON COLUMN app.user_device.device_class IS 'Class.';
COMMENT ON COLUMN app.user_device.device_label IS 'Label. Length 1–100.';
COMMENT ON COLUMN app.user_device.registered_at IS 'Registered.';
COMMENT ON COLUMN app.user_device.last_seen_at IS 'Last seen.';
COMMENT ON COLUMN app.user_device.remembered_until IS 'Only guardians and students; ≤ registered_at + 60 days (setting).';
COMMENT ON COLUMN app.user_device.revoked_at IS 'Revoked.';
COMMENT ON COLUMN app.user_device.revoked_reason IS 'Required with revoked_at.';
COMMENT ON COLUMN app.user_device.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.user_device.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.user_device.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.user_device.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.user_device.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.staff_profile (
  tenant_id uuid NOT NULL,
  staff_profile_id uuid NOT NULL,
  user_account_id uuid,
  staff_code text NOT NULL,
  full_name text NOT NULL,
  date_of_birth date,
  personal_email text,
  personal_mobile text,
  national_id_last4 char(4),
  photo_file_id uuid,
  employment_status text NOT NULL DEFAULT 'ACTIVE',
  joined_on date,
  left_on date,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_staff_profile PRIMARY KEY (tenant_id, staff_profile_id),
  CONSTRAINT ck_staff_profile_staff_code CHECK (char_length(staff_code) BETWEEN 1 AND 30),
  CONSTRAINT ck_staff_profile_full_name CHECK (char_length(full_name) BETWEEN 1 AND 150),
  CONSTRAINT ck_staff_profile_personal_email CHECK (char_length(personal_email) BETWEEN 3 AND 254),
  CONSTRAINT ck_staff_profile_personal_mobile CHECK (personal_mobile IS NULL OR personal_mobile ~ '^\+[1-9][0-9]{7,14}$'),
  CONSTRAINT ck_staff_profile_national_id_last4 CHECK (national_id_last4 IS NULL OR national_id_last4 ~ '^[0-9A-Za-z]{4}$'),
  CONSTRAINT ck_staff_profile_employment_status CHECK (employment_status IN ('ACTIVE', 'INACTIVE', 'LEFT')),
  CONSTRAINT ck_staff_profile_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_staff_profile_r1 CHECK (left_on IS NULL OR joined_on IS NULL OR left_on >= joined_on),
  CONSTRAINT ck_staff_profile_r2 CHECK (employment_status <> 'LEFT' OR left_on IS NOT NULL),
  CONSTRAINT ck_staff_profile_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_staff_profile_tid UNIQUE (tenant_id, staff_profile_id)
);
COMMENT ON TABLE app.staff_profile IS 'Person record of a staff member, separate from the login [PQ-28, HI-06].';
COMMENT ON COLUMN app.staff_profile.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.staff_profile.staff_profile_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.staff_profile.user_account_id IS 'One login per person; NULL until invited.';
COMMENT ON COLUMN app.staff_profile.staff_code IS 'Employee code, unique per tenant.';
COMMENT ON COLUMN app.staff_profile.full_name IS 'Name. Length 1–150.';
COMMENT ON COLUMN app.staff_profile.date_of_birth IS 'Optional; field list OD-16. (OD-16)';
COMMENT ON COLUMN app.staff_profile.personal_email IS 'Optional. Length 3–254.';
COMMENT ON COLUMN app.staff_profile.personal_mobile IS 'Optional.';
COMMENT ON COLUMN app.staff_profile.national_id_last4 IS 'Last 4 characters only [R2-34]. [R2-34]';
COMMENT ON COLUMN app.staff_profile.photo_file_id IS 'Photo.';
COMMENT ON COLUMN app.staff_profile.employment_status IS 'Employment status.';
COMMENT ON COLUMN app.staff_profile.joined_on IS 'Joining date.';
COMMENT ON COLUMN app.staff_profile.left_on IS 'Required when LEFT.';
COMMENT ON COLUMN app.staff_profile.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.staff_profile.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.staff_profile.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.staff_profile.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.staff_profile.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.staff_service_record (
  tenant_id uuid NOT NULL,
  staff_service_record_id uuid NOT NULL,
  staff_profile_id uuid NOT NULL,
  event_type text NOT NULL,
  effective_on date NOT NULL,
  details text NOT NULL,
  staff_assignment_id uuid,
  document_file_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_staff_service_record PRIMARY KEY (tenant_id, staff_service_record_id),
  CONSTRAINT ck_staff_service_record_event_type CHECK (event_type IN ('JOINED', 'DESIGNATION_CHANGE', 'TRANSFER', 'CONFIRMATION', 'LEAVE_OF_ABSENCE', 'LEFT', 'OTHER')),
  CONSTRAINT ck_staff_service_record_details CHECK (char_length(details) BETWEEN 1 AND 2000),
  CONSTRAINT ck_staff_service_record_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_staff_service_record_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_staff_service_record_tid UNIQUE (tenant_id, staff_service_record_id)
);
COMMENT ON TABLE app.staff_service_record IS 'Staff service-record events (STAFF_RECORD_MANAGE "service records" [§8.15]). Doc 1 modelled none (finding 12). Event list is minimal and proposed; the field list stays OD-16.';
COMMENT ON COLUMN app.staff_service_record.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.staff_service_record.staff_service_record_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.staff_service_record.staff_profile_id IS 'Person.';
COMMENT ON COLUMN app.staff_service_record.event_type IS 'Event (proposed list, OD-16). (OD-16)';
COMMENT ON COLUMN app.staff_service_record.effective_on IS 'Effective date.';
COMMENT ON COLUMN app.staff_service_record.details IS 'Details. Length 1–2000.';
COMMENT ON COLUMN app.staff_service_record.staff_assignment_id IS 'Assignment concerned.';
COMMENT ON COLUMN app.staff_service_record.document_file_id IS 'Order or letter.';
COMMENT ON COLUMN app.staff_service_record.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.staff_service_record.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.staff_service_record.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.staff_service_record.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.staff_service_record.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.staff_assignment (
  tenant_id uuid NOT NULL,
  staff_assignment_id uuid NOT NULL,
  staff_profile_id uuid NOT NULL,
  institution_id uuid,
  department_id uuid,
  designation text NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_staff_assignment PRIMARY KEY (tenant_id, staff_assignment_id),
  CONSTRAINT ck_staff_assignment_designation CHECK (char_length(designation) BETWEEN 1 AND 100),
  CONSTRAINT ck_staff_assignment_status CHECK (status IN ('ACTIVE', 'ENDED')),
  CONSTRAINT ck_staff_assignment_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_staff_assignment_r1 CHECK (effective_to IS NULL OR effective_to >= effective_from),
  CONSTRAINT ck_staff_assignment_r2 CHECK (department_id IS NULL OR institution_id IS NOT NULL),
  CONSTRAINT ck_staff_assignment_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_staff_assignment_tid UNIQUE (tenant_id, staff_assignment_id)
);
COMMENT ON TABLE app.staff_assignment IS 'One of several assignments of a person, each with its own scope [HI-06, PQ-13].';
COMMENT ON COLUMN app.staff_assignment.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.staff_assignment.staff_assignment_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.staff_assignment.staff_profile_id IS 'Person.';
COMMENT ON COLUMN app.staff_assignment.institution_id IS 'NULL = university-level staff. [§6.1]';
COMMENT ON COLUMN app.staff_assignment.department_id IS 'College only; requires institution.';
COMMENT ON COLUMN app.staff_assignment.designation IS 'e.g. Associate Professor. Length 1–100.';
COMMENT ON COLUMN app.staff_assignment.effective_from IS 'Start.';
COMMENT ON COLUMN app.staff_assignment.effective_to IS 'End.';
COMMENT ON COLUMN app.staff_assignment.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.staff_assignment.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.staff_assignment.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.staff_assignment.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.staff_assignment.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.staff_assignment.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.staff_document (
  tenant_id uuid NOT NULL,
  staff_document_id uuid NOT NULL,
  staff_profile_id uuid NOT NULL,
  document_type text NOT NULL,
  file_id uuid NOT NULL,
  verification_status text NOT NULL DEFAULT 'UPLOADED',
  verified_by uuid,
  verified_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_staff_document PRIMARY KEY (tenant_id, staff_document_id),
  CONSTRAINT ck_staff_document_document_type CHECK (document_type IN ('DEGREE', 'ID_PROOF', 'OTHER')),
  CONSTRAINT ck_staff_document_verification_status CHECK (verification_status IN ('UPLOADED', 'VERIFIED', 'REJECTED')),
  CONSTRAINT ck_staff_document_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_staff_document_r1 CHECK ((verification_status = 'UPLOADED') = (verified_by IS NULL AND verified_at IS NULL)),
  CONSTRAINT ck_staff_document_r2 CHECK (verified_by IS NULL OR verified_by <> created_by),
  CONSTRAINT ck_staff_document_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_staff_document_tid UNIQUE (tenant_id, staff_document_id)
);
COMMENT ON TABLE app.staff_document IS 'Degrees and ID uploaded by the HR Executive, verified by the HR Manager (School Admin in schools) [MD-25].';
COMMENT ON COLUMN app.staff_document.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.staff_document.staff_document_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.staff_document.staff_profile_id IS 'Person.';
COMMENT ON COLUMN app.staff_document.document_type IS 'Kind.';
COMMENT ON COLUMN app.staff_document.file_id IS 'File.';
COMMENT ON COLUMN app.staff_document.verification_status IS 'Verification.';
COMMENT ON COLUMN app.staff_document.verified_by IS 'Verifier; ≠ created_by (uploader).';
COMMENT ON COLUMN app.staff_document.verified_at IS 'Verification time.';
COMMENT ON COLUMN app.staff_document.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.staff_document.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.staff_document.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.staff_document.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.staff_document.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.role_definition (
  tenant_id uuid NOT NULL,
  role_definition_id uuid NOT NULL,
  role_code text NOT NULL,
  role_name text NOT NULL,
  role_origin text NOT NULL,
  role_level text NOT NULL,
  base_standard_role text,
  custom_scope text,
  is_sensitive_role boolean NOT NULL DEFAULT false,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_role_definition PRIMARY KEY (tenant_id, role_definition_id),
  CONSTRAINT ck_role_definition_role_code CHECK (role_code ~ '^[A-Z][A-Z0-9_]{2,59}$'),
  CONSTRAINT ck_role_definition_role_name CHECK (char_length(role_name) BETWEEN 2 AND 100),
  CONSTRAINT ck_role_definition_role_origin CHECK (role_origin IN ('STANDARD', 'CUSTOM')),
  CONSTRAINT ck_role_definition_role_level CHECK (role_level IN ('TENANT', 'UNIVERSITY', 'COLLEGE', 'SCHOOL', 'EXTERNAL')),
  CONSTRAINT ck_role_definition_custom_scope CHECK (custom_scope IN ('UNIVERSITY_OFFICE', 'INSTITUTION')),
  CONSTRAINT ck_role_definition_status CHECK (status IN ('ACTIVE', 'RETIRED')),
  CONSTRAINT ck_role_definition_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_role_definition_r1 CHECK ((role_origin = 'CUSTOM') = (custom_scope IS NOT NULL)),
  CONSTRAINT ck_role_definition_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_role_definition_tid UNIQUE (tenant_id, role_definition_id)
);
COMMENT ON TABLE app.role_definition IS 'Standard roles copied at provisioning (65 tenant-side) and custom roles [R2-02, C4].';
COMMENT ON COLUMN app.role_definition.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.role_definition.role_definition_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.role_definition.role_code IS 'Code, unique per tenant.';
COMMENT ON COLUMN app.role_definition.role_name IS 'Name. Length 2–100.';
COMMENT ON COLUMN app.role_definition.role_origin IS 'Origin.';
COMMENT ON COLUMN app.role_definition.role_level IS 'Level.';
COMMENT ON COLUMN app.role_definition.base_standard_role IS 'Template the custom role started from.';
COMMENT ON COLUMN app.role_definition.custom_scope IS 'For CUSTOM roles: University Admin builds university-office roles only [C4]. [C4]';
COMMENT ON COLUMN app.role_definition.is_sensitive_role IS 'True if the role holds any approval, finance, HR or exam action; IT roles may assign only non-sensitive roles [MX-04]. Maintained by trigger on role_action. [MX-04]';
COMMENT ON COLUMN app.role_definition.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.role_definition.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.role_definition.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.role_definition.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.role_definition.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.role_definition.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.role_action (
  tenant_id uuid NOT NULL,
  role_action_id uuid NOT NULL,
  role_definition_id uuid NOT NULL,
  action_code text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_role_action PRIMARY KEY (tenant_id, role_action_id),
  CONSTRAINT ck_role_action_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_role_action_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_role_action_tid UNIQUE (tenant_id, role_action_id)
);
COMMENT ON TABLE app.role_action IS 'Actions held by a role (standard rows from ref.standard_role_action; custom rows picked by an Admin).';
COMMENT ON COLUMN app.role_action.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.role_action.role_action_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.role_action.role_definition_id IS 'Role.';
COMMENT ON COLUMN app.role_action.action_code IS 'Action; approval actions only where the institution places the role in that chain (service).';
COMMENT ON COLUMN app.role_action.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.role_action.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.role_action.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.role_action.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.role_action.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.user_role_assignment (
  tenant_id uuid NOT NULL,
  user_role_assignment_id uuid NOT NULL,
  user_account_id uuid NOT NULL,
  role_definition_id uuid NOT NULL,
  scope_type text NOT NULL,
  institution_id uuid,
  department_id uuid,
  effective_from timestamptz NOT NULL DEFAULT now(),
  effective_to timestamptz,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_user_role_assignment PRIMARY KEY (tenant_id, user_role_assignment_id),
  CONSTRAINT ck_user_role_assignment_scope_type CHECK (scope_type IN ('TENANT', 'INSTITUTION', 'DEPARTMENT')),
  CONSTRAINT ck_user_role_assignment_status CHECK (status IN ('ACTIVE', 'ENDED')),
  CONSTRAINT ck_user_role_assignment_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_user_role_assignment_r1 CHECK ((scope_type = 'TENANT') = (institution_id IS NULL)),
  CONSTRAINT ck_user_role_assignment_r2 CHECK ((scope_type = 'DEPARTMENT') = (department_id IS NOT NULL)),
  CONSTRAINT ck_user_role_assignment_r3 CHECK (effective_to IS NULL OR effective_to > effective_from),
  CONSTRAINT ck_user_role_assignment_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_user_role_assignment_tid UNIQUE (tenant_id, user_role_assignment_id)
);
COMMENT ON TABLE app.user_role_assignment IS 'A (role + scope) held by a user; one active role per session [HI-14, HI-15, PQ-17].';
COMMENT ON COLUMN app.user_role_assignment.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.user_role_assignment.user_role_assignment_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.user_role_assignment.user_account_id IS 'User.';
COMMENT ON COLUMN app.user_role_assignment.role_definition_id IS 'Role.';
COMMENT ON COLUMN app.user_role_assignment.scope_type IS 'Scope.';
COMMENT ON COLUMN app.user_role_assignment.institution_id IS 'Required for INSTITUTION and DEPARTMENT. [§6.1]';
COMMENT ON COLUMN app.user_role_assignment.department_id IS 'Required for DEPARTMENT.';
COMMENT ON COLUMN app.user_role_assignment.effective_from IS 'Start.';
COMMENT ON COLUMN app.user_role_assignment.effective_to IS 'End.';
COMMENT ON COLUMN app.user_role_assignment.status IS 'Ending logs the user out; new rights within 1 minute [WF-27, PQ-20].';
COMMENT ON COLUMN app.user_role_assignment.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.user_role_assignment.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.user_role_assignment.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.user_role_assignment.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.user_role_assignment.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.delegation (
  tenant_id uuid NOT NULL,
  delegation_id uuid NOT NULL,
  delegator_user_id uuid NOT NULL,
  delegate_user_id uuid NOT NULL,
  delegation_basis text NOT NULL,
  process_code text,
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  cancelled_by uuid,
  cancelled_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_delegation PRIMARY KEY (tenant_id, delegation_id),
  CONSTRAINT ck_delegation_delegation_basis CHECK (delegation_basis IN ('SAME_OR_HIGHER', 'DESIGNATED_DEPUTY')),
  CONSTRAINT ck_delegation_status CHECK (status IN ('ACTIVE', 'ENDED', 'CANCELLED')),
  CONSTRAINT ck_delegation_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_delegation_r1 CHECK (delegator_user_id <> delegate_user_id),
  CONSTRAINT ck_delegation_r2 CHECK (ends_at > starts_at),
  CONSTRAINT ck_delegation_r3 CHECK ((cancelled_at IS NULL) = (cancelled_by IS NULL)),
  CONSTRAINT ck_delegation_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_delegation_tid UNIQUE (tenant_id, delegation_id)
);
COMMENT ON TABLE app.delegation IS 'Time-limited hand-over of approvals [AP-06..10, PQ-19, D-001, C12].';
COMMENT ON COLUMN app.delegation.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.delegation.delegation_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.delegation.delegator_user_id IS 'Approver handing over.';
COMMENT ON COLUMN app.delegation.delegate_user_id IS 'Receiver.';
COMMENT ON COLUMN app.delegation.delegation_basis IS 'Same-or-higher role (ranking OD-17), or the one designated deputy: Principal → Vice-Principal [D-001]. [D-001] (OD-17)';
COMMENT ON COLUMN app.delegation.process_code IS 'NULL = all of the delegator''s approvals; merit lists cannot be delegated [WF-37].';
COMMENT ON COLUMN app.delegation.starts_at IS 'Start.';
COMMENT ON COLUMN app.delegation.ends_at IS 'End.';
COMMENT ON COLUMN app.delegation.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.delegation.cancelled_by IS 'Delegator.';
COMMENT ON COLUMN app.delegation.cancelled_at IS 'Cancel time.';
COMMENT ON COLUMN app.delegation.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.delegation.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.delegation.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.delegation.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.delegation.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.approval_request (
  tenant_id uuid NOT NULL,
  approval_request_id uuid NOT NULL,
  institution_id uuid,
  process_code text NOT NULL,
  subject_type text NOT NULL,
  subject_id uuid NOT NULL,
  requested_by_user_id uuid NOT NULL,
  requester_active_role text NOT NULL,
  status text NOT NULL DEFAULT 'PENDING',
  current_step_no smallint NOT NULL DEFAULT 1,
  final_decided_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_approval_request PRIMARY KEY (tenant_id, approval_request_id),
  CONSTRAINT ck_approval_request_subject_type CHECK (char_length(subject_type) BETWEEN 2 AND 60),
  CONSTRAINT ck_approval_request_requester_active_role CHECK (char_length(requester_active_role) BETWEEN 2 AND 60),
  CONSTRAINT ck_approval_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'BLOCKED')),
  CONSTRAINT ck_approval_request_current_step_no CHECK (current_step_no >= 1),
  CONSTRAINT ck_approval_request_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_approval_request_r1 CHECK ((status IN ('PENDING','BLOCKED')) = (final_decided_at IS NULL)),
  CONSTRAINT ck_approval_request_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_approval_request_tid UNIQUE (tenant_id, approval_request_id)
);
COMMENT ON TABLE app.approval_request IS 'Generic approval engine instance (one per business request) for every chain in §7.1 (PTD-25).';
COMMENT ON COLUMN app.approval_request.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.approval_request.approval_request_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.approval_request.institution_id IS 'NULL for tenant / university level. [§6.1]';
COMMENT ON COLUMN app.approval_request.process_code IS 'Chain.';
COMMENT ON COLUMN app.approval_request.subject_type IS 'Entity type of the subject. Length 2–60.';
COMMENT ON COLUMN app.approval_request.subject_id IS 'Subject id; polymorphic (PTD-24); the subject row points back with its own FK.';
COMMENT ON COLUMN app.approval_request.requested_by_user_id IS 'Requester.';
COMMENT ON COLUMN app.approval_request.requester_active_role IS 'Active role at submission [HI-15]. Length 2–60.';
COMMENT ON COLUMN app.approval_request.status IS 'BLOCKED = no eligible approver at a step (e.g. Principal''s-leave approver missing): never skipped or auto-approved [D-002, C7]. [D-002, C7]';
COMMENT ON COLUMN app.approval_request.current_step_no IS 'Current step.';
COMMENT ON COLUMN app.approval_request.final_decided_at IS 'Set on APPROVED / REJECTED / CANCELLED.';
COMMENT ON COLUMN app.approval_request.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.approval_request.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.approval_request.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.approval_request.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.approval_request.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.approval_batch (
  tenant_id uuid NOT NULL,
  approval_batch_id uuid NOT NULL,
  acted_by_user_id uuid NOT NULL,
  acted_active_role text NOT NULL,
  batch_comment text,
  item_count integer NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_approval_batch PRIMARY KEY (tenant_id, approval_batch_id),
  CONSTRAINT ck_approval_batch_acted_active_role CHECK (char_length(acted_active_role) BETWEEN 2 AND 60),
  CONSTRAINT ck_approval_batch_batch_comment CHECK (char_length(batch_comment) BETWEEN 1 AND 1000),
  CONSTRAINT ck_approval_batch_item_count CHECK (item_count BETWEEN 1 AND 500),
  CONSTRAINT ck_approval_batch_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_approval_batch_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_approval_batch_tid UNIQUE (tenant_id, approval_batch_id)
);
COMMENT ON TABLE app.approval_batch IS 'Bulk approval: many steps approved together with one optional comment, each logged separately [AP-25, APPROVAL_BULK]. Doc 1 had no model (OD-72, finding 10).';
COMMENT ON COLUMN app.approval_batch.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.approval_batch.approval_batch_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.approval_batch.acted_by_user_id IS 'Approver.';
COMMENT ON COLUMN app.approval_batch.acted_active_role IS 'Active role. Length 2–60.';
COMMENT ON COLUMN app.approval_batch.batch_comment IS 'One optional comment applied to every item. Length 1–1000.';
COMMENT ON COLUMN app.approval_batch.item_count IS 'Number of steps (limit proposed, OD-76). (OD-76)';
COMMENT ON COLUMN app.approval_batch.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.approval_batch.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.approval_batch.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.approval_batch.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.approval_batch.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.approval_step (
  tenant_id uuid NOT NULL,
  approval_step_id uuid NOT NULL,
  approval_request_id uuid NOT NULL,
  step_no smallint NOT NULL,
  approver_role_code text NOT NULL,
  approver_user_id uuid,
  decision text NOT NULL DEFAULT 'PENDING',
  acted_by_user_id uuid,
  acting_via_delegation_id uuid,
  acted_active_role text,
  remark text,
  skip_reason text,
  approval_batch_id uuid,
  decided_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_approval_step PRIMARY KEY (tenant_id, approval_step_id),
  CONSTRAINT ck_approval_step_step_no CHECK (step_no >= 1),
  CONSTRAINT ck_approval_step_approver_role_code CHECK (char_length(approver_role_code) BETWEEN 2 AND 60),
  CONSTRAINT ck_approval_step_decision CHECK (decision IN ('PENDING', 'APPROVED', 'REJECTED', 'FORWARDED', 'SKIPPED')),
  CONSTRAINT ck_approval_step_acted_active_role CHECK (char_length(acted_active_role) BETWEEN 2 AND 60),
  CONSTRAINT ck_approval_step_remark CHECK (char_length(remark) BETWEEN 1 AND 1000),
  CONSTRAINT ck_approval_step_skip_reason CHECK (char_length(skip_reason) BETWEEN 1 AND 300),
  CONSTRAINT ck_approval_step_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_approval_step_r1 CHECK (decision <> 'REJECTED' OR remark IS NOT NULL),
  CONSTRAINT ck_approval_step_r2 CHECK (decision <> 'SKIPPED' OR skip_reason IS NOT NULL),
  CONSTRAINT ck_approval_step_r3 CHECK ((decision = 'PENDING') = (decided_at IS NULL)),
  CONSTRAINT ck_approval_step_r4 CHECK (decision NOT IN ('APPROVED','REJECTED','FORWARDED') OR (acted_by_user_id IS NOT NULL AND acted_active_role IS NOT NULL)),
  CONSTRAINT ck_approval_step_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_approval_step_tid UNIQUE (tenant_id, approval_step_id)
);
COMMENT ON TABLE app.approval_step IS 'One step of an approval request.';
COMMENT ON COLUMN app.approval_step.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.approval_step.approval_step_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.approval_step.approval_request_id IS 'Request.';
COMMENT ON COLUMN app.approval_step.step_no IS 'Order.';
COMMENT ON COLUMN app.approval_step.approver_role_code IS 'Role expected at this step. Length 2–60.';
COMMENT ON COLUMN app.approval_step.approver_user_id IS 'Named approver when fixed (e.g. leave approver setting).';
COMMENT ON COLUMN app.approval_step.decision IS 'Approve or reject only [R2-01]; FORWARDED only for a forward-only step (mentor, leave > 2 days [F-11]). [R2-01, F-11]';
COMMENT ON COLUMN app.approval_step.acted_by_user_id IS 'Decider; never the requester (trigger).';
COMMENT ON COLUMN app.approval_step.acting_via_delegation_id IS 'Set when a delegate acts.';
COMMENT ON COLUMN app.approval_step.acted_active_role IS 'Active role at decision [HI-15]. Length 2–60.';
COMMENT ON COLUMN app.approval_step.remark IS 'Required on REJECTED [AP-24]. Length 1–1000.';
COMMENT ON COLUMN app.approval_step.skip_reason IS 'Required on SKIPPED [AP-04, AP-05]. Length 1–300.';
COMMENT ON COLUMN app.approval_step.approval_batch_id IS 'Set when decided through bulk approval.';
COMMENT ON COLUMN app.approval_step.decided_at IS 'Required unless PENDING.';
COMMENT ON COLUMN app.approval_step.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.approval_step.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.approval_step.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.approval_step.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.approval_step.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.student_profile (
  tenant_id uuid NOT NULL,
  student_profile_id uuid NOT NULL,
  user_account_id uuid,
  institution_id uuid NOT NULL,
  student_code text NOT NULL,
  full_name text NOT NULL,
  date_of_birth date,
  admission_date date,
  national_id_last4 char(4),
  photo_file_id uuid,
  status text NOT NULL DEFAULT 'ACTIVE',
  status_reason text,
  status_changed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_student_profile PRIMARY KEY (tenant_id, student_profile_id),
  CONSTRAINT ck_student_profile_student_code CHECK (char_length(student_code) BETWEEN 1 AND 40),
  CONSTRAINT ck_student_profile_full_name CHECK (char_length(full_name) BETWEEN 1 AND 150),
  CONSTRAINT ck_student_profile_national_id_last4 CHECK (national_id_last4 IS NULL OR national_id_last4 ~ '^[0-9A-Za-z]{4}$'),
  CONSTRAINT ck_student_profile_status CHECK (status IN ('ACTIVE', 'ON_HOLD', 'WITHDRAWN', 'TRANSFERRED', 'DROPPED', 'DETAINED', 'GRADUATED', 'APPLICANT', 'ALUMNI')),
  CONSTRAINT ck_student_profile_status_reason CHECK (char_length(status_reason) BETWEEN 1 AND 500),
  CONSTRAINT ck_student_profile_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_student_profile_r1 CHECK (status = 'ACTIVE' OR status_reason IS NOT NULL),
  CONSTRAINT ck_student_profile_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_student_profile_tid UNIQUE (tenant_id, student_profile_id),
  CONSTRAINT uq_student_profile_tiid UNIQUE (tenant_id, institution_id, student_profile_id)
);
COMMENT ON TABLE app.student_profile IS 'Student record.';
COMMENT ON COLUMN app.student_profile.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.student_profile.student_profile_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.student_profile.user_account_id IS 'College students, and school students where login is on [PR-04].';
COMMENT ON COLUMN app.student_profile.institution_id IS 'Current home institution; transfers are controlled operations [PQ-14]. [§6.1]';
COMMENT ON COLUMN app.student_profile.student_code IS 'Auto-generated, configurable pattern [MD-29]; unique per tenant (scope OD-12); never reused. (OD-12)';
COMMENT ON COLUMN app.student_profile.full_name IS 'Name. Length 1–150.';
COMMENT ON COLUMN app.student_profile.date_of_birth IS 'Optional (field list OD-16).';
COMMENT ON COLUMN app.student_profile.admission_date IS 'Admission date.';
COMMENT ON COLUMN app.student_profile.national_id_last4 IS 'Last 4 only [R2-34].';
COMMENT ON COLUMN app.student_profile.photo_file_id IS 'For ID card export [OB-12].';
COMMENT ON COLUMN app.student_profile.status IS '[MD-30]; APPLICANT (Phase 2) and ALUMNI (Phase 3) reserved; "delete" sets WITHDRAWN [MX-09]. STUDENT_RECORD_EDIT cannot change it [answer 8]. [MD-30, MX-09, answer 8]';
COMMENT ON COLUMN app.student_profile.status_reason IS 'Required for any status other than ACTIVE. Length 1–500.';
COMMENT ON COLUMN app.student_profile.status_changed_at IS 'Last status change.';
COMMENT ON COLUMN app.student_profile.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.student_profile.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.student_profile.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.student_profile.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.student_profile.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.student_code_sequence (
  tenant_id uuid NOT NULL,
  student_code_sequence_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  pattern text NOT NULL,
  last_value bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_student_code_sequence PRIMARY KEY (tenant_id, student_code_sequence_id),
  CONSTRAINT ck_student_code_sequence_pattern CHECK (char_length(pattern) BETWEEN 1 AND 60),
  CONSTRAINT ck_student_code_sequence_last_value CHECK (last_value >= 0),
  CONSTRAINT ck_student_code_sequence_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_student_code_sequence_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_student_code_sequence_tid UNIQUE (tenant_id, student_code_sequence_id),
  CONSTRAINT uq_student_code_sequence_tiid UNIQUE (tenant_id, institution_id, student_code_sequence_id)
);
COMMENT ON TABLE app.student_code_sequence IS 'Per-institution student-code counter [MD-29].';
COMMENT ON COLUMN app.student_code_sequence.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.student_code_sequence.student_code_sequence_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.student_code_sequence.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.student_code_sequence.pattern IS 'Token pattern, e.g. {INST}{YY}-{SEQ:5} (tokens OD-12). Length 1–60. (OD-12)';
COMMENT ON COLUMN app.student_code_sequence.last_value IS 'Taken with UPDATE … RETURNING in the admission transaction.';
COMMENT ON COLUMN app.student_code_sequence.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.student_code_sequence.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.student_code_sequence.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.student_code_sequence.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.student_code_sequence.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.duplicate_review (
  tenant_id uuid NOT NULL,
  duplicate_review_id uuid NOT NULL,
  record_type text NOT NULL,
  record_a_id uuid NOT NULL,
  record_b_id uuid NOT NULL,
  match_basis text NOT NULL,
  status text NOT NULL DEFAULT 'OPEN',
  reviewed_by uuid,
  reviewed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_duplicate_review PRIMARY KEY (tenant_id, duplicate_review_id),
  CONSTRAINT ck_duplicate_review_record_type CHECK (record_type IN ('STUDENT', 'STAFF', 'GUARDIAN')),
  CONSTRAINT ck_duplicate_review_match_basis CHECK (char_length(match_basis) BETWEEN 1 AND 300),
  CONSTRAINT ck_duplicate_review_status CHECK (status IN ('OPEN', 'CONFIRMED_DISTINCT', 'RESOLVED')),
  CONSTRAINT ck_duplicate_review_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_duplicate_review_r1 CHECK (record_a_id < record_b_id),
  CONSTRAINT ck_duplicate_review_r2 CHECK ((status = 'OPEN') = (reviewed_by IS NULL)),
  CONSTRAINT ck_duplicate_review_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_duplicate_review_tid UNIQUE (tenant_id, duplicate_review_id)
);
COMMENT ON TABLE app.duplicate_review IS 'Likely duplicates flagged for review, never auto-merged [PQ-24].';
COMMENT ON COLUMN app.duplicate_review.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.duplicate_review.duplicate_review_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.duplicate_review.record_type IS 'Kind.';
COMMENT ON COLUMN app.duplicate_review.record_a_id IS 'First record (lower uuid).';
COMMENT ON COLUMN app.duplicate_review.record_b_id IS 'Second record.';
COMMENT ON COLUMN app.duplicate_review.match_basis IS 'Why flagged (matching rules OD-18). Length 1–300. (OD-18)';
COMMENT ON COLUMN app.duplicate_review.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.duplicate_review.reviewed_by IS 'Reviewer (action OD-91: proposed DATA_IMPORT / STUDENT_RECORD_EDIT holders). (OD-91)';
COMMENT ON COLUMN app.duplicate_review.reviewed_at IS 'Review time.';
COMMENT ON COLUMN app.duplicate_review.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.duplicate_review.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.duplicate_review.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.duplicate_review.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.duplicate_review.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.guardian_profile (
  tenant_id uuid NOT NULL,
  guardian_profile_id uuid NOT NULL,
  user_account_id uuid NOT NULL,
  full_name text NOT NULL,
  mobile text NOT NULL,
  mobile_verified_at timestamptz,
  email text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_guardian_profile PRIMARY KEY (tenant_id, guardian_profile_id),
  CONSTRAINT ck_guardian_profile_full_name CHECK (char_length(full_name) BETWEEN 1 AND 150),
  CONSTRAINT ck_guardian_profile_mobile CHECK (mobile ~ '^\+[1-9][0-9]{7,14}$'),
  CONSTRAINT ck_guardian_profile_email CHECK (char_length(email) BETWEEN 3 AND 254),
  CONSTRAINT ck_guardian_profile_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_guardian_profile_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_guardian_profile_tid UNIQUE (tenant_id, guardian_profile_id)
);
COMMENT ON TABLE app.guardian_profile IS 'Parent / guardian person (one login per tenant [HI-07]).';
COMMENT ON COLUMN app.guardian_profile.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.guardian_profile.guardian_profile_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.guardian_profile.user_account_id IS 'Parent login.';
COMMENT ON COLUMN app.guardian_profile.full_name IS 'Name. Length 1–150.';
COMMENT ON COLUMN app.guardian_profile.mobile IS 'Copied from the account; unique per tenant [WF-21].';
COMMENT ON COLUMN app.guardian_profile.mobile_verified_at IS 'New number verified by code; alert to old number [WF-30].';
COMMENT ON COLUMN app.guardian_profile.email IS 'Optional. Length 3–254.';
COMMENT ON COLUMN app.guardian_profile.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.guardian_profile.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.guardian_profile.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.guardian_profile.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.guardian_profile.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.student_guardian_link (
  tenant_id uuid NOT NULL,
  student_guardian_link_id uuid NOT NULL,
  student_profile_id uuid NOT NULL,
  guardian_profile_id uuid NOT NULL,
  relationship text NOT NULL,
  is_primary boolean NOT NULL DEFAULT false,
  status text NOT NULL DEFAULT 'PENDING_ACTIVATION',
  change_approval_request_id uuid,
  blocked_at timestamptz,
  block_reason text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_student_guardian_link PRIMARY KEY (tenant_id, student_guardian_link_id),
  CONSTRAINT ck_student_guardian_link_relationship CHECK (relationship IN ('FATHER', 'MOTHER', 'LEGAL_GUARDIAN', 'OTHER')),
  CONSTRAINT ck_student_guardian_link_status CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'BLOCKED')),
  CONSTRAINT ck_student_guardian_link_block_reason CHECK (char_length(block_reason) BETWEEN 1 AND 500),
  CONSTRAINT ck_student_guardian_link_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_student_guardian_link_r1 CHECK ((status = 'BLOCKED') = (blocked_at IS NOT NULL)),
  CONSTRAINT ck_student_guardian_link_r2 CHECK (status <> 'BLOCKED' OR (block_reason IS NOT NULL AND change_approval_request_id IS NOT NULL)),
  CONSTRAINT ck_student_guardian_link_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_student_guardian_link_tid UNIQUE (tenant_id, student_guardian_link_id)
);
COMMENT ON TABLE app.student_guardian_link IS 'Guardian of a student: up to 3, exactly one primary [WF-21..25].';
COMMENT ON COLUMN app.student_guardian_link.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.student_guardian_link.student_guardian_link_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.student_guardian_link.student_profile_id IS 'Student.';
COMMENT ON COLUMN app.student_guardian_link.guardian_profile_id IS 'Guardian.';
COMMENT ON COLUMN app.student_guardian_link.relationship IS 'Relationship.';
COMMENT ON COLUMN app.student_guardian_link.is_primary IS 'Primary pays fees and applies for leave [WF-24].';
COMMENT ON COLUMN app.student_guardian_link.status IS 'Activation by code to that mobile [WF-22].';
COMMENT ON COLUMN app.student_guardian_link.change_approval_request_id IS 'Change / block needs reason + Principal approval [WF-23, WF-25].';
COMMENT ON COLUMN app.student_guardian_link.blocked_at IS 'Old guardian loses access at once.';
COMMENT ON COLUMN app.student_guardian_link.block_reason IS 'Reason. Length 1–500.';
COMMENT ON COLUMN app.student_guardian_link.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.student_guardian_link.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.student_guardian_link.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.student_guardian_link.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.student_guardian_link.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.parent_consent (
  tenant_id uuid NOT NULL,
  parent_consent_id uuid NOT NULL,
  student_profile_id uuid NOT NULL,
  guardian_profile_id uuid NOT NULL,
  consent_type text NOT NULL,
  consent_date date NOT NULL,
  source text NOT NULL,
  paper_file_id uuid,
  status text NOT NULL DEFAULT 'GIVEN',
  withdrawn_on date,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_parent_consent PRIMARY KEY (tenant_id, parent_consent_id),
  CONSTRAINT ck_parent_consent_consent_type CHECK (char_length(consent_type) BETWEEN 2 AND 60),
  CONSTRAINT ck_parent_consent_source CHECK (source IN ('IN_APP', 'PAPER')),
  CONSTRAINT ck_parent_consent_status CHECK (status IN ('GIVEN', 'WITHDRAWN')),
  CONSTRAINT ck_parent_consent_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_parent_consent_r1 CHECK ((status = 'WITHDRAWN') = (withdrawn_on IS NOT NULL)),
  CONSTRAINT ck_parent_consent_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_parent_consent_tid UNIQUE (tenant_id, parent_consent_id)
);
COMMENT ON TABLE app.parent_consent IS 'Parent consent with its date: in the app at onboarding, or from paper by the Admin (CONSENT_RECORD) [PV-03, F-13]. New in V3.2; absent from Doc 1.';
COMMENT ON COLUMN app.parent_consent.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.parent_consent.parent_consent_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.parent_consent.student_profile_id IS 'Child.';
COMMENT ON COLUMN app.parent_consent.guardian_profile_id IS 'Consenting parent.';
COMMENT ON COLUMN app.parent_consent.consent_type IS 'What is consented to (list OD-132). Length 2–60. (OD-132)';
COMMENT ON COLUMN app.parent_consent.consent_date IS 'Date of consent.';
COMMENT ON COLUMN app.parent_consent.source IS 'Onboarding in app, or paper entered by the Admin.';
COMMENT ON COLUMN app.parent_consent.paper_file_id IS 'Scan of the paper form (optional).';
COMMENT ON COLUMN app.parent_consent.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.parent_consent.withdrawn_on IS 'Withdrawal date.';
COMMENT ON COLUMN app.parent_consent.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.parent_consent.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.parent_consent.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.parent_consent.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.parent_consent.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.student_enrollment (
  tenant_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  student_profile_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  academic_session_id uuid NOT NULL,
  section_id uuid NOT NULL,
  roll_number text,
  outcome text NOT NULL DEFAULT 'IN_PROGRESS',
  promoted_from_enrollment_id uuid,
  started_on date NOT NULL,
  ended_on date,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_student_enrollment PRIMARY KEY (tenant_id, enrollment_id),
  CONSTRAINT ck_student_enrollment_roll_number CHECK (char_length(roll_number) BETWEEN 1 AND 20),
  CONSTRAINT ck_student_enrollment_outcome CHECK (outcome IN ('IN_PROGRESS', 'PROMOTED', 'DETAINED', 'WITHDRAWN', 'TRANSFERRED', 'DROPPED', 'GRADUATED')),
  CONSTRAINT ck_student_enrollment_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_student_enrollment_r1 CHECK (ended_on IS NULL OR ended_on >= started_on),
  CONSTRAINT ck_student_enrollment_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_student_enrollment_tid UNIQUE (tenant_id, enrollment_id),
  CONSTRAINT uq_student_enrollment_tiid UNIQUE (tenant_id, institution_id, enrollment_id)
);
COMMENT ON TABLE app.student_enrollment IS 'A student''s placement in one session; keeps historical class grouping [PQ-61, HI-03] (confirmed OD-02).';
COMMENT ON COLUMN app.student_enrollment.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.student_enrollment.enrollment_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.student_enrollment.student_profile_id IS 'Student.';
COMMENT ON COLUMN app.student_enrollment.institution_id IS '= section institution (composite FK). [§6.1]';
COMMENT ON COLUMN app.student_enrollment.academic_session_id IS 'Session.';
COMMENT ON COLUMN app.student_enrollment.section_id IS 'Section (carries semester or class).';
COMMENT ON COLUMN app.student_enrollment.roll_number IS 'Unique per section. Length 1–20.';
COMMENT ON COLUMN app.student_enrollment.outcome IS 'Outcome.';
COMMENT ON COLUMN app.student_enrollment.promoted_from_enrollment_id IS 'Previous enrolment.';
COMMENT ON COLUMN app.student_enrollment.started_on IS 'Start.';
COMMENT ON COLUMN app.student_enrollment.ended_on IS 'End.';
COMMENT ON COLUMN app.student_enrollment.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.student_enrollment.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.student_enrollment.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.student_enrollment.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.student_enrollment.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.promotion_run (
  tenant_id uuid NOT NULL,
  promotion_run_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  from_session_id uuid NOT NULL,
  to_session_id uuid NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT',
  approval_request_id uuid,
  executed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_promotion_run PRIMARY KEY (tenant_id, promotion_run_id),
  CONSTRAINT ck_promotion_run_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'EXECUTED')),
  CONSTRAINT ck_promotion_run_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_promotion_run_r1 CHECK (from_session_id <> to_session_id),
  CONSTRAINT ck_promotion_run_r2 CHECK (status = 'DRAFT' OR approval_request_id IS NOT NULL),
  CONSTRAINT ck_promotion_run_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_promotion_run_tid UNIQUE (tenant_id, promotion_run_id),
  CONSTRAINT uq_promotion_run_tiid UNIQUE (tenant_id, institution_id, promotion_run_id)
);
COMMENT ON TABLE app.promotion_run IS 'Year-end promotion: Admin runs, Principal approves [HI-03].';
COMMENT ON COLUMN app.promotion_run.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.promotion_run.promotion_run_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.promotion_run.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.promotion_run.from_session_id IS 'From.';
COMMENT ON COLUMN app.promotion_run.to_session_id IS 'To.';
COMMENT ON COLUMN app.promotion_run.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.promotion_run.approval_request_id IS 'Required from PENDING_APPROVAL.';
COMMENT ON COLUMN app.promotion_run.executed_at IS 'Execution.';
COMMENT ON COLUMN app.promotion_run.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.promotion_run.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.promotion_run.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.promotion_run.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.promotion_run.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.promotion_run_item (
  tenant_id uuid NOT NULL,
  promotion_run_item_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  promotion_run_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  decision text NOT NULL,
  left_out_reason text,
  target_section_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_promotion_run_item PRIMARY KEY (tenant_id, promotion_run_item_id),
  CONSTRAINT ck_promotion_run_item_decision CHECK (decision IN ('PROMOTE', 'LEFT_OUT')),
  CONSTRAINT ck_promotion_run_item_left_out_reason CHECK (char_length(left_out_reason) BETWEEN 1 AND 500),
  CONSTRAINT ck_promotion_run_item_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_promotion_run_item_r1 CHECK (decision <> 'LEFT_OUT' OR left_out_reason IS NOT NULL),
  CONSTRAINT ck_promotion_run_item_r2 CHECK (decision <> 'PROMOTE' OR target_section_id IS NOT NULL),
  CONSTRAINT ck_promotion_run_item_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_promotion_run_item_tid UNIQUE (tenant_id, promotion_run_item_id),
  CONSTRAINT uq_promotion_run_item_tiid UNIQUE (tenant_id, institution_id, promotion_run_item_id)
);
COMMENT ON TABLE app.promotion_run_item IS 'One student in a promotion run.';
COMMENT ON COLUMN app.promotion_run_item.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.promotion_run_item.promotion_run_item_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.promotion_run_item.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.promotion_run_item.promotion_run_id IS 'Run.';
COMMENT ON COLUMN app.promotion_run_item.enrollment_id IS 'Enrolment.';
COMMENT ON COLUMN app.promotion_run_item.decision IS 'Decision.';
COMMENT ON COLUMN app.promotion_run_item.left_out_reason IS 'Required for LEFT_OUT. Length 1–500.';
COMMENT ON COLUMN app.promotion_run_item.target_section_id IS 'Required for PROMOTE.';
COMMENT ON COLUMN app.promotion_run_item.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.promotion_run_item.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.promotion_run_item.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.promotion_run_item.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.promotion_run_item.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.subject_allocation (
  tenant_id uuid NOT NULL,
  subject_allocation_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  course_offering_id uuid NOT NULL,
  section_id uuid,
  subject_group_id uuid,
  staff_assignment_id uuid NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_subject_allocation PRIMARY KEY (tenant_id, subject_allocation_id),
  CONSTRAINT ck_subject_allocation_status CHECK (status IN ('ACTIVE', 'ENDED')),
  CONSTRAINT ck_subject_allocation_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_subject_allocation_r1 CHECK (num_nonnulls(section_id, subject_group_id) = 1),
  CONSTRAINT ck_subject_allocation_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_subject_allocation_tid UNIQUE (tenant_id, subject_allocation_id),
  CONSTRAINT uq_subject_allocation_tiid UNIQUE (tenant_id, institution_id, subject_allocation_id)
);
COMMENT ON TABLE app.subject_allocation IS 'Gives a subject to a teacher for a section or elective group (SUBJECT_ALLOCATE); defines "assigned students" scope [AT-03].';
COMMENT ON COLUMN app.subject_allocation.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.subject_allocation.subject_allocation_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.subject_allocation.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.subject_allocation.course_offering_id IS 'Offering.';
COMMENT ON COLUMN app.subject_allocation.section_id IS 'Whole-section teaching.';
COMMENT ON COLUMN app.subject_allocation.subject_group_id IS 'Elective group teaching.';
COMMENT ON COLUMN app.subject_allocation.staff_assignment_id IS 'Teacher; same institution (composite FK).';
COMMENT ON COLUMN app.subject_allocation.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.subject_allocation.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.subject_allocation.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.subject_allocation.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.subject_allocation.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.subject_allocation.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.mentor_assignment (
  tenant_id uuid NOT NULL,
  mentor_assignment_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  section_id uuid NOT NULL,
  staff_assignment_id uuid NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_mentor_assignment PRIMARY KEY (tenant_id, mentor_assignment_id),
  CONSTRAINT ck_mentor_assignment_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_mentor_assignment_r1 CHECK (effective_to IS NULL OR effective_to >= effective_from),
  CONSTRAINT ck_mentor_assignment_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_mentor_assignment_tid UNIQUE (tenant_id, mentor_assignment_id),
  CONSTRAINT uq_mentor_assignment_tiid UNIQUE (tenant_id, institution_id, mentor_assignment_id)
);
COMMENT ON TABLE app.mentor_assignment IS 'Mentor / class coordinator of a college section (MENTOR_ASSIGN by HOD); final approver for student leave ≤ 2 days [AP-13].';
COMMENT ON COLUMN app.mentor_assignment.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.mentor_assignment.mentor_assignment_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.mentor_assignment.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.mentor_assignment.section_id IS 'Section.';
COMMENT ON COLUMN app.mentor_assignment.staff_assignment_id IS 'Faculty of the section department (trigger).';
COMMENT ON COLUMN app.mentor_assignment.effective_from IS 'Start.';
COMMENT ON COLUMN app.mentor_assignment.effective_to IS 'End.';
COMMENT ON COLUMN app.mentor_assignment.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.mentor_assignment.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.mentor_assignment.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.mentor_assignment.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.mentor_assignment.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.class_teacher_assignment (
  tenant_id uuid NOT NULL,
  class_teacher_assignment_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  section_id uuid NOT NULL,
  staff_assignment_id uuid NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_class_teacher_assignment PRIMARY KEY (tenant_id, class_teacher_assignment_id),
  CONSTRAINT ck_class_teacher_assignment_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_class_teacher_assignment_r1 CHECK (effective_to IS NULL OR effective_to >= effective_from),
  CONSTRAINT ck_class_teacher_assignment_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_class_teacher_assignment_tid UNIQUE (tenant_id, class_teacher_assignment_id),
  CONSTRAINT uq_class_teacher_assignment_tiid UNIQUE (tenant_id, institution_id, class_teacher_assignment_id)
);
COMMENT ON TABLE app.class_teacher_assignment IS 'Class Teacher of a school section (CLASS_TEACHER_ASSIGN). Unit: section (OD-24 asks class vs section).';
COMMENT ON COLUMN app.class_teacher_assignment.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.class_teacher_assignment.class_teacher_assignment_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.class_teacher_assignment.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.class_teacher_assignment.section_id IS 'Section (unit OD-24). (OD-24)';
COMMENT ON COLUMN app.class_teacher_assignment.staff_assignment_id IS 'Teacher.';
COMMENT ON COLUMN app.class_teacher_assignment.effective_from IS 'Start.';
COMMENT ON COLUMN app.class_teacher_assignment.effective_to IS 'End.';
COMMENT ON COLUMN app.class_teacher_assignment.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.class_teacher_assignment.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.class_teacher_assignment.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.class_teacher_assignment.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.class_teacher_assignment.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.timetable_slot (
  tenant_id uuid NOT NULL,
  timetable_slot_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  academic_session_id uuid NOT NULL,
  section_id uuid,
  subject_group_id uuid,
  subject_allocation_id uuid NOT NULL,
  day_of_week smallint NOT NULL,
  period_no smallint NOT NULL,
  start_time time NOT NULL,
  end_time time NOT NULL,
  room_label text,
  is_online boolean NOT NULL DEFAULT false,
  status text NOT NULL DEFAULT 'DRAFT',
  supersedes_slot_id uuid,
  valid_from date NOT NULL,
  valid_to date,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_timetable_slot PRIMARY KEY (tenant_id, timetable_slot_id),
  CONSTRAINT ck_timetable_slot_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
  CONSTRAINT ck_timetable_slot_period_no CHECK (period_no BETWEEN 1 AND 20),
  CONSTRAINT ck_timetable_slot_room_label CHECK (char_length(room_label) BETWEEN 1 AND 50),
  CONSTRAINT ck_timetable_slot_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
  CONSTRAINT ck_timetable_slot_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_timetable_slot_r1 CHECK (num_nonnulls(section_id, subject_group_id) = 1),
  CONSTRAINT ck_timetable_slot_r2 CHECK (end_time > start_time),
  CONSTRAINT ck_timetable_slot_r3 CHECK (valid_to IS NULL OR valid_to >= valid_from),
  CONSTRAINT ck_timetable_slot_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_timetable_slot_tid UNIQUE (tenant_id, timetable_slot_id),
  CONSTRAINT uq_timetable_slot_tiid UNIQUE (tenant_id, institution_id, timetable_slot_id)
);
COMMENT ON TABLE app.timetable_slot IS 'Manual timetable slot; teacher / room clashes are warnings, not blocks [MD-05, PQ-30]; changes keep history [PQ-31].';
COMMENT ON COLUMN app.timetable_slot.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.timetable_slot.timetable_slot_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.timetable_slot.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.timetable_slot.academic_session_id IS 'Session.';
COMMENT ON COLUMN app.timetable_slot.section_id IS 'Section.';
COMMENT ON COLUMN app.timetable_slot.subject_group_id IS 'Group.';
COMMENT ON COLUMN app.timetable_slot.subject_allocation_id IS 'Subject + teacher.';
COMMENT ON COLUMN app.timetable_slot.day_of_week IS 'ISO day.';
COMMENT ON COLUMN app.timetable_slot.period_no IS 'Period (bound proposed).';
COMMENT ON COLUMN app.timetable_slot.start_time IS 'Tenant time.';
COMMENT ON COLUMN app.timetable_slot.end_time IS '> start.';
COMMENT ON COLUMN app.timetable_slot.room_label IS 'Free text (room entity OD-20). Length 1–50. (OD-20)';
COMMENT ON COLUMN app.timetable_slot.is_online IS 'Online period; meeting created automatically [OB-07] (ONLINE_PERIOD_SET).';
COMMENT ON COLUMN app.timetable_slot.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.timetable_slot.supersedes_slot_id IS 'Change to a published slot creates a new row [PQ-31].';
COMMENT ON COLUMN app.timetable_slot.valid_from IS 'Start.';
COMMENT ON COLUMN app.timetable_slot.valid_to IS 'End.';
COMMENT ON COLUMN app.timetable_slot.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.timetable_slot.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.timetable_slot.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.timetable_slot.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.timetable_slot.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.meeting_link (
  tenant_id uuid NOT NULL,
  meeting_link_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  timetable_slot_id uuid NOT NULL,
  occurrence_date date NOT NULL,
  provider text NOT NULL,
  join_url text,
  external_meeting_id text,
  status text NOT NULL DEFAULT 'CREATED',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_meeting_link PRIMARY KEY (tenant_id, meeting_link_id),
  CONSTRAINT ck_meeting_link_provider CHECK (provider IN ('ZOOM', 'GOOGLE_MEET', 'MS_TEAMS')),
  CONSTRAINT ck_meeting_link_join_url CHECK (char_length(join_url) BETWEEN 8 AND 2000),
  CONSTRAINT ck_meeting_link_external_meeting_id CHECK (char_length(external_meeting_id) BETWEEN 1 AND 200),
  CONSTRAINT ck_meeting_link_status CHECK (status IN ('CREATED', 'FAILED', 'CANCELLED')),
  CONSTRAINT ck_meeting_link_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_meeting_link_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_meeting_link_tid UNIQUE (tenant_id, meeting_link_id),
  CONSTRAINT uq_meeting_link_tiid UNIQUE (tenant_id, institution_id, meeting_link_id)
);
COMMENT ON TABLE app.meeting_link IS 'Meeting created for an online period [OB-07].';
COMMENT ON COLUMN app.meeting_link.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.meeting_link.meeting_link_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.meeting_link.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.meeting_link.timetable_slot_id IS 'Slot.';
COMMENT ON COLUMN app.meeting_link.occurrence_date IS 'Date.';
COMMENT ON COLUMN app.meeting_link.provider IS 'Tool.';
COMMENT ON COLUMN app.meeting_link.join_url IS 'Access-bearing; masked in logs. Length 8–2000.';
COMMENT ON COLUMN app.meeting_link.external_meeting_id IS 'Provider id. Length 1–200.';
COMMENT ON COLUMN app.meeting_link.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.meeting_link.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.meeting_link.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.meeting_link.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.meeting_link.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.meeting_link.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.substitute_assignment (
  tenant_id uuid NOT NULL,
  substitute_assignment_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  subject_allocation_id uuid NOT NULL,
  substitute_staff_assignment_id uuid NOT NULL,
  start_date date NOT NULL,
  end_date date NOT NULL,
  leave_request_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_substitute_assignment PRIMARY KEY (tenant_id, substitute_assignment_id),
  CONSTRAINT ck_substitute_assignment_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_substitute_assignment_r1 CHECK (end_date >= start_date),
  CONSTRAINT ck_substitute_assignment_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_substitute_assignment_tid UNIQUE (tenant_id, substitute_assignment_id),
  CONSTRAINT uq_substitute_assignment_tiid UNIQUE (tenant_id, institution_id, substitute_assignment_id)
);
COMMENT ON TABLE app.substitute_assignment IS 'Substitute teacher for given dates [AT-03, LV-11] (SUBSTITUTE_ASSIGN).';
COMMENT ON COLUMN app.substitute_assignment.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.substitute_assignment.substitute_assignment_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.substitute_assignment.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.substitute_assignment.subject_allocation_id IS 'Original allocation.';
COMMENT ON COLUMN app.substitute_assignment.substitute_staff_assignment_id IS 'Substitute; ≠ original teacher (trigger).';
COMMENT ON COLUMN app.substitute_assignment.start_date IS 'Start.';
COMMENT ON COLUMN app.substitute_assignment.end_date IS 'End.';
COMMENT ON COLUMN app.substitute_assignment.leave_request_id IS 'Set when assigned during staff-leave approval.';
COMMENT ON COLUMN app.substitute_assignment.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.substitute_assignment.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.substitute_assignment.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.substitute_assignment.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.substitute_assignment.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.attendance_session (
  tenant_id uuid NOT NULL,
  attendance_session_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  section_id uuid,
  subject_group_id uuid,
  timetable_slot_id uuid,
  subject_allocation_id uuid,
  session_kind text NOT NULL,
  attendance_date date NOT NULL,
  academic_year smallint NOT NULL,
  held_status text NOT NULL DEFAULT 'HELD',
  marked_by_user_id uuid NOT NULL,
  marked_as text NOT NULL,
  substitute_assignment_id uuid,
  submitted_at timestamptz NOT NULL DEFAULT now(),
  sync_source text NOT NULL DEFAULT 'ONLINE',
  client_captured_at timestamptz,
  client_draft_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_attendance_session PRIMARY KEY (tenant_id, attendance_session_id),
  CONSTRAINT ck_attendance_session_session_kind CHECK (session_kind IN ('PERIOD', 'SCHOOL_FIRST_HALF', 'SCHOOL_SECOND_HALF')),
  CONSTRAINT ck_attendance_session_academic_year CHECK (academic_year BETWEEN 2000 AND 2200),
  CONSTRAINT ck_attendance_session_held_status CHECK (held_status IN ('HELD', 'NOT_HELD')),
  CONSTRAINT ck_attendance_session_marked_as CHECK (marked_as IN ('ASSIGNED', 'SUBSTITUTE')),
  CONSTRAINT ck_attendance_session_sync_source CHECK (sync_source IN ('ONLINE', 'OFFLINE_SYNC')),
  CONSTRAINT ck_attendance_session_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_attendance_session_r1 CHECK (num_nonnulls(section_id, subject_group_id) = 1),
  CONSTRAINT ck_attendance_session_r2 CHECK (marked_as <> 'SUBSTITUTE' OR substitute_assignment_id IS NOT NULL),
  CONSTRAINT ck_attendance_session_r3 CHECK (sync_source = 'ONLINE' OR (client_captured_at IS NOT NULL AND client_draft_id IS NOT NULL)),
  CONSTRAINT ck_attendance_session_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_attendance_session_tid UNIQUE (tenant_id, attendance_session_id),
  CONSTRAINT uq_attendance_session_tiid UNIQUE (tenant_id, institution_id, attendance_session_id)
);
COMMENT ON TABLE app.attendance_session IS 'One marking event: a period (college) or half-day (school) [AT-01] (granularity OD-04).';
COMMENT ON COLUMN app.attendance_session.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.attendance_session.attendance_session_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.attendance_session.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.attendance_session.section_id IS 'Section.';
COMMENT ON COLUMN app.attendance_session.subject_group_id IS 'Group.';
COMMENT ON COLUMN app.attendance_session.timetable_slot_id IS 'NULL for school day sessions.';
COMMENT ON COLUMN app.attendance_session.subject_allocation_id IS 'NULL for school day sessions (OD-22). (OD-22)';
COMMENT ON COLUMN app.attendance_session.session_kind IS 'Kind.';
COMMENT ON COLUMN app.attendance_session.attendance_date IS 'Business date (tenant zone).';
COMMENT ON COLUMN app.attendance_session.academic_year IS 'Session start year (partition key of entries).';
COMMENT ON COLUMN app.attendance_session.held_status IS 'Not held = cancelled, excluded from % [AT-12].';
COMMENT ON COLUMN app.attendance_session.marked_by_user_id IS 'Teacher.';
COMMENT ON COLUMN app.attendance_session.marked_as IS 'Basis.';
COMMENT ON COLUMN app.attendance_session.substitute_assignment_id IS 'Required for SUBSTITUTE.';
COMMENT ON COLUMN app.attendance_session.submitted_at IS 'Server receipt time.';
COMMENT ON COLUMN app.attendance_session.sync_source IS 'Offline drafts synchronised later [PQ-36]; conflict rule OD-07. (OD-07)';
COMMENT ON COLUMN app.attendance_session.client_captured_at IS 'Device time the offline draft was captured.';
COMMENT ON COLUMN app.attendance_session.client_draft_id IS 'Device draft id; makes offline re-sync idempotent.';
COMMENT ON COLUMN app.attendance_session.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.attendance_session.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.attendance_session.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.attendance_session.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.attendance_session.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.attendance_sync_conflict (
  tenant_id uuid NOT NULL,
  attendance_sync_conflict_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  existing_attendance_session_id uuid NOT NULL,
  submitted_by_user_id uuid NOT NULL,
  client_draft_id uuid NOT NULL,
  client_captured_at timestamptz NOT NULL,
  draft_payload jsonb NOT NULL,
  status text NOT NULL DEFAULT 'OPEN',
  resolved_by uuid,
  resolved_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_attendance_sync_conflict PRIMARY KEY (tenant_id, attendance_sync_conflict_id),
  CONSTRAINT ck_attendance_sync_conflict_status CHECK (status IN ('OPEN', 'DISCARDED', 'CONVERTED_TO_CORRECTION')),
  CONSTRAINT ck_attendance_sync_conflict_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_attendance_sync_conflict_r1 CHECK ((status = 'OPEN') = (resolved_at IS NULL)),
  CONSTRAINT ck_attendance_sync_conflict_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_attendance_sync_conflict_tid UNIQUE (tenant_id, attendance_sync_conflict_id),
  CONSTRAINT uq_attendance_sync_conflict_tiid UNIQUE (tenant_id, institution_id, attendance_sync_conflict_id)
);
COMMENT ON TABLE app.attendance_sync_conflict IS 'Offline submission that lost to an existing session; kept, never silently overwritten [PQ-36]. Resolution rule OD-07. Fixes finding 17 structurally.';
COMMENT ON COLUMN app.attendance_sync_conflict.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.attendance_sync_conflict.attendance_sync_conflict_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.attendance_sync_conflict.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.attendance_sync_conflict.existing_attendance_session_id IS 'Session that already exists.';
COMMENT ON COLUMN app.attendance_sync_conflict.submitted_by_user_id IS 'Who synced.';
COMMENT ON COLUMN app.attendance_sync_conflict.client_draft_id IS 'Device draft id.';
COMMENT ON COLUMN app.attendance_sync_conflict.client_captured_at IS 'Device capture time.';
COMMENT ON COLUMN app.attendance_sync_conflict.draft_payload IS 'The full offline draft (entries).';
COMMENT ON COLUMN app.attendance_sync_conflict.status IS 'Converted means a correction request was raised from it.';
COMMENT ON COLUMN app.attendance_sync_conflict.resolved_by IS 'Resolver.';
COMMENT ON COLUMN app.attendance_sync_conflict.resolved_at IS 'Time.';
COMMENT ON COLUMN app.attendance_sync_conflict.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.attendance_sync_conflict.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.attendance_sync_conflict.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.attendance_sync_conflict.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.attendance_sync_conflict.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.attendance_entry (
  tenant_id uuid NOT NULL,
  attendance_entry_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  attendance_session_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  academic_year smallint NOT NULL,
  status text NOT NULL,
  recorded_via text NOT NULL DEFAULT 'MANUAL',
  leave_request_id uuid,
  status_before_leave_adjustment text,
  recorded_by_user_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_attendance_entry PRIMARY KEY (tenant_id, attendance_entry_id, academic_year),
  CONSTRAINT ck_attendance_entry_academic_year CHECK (academic_year BETWEEN 2000 AND 2200),
  CONSTRAINT ck_attendance_entry_status CHECK (status IN ('PRESENT', 'ABSENT', 'ON_APPROVED_LEAVE', 'NOT_HELD')),
  CONSTRAINT ck_attendance_entry_recorded_via CHECK (recorded_via IN ('MANUAL', 'BULK_UPLOAD', 'LEAVE_ADJUSTMENT', 'CORRECTION', 'OFFLINE_SYNC')),
  CONSTRAINT ck_attendance_entry_status_before_leave_adjustment CHECK (status_before_leave_adjustment IN ('PRESENT', 'ABSENT')),
  CONSTRAINT ck_attendance_entry_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_attendance_entry_r1 CHECK (recorded_via <> 'LEAVE_ADJUSTMENT' OR leave_request_id IS NOT NULL),
  CONSTRAINT ck_attendance_entry_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_attendance_entry_tid UNIQUE (tenant_id, attendance_entry_id, academic_year),
  CONSTRAINT uq_attendance_entry_tiid UNIQUE (tenant_id, institution_id, attendance_entry_id, academic_year)
) PARTITION BY LIST (academic_year);
COMMENT ON TABLE app.attendance_entry IS 'One student in one attendance session. Partitioned by academic_year (PTD-28).';
COMMENT ON COLUMN app.attendance_entry.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.attendance_entry.attendance_entry_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.attendance_entry.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.attendance_entry.attendance_session_id IS 'Session.';
COMMENT ON COLUMN app.attendance_entry.enrollment_id IS 'Student enrolment; must be in the section / group (trigger).';
COMMENT ON COLUMN app.attendance_entry.academic_year IS 'Partition key = session year (trigger).';
COMMENT ON COLUMN app.attendance_entry.status IS '"Late" removed; old Late imports as PRESENT [R2-23]. [R2-23]';
COMMENT ON COLUMN app.attendance_entry.recorded_via IS 'Path.';
COMMENT ON COLUMN app.attendance_entry.leave_request_id IS 'Required for LEAVE_ADJUSTMENT.';
COMMENT ON COLUMN app.attendance_entry.status_before_leave_adjustment IS 'Kept so a cancelled leave restores the original (Doc 2 D11). [LV-07]';
COMMENT ON COLUMN app.attendance_entry.recorded_by_user_id IS 'Writer (SYSTEM for leave adjustment).';
COMMENT ON COLUMN app.attendance_entry.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.attendance_entry.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.attendance_entry.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.attendance_entry.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.attendance_entry.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.attendance_entry_y2025 PARTITION OF app.attendance_entry FOR VALUES IN (2025);
CREATE TABLE app.attendance_entry_y2026 PARTITION OF app.attendance_entry FOR VALUES IN (2026);
CREATE TABLE app.attendance_entry_y2027 PARTITION OF app.attendance_entry FOR VALUES IN (2027);
CREATE TABLE app.attendance_correction_request (
  tenant_id uuid NOT NULL,
  attendance_correction_request_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  attendance_session_id uuid NOT NULL,
  requested_by_user_id uuid NOT NULL,
  reason text NOT NULL,
  approval_request_id uuid,
  bulk_correction_item_id uuid,
  status text NOT NULL DEFAULT 'DRAFT',
  applied_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_attendance_correction_request PRIMARY KEY (tenant_id, attendance_correction_request_id),
  CONSTRAINT ck_attendance_correction_request_reason CHECK (char_length(reason) BETWEEN 1 AND 500),
  CONSTRAINT ck_attendance_correction_request_status CHECK (status IN ('DRAFT', 'REQUESTED', 'APPROVED', 'REJECTED')),
  CONSTRAINT ck_attendance_correction_request_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_attendance_correction_request_r1 CHECK (status = 'DRAFT' OR approval_request_id IS NOT NULL),
  CONSTRAINT ck_attendance_correction_request_r2 CHECK ((status = 'APPROVED') = (applied_at IS NOT NULL)),
  CONSTRAINT ck_attendance_correction_request_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_attendance_correction_request_tid UNIQUE (tenant_id, attendance_correction_request_id),
  CONSTRAINT uq_attendance_correction_request_tiid UNIQUE (tenant_id, institution_id, attendance_correction_request_id)
);
COMMENT ON TABLE app.attendance_correction_request IS 'One correction request by a teacher for one session (header). One approval request per header; the lines are attendance_correction rows. Fixes the cardinality conflict between Doc 1 (row per entry) and Doc 2 (batch approval) (finding 37 / X-2).';
COMMENT ON COLUMN app.attendance_correction_request.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.attendance_correction_request.attendance_correction_request_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.attendance_correction_request.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.attendance_correction_request.attendance_session_id IS 'Session.';
COMMENT ON COLUMN app.attendance_correction_request.requested_by_user_id IS 'Teacher of the period.';
COMMENT ON COLUMN app.attendance_correction_request.reason IS 'Required [AT-02]. Length 1–500.';
COMMENT ON COLUMN app.attendance_correction_request.approval_request_id IS 'HOD (college) or School Principal / Vice-Principal (school) [AP-14]; required once REQUESTED.';
COMMENT ON COLUMN app.attendance_correction_request.bulk_correction_item_id IS 'Set when raised from a bulk correction (BULK_CORRECT).';
COMMENT ON COLUMN app.attendance_correction_request.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.attendance_correction_request.applied_at IS 'Lines applied in the approval transaction.';
COMMENT ON COLUMN app.attendance_correction_request.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.attendance_correction_request.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.attendance_correction_request.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.attendance_correction_request.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.attendance_correction_request.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.attendance_correction (
  tenant_id uuid NOT NULL,
  attendance_correction_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  attendance_correction_request_id uuid NOT NULL,
  attendance_entry_id uuid NOT NULL,
  academic_year smallint NOT NULL,
  old_status text NOT NULL,
  new_status text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_attendance_correction PRIMARY KEY (tenant_id, attendance_correction_id),
  CONSTRAINT ck_attendance_correction_old_status CHECK (old_status IN ('PRESENT', 'ABSENT', 'ON_APPROVED_LEAVE', 'NOT_HELD')),
  CONSTRAINT ck_attendance_correction_new_status CHECK (new_status IN ('PRESENT', 'ABSENT', 'ON_APPROVED_LEAVE', 'NOT_HELD')),
  CONSTRAINT ck_attendance_correction_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_attendance_correction_r1 CHECK (old_status <> new_status),
  CONSTRAINT ck_attendance_correction_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_attendance_correction_tid UNIQUE (tenant_id, attendance_correction_id),
  CONSTRAINT uq_attendance_correction_tiid UNIQUE (tenant_id, institution_id, attendance_correction_id)
);
COMMENT ON TABLE app.attendance_correction IS 'One corrected entry inside a correction request; original kept [PQ-33].';
COMMENT ON COLUMN app.attendance_correction.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.attendance_correction.attendance_correction_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.attendance_correction.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.attendance_correction.attendance_correction_request_id IS 'Header.';
COMMENT ON COLUMN app.attendance_correction.attendance_entry_id IS 'Entry (composite FK with academic_year to the partitioned table).';
COMMENT ON COLUMN app.attendance_correction.academic_year IS 'Partition key of the entry.';
COMMENT ON COLUMN app.attendance_correction.old_status IS 'Value at request.';
COMMENT ON COLUMN app.attendance_correction.new_status IS 'Requested value.';
COMMENT ON COLUMN app.attendance_correction.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.attendance_correction.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.attendance_correction.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.attendance_correction.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.attendance_correction.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.attendance_bulk_upload (
  tenant_id uuid NOT NULL,
  attendance_bulk_upload_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  import_job_id uuid NOT NULL,
  uploader_kind text NOT NULL,
  status text NOT NULL DEFAULT 'STAGED',
  approval_request_id uuid,
  row_count integer NOT NULL DEFAULT 0,
  error_count integer NOT NULL DEFAULT 0,
  applied_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_attendance_bulk_upload PRIMARY KEY (tenant_id, attendance_bulk_upload_id),
  CONSTRAINT ck_attendance_bulk_upload_uploader_kind CHECK (uploader_kind IN ('ASSIGNED_TEACHER', 'ADMIN')),
  CONSTRAINT ck_attendance_bulk_upload_status CHECK (status IN ('STAGED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'APPLIED', 'FAILED')),
  CONSTRAINT ck_attendance_bulk_upload_row_count CHECK (row_count >= 0),
  CONSTRAINT ck_attendance_bulk_upload_error_count CHECK (error_count >= 0),
  CONSTRAINT ck_attendance_bulk_upload_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_attendance_bulk_upload_r1 CHECK (uploader_kind <> 'ADMIN' OR status IN ('STAGED','FAILED') OR approval_request_id IS NOT NULL),
  CONSTRAINT ck_attendance_bulk_upload_r2 CHECK (uploader_kind <> 'ASSIGNED_TEACHER' OR approval_request_id IS NULL),
  CONSTRAINT ck_attendance_bulk_upload_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_attendance_bulk_upload_tid UNIQUE (tenant_id, attendance_bulk_upload_id),
  CONSTRAINT uq_attendance_bulk_upload_tiid UNIQUE (tenant_id, institution_id, attendance_bulk_upload_id)
);
COMMENT ON TABLE app.attendance_bulk_upload IS 'Attendance from a file: assigned teachers without approval, Admin uploads need Principal approval [AT-11].';
COMMENT ON COLUMN app.attendance_bulk_upload.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.attendance_bulk_upload.attendance_bulk_upload_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.attendance_bulk_upload.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.attendance_bulk_upload.import_job_id IS 'Shared validation job.';
COMMENT ON COLUMN app.attendance_bulk_upload.uploader_kind IS 'Basis.';
COMMENT ON COLUMN app.attendance_bulk_upload.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.attendance_bulk_upload.approval_request_id IS 'Required for ADMIN beyond STAGED.';
COMMENT ON COLUMN app.attendance_bulk_upload.row_count IS 'Rows.';
COMMENT ON COLUMN app.attendance_bulk_upload.error_count IS 'Bad rows.';
COMMENT ON COLUMN app.attendance_bulk_upload.applied_at IS 'Applied.';
COMMENT ON COLUMN app.attendance_bulk_upload.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.attendance_bulk_upload.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.attendance_bulk_upload.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.attendance_bulk_upload.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.attendance_bulk_upload.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.term_lock (
  tenant_id uuid NOT NULL,
  term_lock_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  academic_session_id uuid NOT NULL,
  semester_id uuid,
  lock_state text NOT NULL,
  unlock_until timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_term_lock PRIMARY KEY (tenant_id, term_lock_id),
  CONSTRAINT ck_term_lock_lock_state CHECK (lock_state IN ('LOCKED', 'TEMP_UNLOCKED')),
  CONSTRAINT ck_term_lock_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_term_lock_r1 CHECK (lock_state <> 'TEMP_UNLOCKED' OR unlock_until IS NOT NULL),
  CONSTRAINT ck_term_lock_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_term_lock_tid UNIQUE (tenant_id, term_lock_id),
  CONSTRAINT uq_term_lock_tiid UNIQUE (tenant_id, institution_id, term_lock_id)
);
COMMENT ON TABLE app.term_lock IS 'Term lock; unlock ≤ 3 days with reason, then relock [R2-08, AT-09].';
COMMENT ON COLUMN app.term_lock.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.term_lock.term_lock_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.term_lock.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.term_lock.academic_session_id IS 'Session.';
COMMENT ON COLUMN app.term_lock.semester_id IS 'NULL = whole session (schools).';
COMMENT ON COLUMN app.term_lock.lock_state IS 'State.';
COMMENT ON COLUMN app.term_lock.unlock_until IS 'Required when TEMP_UNLOCKED.';
COMMENT ON COLUMN app.term_lock.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.term_lock.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.term_lock.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.term_lock.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.term_lock.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.term_lock_event (
  tenant_id uuid NOT NULL,
  term_lock_event_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  term_lock_id uuid NOT NULL,
  event_type text NOT NULL,
  actor_user_id uuid NOT NULL,
  reason text,
  unlock_until timestamptz,
  occurred_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_term_lock_event PRIMARY KEY (tenant_id, term_lock_event_id),
  CONSTRAINT ck_term_lock_event_event_type CHECK (event_type IN ('LOCK', 'UNLOCK', 'AUTO_RELOCK', 'RELOCK')),
  CONSTRAINT ck_term_lock_event_reason CHECK (char_length(reason) BETWEEN 1 AND 500),
  CONSTRAINT ck_term_lock_event_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_term_lock_event_r1 CHECK (event_type <> 'UNLOCK' OR (reason IS NOT NULL AND unlock_until IS NOT NULL)),
  CONSTRAINT ck_term_lock_event_r2 CHECK (unlock_until IS NULL OR unlock_until <= occurred_at + interval '3 days'),
  CONSTRAINT ck_term_lock_event_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_term_lock_event_tid UNIQUE (tenant_id, term_lock_event_id),
  CONSTRAINT uq_term_lock_event_tiid UNIQUE (tenant_id, institution_id, term_lock_event_id)
);
COMMENT ON TABLE app.term_lock_event IS 'Append-only history of locks / unlocks.';
COMMENT ON COLUMN app.term_lock_event.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.term_lock_event.term_lock_event_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.term_lock_event.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.term_lock_event.term_lock_id IS 'Lock.';
COMMENT ON COLUMN app.term_lock_event.event_type IS 'Event.';
COMMENT ON COLUMN app.term_lock_event.actor_user_id IS 'Principal or SYSTEM.';
COMMENT ON COLUMN app.term_lock_event.reason IS 'Required for UNLOCK. Length 1–500.';
COMMENT ON COLUMN app.term_lock_event.unlock_until IS '≤ occurred_at + 3 days.';
COMMENT ON COLUMN app.term_lock_event.occurred_at IS 'Time.';
COMMENT ON COLUMN app.term_lock_event.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.term_lock_event.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.term_lock_event.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.term_lock_event.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.term_lock_event.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.risk_flag (
  tenant_id uuid NOT NULL,
  risk_flag_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  attendance_percent numeric(5,2),
  attendance_level text NOT NULL,
  previous_attendance_level text,
  continuous_absence boolean NOT NULL DEFAULT false,
  low_marks boolean NOT NULL DEFAULT false,
  missed_assignments_30d smallint NOT NULL DEFAULT 0,
  academic_risk boolean NOT NULL DEFAULT false,
  computed_at timestamptz NOT NULL DEFAULT now(),
  level_changed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_risk_flag PRIMARY KEY (tenant_id, risk_flag_id),
  CONSTRAINT ck_risk_flag_attendance_percent CHECK (attendance_percent IS NULL OR attendance_percent BETWEEN 0 AND 100),
  CONSTRAINT ck_risk_flag_attendance_level CHECK (attendance_level IN ('NORMAL', 'WARNING', 'CRITICAL')),
  CONSTRAINT ck_risk_flag_previous_attendance_level CHECK (previous_attendance_level IN ('NORMAL', 'WARNING', 'CRITICAL')),
  CONSTRAINT ck_risk_flag_missed_assignments_30d CHECK (missed_assignments_30d >= 0),
  CONSTRAINT ck_risk_flag_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_risk_flag_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_risk_flag_tid UNIQUE (tenant_id, risk_flag_id),
  CONSTRAINT uq_risk_flag_tiid UNIQUE (tenant_id, institution_id, risk_flag_id)
);
COMMENT ON TABLE app.risk_flag IS 'Current risk status per enrolment [§7.7, RK-01..04].';
COMMENT ON COLUMN app.risk_flag.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.risk_flag.risk_flag_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.risk_flag.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.risk_flag.enrollment_id IS 'Enrolment.';
COMMENT ON COLUMN app.risk_flag.attendance_percent IS '§20 formula; NULL if no period held.';
COMMENT ON COLUMN app.risk_flag.attendance_level IS 'Defaults ≥75 / 65–75 / <65, configurable.';
COMMENT ON COLUMN app.risk_flag.previous_attendance_level IS 'Parents alerted only on change [RK-04].';
COMMENT ON COLUMN app.risk_flag.continuous_absence IS '≥ 3 working days in a row (setting).';
COMMENT ON COLUMN app.risk_flag.low_marks IS 'Low marks.';
COMMENT ON COLUMN app.risk_flag.missed_assignments_30d IS 'Missed in 30 days.';
COMMENT ON COLUMN app.risk_flag.academic_risk IS 'Any of the risk conditions.';
COMMENT ON COLUMN app.risk_flag.computed_at IS 'Recalculated after every attendance / marks change [RK-02].';
COMMENT ON COLUMN app.risk_flag.level_changed_at IS 'Level change.';
COMMENT ON COLUMN app.risk_flag.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.risk_flag.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.risk_flag.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.risk_flag.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.risk_flag.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.risk_remark (
  tenant_id uuid NOT NULL,
  risk_remark_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  remark text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_risk_remark PRIMARY KEY (tenant_id, risk_remark_id),
  CONSTRAINT ck_risk_remark_remark CHECK (char_length(remark) BETWEEN 1 AND 1000),
  CONSTRAINT ck_risk_remark_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_risk_remark_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_risk_remark_tid UNIQUE (tenant_id, risk_remark_id),
  CONSTRAINT uq_risk_remark_tiid UNIQUE (tenant_id, institution_id, risk_remark_id)
);
COMMENT ON TABLE app.risk_remark IS 'Remarks on a flagged student, kept after the flag clears [RK-03].';
COMMENT ON COLUMN app.risk_remark.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.risk_remark.risk_remark_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.risk_remark.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.risk_remark.enrollment_id IS 'Enrolment.';
COMMENT ON COLUMN app.risk_remark.remark IS 'Remark. Length 1–1000.';
COMMENT ON COLUMN app.risk_remark.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.risk_remark.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.risk_remark.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.risk_remark.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.risk_remark.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.leave_type (
  tenant_id uuid NOT NULL,
  leave_type_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  audience text NOT NULL,
  leave_code text NOT NULL,
  leave_name text NOT NULL,
  tracks_balance boolean NOT NULL DEFAULT false,
  is_loss_of_pay boolean NOT NULL DEFAULT false,
  is_default boolean NOT NULL DEFAULT false,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_leave_type PRIMARY KEY (tenant_id, leave_type_id),
  CONSTRAINT ck_leave_type_audience CHECK (audience IN ('STUDENT', 'STAFF')),
  CONSTRAINT ck_leave_type_leave_code CHECK (leave_code ~ '^[A-Z_]{2,30}$'),
  CONSTRAINT ck_leave_type_leave_name CHECK (char_length(leave_name) BETWEEN 1 AND 60),
  CONSTRAINT ck_leave_type_status CHECK (status IN ('ACTIVE', 'RETIRED')),
  CONSTRAINT ck_leave_type_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_leave_type_r1 CHECK (NOT tracks_balance OR (audience = 'STAFF' AND NOT is_loss_of_pay)),
  CONSTRAINT ck_leave_type_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_leave_type_tid UNIQUE (tenant_id, leave_type_id),
  CONSTRAINT uq_leave_type_tiid UNIQUE (tenant_id, institution_id, leave_type_id)
);
COMMENT ON TABLE app.leave_type IS 'Leave types; defaults per LV-01; set by College / School Admin [Q8].';
COMMENT ON COLUMN app.leave_type.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.leave_type.leave_type_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.leave_type.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.leave_type.audience IS 'Who.';
COMMENT ON COLUMN app.leave_type.leave_code IS 'e.g. SICK.';
COMMENT ON COLUMN app.leave_type.leave_name IS 'Name. Length 1–60.';
COMMENT ON COLUMN app.leave_type.tracks_balance IS 'Staff types only; never student types or loss of pay.';
COMMENT ON COLUMN app.leave_type.is_loss_of_pay IS 'Loss of pay type.';
COMMENT ON COLUMN app.leave_type.is_default IS 'Seeded default.';
COMMENT ON COLUMN app.leave_type.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.leave_type.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.leave_type.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.leave_type.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.leave_type.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.leave_type.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.leave_balance (
  tenant_id uuid NOT NULL,
  leave_balance_id uuid NOT NULL,
  institution_id uuid,
  staff_profile_id uuid NOT NULL,
  leave_type_id uuid NOT NULL,
  leave_year smallint NOT NULL,
  credited_days numeric(5,1) NOT NULL,
  used_days numeric(5,1) NOT NULL DEFAULT 0,
  credited_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_leave_balance PRIMARY KEY (tenant_id, leave_balance_id),
  CONSTRAINT ck_leave_balance_leave_year CHECK (leave_year BETWEEN 2000 AND 2200),
  CONSTRAINT ck_leave_balance_credited_days CHECK (credited_days >= 0 AND credited_days * 2 = floor(credited_days * 2)),
  CONSTRAINT ck_leave_balance_used_days CHECK (used_days >= 0),
  CONSTRAINT ck_leave_balance_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_leave_balance_r1 CHECK (used_days <= credited_days),
  CONSTRAINT ck_leave_balance_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_leave_balance_tid UNIQUE (tenant_id, leave_balance_id)
);
COMMENT ON TABLE app.leave_balance IS 'Yearly staff balance; no carry-forward or encashment [R2-09, WF-32].';
COMMENT ON COLUMN app.leave_balance.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.leave_balance.leave_balance_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.leave_balance.institution_id IS 'Institution of the staff member; NULL for university staff. [§6.1]';
COMMENT ON COLUMN app.leave_balance.staff_profile_id IS 'Person.';
COMMENT ON COLUMN app.leave_balance.leave_type_id IS 'Staff type with tracks_balance (trigger).';
COMMENT ON COLUMN app.leave_balance.leave_year IS 'Year (start month setting).';
COMMENT ON COLUMN app.leave_balance.credited_days IS 'Half-day steps.';
COMMENT ON COLUMN app.leave_balance.used_days IS 'Changed only when an approved request is finalised.';
COMMENT ON COLUMN app.leave_balance.credited_by IS 'HR / School Admin.';
COMMENT ON COLUMN app.leave_balance.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.leave_balance.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.leave_balance.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.leave_balance.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.leave_balance.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.leave_request (
  tenant_id uuid NOT NULL,
  leave_request_id uuid NOT NULL,
  institution_id uuid,
  requested_by_user_id uuid NOT NULL,
  for_enrollment_id uuid,
  for_staff_profile_id uuid,
  leave_type_id uuid NOT NULL,
  unit text NOT NULL,
  half_day_part text,
  start_date date NOT NULL,
  end_date date NOT NULL,
  period_numbers smallint[],
  reason text NOT NULL,
  document_file_id uuid,
  days_counted numeric(5,1) NOT NULL,
  loss_of_pay_days numeric(5,1) NOT NULL DEFAULT 0,
  loss_of_pay_confirmed_by uuid,
  loss_of_pay_confirmed_at timestamptz,
  process_code text NOT NULL,
  approval_request_id uuid,
  status text NOT NULL DEFAULT 'DRAFT',
  submitted_at timestamptz,
  cancelled_at timestamptz,
  cancelled_by uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_leave_request PRIMARY KEY (tenant_id, leave_request_id),
  CONSTRAINT ck_leave_request_unit CHECK (unit IN ('FULL_DAY', 'HALF_DAY', 'PERIOD')),
  CONSTRAINT ck_leave_request_half_day_part CHECK (half_day_part IN ('FIRST_HALF', 'SECOND_HALF')),
  CONSTRAINT ck_leave_request_reason CHECK (char_length(reason) BETWEEN 1 AND 1000),
  CONSTRAINT ck_leave_request_days_counted CHECK (days_counted >= 0),
  CONSTRAINT ck_leave_request_loss_of_pay_days CHECK (loss_of_pay_days >= 0),
  CONSTRAINT ck_leave_request_status CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'BLOCKED')),
  CONSTRAINT ck_leave_request_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_leave_request_r1 CHECK (num_nonnulls(for_enrollment_id, for_staff_profile_id) = 1),
  CONSTRAINT ck_leave_request_r2 CHECK (end_date >= start_date),
  CONSTRAINT ck_leave_request_r3 CHECK ((unit = 'PERIOD') = (period_numbers IS NOT NULL)),
  CONSTRAINT ck_leave_request_r4 CHECK ((unit = 'HALF_DAY') = (half_day_part IS NOT NULL)),
  CONSTRAINT ck_leave_request_r5 CHECK (loss_of_pay_days <= days_counted),
  CONSTRAINT ck_leave_request_r6 CHECK (for_staff_profile_id IS NOT NULL OR loss_of_pay_days = 0),
  CONSTRAINT ck_leave_request_r7 CHECK (status = 'DRAFT' OR (submitted_at IS NOT NULL AND approval_request_id IS NOT NULL)),
  CONSTRAINT ck_leave_request_r8 CHECK ((loss_of_pay_confirmed_by IS NULL) = (loss_of_pay_confirmed_at IS NULL)),
  CONSTRAINT ck_leave_request_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_leave_request_tid UNIQUE (tenant_id, leave_request_id)
);
COMMENT ON TABLE app.leave_request IS 'Student or staff leave request [§7.2]; chains in §4.13.';
COMMENT ON COLUMN app.leave_request.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.leave_request.leave_request_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.leave_request.institution_id IS 'NULL for university / tenant-level staff (Registrar, VC, DPC). [§6.1]';
COMMENT ON COLUMN app.leave_request.requested_by_user_id IS 'Student, primary guardian [WF-24], or the staff member.';
COMMENT ON COLUMN app.leave_request.for_enrollment_id IS 'Student leave.';
COMMENT ON COLUMN app.leave_request.for_staff_profile_id IS 'Staff leave.';
COMMENT ON COLUMN app.leave_request.leave_type_id IS 'Audience must match (trigger).';
COMMENT ON COLUMN app.leave_request.unit IS '[LV-02].';
COMMENT ON COLUMN app.leave_request.half_day_part IS 'Required for HALF_DAY (Doc 2 D10; overlap rule OD-21). (OD-21)';
COMMENT ON COLUMN app.leave_request.start_date IS 'Start.';
COMMENT ON COLUMN app.leave_request.end_date IS 'End.';
COMMENT ON COLUMN app.leave_request.period_numbers IS 'Required for PERIOD.';
COMMENT ON COLUMN app.leave_request.reason IS 'Reason. Length 1–1000.';
COMMENT ON COLUMN app.leave_request.document_file_id IS 'Optional medical note [R2-10]; sensitive (V3.3).';
COMMENT ON COLUMN app.leave_request.days_counted IS 'Working days (weekends / holidays excluded; sandwich rule optional [LV-08]).';
COMMENT ON COLUMN app.leave_request.loss_of_pay_days IS 'Staff only; flagged when balance runs out.';
COMMENT ON COLUMN app.leave_request.loss_of_pay_confirmed_by IS 'HR Manager / School Admin (LOSS_OF_PAY_CONFIRM) (Doc 2 D12).';
COMMENT ON COLUMN app.leave_request.loss_of_pay_confirmed_at IS 'Confirmation time.';
COMMENT ON COLUMN app.leave_request.process_code IS 'Chain chosen at submission (by audience, role and length) [AP-11, AP-12, F-11, C6].';
COMMENT ON COLUMN app.leave_request.approval_request_id IS 'Required once PENDING.';
COMMENT ON COLUMN app.leave_request.status IS 'BLOCKED = required approver missing (Principal / VC leave) [D-002, C7].';
COMMENT ON COLUMN app.leave_request.submitted_at IS 'Required once not DRAFT.';
COMMENT ON COLUMN app.leave_request.cancelled_at IS 'Before start: requester; after: final approver [LV-07].';
COMMENT ON COLUMN app.leave_request.cancelled_by IS 'Who cancelled.';
COMMENT ON COLUMN app.leave_request.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.leave_request.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.leave_request.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.leave_request.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.leave_request.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.grading_scheme_version (
  tenant_id uuid NOT NULL,
  grading_scheme_version_id uuid NOT NULL,
  institution_id uuid,
  scheme_name text NOT NULL,
  version_no integer NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT',
  effective_from date NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_grading_scheme_version PRIMARY KEY (tenant_id, grading_scheme_version_id),
  CONSTRAINT ck_grading_scheme_version_scheme_name CHECK (char_length(scheme_name) BETWEEN 2 AND 100),
  CONSTRAINT ck_grading_scheme_version_version_no CHECK (version_no >= 1),
  CONSTRAINT ck_grading_scheme_version_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
  CONSTRAINT ck_grading_scheme_version_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_grading_scheme_version_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_grading_scheme_version_tid UNIQUE (tenant_id, grading_scheme_version_id)
);
COMMENT ON TABLE app.grading_scheme_version IS 'Versioned grading scheme; results keep the version used [PQ-38, PQ-43].';
COMMENT ON COLUMN app.grading_scheme_version.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.grading_scheme_version.grading_scheme_version_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.grading_scheme_version.institution_id IS 'NULL = university-wide (Registrar); set = standalone or institution band set. [§6.1]';
COMMENT ON COLUMN app.grading_scheme_version.scheme_name IS 'Name. Length 2–100.';
COMMENT ON COLUMN app.grading_scheme_version.version_no IS 'Version; standalone changes by the Admin need no approval but are versioned and the Principal is notified [C5]. [C5]';
COMMENT ON COLUMN app.grading_scheme_version.status IS 'Frozen once referenced by a result (trigger).';
COMMENT ON COLUMN app.grading_scheme_version.effective_from IS 'Start.';
COMMENT ON COLUMN app.grading_scheme_version.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.grading_scheme_version.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.grading_scheme_version.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.grading_scheme_version.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.grading_scheme_version.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.grade_band (
  tenant_id uuid NOT NULL,
  grade_band_id uuid NOT NULL,
  grading_scheme_version_id uuid NOT NULL,
  lower_percent numeric(5,2) NOT NULL,
  grade_label text NOT NULL,
  grade_points numeric(4,2) NOT NULL,
  is_pass_grade boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_grade_band PRIMARY KEY (tenant_id, grade_band_id),
  CONSTRAINT ck_grade_band_lower_percent CHECK (lower_percent BETWEEN 0 AND 100),
  CONSTRAINT ck_grade_band_grade_label CHECK (char_length(grade_label) BETWEEN 1 AND 5),
  CONSTRAINT ck_grade_band_grade_points CHECK (grade_points >= 0),
  CONSTRAINT ck_grade_band_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_grade_band_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_grade_band_tid UNIQUE (tenant_id, grade_band_id)
);
COMMENT ON TABLE app.grade_band IS 'Grade band; only the lower limit is stored so bands cannot overlap (PTD-31).';
COMMENT ON COLUMN app.grade_band.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.grade_band.grade_band_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.grade_band.grading_scheme_version_id IS 'Scheme.';
COMMENT ON COLUMN app.grade_band.lower_percent IS 'Band start; runs to the next band.';
COMMENT ON COLUMN app.grade_band.grade_label IS 'e.g. A. Length 1–5.';
COMMENT ON COLUMN app.grade_band.grade_points IS 'Points.';
COMMENT ON COLUMN app.grade_band.is_pass_grade IS 'Pass grade.';
COMMENT ON COLUMN app.grade_band.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.grade_band.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.grade_band.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.grade_band.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.grade_band.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.exam_rule (
  tenant_id uuid NOT NULL,
  exam_rule_id uuid NOT NULL,
  institution_id uuid,
  rule_name text NOT NULL,
  internal_min_percent numeric(5,2),
  internal_max_percent numeric(5,2),
  grading_scheme_version_id uuid NOT NULL,
  version_no integer NOT NULL DEFAULT 1,
  effective_from date NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_exam_rule PRIMARY KEY (tenant_id, exam_rule_id),
  CONSTRAINT ck_exam_rule_rule_name CHECK (char_length(rule_name) BETWEEN 2 AND 150),
  CONSTRAINT ck_exam_rule_internal_min_percent CHECK (internal_min_percent IS NULL OR internal_min_percent BETWEEN 0 AND 100),
  CONSTRAINT ck_exam_rule_internal_max_percent CHECK (internal_max_percent IS NULL OR internal_max_percent BETWEEN 0 AND 100),
  CONSTRAINT ck_exam_rule_version_no CHECK (version_no >= 1),
  CONSTRAINT ck_exam_rule_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
  CONSTRAINT ck_exam_rule_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_exam_rule_r1 CHECK (internal_min_percent IS NULL OR internal_max_percent IS NULL OR internal_min_percent <= internal_max_percent),
  CONSTRAINT ck_exam_rule_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_exam_rule_tid UNIQUE (tenant_id, exam_rule_id)
);
COMMENT ON TABLE app.exam_rule IS 'Exam rules of the Registrar (university) or the standalone Admin [R2-06, C5]; field list OD-25.';
COMMENT ON COLUMN app.exam_rule.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.exam_rule.exam_rule_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.exam_rule.institution_id IS 'NULL = university-wide. [§6.1]';
COMMENT ON COLUMN app.exam_rule.rule_name IS 'Name. Length 2–150.';
COMMENT ON COLUMN app.exam_rule.internal_min_percent IS 'Allowed internal share (proposed, OD-25). (OD-25)';
COMMENT ON COLUMN app.exam_rule.internal_max_percent IS '(proposed, OD-25). (OD-25)';
COMMENT ON COLUMN app.exam_rule.grading_scheme_version_id IS 'Scheme.';
COMMENT ON COLUMN app.exam_rule.version_no IS 'Versioned and logged [C5].';
COMMENT ON COLUMN app.exam_rule.effective_from IS 'Start.';
COMMENT ON COLUMN app.exam_rule.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.exam_rule.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.exam_rule.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.exam_rule.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.exam_rule.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.exam_rule.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.exam_pattern (
  tenant_id uuid NOT NULL,
  exam_pattern_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  exam_rule_id uuid NOT NULL,
  pattern_name text NOT NULL,
  version_no integer NOT NULL DEFAULT 1,
  grading_scheme_version_id uuid NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_exam_pattern PRIMARY KEY (tenant_id, exam_pattern_id),
  CONSTRAINT ck_exam_pattern_pattern_name CHECK (char_length(pattern_name) BETWEEN 2 AND 100),
  CONSTRAINT ck_exam_pattern_version_no CHECK (version_no >= 1),
  CONSTRAINT ck_exam_pattern_status CHECK (status IN ('ACTIVE', 'RETIRED')),
  CONSTRAINT ck_exam_pattern_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_exam_pattern_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_exam_pattern_tid UNIQUE (tenant_id, exam_pattern_id),
  CONSTRAINT uq_exam_pattern_tiid UNIQUE (tenant_id, institution_id, exam_pattern_id)
);
COMMENT ON TABLE app.exam_pattern IS 'Exam pattern set by the Admin within the rules (EXAM_PATTERN_SET; standalone: no approval, Principal notified, versioned [C5]).';
COMMENT ON COLUMN app.exam_pattern.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.exam_pattern.exam_pattern_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.exam_pattern.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.exam_pattern.exam_rule_id IS 'Rule.';
COMMENT ON COLUMN app.exam_pattern.pattern_name IS 'Name. Length 2–100.';
COMMENT ON COLUMN app.exam_pattern.version_no IS 'Version [C5].';
COMMENT ON COLUMN app.exam_pattern.grading_scheme_version_id IS 'Scheme.';
COMMENT ON COLUMN app.exam_pattern.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.exam_pattern.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.exam_pattern.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.exam_pattern.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.exam_pattern.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.exam_pattern.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.exam_pattern_component (
  tenant_id uuid NOT NULL,
  component_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  exam_pattern_id uuid NOT NULL,
  component_name text NOT NULL,
  is_internal boolean NOT NULL DEFAULT true,
  max_marks numeric(6,2) NOT NULL,
  pass_marks numeric(6,2),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_exam_pattern_component PRIMARY KEY (tenant_id, component_id),
  CONSTRAINT ck_exam_pattern_component_component_name CHECK (char_length(component_name) BETWEEN 1 AND 60),
  CONSTRAINT ck_exam_pattern_component_max_marks CHECK (max_marks > 0),
  CONSTRAINT ck_exam_pattern_component_pass_marks CHECK (pass_marks IS NULL OR pass_marks >= 0),
  CONSTRAINT ck_exam_pattern_component_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_exam_pattern_component_r1 CHECK (pass_marks IS NULL OR pass_marks <= max_marks),
  CONSTRAINT ck_exam_pattern_component_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_exam_pattern_component_tid UNIQUE (tenant_id, component_id),
  CONSTRAINT uq_exam_pattern_component_tiid UNIQUE (tenant_id, institution_id, component_id)
);
COMMENT ON TABLE app.exam_pattern_component IS 'Component of a pattern (internal / external split).';
COMMENT ON COLUMN app.exam_pattern_component.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.exam_pattern_component.component_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.exam_pattern_component.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.exam_pattern_component.exam_pattern_id IS 'Pattern.';
COMMENT ON COLUMN app.exam_pattern_component.component_name IS 'e.g. Theory. Length 1–60.';
COMMENT ON COLUMN app.exam_pattern_component.is_internal IS 'Internal vs external.';
COMMENT ON COLUMN app.exam_pattern_component.max_marks IS 'Maximum.';
COMMENT ON COLUMN app.exam_pattern_component.pass_marks IS '≤ max.';
COMMENT ON COLUMN app.exam_pattern_component.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.exam_pattern_component.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.exam_pattern_component.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.exam_pattern_component.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.exam_pattern_component.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.exam (
  tenant_id uuid NOT NULL,
  exam_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  academic_session_id uuid NOT NULL,
  semester_id uuid,
  class_id uuid,
  exam_type text NOT NULL,
  exam_name text NOT NULL,
  exam_pattern_id uuid NOT NULL,
  start_date date NOT NULL,
  end_date date NOT NULL,
  schedule_owner text NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT',
  schedule_approval_request_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_exam PRIMARY KEY (tenant_id, exam_id),
  CONSTRAINT ck_exam_exam_type CHECK (exam_type IN ('UNIVERSITY', 'INTERNAL', 'SUPPLEMENTARY')),
  CONSTRAINT ck_exam_exam_name CHECK (char_length(exam_name) BETWEEN 2 AND 150),
  CONSTRAINT ck_exam_schedule_owner CHECK (schedule_owner IN ('CONTROLLER', 'ADMIN_OR_CLERK')),
  CONSTRAINT ck_exam_status CHECK (status IN ('DRAFT', 'PENDING_SCHEDULE_APPROVAL', 'SCHEDULED', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT ck_exam_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_exam_r1 CHECK (num_nonnulls(semester_id, class_id) = 1),
  CONSTRAINT ck_exam_r2 CHECK (end_date >= start_date),
  CONSTRAINT ck_exam_r3 CHECK (exam_type <> 'UNIVERSITY' OR schedule_owner = 'CONTROLLER'),
  CONSTRAINT ck_exam_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_exam_tid UNIQUE (tenant_id, exam_id),
  CONSTRAINT uq_exam_tiid UNIQUE (tenant_id, institution_id, exam_id)
);
COMMENT ON TABLE app.exam IS 'Exam (university, internal or supplementary).';
COMMENT ON COLUMN app.exam.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.exam.exam_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.exam.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.exam.academic_session_id IS 'Session.';
COMMENT ON COLUMN app.exam.semester_id IS 'College.';
COMMENT ON COLUMN app.exam.class_id IS 'School.';
COMMENT ON COLUMN app.exam.exam_type IS 'Type.';
COMMENT ON COLUMN app.exam.exam_name IS 'Name. Length 2–150.';
COMMENT ON COLUMN app.exam.exam_pattern_id IS 'Pattern.';
COMMENT ON COLUMN app.exam.start_date IS 'Start.';
COMMENT ON COLUMN app.exam.end_date IS 'End.';
COMMENT ON COLUMN app.exam.schedule_owner IS 'University exams by the Controller [WF-18].';
COMMENT ON COLUMN app.exam.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.exam.schedule_approval_request_id IS 'Optional Vice-Principal approval [Q9].';
COMMENT ON COLUMN app.exam.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.exam.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.exam.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.exam.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.exam.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.exam_schedule_item (
  tenant_id uuid NOT NULL,
  exam_schedule_item_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  exam_id uuid NOT NULL,
  course_offering_id uuid NOT NULL,
  exam_date date NOT NULL,
  start_time time NOT NULL,
  end_time time NOT NULL,
  max_marks_total numeric(6,2) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_exam_schedule_item PRIMARY KEY (tenant_id, exam_schedule_item_id),
  CONSTRAINT ck_exam_schedule_item_max_marks_total CHECK (max_marks_total > 0),
  CONSTRAINT ck_exam_schedule_item_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_exam_schedule_item_r1 CHECK (end_time > start_time),
  CONSTRAINT ck_exam_schedule_item_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_exam_schedule_item_tid UNIQUE (tenant_id, exam_schedule_item_id),
  CONSTRAINT uq_exam_schedule_item_tiid UNIQUE (tenant_id, institution_id, exam_schedule_item_id)
);
COMMENT ON TABLE app.exam_schedule_item IS 'Paper / subject slot inside an exam (seating and invigilation are a later phase [EX-11]).';
COMMENT ON COLUMN app.exam_schedule_item.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.exam_schedule_item.exam_schedule_item_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.exam_schedule_item.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.exam_schedule_item.exam_id IS 'Exam.';
COMMENT ON COLUMN app.exam_schedule_item.course_offering_id IS 'Offering.';
COMMENT ON COLUMN app.exam_schedule_item.exam_date IS 'Inside the exam range (trigger).';
COMMENT ON COLUMN app.exam_schedule_item.start_time IS 'Start.';
COMMENT ON COLUMN app.exam_schedule_item.end_time IS 'End.';
COMMENT ON COLUMN app.exam_schedule_item.max_marks_total IS 'Paper maximum.';
COMMENT ON COLUMN app.exam_schedule_item.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.exam_schedule_item.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.exam_schedule_item.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.exam_schedule_item.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.exam_schedule_item.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.hall_ticket (
  tenant_id uuid NOT NULL,
  hall_ticket_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  exam_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  status text NOT NULL,
  withheld_reasons text[],
  eligibility_checked_at timestamptz NOT NULL,
  override_by_user_id uuid,
  override_reason text,
  overridden_at timestamptz,
  file_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_hall_ticket PRIMARY KEY (tenant_id, hall_ticket_id),
  CONSTRAINT ck_hall_ticket_status CHECK (status IN ('GENERATED', 'WITHHELD', 'OVERRIDDEN')),
  CONSTRAINT ck_hall_ticket_withheld_reasons CHECK (withheld_reasons IS NULL OR withheld_reasons <@ ARRAY['LOW_ATTENDANCE','OVERDUE_FEES']::text[]),
  CONSTRAINT ck_hall_ticket_override_reason CHECK (char_length(override_reason) BETWEEN 1 AND 500),
  CONSTRAINT ck_hall_ticket_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_hall_ticket_r1 CHECK (status = 'GENERATED' OR withheld_reasons IS NOT NULL),
  CONSTRAINT ck_hall_ticket_r2 CHECK ((status = 'OVERRIDDEN') = (override_by_user_id IS NOT NULL AND override_reason IS NOT NULL AND overridden_at IS NOT NULL)),
  CONSTRAINT ck_hall_ticket_r3 CHECK (status = 'WITHHELD' OR file_id IS NOT NULL),
  CONSTRAINT ck_hall_ticket_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_hall_ticket_tid UNIQUE (tenant_id, hall_ticket_id),
  CONSTRAINT uq_hall_ticket_tiid UNIQUE (tenant_id, institution_id, hall_ticket_id)
);
COMMENT ON TABLE app.hall_ticket IS 'Hall ticket; withheld for low attendance or any overdue fee, each check switchable [R2-20]; Principal override [EX-09].';
COMMENT ON COLUMN app.hall_ticket.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.hall_ticket.hall_ticket_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.hall_ticket.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.hall_ticket.exam_id IS 'Exam.';
COMMENT ON COLUMN app.hall_ticket.enrollment_id IS 'Student.';
COMMENT ON COLUMN app.hall_ticket.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.hall_ticket.withheld_reasons IS 'Reasons.';
COMMENT ON COLUMN app.hall_ticket.eligibility_checked_at IS 'Check time.';
COMMENT ON COLUMN app.hall_ticket.override_by_user_id IS 'Principal (HALL_TICKET_OVERRIDE).';
COMMENT ON COLUMN app.hall_ticket.override_reason IS 'Reason. Length 1–500.';
COMMENT ON COLUMN app.hall_ticket.overridden_at IS 'Time.';
COMMENT ON COLUMN app.hall_ticket.file_id IS 'Generated document.';
COMMENT ON COLUMN app.hall_ticket.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.hall_ticket.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.hall_ticket.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.hall_ticket.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.hall_ticket.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.marks_submission (
  tenant_id uuid NOT NULL,
  marks_submission_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  exam_id uuid NOT NULL,
  course_offering_id uuid NOT NULL,
  section_id uuid,
  subject_group_id uuid,
  entered_by_user_id uuid NOT NULL,
  source text NOT NULL,
  source_file_id uuid,
  source_note text,
  status text NOT NULL DEFAULT 'DRAFT',
  approval_request_id uuid,
  supersedes_submission_id uuid,
  submitted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_marks_submission PRIMARY KEY (tenant_id, marks_submission_id),
  CONSTRAINT ck_marks_submission_source CHECK (source IN ('TEACHER_ENTRY', 'EXTERNAL_UPLOAD')),
  CONSTRAINT ck_marks_submission_source_note CHECK (char_length(source_note) BETWEEN 1 AND 500),
  CONSTRAINT ck_marks_submission_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'HOD_APPROVED', 'CLASS_TEACHER_REVIEWED', 'PRINCIPAL_APPROVED', 'REJECTED', 'PUBLISHED', 'WITHDRAWN')),
  CONSTRAINT ck_marks_submission_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_marks_submission_r1 CHECK (num_nonnulls(section_id, subject_group_id) = 1),
  CONSTRAINT ck_marks_submission_r2 CHECK (source <> 'EXTERNAL_UPLOAD' OR source_file_id IS NOT NULL),
  CONSTRAINT ck_marks_submission_r3 CHECK (status = 'DRAFT' OR (submitted_at IS NOT NULL AND approval_request_id IS NOT NULL)),
  CONSTRAINT ck_marks_submission_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_marks_submission_tid UNIQUE (tenant_id, marks_submission_id),
  CONSTRAINT uq_marks_submission_tiid UNIQUE (tenant_id, institution_id, marks_submission_id)
);
COMMENT ON TABLE app.marks_submission IS 'Marks for one exam × offering × section/group [§7.4].';
COMMENT ON COLUMN app.marks_submission.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.marks_submission.marks_submission_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.marks_submission.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.marks_submission.exam_id IS 'Exam.';
COMMENT ON COLUMN app.marks_submission.course_offering_id IS 'Offering.';
COMMENT ON COLUMN app.marks_submission.section_id IS 'Section.';
COMMENT ON COLUMN app.marks_submission.subject_group_id IS 'Group.';
COMMENT ON COLUMN app.marks_submission.entered_by_user_id IS 'Assigned teacher / HOD for own subjects; Examination Clerk for external marks.';
COMMENT ON COLUMN app.marks_submission.source IS '[EX-01, PQ-42].';
COMMENT ON COLUMN app.marks_submission.source_file_id IS 'Required for EXTERNAL_UPLOAD [PQ-42].';
COMMENT ON COLUMN app.marks_submission.source_note IS 'e.g. external examiner reference. Length 1–500.';
COMMENT ON COLUMN app.marks_submission.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.marks_submission.approval_request_id IS 'Required once SUBMITTED.';
COMMENT ON COLUMN app.marks_submission.supersedes_submission_id IS 'Rejected submission stays locked; teacher makes a new one [R2-01].';
COMMENT ON COLUMN app.marks_submission.submitted_at IS 'Required when not DRAFT.';
COMMENT ON COLUMN app.marks_submission.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.marks_submission.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.marks_submission.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.marks_submission.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.marks_submission.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.marks_entry (
  tenant_id uuid NOT NULL,
  marks_entry_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  marks_submission_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  component_id uuid NOT NULL,
  max_marks numeric(6,2) NOT NULL,
  marks_obtained numeric(6,2),
  special_code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_marks_entry PRIMARY KEY (tenant_id, marks_entry_id),
  CONSTRAINT ck_marks_entry_max_marks CHECK (max_marks > 0),
  CONSTRAINT ck_marks_entry_marks_obtained CHECK (marks_obtained IS NULL OR marks_obtained >= 0),
  CONSTRAINT ck_marks_entry_special_code CHECK (special_code IN ('AB', 'MP', 'WH', 'MEDICAL_ABSENT')),
  CONSTRAINT ck_marks_entry_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_marks_entry_r1 CHECK (num_nonnulls(marks_obtained, special_code) = 1),
  CONSTRAINT ck_marks_entry_r2 CHECK (marks_obtained IS NULL OR marks_obtained <= max_marks),
  CONSTRAINT ck_marks_entry_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_marks_entry_tid UNIQUE (tenant_id, marks_entry_id),
  CONSTRAINT uq_marks_entry_tiid UNIQUE (tenant_id, institution_id, marks_entry_id)
);
COMMENT ON TABLE app.marks_entry IS 'Marks or special code of one student for one component.';
COMMENT ON COLUMN app.marks_entry.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.marks_entry.marks_entry_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.marks_entry.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.marks_entry.marks_submission_id IS 'Submission; edits only while DRAFT (trigger).';
COMMENT ON COLUMN app.marks_entry.enrollment_id IS 'Student in the target (trigger).';
COMMENT ON COLUMN app.marks_entry.component_id IS 'Component of the exam pattern.';
COMMENT ON COLUMN app.marks_entry.max_marks IS 'Snapshot.';
COMMENT ON COLUMN app.marks_entry.marks_obtained IS '0 … max [EX-17].';
COMMENT ON COLUMN app.marks_entry.special_code IS '[EX-02].';
COMMENT ON COLUMN app.marks_entry.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.marks_entry.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.marks_entry.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.marks_entry.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.marks_entry.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.backlog (
  tenant_id uuid NOT NULL,
  backlog_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  student_profile_id uuid NOT NULL,
  subject_id uuid NOT NULL,
  origin_result_line_id uuid NOT NULL,
  status text NOT NULL DEFAULT 'OPEN',
  cleared_result_line_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_backlog PRIMARY KEY (tenant_id, backlog_id),
  CONSTRAINT ck_backlog_status CHECK (status IN ('OPEN', 'REGISTERED', 'CLEARED')),
  CONSTRAINT ck_backlog_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_backlog_r1 CHECK ((status = 'CLEARED') = (cleared_result_line_id IS NOT NULL)),
  CONSTRAINT ck_backlog_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_backlog_tid UNIQUE (tenant_id, backlog_id),
  CONSTRAINT uq_backlog_tiid UNIQUE (tenant_id, institution_id, backlog_id)
);
COMMENT ON TABLE app.backlog IS 'Failed subject to clear [EX-08].';
COMMENT ON COLUMN app.backlog.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.backlog.backlog_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.backlog.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.backlog.student_profile_id IS 'Student.';
COMMENT ON COLUMN app.backlog.subject_id IS 'Subject.';
COMMENT ON COLUMN app.backlog.origin_result_line_id IS 'Failing line.';
COMMENT ON COLUMN app.backlog.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.backlog.cleared_result_line_id IS 'Required when CLEARED.';
COMMENT ON COLUMN app.backlog.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.backlog.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.backlog.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.backlog.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.backlog.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.supplementary_registration (
  tenant_id uuid NOT NULL,
  supplementary_registration_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  backlog_id uuid NOT NULL,
  exam_id uuid NOT NULL,
  registered_by uuid NOT NULL,
  registered_at timestamptz NOT NULL DEFAULT now(),
  status text NOT NULL DEFAULT 'REGISTERED',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_supplementary_registration PRIMARY KEY (tenant_id, supplementary_registration_id),
  CONSTRAINT ck_supplementary_registration_status CHECK (status IN ('REGISTERED', 'CANCELLED')),
  CONSTRAINT ck_supplementary_registration_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_supplementary_registration_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_supplementary_registration_tid UNIQUE (tenant_id, supplementary_registration_id),
  CONSTRAINT uq_supplementary_registration_tiid UNIQUE (tenant_id, institution_id, supplementary_registration_id)
);
COMMENT ON TABLE app.supplementary_registration IS 'Self-registration for a supplementary exam (SUPPLEMENTARY_REGISTER).';
COMMENT ON COLUMN app.supplementary_registration.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.supplementary_registration.supplementary_registration_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.supplementary_registration.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.supplementary_registration.backlog_id IS 'Backlog.';
COMMENT ON COLUMN app.supplementary_registration.exam_id IS 'Supplementary exam (trigger).';
COMMENT ON COLUMN app.supplementary_registration.registered_by IS 'The college student.';
COMMENT ON COLUMN app.supplementary_registration.registered_at IS 'Time.';
COMMENT ON COLUMN app.supplementary_registration.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.supplementary_registration.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.supplementary_registration.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.supplementary_registration.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.supplementary_registration.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.supplementary_registration.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.result (
  tenant_id uuid NOT NULL,
  result_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  exam_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  version_no integer NOT NULL DEFAULT 1,
  grading_scheme_version_id uuid NOT NULL,
  total_marks numeric(8,2),
  max_total_marks numeric(8,2),
  percentage numeric(5,2),
  overall_outcome text NOT NULL,
  semester_gpa numeric(4,2),
  cgpa numeric(4,2),
  status text NOT NULL DEFAULT 'CALCULATED',
  calculated_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_result PRIMARY KEY (tenant_id, result_id),
  CONSTRAINT ck_result_version_no CHECK (version_no >= 1),
  CONSTRAINT ck_result_total_marks CHECK (total_marks IS NULL OR total_marks >= 0),
  CONSTRAINT ck_result_max_total_marks CHECK (max_total_marks IS NULL OR max_total_marks > 0),
  CONSTRAINT ck_result_percentage CHECK (percentage IS NULL OR percentage BETWEEN 0 AND 100),
  CONSTRAINT ck_result_overall_outcome CHECK (overall_outcome IN ('PASS', 'FAIL', 'WITHHELD', 'ABSENT')),
  CONSTRAINT ck_result_semester_gpa CHECK (semester_gpa IS NULL OR semester_gpa >= 0),
  CONSTRAINT ck_result_cgpa CHECK (cgpa IS NULL OR cgpa >= 0),
  CONSTRAINT ck_result_status CHECK (status IN ('CALCULATED', 'APPROVED', 'PUBLISHED', 'WITHDRAWN', 'SUPERSEDED')),
  CONSTRAINT ck_result_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_result_r1 CHECK (total_marks IS NULL OR max_total_marks IS NULL OR total_marks <= max_total_marks),
  CONSTRAINT ck_result_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_result_tid UNIQUE (tenant_id, result_id),
  CONSTRAINT uq_result_tiid UNIQUE (tenant_id, institution_id, result_id)
);
COMMENT ON TABLE app.result IS 'Result of one student for one exam and version [PQ-38, PQ-40, PQ-43].';
COMMENT ON COLUMN app.result.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.result.result_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.result.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.result.exam_id IS 'Exam.';
COMMENT ON COLUMN app.result.enrollment_id IS 'Student.';
COMMENT ON COLUMN app.result.version_no IS 'A corrected result after withdrawal is a new version.';
COMMENT ON COLUMN app.result.grading_scheme_version_id IS 'Frozen at calculation.';
COMMENT ON COLUMN app.result.total_marks IS 'Total.';
COMMENT ON COLUMN app.result.max_total_marks IS 'Maximum.';
COMMENT ON COLUMN app.result.percentage IS 'Percentage.';
COMMENT ON COLUMN app.result.overall_outcome IS 'AB students excluded from pass % [§20].';
COMMENT ON COLUMN app.result.semester_gpa IS 'GPA.';
COMMENT ON COLUMN app.result.cgpa IS 'CGPA.';
COMMENT ON COLUMN app.result.status IS 'Published → only WITHDRAWN (trigger).';
COMMENT ON COLUMN app.result.calculated_at IS 'Calculation time.';
COMMENT ON COLUMN app.result.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.result.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.result.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.result.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.result.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.result_line (
  tenant_id uuid NOT NULL,
  result_line_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  result_id uuid NOT NULL,
  subject_id uuid NOT NULL,
  marks_obtained numeric(6,2),
  special_code text,
  grade_label text,
  grade_points numeric(4,2),
  credits numeric(4,1),
  is_pass boolean,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_result_line PRIMARY KEY (tenant_id, result_line_id),
  CONSTRAINT ck_result_line_marks_obtained CHECK (marks_obtained IS NULL OR marks_obtained >= 0),
  CONSTRAINT ck_result_line_special_code CHECK (special_code IN ('AB', 'MP', 'WH', 'MEDICAL_ABSENT')),
  CONSTRAINT ck_result_line_grade_label CHECK (char_length(grade_label) BETWEEN 1 AND 5),
  CONSTRAINT ck_result_line_grade_points CHECK (grade_points IS NULL OR grade_points >= 0),
  CONSTRAINT ck_result_line_credits CHECK (credits IS NULL OR credits >= 0),
  CONSTRAINT ck_result_line_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_result_line_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_result_line_tid UNIQUE (tenant_id, result_line_id),
  CONSTRAINT uq_result_line_tiid UNIQUE (tenant_id, institution_id, result_line_id)
);
COMMENT ON TABLE app.result_line IS 'Per-subject line of a result; absent / withheld / incomplete / exempt explicit [PQ-41] (codes for exempt and incomplete OD-27).';
COMMENT ON COLUMN app.result_line.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.result_line.result_line_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.result_line.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.result_line.result_id IS 'Result.';
COMMENT ON COLUMN app.result_line.subject_id IS 'Subject.';
COMMENT ON COLUMN app.result_line.marks_obtained IS 'Marks.';
COMMENT ON COLUMN app.result_line.special_code IS 'Code (EXEMPT / INCOMPLETE pending OD-27). (OD-27)';
COMMENT ON COLUMN app.result_line.grade_label IS 'Grade. Length 1–5.';
COMMENT ON COLUMN app.result_line.grade_points IS 'Points.';
COMMENT ON COLUMN app.result_line.credits IS 'Snapshot.';
COMMENT ON COLUMN app.result_line.is_pass IS 'NULL when withheld or absent (explicit tri-state; exception to PTD-16).';
COMMENT ON COLUMN app.result_line.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.result_line.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.result_line.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.result_line.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.result_line.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.result_publication (
  tenant_id uuid NOT NULL,
  result_publication_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  exam_id uuid NOT NULL,
  section_id uuid,
  published_by_user_id uuid NOT NULL,
  published_active_role text NOT NULL,
  publisher_basis text NOT NULL,
  published_at timestamptz NOT NULL DEFAULT now(),
  status text NOT NULL DEFAULT 'PUBLISHED',
  withdrawal_request_id uuid,
  withdrawn_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_result_publication PRIMARY KEY (tenant_id, result_publication_id),
  CONSTRAINT ck_result_publication_published_active_role CHECK (char_length(published_active_role) BETWEEN 2 AND 60),
  CONSTRAINT ck_result_publication_publisher_basis CHECK (publisher_basis IN ('CONTROLLER', 'COLLEGE_ADMIN', 'SCHOOL_PRINCIPAL', 'SCHOOL_ADMIN')),
  CONSTRAINT ck_result_publication_status CHECK (status IN ('PUBLISHED', 'WITHDRAWN')),
  CONSTRAINT ck_result_publication_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_result_publication_r1 CHECK ((status = 'WITHDRAWN') = (withdrawal_request_id IS NOT NULL AND withdrawn_at IS NOT NULL)),
  CONSTRAINT ck_result_publication_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_result_publication_tid UNIQUE (tenant_id, result_publication_id),
  CONSTRAINT uq_result_publication_tiid UNIQUE (tenant_id, institution_id, result_publication_id)
);
COMMENT ON TABLE app.result_publication IS 'Publication of results for an exam scope (unit OD-26) by the mode-correct publisher [§7.1].';
COMMENT ON COLUMN app.result_publication.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.result_publication.result_publication_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.result_publication.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.result_publication.exam_id IS 'Exam.';
COMMENT ON COLUMN app.result_publication.section_id IS 'NULL = whole exam (unit OD-26). (OD-26)';
COMMENT ON COLUMN app.result_publication.published_by_user_id IS 'Publisher.';
COMMENT ON COLUMN app.result_publication.published_active_role IS 'Active role [HI-15]. Length 2–60.';
COMMENT ON COLUMN app.result_publication.publisher_basis IS 'Mode-correct publisher (trigger checks institution mode).';
COMMENT ON COLUMN app.result_publication.published_at IS 'Time.';
COMMENT ON COLUMN app.result_publication.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.result_publication.withdrawal_request_id IS 'Approved / executed withdrawal; required when WITHDRAWN.';
COMMENT ON COLUMN app.result_publication.withdrawn_at IS 'Time.';
COMMENT ON COLUMN app.result_publication.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.result_publication.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.result_publication.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.result_publication.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.result_publication.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.result_withdrawal_request (
  tenant_id uuid NOT NULL,
  result_withdrawal_request_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  result_publication_id uuid NOT NULL,
  requested_by_user_id uuid NOT NULL,
  requested_active_role text NOT NULL,
  reason text NOT NULL,
  requires_approval boolean NOT NULL DEFAULT false,
  approval_request_id uuid,
  status text NOT NULL DEFAULT 'REQUESTED',
  executed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_result_withdrawal_request PRIMARY KEY (tenant_id, result_withdrawal_request_id),
  CONSTRAINT ck_result_withdrawal_request_requested_active_role CHECK (char_length(requested_active_role) BETWEEN 2 AND 60),
  CONSTRAINT ck_result_withdrawal_request_reason CHECK (char_length(reason) BETWEEN 1 AND 1000),
  CONSTRAINT ck_result_withdrawal_request_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'EXECUTED')),
  CONSTRAINT ck_result_withdrawal_request_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_result_withdrawal_request_r1 CHECK (NOT requires_approval OR approval_request_id IS NOT NULL),
  CONSTRAINT ck_result_withdrawal_request_r2 CHECK ((status = 'EXECUTED') = (executed_at IS NOT NULL)),
  CONSTRAINT ck_result_withdrawal_request_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_result_withdrawal_request_tid UNIQUE (tenant_id, result_withdrawal_request_id),
  CONSTRAINT uq_result_withdrawal_request_tiid UNIQUE (tenant_id, institution_id, result_withdrawal_request_id)
);
COMMENT ON TABLE app.result_withdrawal_request IS 'Withdrawal of a published result with a reason [EX-07]; College Admin withdrawals need Principal approval (RESULT_WITHDRAW_APPROVE) [Q4]. Fixes the Doc 1 / Doc 2 mismatch (finding 41 / X-6).';
COMMENT ON COLUMN app.result_withdrawal_request.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.result_withdrawal_request.result_withdrawal_request_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.result_withdrawal_request.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.result_withdrawal_request.result_publication_id IS 'Publication.';
COMMENT ON COLUMN app.result_withdrawal_request.requested_by_user_id IS 'Controller, School Principal or College Admin (RESULT_WITHDRAW).';
COMMENT ON COLUMN app.result_withdrawal_request.requested_active_role IS 'Active role. Length 2–60.';
COMMENT ON COLUMN app.result_withdrawal_request.reason IS 'Required [EX-07]. Length 1–1000.';
COMMENT ON COLUMN app.result_withdrawal_request.requires_approval IS 'True when the requester is the College Admin [Q4].';
COMMENT ON COLUMN app.result_withdrawal_request.approval_request_id IS 'Principal approval when required.';
COMMENT ON COLUMN app.result_withdrawal_request.status IS 'Without approval: REQUESTED → EXECUTED in one transaction.';
COMMENT ON COLUMN app.result_withdrawal_request.executed_at IS 'When results became WITHDRAWN and everyone was notified.';
COMMENT ON COLUMN app.result_withdrawal_request.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.result_withdrawal_request.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.result_withdrawal_request.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.result_withdrawal_request.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.result_withdrawal_request.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.fee_head (
  tenant_id uuid NOT NULL,
  fee_head_id uuid NOT NULL,
  fee_head_code text NOT NULL,
  fee_head_name text NOT NULL,
  category text NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_fee_head PRIMARY KEY (tenant_id, fee_head_id),
  CONSTRAINT ck_fee_head_fee_head_code CHECK (fee_head_code ~ '^[A-Z0-9_]{2,30}$'),
  CONSTRAINT ck_fee_head_fee_head_name CHECK (char_length(fee_head_name) BETWEEN 1 AND 100),
  CONSTRAINT ck_fee_head_category CHECK (category IN ('ACADEMIC', 'HOSTEL', 'MESS', 'TRANSPORT', 'APPLICATION', 'OTHER')),
  CONSTRAINT ck_fee_head_status CHECK (status IN ('ACTIVE', 'RETIRED')),
  CONSTRAINT ck_fee_head_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_fee_head_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_fee_head_tid UNIQUE (tenant_id, fee_head_id)
);
COMMENT ON TABLE app.fee_head IS 'Fee head; hostel, mess and transport are fee heads in the same ledger [FE-22].';
COMMENT ON COLUMN app.fee_head.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.fee_head.fee_head_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.fee_head.fee_head_code IS 'Code, unique per tenant.';
COMMENT ON COLUMN app.fee_head.fee_head_name IS 'Name. Length 1–100.';
COMMENT ON COLUMN app.fee_head.category IS 'APPLICATION used from Phase 2 admissions [FE-23].';
COMMENT ON COLUMN app.fee_head.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.fee_head.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.fee_head.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.fee_head.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.fee_head.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.fee_head.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.fee_policy (
  tenant_id uuid NOT NULL,
  fee_policy_id uuid NOT NULL,
  owner_kind text NOT NULL,
  institution_id uuid,
  version_no integer NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT',
  published_by uuid,
  published_at timestamptz,
  penalty_policy_note text,
  effective_from date NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_fee_policy PRIMARY KEY (tenant_id, fee_policy_id),
  CONSTRAINT ck_fee_policy_owner_kind CHECK (owner_kind IN ('FINANCE_DIRECTOR', 'MANAGEMENT', 'PRINCIPAL')),
  CONSTRAINT ck_fee_policy_version_no CHECK (version_no >= 1),
  CONSTRAINT ck_fee_policy_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
  CONSTRAINT ck_fee_policy_penalty_policy_note CHECK (char_length(penalty_policy_note) BETWEEN 1 AND 2000),
  CONSTRAINT ck_fee_policy_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_fee_policy_r1 CHECK ((owner_kind = 'FINANCE_DIRECTOR') = (institution_id IS NULL)),
  CONSTRAINT ck_fee_policy_r2 CHECK ((status = 'DRAFT') = (published_at IS NULL)),
  CONSTRAINT ck_fee_policy_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_fee_policy_tid UNIQUE (tenant_id, fee_policy_id)
);
COMMENT ON TABLE app.fee_policy IS 'Fee policy: Finance Director (university); Management, else Principal (standalone) [HI-11]. University Accountant enters data (FEE_POLICY_DATA_ENTER).';
COMMENT ON COLUMN app.fee_policy.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.fee_policy.fee_policy_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.fee_policy.owner_kind IS 'Owner.';
COMMENT ON COLUMN app.fee_policy.institution_id IS 'NULL = university policy; set = standalone institution. [§6.1]';
COMMENT ON COLUMN app.fee_policy.version_no IS 'Version.';
COMMENT ON COLUMN app.fee_policy.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.fee_policy.published_by IS 'Owner role holder.';
COMMENT ON COLUMN app.fee_policy.published_at IS 'Time.';
COMMENT ON COLUMN app.fee_policy.penalty_policy_note IS 'Penalty policy; no refund policy in v1. Length 1–2000.';
COMMENT ON COLUMN app.fee_policy.effective_from IS 'Start.';
COMMENT ON COLUMN app.fee_policy.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.fee_policy.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.fee_policy.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.fee_policy.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.fee_policy.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.fee_head_rule (
  tenant_id uuid NOT NULL,
  fee_head_rule_id uuid NOT NULL,
  fee_policy_id uuid NOT NULL,
  fee_head_id uuid NOT NULL,
  min_amount numeric(14,2) NOT NULL,
  max_amount numeric(14,2) NOT NULL,
  proration_mode text NOT NULL DEFAULT 'FULL',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_fee_head_rule PRIMARY KEY (tenant_id, fee_head_rule_id),
  CONSTRAINT ck_fee_head_rule_min_amount CHECK (min_amount >= 0),
  CONSTRAINT ck_fee_head_rule_max_amount CHECK (max_amount >= 0),
  CONSTRAINT ck_fee_head_rule_proration_mode CHECK (proration_mode IN ('FULL', 'PRO_RATA')),
  CONSTRAINT ck_fee_head_rule_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_fee_head_rule_r1 CHECK (min_amount <= max_amount),
  CONSTRAINT ck_fee_head_rule_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_fee_head_rule_tid UNIQUE (tenant_id, fee_head_rule_id)
);
COMMENT ON TABLE app.fee_head_rule IS 'Allowed range per fee head within a policy [R2-03].';
COMMENT ON COLUMN app.fee_head_rule.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.fee_head_rule.fee_head_rule_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.fee_head_rule.fee_policy_id IS 'Policy.';
COMMENT ON COLUMN app.fee_head_rule.fee_head_id IS 'Head.';
COMMENT ON COLUMN app.fee_head_rule.min_amount IS 'Minimum. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.fee_head_rule.max_amount IS 'Maximum. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.fee_head_rule.proration_mode IS 'Mid-year joiners [FE-03].';
COMMENT ON COLUMN app.fee_head_rule.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.fee_head_rule.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.fee_head_rule.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.fee_head_rule.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.fee_head_rule.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.fee_structure (
  tenant_id uuid NOT NULL,
  fee_structure_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  academic_session_id uuid NOT NULL,
  program_year_id uuid,
  class_id uuid,
  fee_policy_id uuid NOT NULL,
  version_no integer NOT NULL DEFAULT 1,
  status text NOT NULL DEFAULT 'DRAFT',
  approval_request_id uuid,
  applies_from_date date,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_fee_structure PRIMARY KEY (tenant_id, fee_structure_id),
  CONSTRAINT ck_fee_structure_version_no CHECK (version_no >= 1),
  CONSTRAINT ck_fee_structure_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'LIVE', 'SUPERSEDED', 'REJECTED')),
  CONSTRAINT ck_fee_structure_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_fee_structure_r1 CHECK (num_nonnulls(program_year_id, class_id) = 1),
  CONSTRAINT ck_fee_structure_r2 CHECK (status = 'DRAFT' OR approval_request_id IS NOT NULL),
  CONSTRAINT ck_fee_structure_r3 CHECK (NOT (status = 'LIVE' AND version_no > 1) OR applies_from_date IS NOT NULL),
  CONSTRAINT ck_fee_structure_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_fee_structure_tid UNIQUE (tenant_id, fee_structure_id),
  CONSTRAINT uq_fee_structure_tiid UNIQUE (tenant_id, institution_id, fee_structure_id)
);
COMMENT ON TABLE app.fee_structure IS 'Fee structure built by the Accountant within the policy; live only after Principal approval [R2-03]. Any change after DRAFT is a new version through approval (fixes finding 36 / X-1).';
COMMENT ON COLUMN app.fee_structure.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.fee_structure.fee_structure_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.fee_structure.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.fee_structure.academic_session_id IS 'Session.';
COMMENT ON COLUMN app.fee_structure.program_year_id IS 'College.';
COMMENT ON COLUMN app.fee_structure.class_id IS 'School.';
COMMENT ON COLUMN app.fee_structure.fee_policy_id IS 'Policy version.';
COMMENT ON COLUMN app.fee_structure.version_no IS 'Version.';
COMMENT ON COLUMN app.fee_structure.status IS 'REJECTED ends the request; a new version is a new row (V3.3 HTML drawing-error decision).';
COMMENT ON COLUMN app.fee_structure.approval_request_id IS 'Principal approval; required once not DRAFT.';
COMMENT ON COLUMN app.fee_structure.applies_from_date IS 'Required for LIVE version > 1; new admissions and future installments only [FE-02].';
COMMENT ON COLUMN app.fee_structure.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.fee_structure.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.fee_structure.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.fee_structure.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.fee_structure.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.fee_structure_line (
  tenant_id uuid NOT NULL,
  fee_structure_line_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  fee_structure_id uuid NOT NULL,
  fee_head_id uuid NOT NULL,
  amount numeric(14,2) NOT NULL,
  fee_exception_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_fee_structure_line PRIMARY KEY (tenant_id, fee_structure_line_id),
  CONSTRAINT ck_fee_structure_line_amount CHECK (amount >= 0),
  CONSTRAINT ck_fee_structure_line_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_fee_structure_line_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_fee_structure_line_tid UNIQUE (tenant_id, fee_structure_line_id),
  CONSTRAINT uq_fee_structure_line_tiid UNIQUE (tenant_id, institution_id, fee_structure_line_id)
);
COMMENT ON TABLE app.fee_structure_line IS 'Amount per fee head; outside the range only with an approved exception [R2-03].';
COMMENT ON COLUMN app.fee_structure_line.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.fee_structure_line.fee_structure_line_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.fee_structure_line.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.fee_structure_line.fee_structure_id IS 'Structure (must be DRAFT to change).';
COMMENT ON COLUMN app.fee_structure_line.fee_head_id IS 'Head.';
COMMENT ON COLUMN app.fee_structure_line.amount IS 'Amount. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.fee_structure_line.fee_exception_id IS 'Required (and APPROVED) when outside the policy range (trigger).';
COMMENT ON COLUMN app.fee_structure_line.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.fee_structure_line.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.fee_structure_line.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.fee_structure_line.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.fee_structure_line.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.fee_structure_installment (
  tenant_id uuid NOT NULL,
  installment_template_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  fee_structure_id uuid NOT NULL,
  installment_no smallint NOT NULL,
  due_date date NOT NULL,
  amount numeric(14,2) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_fee_structure_installment PRIMARY KEY (tenant_id, installment_template_id),
  CONSTRAINT ck_fee_structure_installment_installment_no CHECK (installment_no >= 1),
  CONSTRAINT ck_fee_structure_installment_amount CHECK (amount > 0),
  CONSTRAINT ck_fee_structure_installment_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_fee_structure_installment_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_fee_structure_installment_tid UNIQUE (tenant_id, installment_template_id),
  CONSTRAINT uq_fee_structure_installment_tiid UNIQUE (tenant_id, institution_id, installment_template_id)
);
COMMENT ON TABLE app.fee_structure_installment IS 'Installment template of a structure (full installments only [FE-04]).';
COMMENT ON COLUMN app.fee_structure_installment.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.fee_structure_installment.installment_template_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.fee_structure_installment.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.fee_structure_installment.fee_structure_id IS 'Structure.';
COMMENT ON COLUMN app.fee_structure_installment.installment_no IS 'Order.';
COMMENT ON COLUMN app.fee_structure_installment.due_date IS 'Strictly increasing with number (deferred trigger).';
COMMENT ON COLUMN app.fee_structure_installment.amount IS 'Amount. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.fee_structure_installment.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.fee_structure_installment.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.fee_structure_installment.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.fee_structure_installment.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.fee_structure_installment.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.fee_exception (
  tenant_id uuid NOT NULL,
  fee_exception_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  fee_structure_id uuid NOT NULL,
  fee_head_id uuid NOT NULL,
  requested_amount numeric(14,2) NOT NULL,
  policy_min_amount numeric(14,2) NOT NULL,
  policy_max_amount numeric(14,2) NOT NULL,
  reason text NOT NULL,
  requested_by uuid NOT NULL,
  approval_request_id uuid,
  status text NOT NULL DEFAULT 'REQUESTED',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_fee_exception PRIMARY KEY (tenant_id, fee_exception_id),
  CONSTRAINT ck_fee_exception_requested_amount CHECK (requested_amount >= 0),
  CONSTRAINT ck_fee_exception_policy_min_amount CHECK (policy_min_amount >= 0),
  CONSTRAINT ck_fee_exception_policy_max_amount CHECK (policy_max_amount >= 0),
  CONSTRAINT ck_fee_exception_reason CHECK (char_length(reason) BETWEEN 1 AND 1000),
  CONSTRAINT ck_fee_exception_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'USED')),
  CONSTRAINT ck_fee_exception_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_fee_exception_r1 CHECK (requested_amount < policy_min_amount OR requested_amount > policy_max_amount),
  CONSTRAINT ck_fee_exception_r2 CHECK (status = 'REQUESTED' OR approval_request_id IS NOT NULL),
  CONSTRAINT ck_fee_exception_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_fee_exception_tid UNIQUE (tenant_id, fee_exception_id),
  CONSTRAINT uq_fee_exception_tiid UNIQUE (tenant_id, institution_id, fee_exception_id)
);
COMMENT ON TABLE app.fee_exception IS 'Out-of-range fee: Accountant → Finance Director (standalone: Management, skipped if none) → Principal [R2-03].';
COMMENT ON COLUMN app.fee_exception.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.fee_exception.fee_exception_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.fee_exception.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.fee_exception.fee_structure_id IS 'Structure.';
COMMENT ON COLUMN app.fee_exception.fee_head_id IS 'Head.';
COMMENT ON COLUMN app.fee_exception.requested_amount IS 'Outside the range (trigger). Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.fee_exception.policy_min_amount IS 'Snapshot. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.fee_exception.policy_max_amount IS 'Snapshot. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.fee_exception.reason IS 'Reason. Length 1–1000.';
COMMENT ON COLUMN app.fee_exception.requested_by IS 'Accountant.';
COMMENT ON COLUMN app.fee_exception.approval_request_id IS 'Chain FEE_EXCEPTION.';
COMMENT ON COLUMN app.fee_exception.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.fee_exception.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.fee_exception.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.fee_exception.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.fee_exception.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.fee_exception.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.student_fee_account (
  tenant_id uuid NOT NULL,
  student_fee_account_id uuid NOT NULL,
  student_profile_id uuid NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  currency_code char(3) NOT NULL DEFAULT 'INR',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_student_fee_account PRIMARY KEY (tenant_id, student_fee_account_id),
  CONSTRAINT ck_student_fee_account_status CHECK (status IN ('ACTIVE', 'CLOSED')),
  CONSTRAINT ck_student_fee_account_currency_code CHECK (currency_code = 'INR'),
  CONSTRAINT ck_student_fee_account_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_student_fee_account_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_student_fee_account_tid UNIQUE (tenant_id, student_fee_account_id)
);
COMMENT ON TABLE app.student_fee_account IS 'One account per student for all heads and one receipt series [FE-22].';
COMMENT ON COLUMN app.student_fee_account.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.student_fee_account.student_fee_account_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.student_fee_account.student_profile_id IS 'Student.';
COMMENT ON COLUMN app.student_fee_account.status IS 'Never deleted.';
COMMENT ON COLUMN app.student_fee_account.currency_code IS 'ISO currency. INR only in v1; column kept so other countries can be added later (PR-01). [PTD-06, PR-01]';
COMMENT ON COLUMN app.student_fee_account.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.student_fee_account.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.student_fee_account.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.student_fee_account.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.student_fee_account.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.fee_assignment (
  tenant_id uuid NOT NULL,
  fee_assignment_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  student_fee_account_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  fee_structure_id uuid NOT NULL,
  proration_factor numeric(5,4) NOT NULL DEFAULT 1,
  effective_from date NOT NULL,
  effective_to date,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_fee_assignment PRIMARY KEY (tenant_id, fee_assignment_id),
  CONSTRAINT ck_fee_assignment_proration_factor CHECK (proration_factor > 0 AND proration_factor <= 1),
  CONSTRAINT ck_fee_assignment_status CHECK (status IN ('ACTIVE', 'ENDED')),
  CONSTRAINT ck_fee_assignment_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_fee_assignment_r1 CHECK (effective_to IS NULL OR effective_to >= effective_from),
  CONSTRAINT ck_fee_assignment_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_fee_assignment_tid UNIQUE (tenant_id, fee_assignment_id),
  CONSTRAINT uq_fee_assignment_tiid UNIQUE (tenant_id, institution_id, fee_assignment_id)
);
COMMENT ON TABLE app.fee_assignment IS 'Structure applied to a student enrolment; category change ends the row and opens a new one [PQ-44, PQ-45].';
COMMENT ON COLUMN app.fee_assignment.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.fee_assignment.fee_assignment_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.fee_assignment.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.fee_assignment.student_fee_account_id IS 'Account.';
COMMENT ON COLUMN app.fee_assignment.enrollment_id IS 'Enrolment.';
COMMENT ON COLUMN app.fee_assignment.fee_structure_id IS 'Must be LIVE (trigger).';
COMMENT ON COLUMN app.fee_assignment.proration_factor IS '< 1 only for PRO_RATA heads.';
COMMENT ON COLUMN app.fee_assignment.effective_from IS 'Start.';
COMMENT ON COLUMN app.fee_assignment.effective_to IS 'End.';
COMMENT ON COLUMN app.fee_assignment.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.fee_assignment.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.fee_assignment.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.fee_assignment.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.fee_assignment.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.fee_assignment.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.student_installment (
  tenant_id uuid NOT NULL,
  student_installment_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  student_fee_account_id uuid NOT NULL,
  fee_assignment_id uuid NOT NULL,
  installment_no smallint NOT NULL,
  due_date date NOT NULL,
  billed_amount numeric(14,2) NOT NULL,
  status text NOT NULL DEFAULT 'UNPAID',
  settled_by text,
  paid_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_student_installment PRIMARY KEY (tenant_id, student_installment_id),
  CONSTRAINT ck_student_installment_installment_no CHECK (installment_no >= 1),
  CONSTRAINT ck_student_installment_billed_amount CHECK (billed_amount > 0),
  CONSTRAINT ck_student_installment_status CHECK (status IN ('UNPAID', 'PAID', 'CANCELLED')),
  CONSTRAINT ck_student_installment_settled_by CHECK (settled_by IN ('PAYMENT', 'CREDIT')),
  CONSTRAINT ck_student_installment_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_student_installment_r1 CHECK ((status = 'PAID') = (paid_at IS NOT NULL AND settled_by IS NOT NULL)),
  CONSTRAINT ck_student_installment_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_student_installment_tid UNIQUE (tenant_id, student_installment_id),
  CONSTRAINT uq_student_installment_tiid UNIQUE (tenant_id, institution_id, student_installment_id)
);
COMMENT ON TABLE app.student_installment IS 'Per-student billed installment.';
COMMENT ON COLUMN app.student_installment.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.student_installment.student_installment_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.student_installment.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.student_installment.student_fee_account_id IS 'Account.';
COMMENT ON COLUMN app.student_installment.fee_assignment_id IS 'Assignment.';
COMMENT ON COLUMN app.student_installment.installment_no IS 'Number.';
COMMENT ON COLUMN app.student_installment.due_date IS 'Due; overdue = UNPAID and past due (derived).';
COMMENT ON COLUMN app.student_installment.billed_amount IS 'Fixed once billed [FE-02]. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.student_installment.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.student_installment.settled_by IS 'How it was settled.';
COMMENT ON COLUMN app.student_installment.paid_at IS 'Set with PAID.';
COMMENT ON COLUMN app.student_installment.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.student_installment.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.student_installment.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.student_installment.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.student_installment.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.ledger_entry (
  tenant_id uuid NOT NULL,
  ledger_entry_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  student_fee_account_id uuid NOT NULL,
  academic_session_id uuid NOT NULL,
  financial_year smallint NOT NULL,
  posting_date date NOT NULL,
  entry_type text NOT NULL,
  direction text NOT NULL,
  amount numeric(14,2) NOT NULL,
  currency_code char(3) NOT NULL DEFAULT 'INR',
  fee_head_id uuid,
  student_installment_id uuid,
  payment_id uuid,
  scholarship_award_id uuid,
  concession_id uuid,
  late_fee_waiver_id uuid,
  credit_application_id uuid,
  reverses_entry_id uuid,
  narrative text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_ledger_entry PRIMARY KEY (tenant_id, ledger_entry_id),
  CONSTRAINT ck_ledger_entry_financial_year CHECK (financial_year BETWEEN 2000 AND 2200),
  CONSTRAINT ck_ledger_entry_entry_type CHECK (entry_type IN ('CHARGE', 'LATE_FEE', 'PAYMENT', 'SCHOLARSHIP', 'CONCESSION', 'LATE_FEE_WAIVER', 'CREDIT_ADDED', 'CREDIT_CONSUMED', 'CREDIT_APPLIED', 'REVERSAL')),
  CONSTRAINT ck_ledger_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
  CONSTRAINT ck_ledger_entry_amount CHECK (amount > 0),
  CONSTRAINT ck_ledger_entry_currency_code CHECK (currency_code = 'INR'),
  CONSTRAINT ck_ledger_entry_narrative CHECK (char_length(narrative) BETWEEN 1 AND 500),
  CONSTRAINT ck_ledger_entry_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_ledger_entry_r1 CHECK (entry_type = 'REVERSAL' OR direction = CASE WHEN entry_type IN ('CHARGE','LATE_FEE','CREDIT_CONSUMED') THEN 'DEBIT' ELSE 'CREDIT' END),
  CONSTRAINT ck_ledger_entry_r2 CHECK (entry_type NOT IN ('CHARGE','SCHOLARSHIP','CONCESSION') OR fee_head_id IS NOT NULL),
  CONSTRAINT ck_ledger_entry_r3 CHECK (entry_type NOT IN ('PAYMENT','CREDIT_ADDED') OR payment_id IS NOT NULL),
  CONSTRAINT ck_ledger_entry_r4 CHECK (entry_type <> 'SCHOLARSHIP' OR scholarship_award_id IS NOT NULL),
  CONSTRAINT ck_ledger_entry_r5 CHECK (entry_type <> 'CONCESSION' OR concession_id IS NOT NULL),
  CONSTRAINT ck_ledger_entry_r6 CHECK (entry_type <> 'LATE_FEE_WAIVER' OR late_fee_waiver_id IS NOT NULL),
  CONSTRAINT ck_ledger_entry_r7 CHECK (entry_type NOT IN ('CREDIT_CONSUMED','CREDIT_APPLIED') OR credit_application_id IS NOT NULL),
  CONSTRAINT ck_ledger_entry_r8 CHECK ((entry_type = 'REVERSAL') = (reverses_entry_id IS NOT NULL)),
  CONSTRAINT ck_ledger_entry_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_ledger_entry_tid UNIQUE (tenant_id, ledger_entry_id),
  CONSTRAINT uq_ledger_entry_tiid UNIQUE (tenant_id, institution_id, ledger_entry_id)
);
COMMENT ON TABLE app.ledger_entry IS 'Append-only money movements; the single source of truth [PQ-45, PQ-49]. Credit application is two entries (Doc 1 C1 / PTD-57).';
COMMENT ON COLUMN app.ledger_entry.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.ledger_entry.ledger_entry_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.ledger_entry.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.ledger_entry.student_fee_account_id IS 'Account.';
COMMENT ON COLUMN app.ledger_entry.academic_session_id IS 'Session.';
COMMENT ON COLUMN app.ledger_entry.financial_year IS 'Start year of April–March year of posting_date (trigger) [FE-21].';
COMMENT ON COLUMN app.ledger_entry.posting_date IS 'Must fall in an unlocked month (trigger) [FE-20].';
COMMENT ON COLUMN app.ledger_entry.entry_type IS 'No REFUND type in v1 [R2-21].';
COMMENT ON COLUMN app.ledger_entry.direction IS 'DEBIT = owes more. Tied to entry_type by CHECK.';
COMMENT ON COLUMN app.ledger_entry.amount IS 'Amount. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.ledger_entry.currency_code IS 'ISO currency. INR only in v1; column kept so other countries can be added later (PR-01). [PTD-06, PR-01]';
COMMENT ON COLUMN app.ledger_entry.fee_head_id IS 'Required for CHARGE, SCHOLARSHIP, CONCESSION.';
COMMENT ON COLUMN app.ledger_entry.student_installment_id IS 'Installment.';
COMMENT ON COLUMN app.ledger_entry.payment_id IS 'Required for PAYMENT, CREDIT_ADDED.';
COMMENT ON COLUMN app.ledger_entry.scholarship_award_id IS 'Required for SCHOLARSHIP.';
COMMENT ON COLUMN app.ledger_entry.concession_id IS 'Required for CONCESSION.';
COMMENT ON COLUMN app.ledger_entry.late_fee_waiver_id IS 'Required for LATE_FEE_WAIVER.';
COMMENT ON COLUMN app.ledger_entry.credit_application_id IS 'Required for CREDIT_CONSUMED / CREDIT_APPLIED.';
COMMENT ON COLUMN app.ledger_entry.reverses_entry_id IS 'Required for REVERSAL; one reversal per entry.';
COMMENT ON COLUMN app.ledger_entry.narrative IS 'Text. Length 1–500.';
COMMENT ON COLUMN app.ledger_entry.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.ledger_entry.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.ledger_entry.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.ledger_entry.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.ledger_entry.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.payment (
  tenant_id uuid NOT NULL,
  payment_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  student_fee_account_id uuid NOT NULL,
  paid_by_user_id uuid,
  method text NOT NULL,
  quoted_amount numeric(14,2) NOT NULL,
  amount numeric(14,2),
  status text NOT NULL DEFAULT 'STARTED',
  request_key text NOT NULL,
  confirmed_via text,
  gateway_transaction_id uuid,
  manual_gateway_reference text,
  marked_by_user_id uuid,
  cheque_number text,
  cheque_date date,
  cheque_bank text,
  gateway_fee_amount numeric(14,2),
  gateway_fee_borne_by text,
  allocated_amount numeric(14,2),
  credit_added_amount numeric(14,2),
  amount_mismatch boolean NOT NULL DEFAULT false,
  expires_at timestamptz,
  paid_at timestamptz,
  failure_reason text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_payment PRIMARY KEY (tenant_id, payment_id),
  CONSTRAINT ck_payment_method CHECK (method IN ('GATEWAY', 'CASH', 'CHEQUE')),
  CONSTRAINT ck_payment_quoted_amount CHECK (quoted_amount > 0),
  CONSTRAINT ck_payment_amount CHECK (amount > 0),
  CONSTRAINT ck_payment_status CHECK (status IN ('STARTED', 'PENDING', 'PAID', 'FAILED', 'EXPIRED')),
  CONSTRAINT ck_payment_request_key CHECK (char_length(request_key) BETWEEN 8 AND 100),
  CONSTRAINT ck_payment_confirmed_via CHECK (confirmed_via IN ('WEBHOOK', 'SERVER_CHECK', 'MANUAL_REFERENCE', 'OFFLINE_ENTRY')),
  CONSTRAINT ck_payment_manual_gateway_reference CHECK (char_length(manual_gateway_reference) BETWEEN 4 AND 100),
  CONSTRAINT ck_payment_cheque_number CHECK (char_length(cheque_number) BETWEEN 1 AND 20),
  CONSTRAINT ck_payment_cheque_bank CHECK (char_length(cheque_bank) BETWEEN 1 AND 100),
  CONSTRAINT ck_payment_gateway_fee_amount CHECK (gateway_fee_amount >= 0),
  CONSTRAINT ck_payment_gateway_fee_borne_by CHECK (gateway_fee_borne_by IN ('INSTITUTION', 'PAYER')),
  CONSTRAINT ck_payment_allocated_amount CHECK (allocated_amount >= 0),
  CONSTRAINT ck_payment_credit_added_amount CHECK (credit_added_amount >= 0),
  CONSTRAINT ck_payment_failure_reason CHECK (char_length(failure_reason) BETWEEN 1 AND 300),
  CONSTRAINT ck_payment_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_payment_r1 CHECK (status <> 'PAID' OR (amount IS NOT NULL AND confirmed_via IS NOT NULL AND paid_at IS NOT NULL AND allocated_amount IS NOT NULL AND credit_added_amount IS NOT NULL)),
  CONSTRAINT ck_payment_r2 CHECK (status <> 'PAID' OR amount = allocated_amount + credit_added_amount),
  CONSTRAINT ck_payment_r3 CHECK (method <> 'GATEWAY' OR status <> 'PAID' OR confirmed_via IN ('WEBHOOK','SERVER_CHECK','MANUAL_REFERENCE')),
  CONSTRAINT ck_payment_r4 CHECK (method <> 'GATEWAY' OR status <> 'PAID' OR confirmed_via = 'MANUAL_REFERENCE' OR gateway_transaction_id IS NOT NULL),
  CONSTRAINT ck_payment_r5 CHECK (method = 'GATEWAY' OR (status = 'PAID' AND confirmed_via = 'OFFLINE_ENTRY' AND marked_by_user_id IS NOT NULL)),
  CONSTRAINT ck_payment_r6 CHECK ((confirmed_via = 'MANUAL_REFERENCE') = (manual_gateway_reference IS NOT NULL)),
  CONSTRAINT ck_payment_r7 CHECK ((method = 'CHEQUE') = (cheque_number IS NOT NULL AND cheque_date IS NOT NULL)),
  CONSTRAINT ck_payment_r8 CHECK (status <> 'FAILED' OR failure_reason IS NOT NULL),
  CONSTRAINT ck_payment_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_payment_tid UNIQUE (tenant_id, payment_id),
  CONSTRAINT uq_payment_tiid UNIQUE (tenant_id, institution_id, payment_id)
);
COMMENT ON TABLE app.payment IS 'One payment attempt (online, cash or cheque). Online: STARTED → PENDING → PAID / FAILED / EXPIRED; only a verified gateway confirmation marks PAID [FE-07]. Redesigned for findings 38 / 39.';
COMMENT ON COLUMN app.payment.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.payment.payment_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.payment.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.payment.student_fee_account_id IS 'One account per payment (no multi-child payment [FE-26]).';
COMMENT ON COLUMN app.payment.paid_by_user_id IS 'Student / primary guardian (online) or Accountant (offline).';
COMMENT ON COLUMN app.payment.method IS 'Method [§7.5].';
COMMENT ON COLUMN app.payment.quoted_amount IS 'Sum of the selected installments'' net due at start (payment_intent_item). Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.payment.amount IS 'Amount actually received / captured; required when PAID. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.payment.status IS 'Offline payments are inserted directly as PAID.';
COMMENT ON COLUMN app.payment.request_key IS 'Idempotency key; repeated clicks ignored [TE-08]. Length 8–100.';
COMMENT ON COLUMN app.payment.confirmed_via IS 'Required when PAID; browser success never counts [FE-07].';
COMMENT ON COLUMN app.payment.gateway_transaction_id IS 'Captured gateway transaction; required for GATEWAY + PAID.';
COMMENT ON COLUMN app.payment.manual_gateway_reference IS 'Required for MANUAL_REFERENCE [R2-33]. Length 4–100.';
COMMENT ON COLUMN app.payment.marked_by_user_id IS 'Accountant for MANUAL_REFERENCE / OFFLINE_ENTRY.';
COMMENT ON COLUMN app.payment.cheque_number IS 'CHEQUE only. Length 1–20.';
COMMENT ON COLUMN app.payment.cheque_date IS 'CHEQUE only.';
COMMENT ON COLUMN app.payment.cheque_bank IS 'CHEQUE only. Length 1–100.';
COMMENT ON COLUMN app.payment.gateway_fee_amount IS 'Gateway charge. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.payment.gateway_fee_borne_by IS 'Institution choice [FE-06] (default OD-49).';
COMMENT ON COLUMN app.payment.allocated_amount IS 'Sum of payment_allocation at PAID. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.payment.credit_added_amount IS 'amount − allocated_amount, posted as CREDIT_ADDED [FE-05, PO-A1]. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.payment.amount_mismatch IS 'True when amount ≠ quoted_amount at confirmation; covered installments settled in due-date order, rest credit, Accountant notified [PO-A1]. [PO-A1]';
COMMENT ON COLUMN app.payment.expires_at IS 'Online attempt expiry (gateway order lifetime, OD-115). (OD-115)';
COMMENT ON COLUMN app.payment.paid_at IS 'Required when PAID.';
COMMENT ON COLUMN app.payment.failure_reason IS 'Required when FAILED. Length 1–300.';
COMMENT ON COLUMN app.payment.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.payment.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.payment.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.payment.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.payment.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.payment_intent_item (
  tenant_id uuid NOT NULL,
  payment_intent_item_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  payment_id uuid NOT NULL,
  student_installment_id uuid NOT NULL,
  quoted_net_due numeric(14,2) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_payment_intent_item PRIMARY KEY (tenant_id, payment_intent_item_id),
  CONSTRAINT ck_payment_intent_item_quoted_net_due CHECK (quoted_net_due > 0),
  CONSTRAINT ck_payment_intent_item_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_payment_intent_item_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_payment_intent_item_tid UNIQUE (tenant_id, payment_intent_item_id),
  CONSTRAINT uq_payment_intent_item_tiid UNIQUE (tenant_id, institution_id, payment_intent_item_id)
);
COMMENT ON TABLE app.payment_intent_item IS 'Installments selected when a payment starts, with the net due quoted (Doc 2 D19, finding 38). Allocations are derived from these at confirmation.';
COMMENT ON COLUMN app.payment_intent_item.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.payment_intent_item.payment_intent_item_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.payment_intent_item.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.payment_intent_item.payment_id IS 'Payment.';
COMMENT ON COLUMN app.payment_intent_item.student_installment_id IS 'Installment (UNPAID at start).';
COMMENT ON COLUMN app.payment_intent_item.quoted_net_due IS 'Net due at start. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.payment_intent_item.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.payment_intent_item.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.payment_intent_item.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.payment_intent_item.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.payment_intent_item.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.gateway_order (
  tenant_id uuid NOT NULL,
  gateway_order_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  payment_id uuid NOT NULL,
  provider text NOT NULL,
  provider_order_id text NOT NULL,
  order_amount numeric(14,2) NOT NULL,
  status text NOT NULL DEFAULT 'CREATED',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_gateway_order PRIMARY KEY (tenant_id, gateway_order_id),
  CONSTRAINT ck_gateway_order_provider CHECK (provider IN ('RAZORPAY', 'STRIPE', 'PAYU')),
  CONSTRAINT ck_gateway_order_provider_order_id CHECK (char_length(provider_order_id) BETWEEN 1 AND 100),
  CONSTRAINT ck_gateway_order_order_amount CHECK (order_amount > 0),
  CONSTRAINT ck_gateway_order_status CHECK (status IN ('CREATED', 'PAID', 'EXPIRED', 'FAILED')),
  CONSTRAINT ck_gateway_order_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_gateway_order_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_gateway_order_tid UNIQUE (tenant_id, gateway_order_id),
  CONSTRAINT uq_gateway_order_tiid UNIQUE (tenant_id, institution_id, gateway_order_id)
);
COMMENT ON TABLE app.gateway_order IS 'Gateway order for an online payment (one per payment); created after the payment row commits (finding 39).';
COMMENT ON COLUMN app.gateway_order.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.gateway_order.gateway_order_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.gateway_order.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.gateway_order.payment_id IS 'Payment.';
COMMENT ON COLUMN app.gateway_order.provider IS 'Razorpay default; others alternatives only [FE-30].';
COMMENT ON COLUMN app.gateway_order.provider_order_id IS 'Provider order id; also in platform.payment_route.gateway_order_id. Length 1–100.';
COMMENT ON COLUMN app.gateway_order.order_amount IS '= payment.quoted_amount. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.gateway_order.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.gateway_order.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.gateway_order.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.gateway_order.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.gateway_order.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.gateway_order.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.gateway_transaction (
  tenant_id uuid NOT NULL,
  gateway_transaction_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  gateway_order_id uuid NOT NULL,
  provider_payment_id text NOT NULL,
  status text NOT NULL,
  amount numeric(14,2) NOT NULL,
  captured_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_gateway_transaction PRIMARY KEY (tenant_id, gateway_transaction_id),
  CONSTRAINT ck_gateway_transaction_provider_payment_id CHECK (char_length(provider_payment_id) BETWEEN 1 AND 100),
  CONSTRAINT ck_gateway_transaction_status CHECK (status IN ('AUTHORIZED', 'CAPTURED', 'FAILED')),
  CONSTRAINT ck_gateway_transaction_amount CHECK (amount > 0),
  CONSTRAINT ck_gateway_transaction_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_gateway_transaction_r1 CHECK ((status = 'CAPTURED') = (captured_at IS NOT NULL)),
  CONSTRAINT ck_gateway_transaction_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_gateway_transaction_tid UNIQUE (tenant_id, gateway_transaction_id),
  CONSTRAINT uq_gateway_transaction_tiid UNIQUE (tenant_id, institution_id, gateway_transaction_id)
);
COMMENT ON TABLE app.gateway_transaction IS 'Provider payment attempt under an order (an order can see several attempts; at most one captured).';
COMMENT ON COLUMN app.gateway_transaction.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.gateway_transaction.gateway_transaction_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.gateway_transaction.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.gateway_transaction.gateway_order_id IS 'Order.';
COMMENT ON COLUMN app.gateway_transaction.provider_payment_id IS 'Provider payment id; one receipt per id [FE-09]. Length 1–100.';
COMMENT ON COLUMN app.gateway_transaction.status IS 'Provider status.';
COMMENT ON COLUMN app.gateway_transaction.amount IS 'Amount. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.gateway_transaction.captured_at IS 'Required when CAPTURED.';
COMMENT ON COLUMN app.gateway_transaction.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.gateway_transaction.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.gateway_transaction.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.gateway_transaction.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.gateway_transaction.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.payment_gateway_event (
  tenant_id uuid NOT NULL,
  payment_gateway_event_id uuid NOT NULL,
  institution_id uuid,
  gateway_order_id uuid,
  gateway_transaction_id uuid,
  provider text NOT NULL,
  provider_event_id text NOT NULL,
  event_type text NOT NULL,
  signature_verified boolean NOT NULL DEFAULT false,
  payload jsonb NOT NULL,
  received_at timestamptz NOT NULL DEFAULT now(),
  processed_at timestamptz,
  outcome text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_payment_gateway_event PRIMARY KEY (tenant_id, payment_gateway_event_id),
  CONSTRAINT ck_payment_gateway_event_provider CHECK (provider IN ('RAZORPAY', 'STRIPE', 'PAYU')),
  CONSTRAINT ck_payment_gateway_event_provider_event_id CHECK (char_length(provider_event_id) BETWEEN 1 AND 200),
  CONSTRAINT ck_payment_gateway_event_event_type CHECK (char_length(event_type) BETWEEN 1 AND 100),
  CONSTRAINT ck_payment_gateway_event_outcome CHECK (outcome IN ('APPLIED', 'IGNORED_DUPLICATE', 'UNMATCHED', 'REJECTED_SIGNATURE', 'NO_CHANGE')),
  CONSTRAINT ck_payment_gateway_event_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_payment_gateway_event_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_payment_gateway_event_tid UNIQUE (tenant_id, payment_gateway_event_id)
);
COMMENT ON TABLE app.payment_gateway_event IS 'Raw callbacks and server checks; duplicates ignored [FE-07, FE-09].';
COMMENT ON COLUMN app.payment_gateway_event.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.payment_gateway_event.payment_gateway_event_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.payment_gateway_event.institution_id IS 'Known once matched. [§6.1]';
COMMENT ON COLUMN app.payment_gateway_event.gateway_order_id IS 'Matched order.';
COMMENT ON COLUMN app.payment_gateway_event.gateway_transaction_id IS 'Matched transaction.';
COMMENT ON COLUMN app.payment_gateway_event.provider IS 'Gateway.';
COMMENT ON COLUMN app.payment_gateway_event.provider_event_id IS 'Provider event id. Length 1–200.';
COMMENT ON COLUMN app.payment_gateway_event.event_type IS 'Provider vocabulary, as received. Length 1–100.';
COMMENT ON COLUMN app.payment_gateway_event.signature_verified IS 'Unverified events never change state.';
COMMENT ON COLUMN app.payment_gateway_event.payload IS 'Masked: no card data, no secrets (shape OD-29). (OD-29)';
COMMENT ON COLUMN app.payment_gateway_event.received_at IS 'Receipt.';
COMMENT ON COLUMN app.payment_gateway_event.processed_at IS 'Processed.';
COMMENT ON COLUMN app.payment_gateway_event.outcome IS 'Result.';
COMMENT ON COLUMN app.payment_gateway_event.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.payment_gateway_event.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.payment_gateway_event.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.payment_gateway_event.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.payment_gateway_event.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.payment_allocation (
  tenant_id uuid NOT NULL,
  payment_allocation_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  payment_id uuid NOT NULL,
  student_installment_id uuid NOT NULL,
  allocated_amount numeric(14,2) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_payment_allocation PRIMARY KEY (tenant_id, payment_allocation_id),
  CONSTRAINT ck_payment_allocation_allocated_amount CHECK (allocated_amount > 0),
  CONSTRAINT ck_payment_allocation_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_payment_allocation_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_payment_allocation_tid UNIQUE (tenant_id, payment_allocation_id),
  CONSTRAINT uq_payment_allocation_tiid UNIQUE (tenant_id, institution_id, payment_allocation_id)
);
COMMENT ON TABLE app.payment_allocation IS 'Installment settled in full by a payment [FE-04]; written only when the payment reaches PAID.';
COMMENT ON COLUMN app.payment_allocation.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.payment_allocation.payment_allocation_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.payment_allocation.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.payment_allocation.payment_id IS 'Payment.';
COMMENT ON COLUMN app.payment_allocation.student_installment_id IS 'Installment; UNPAID before (trigger); settled once.';
COMMENT ON COLUMN app.payment_allocation.allocated_amount IS '= installment net due at confirmation (trigger). Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.payment_allocation.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.payment_allocation.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.payment_allocation.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.payment_allocation.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.payment_allocation.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.credit_application (
  tenant_id uuid NOT NULL,
  credit_application_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  student_fee_account_id uuid NOT NULL,
  student_installment_id uuid NOT NULL,
  amount numeric(14,2) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_credit_application PRIMARY KEY (tenant_id, credit_application_id),
  CONSTRAINT ck_credit_application_amount CHECK (amount > 0),
  CONSTRAINT ck_credit_application_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_credit_application_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_credit_application_tid UNIQUE (tenant_id, credit_application_id),
  CONSTRAINT uq_credit_application_tiid UNIQUE (tenant_id, institution_id, credit_application_id)
);
COMMENT ON TABLE app.credit_application IS 'Installment settled from the credit balance (FE-05; paired CREDIT_CONSUMED / CREDIT_APPLIED entries). Receipt question OD-05a; part-funding not allowed (OD-05b proposed no).';
COMMENT ON COLUMN app.credit_application.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.credit_application.credit_application_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.credit_application.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.credit_application.student_fee_account_id IS 'Account.';
COMMENT ON COLUMN app.credit_application.student_installment_id IS 'Installment, settled in full.';
COMMENT ON COLUMN app.credit_application.amount IS '= net due. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.credit_application.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.credit_application.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.credit_application.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.credit_application.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.credit_application.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.reconciliation_case (
  tenant_id uuid NOT NULL,
  reconciliation_case_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  case_type text NOT NULL,
  payment_id uuid,
  payment_gateway_event_id uuid,
  status text NOT NULL DEFAULT 'OPEN',
  owner_user_id uuid,
  resolution_note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_reconciliation_case PRIMARY KEY (tenant_id, reconciliation_case_id),
  CONSTRAINT ck_reconciliation_case_case_type CHECK (case_type IN ('UNMATCHED_EVENT', 'AMOUNT_MISMATCH', 'DUPLICATE_CAPTURE', 'STATUS_DIFFERENCE', 'POST_RESTORE')),
  CONSTRAINT ck_reconciliation_case_status CHECK (status IN ('OPEN', 'ESCALATED', 'RESOLVED')),
  CONSTRAINT ck_reconciliation_case_resolution_note CHECK (char_length(resolution_note) BETWEEN 1 AND 2000),
  CONSTRAINT ck_reconciliation_case_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_reconciliation_case_r1 CHECK (status <> 'RESOLVED' OR resolution_note IS NOT NULL),
  CONSTRAINT ck_reconciliation_case_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_reconciliation_case_tid UNIQUE (tenant_id, reconciliation_case_id),
  CONSTRAINT uq_reconciliation_case_tiid UNIQUE (tenant_id, institution_id, reconciliation_case_id)
);
COMMENT ON TABLE app.reconciliation_case IS 'Payment mismatch needing a person: unmatched event, amount mismatch (PO-A1), duplicate capture, post-restore gap [PQ-49]. Resolving action OD-133.';
COMMENT ON COLUMN app.reconciliation_case.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.reconciliation_case.reconciliation_case_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.reconciliation_case.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.reconciliation_case.case_type IS 'Kind.';
COMMENT ON COLUMN app.reconciliation_case.payment_id IS 'Payment.';
COMMENT ON COLUMN app.reconciliation_case.payment_gateway_event_id IS 'Event.';
COMMENT ON COLUMN app.reconciliation_case.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.reconciliation_case.owner_user_id IS 'Accountant (action OD-133). (OD-133)';
COMMENT ON COLUMN app.reconciliation_case.resolution_note IS 'Required when RESOLVED. Length 1–2000.';
COMMENT ON COLUMN app.reconciliation_case.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.reconciliation_case.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.reconciliation_case.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.reconciliation_case.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.reconciliation_case.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.receipt_sequence (
  tenant_id uuid NOT NULL,
  receipt_sequence_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  financial_year smallint NOT NULL,
  prefix text NOT NULL,
  last_number integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_receipt_sequence PRIMARY KEY (tenant_id, receipt_sequence_id),
  CONSTRAINT ck_receipt_sequence_financial_year CHECK (financial_year BETWEEN 2000 AND 2200),
  CONSTRAINT ck_receipt_sequence_prefix CHECK (char_length(prefix) <= 10),
  CONSTRAINT ck_receipt_sequence_last_number CHECK (last_number >= 0),
  CONSTRAINT ck_receipt_sequence_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_receipt_sequence_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_receipt_sequence_tid UNIQUE (tenant_id, receipt_sequence_id),
  CONSTRAINT uq_receipt_sequence_tiid UNIQUE (tenant_id, institution_id, receipt_sequence_id)
);
COMMENT ON TABLE app.receipt_sequence IS 'Gap-free receipt counter per institution and financial year [FE-13].';
COMMENT ON COLUMN app.receipt_sequence.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.receipt_sequence.receipt_sequence_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.receipt_sequence.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.receipt_sequence.financial_year IS 'Year.';
COMMENT ON COLUMN app.receipt_sequence.prefix IS 'Configurable prefix. Length 0–10.';
COMMENT ON COLUMN app.receipt_sequence.last_number IS 'Taken with UPDATE … RETURNING in the receipt transaction.';
COMMENT ON COLUMN app.receipt_sequence.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.receipt_sequence.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.receipt_sequence.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.receipt_sequence.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.receipt_sequence.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.receipt (
  tenant_id uuid NOT NULL,
  receipt_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  payment_id uuid NOT NULL,
  financial_year smallint NOT NULL,
  receipt_number integer NOT NULL,
  receipt_label text NOT NULL,
  amount numeric(14,2) NOT NULL,
  status text NOT NULL DEFAULT 'ISSUED',
  void_reason text,
  void_approval_request_id uuid,
  voided_at timestamptz,
  replaced_by_receipt_id uuid,
  issued_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_receipt PRIMARY KEY (tenant_id, receipt_id),
  CONSTRAINT ck_receipt_receipt_number CHECK (receipt_number > 0),
  CONSTRAINT ck_receipt_receipt_label CHECK (char_length(receipt_label) BETWEEN 1 AND 40),
  CONSTRAINT ck_receipt_amount CHECK (amount > 0),
  CONSTRAINT ck_receipt_status CHECK (status IN ('ISSUED', 'VOID')),
  CONSTRAINT ck_receipt_void_reason CHECK (char_length(void_reason) BETWEEN 1 AND 500),
  CONSTRAINT ck_receipt_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_receipt_r1 CHECK ((status = 'VOID') = (void_reason IS NOT NULL AND void_approval_request_id IS NOT NULL AND voided_at IS NOT NULL)),
  CONSTRAINT ck_receipt_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_receipt_tid UNIQUE (tenant_id, receipt_id),
  CONSTRAINT uq_receipt_tiid UNIQUE (tenant_id, institution_id, receipt_id)
);
COMMENT ON TABLE app.receipt IS 'Receipt; never edited; void keeps its number [FE-12, FE-13]; one issued per payment [FE-09].';
COMMENT ON COLUMN app.receipt.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.receipt.receipt_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.receipt.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.receipt.payment_id IS 'Payment.';
COMMENT ON COLUMN app.receipt.financial_year IS 'Year.';
COMMENT ON COLUMN app.receipt.receipt_number IS 'Gap-free.';
COMMENT ON COLUMN app.receipt.receipt_label IS 'Printed label. Length 1–40.';
COMMENT ON COLUMN app.receipt.amount IS '= payment amount (trigger). Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.receipt.status IS 'Only ISSUED → VOID (trigger).';
COMMENT ON COLUMN app.receipt.void_reason IS 'Required for VOID. Length 1–500.';
COMMENT ON COLUMN app.receipt.void_approval_request_id IS 'Principal approval [FE-12].';
COMMENT ON COLUMN app.receipt.voided_at IS 'Time.';
COMMENT ON COLUMN app.receipt.replaced_by_receipt_id IS 'New receipt.';
COMMENT ON COLUMN app.receipt.issued_at IS 'Time.';
COMMENT ON COLUMN app.receipt.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.receipt.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.receipt.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.receipt.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.receipt.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.scholarship_scheme (
  tenant_id uuid NOT NULL,
  scholarship_scheme_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  fee_policy_id uuid NOT NULL,
  academic_session_id uuid NOT NULL,
  scheme_name text NOT NULL,
  eligibility_rule jsonb NOT NULL,
  benefit_type text NOT NULL,
  benefit_value numeric(14,2) NOT NULL,
  fee_head_id uuid,
  status text NOT NULL DEFAULT 'DRAFT',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_scholarship_scheme PRIMARY KEY (tenant_id, scholarship_scheme_id),
  CONSTRAINT ck_scholarship_scheme_scheme_name CHECK (char_length(scheme_name) BETWEEN 2 AND 150),
  CONSTRAINT ck_scholarship_scheme_benefit_type CHECK (benefit_type IN ('PERCENT', 'FIXED')),
  CONSTRAINT ck_scholarship_scheme_benefit_value CHECK (benefit_value > 0),
  CONSTRAINT ck_scholarship_scheme_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
  CONSTRAINT ck_scholarship_scheme_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_scholarship_scheme_r1 CHECK (benefit_type <> 'PERCENT' OR benefit_value <= 100),
  CONSTRAINT ck_scholarship_scheme_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_scholarship_scheme_tid UNIQUE (tenant_id, scholarship_scheme_id),
  CONSTRAINT uq_scholarship_scheme_tiid UNIQUE (tenant_id, institution_id, scholarship_scheme_id)
);
COMMENT ON TABLE app.scholarship_scheme IS 'Scholarship scheme (SCHOLARSHIP_MANAGE).';
COMMENT ON COLUMN app.scholarship_scheme.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.scholarship_scheme.scholarship_scheme_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.scholarship_scheme.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.scholarship_scheme.fee_policy_id IS 'Policy.';
COMMENT ON COLUMN app.scholarship_scheme.academic_session_id IS 'Session.';
COMMENT ON COLUMN app.scholarship_scheme.scheme_name IS 'Name. Length 2–150.';
COMMENT ON COLUMN app.scholarship_scheme.eligibility_rule IS 'Rule run by the Accountant (format OD-30). (OD-30)';
COMMENT ON COLUMN app.scholarship_scheme.benefit_type IS 'Type.';
COMMENT ON COLUMN app.scholarship_scheme.benefit_value IS '≤ 100 when PERCENT.';
COMMENT ON COLUMN app.scholarship_scheme.fee_head_id IS 'NULL = all heads.';
COMMENT ON COLUMN app.scholarship_scheme.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.scholarship_scheme.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.scholarship_scheme.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.scholarship_scheme.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.scholarship_scheme.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.scholarship_scheme.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.scholarship_award_list (
  tenant_id uuid NOT NULL,
  list_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  scholarship_scheme_id uuid NOT NULL,
  run_at timestamptz NOT NULL DEFAULT now(),
  status text NOT NULL DEFAULT 'DRAFT',
  approval_request_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_scholarship_award_list PRIMARY KEY (tenant_id, list_id),
  CONSTRAINT ck_scholarship_award_list_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
  CONSTRAINT ck_scholarship_award_list_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_scholarship_award_list_r1 CHECK (status = 'DRAFT' OR approval_request_id IS NOT NULL),
  CONSTRAINT ck_scholarship_award_list_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_scholarship_award_list_tid UNIQUE (tenant_id, list_id),
  CONSTRAINT uq_scholarship_award_list_tiid UNIQUE (tenant_id, institution_id, list_id)
);
COMMENT ON TABLE app.scholarship_award_list IS 'Eligibility run result; Principal approves the list [WF-31].';
COMMENT ON COLUMN app.scholarship_award_list.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.scholarship_award_list.list_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.scholarship_award_list.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.scholarship_award_list.scholarship_scheme_id IS 'Scheme.';
COMMENT ON COLUMN app.scholarship_award_list.run_at IS 'Run time.';
COMMENT ON COLUMN app.scholarship_award_list.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.scholarship_award_list.approval_request_id IS 'Principal.';
COMMENT ON COLUMN app.scholarship_award_list.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.scholarship_award_list.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.scholarship_award_list.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.scholarship_award_list.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.scholarship_award_list.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.scholarship_award (
  tenant_id uuid NOT NULL,
  scholarship_award_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  list_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  computed_amount numeric(14,2) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_scholarship_award PRIMARY KEY (tenant_id, scholarship_award_id),
  CONSTRAINT ck_scholarship_award_computed_amount CHECK (computed_amount >= 0),
  CONSTRAINT ck_scholarship_award_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_scholarship_award_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_scholarship_award_tid UNIQUE (tenant_id, scholarship_award_id),
  CONSTRAINT uq_scholarship_award_tiid UNIQUE (tenant_id, institution_id, scholarship_award_id)
);
COMMENT ON TABLE app.scholarship_award IS 'Student in an award list; SCHOLARSHIP entries written on approval.';
COMMENT ON COLUMN app.scholarship_award.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.scholarship_award.scholarship_award_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.scholarship_award.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.scholarship_award.list_id IS 'List.';
COMMENT ON COLUMN app.scholarship_award.enrollment_id IS 'Student.';
COMMENT ON COLUMN app.scholarship_award.computed_amount IS 'Amount. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.scholarship_award.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.scholarship_award.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.scholarship_award.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.scholarship_award.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.scholarship_award.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.concession (
  tenant_id uuid NOT NULL,
  concession_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  student_fee_account_id uuid NOT NULL,
  student_installment_id uuid,
  fee_head_id uuid,
  amount numeric(14,2) NOT NULL,
  reason text NOT NULL,
  requested_by uuid NOT NULL,
  approval_request_id uuid,
  status text NOT NULL DEFAULT 'REQUESTED',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_concession PRIMARY KEY (tenant_id, concession_id),
  CONSTRAINT ck_concession_amount CHECK (amount > 0),
  CONSTRAINT ck_concession_reason CHECK (char_length(reason) BETWEEN 1 AND 1000),
  CONSTRAINT ck_concession_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'APPLIED')),
  CONSTRAINT ck_concession_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_concession_r1 CHECK (status = 'REQUESTED' OR approval_request_id IS NOT NULL),
  CONSTRAINT ck_concession_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_concession_tid UNIQUE (tenant_id, concession_id),
  CONSTRAINT uq_concession_tiid UNIQUE (tenant_id, institution_id, concession_id)
);
COMMENT ON TABLE app.concession IS 'One-off discount for one student, Principal approval [FE-17].';
COMMENT ON COLUMN app.concession.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.concession.concession_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.concession.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.concession.student_fee_account_id IS 'Account.';
COMMENT ON COLUMN app.concession.student_installment_id IS 'Installment.';
COMMENT ON COLUMN app.concession.fee_head_id IS 'Head.';
COMMENT ON COLUMN app.concession.amount IS 'Amount. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.concession.reason IS 'Reason. Length 1–1000.';
COMMENT ON COLUMN app.concession.requested_by IS 'Accountant.';
COMMENT ON COLUMN app.concession.approval_request_id IS 'Principal.';
COMMENT ON COLUMN app.concession.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.concession.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.concession.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.concession.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.concession.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.concession.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.late_fee_rule (
  tenant_id uuid NOT NULL,
  late_fee_rule_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  grace_days smallint NOT NULL DEFAULT 0,
  calculation_mode text NOT NULL,
  amount_or_percent numeric(14,2) NOT NULL,
  cap_amount numeric(14,2),
  effective_from date NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_late_fee_rule PRIMARY KEY (tenant_id, late_fee_rule_id),
  CONSTRAINT ck_late_fee_rule_grace_days CHECK (grace_days >= 0),
  CONSTRAINT ck_late_fee_rule_calculation_mode CHECK (calculation_mode IN ('FLAT', 'PERCENT_OF_DUE', 'PER_DAY')),
  CONSTRAINT ck_late_fee_rule_amount_or_percent CHECK (amount_or_percent > 0),
  CONSTRAINT ck_late_fee_rule_cap_amount CHECK (cap_amount > 0),
  CONSTRAINT ck_late_fee_rule_status CHECK (status IN ('ACTIVE', 'RETIRED')),
  CONSTRAINT ck_late_fee_rule_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_late_fee_rule_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_late_fee_rule_tid UNIQUE (tenant_id, late_fee_rule_id),
  CONSTRAINT uq_late_fee_rule_tiid UNIQUE (tenant_id, institution_id, late_fee_rule_id)
);
COMMENT ON TABLE app.late_fee_rule IS 'Late-fee rule (LATE_FEE_RULES_SET); fields proposed (OD-28).';
COMMENT ON COLUMN app.late_fee_rule.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.late_fee_rule.late_fee_rule_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.late_fee_rule.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.late_fee_rule.grace_days IS 'Grace.';
COMMENT ON COLUMN app.late_fee_rule.calculation_mode IS 'Proposed (OD-28). (OD-28)';
COMMENT ON COLUMN app.late_fee_rule.amount_or_percent IS 'Value.';
COMMENT ON COLUMN app.late_fee_rule.cap_amount IS 'Cap. Money, INR in v1 (PTD-06).';
COMMENT ON COLUMN app.late_fee_rule.effective_from IS 'Start.';
COMMENT ON COLUMN app.late_fee_rule.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.late_fee_rule.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.late_fee_rule.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.late_fee_rule.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.late_fee_rule.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.late_fee_rule.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.late_fee_waiver (
  tenant_id uuid NOT NULL,
  late_fee_waiver_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  late_fee_ledger_entry_id uuid NOT NULL,
  reason text NOT NULL,
  requested_by uuid NOT NULL,
  approval_request_id uuid,
  status text NOT NULL DEFAULT 'REQUESTED',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_late_fee_waiver PRIMARY KEY (tenant_id, late_fee_waiver_id),
  CONSTRAINT ck_late_fee_waiver_reason CHECK (char_length(reason) BETWEEN 1 AND 500),
  CONSTRAINT ck_late_fee_waiver_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED')),
  CONSTRAINT ck_late_fee_waiver_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_late_fee_waiver_r1 CHECK (status = 'REQUESTED' OR approval_request_id IS NOT NULL),
  CONSTRAINT ck_late_fee_waiver_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_late_fee_waiver_tid UNIQUE (tenant_id, late_fee_waiver_id),
  CONSTRAINT uq_late_fee_waiver_tiid UNIQUE (tenant_id, institution_id, late_fee_waiver_id)
);
COMMENT ON TABLE app.late_fee_waiver IS 'Waiver of one late-fee entry, Principal approval [FE-19].';
COMMENT ON COLUMN app.late_fee_waiver.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.late_fee_waiver.late_fee_waiver_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.late_fee_waiver.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.late_fee_waiver.late_fee_ledger_entry_id IS 'A LATE_FEE entry; waived once.';
COMMENT ON COLUMN app.late_fee_waiver.reason IS 'Reason. Length 1–500.';
COMMENT ON COLUMN app.late_fee_waiver.requested_by IS 'Accountant.';
COMMENT ON COLUMN app.late_fee_waiver.approval_request_id IS 'Principal.';
COMMENT ON COLUMN app.late_fee_waiver.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.late_fee_waiver.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.late_fee_waiver.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.late_fee_waiver.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.late_fee_waiver.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.late_fee_waiver.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.financial_period_lock (
  tenant_id uuid NOT NULL,
  period_lock_id uuid NOT NULL,
  institution_id uuid,
  period_month date NOT NULL,
  lock_state text NOT NULL,
  locked_by uuid NOT NULL,
  locked_at timestamptz NOT NULL,
  unlock_reason text,
  unlock_approval_request_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_financial_period_lock PRIMARY KEY (tenant_id, period_lock_id),
  CONSTRAINT ck_financial_period_lock_period_month CHECK (period_month = date_trunc('month', period_month)::date),
  CONSTRAINT ck_financial_period_lock_lock_state CHECK (lock_state IN ('LOCKED', 'UNLOCK_REQUESTED', 'UNLOCKED')),
  CONSTRAINT ck_financial_period_lock_unlock_reason CHECK (char_length(unlock_reason) BETWEEN 1 AND 500),
  CONSTRAINT ck_financial_period_lock_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_financial_period_lock_r1 CHECK (lock_state = 'LOCKED' OR unlock_reason IS NOT NULL),
  CONSTRAINT ck_financial_period_lock_r2 CHECK (lock_state <> 'UNLOCKED' OR unlock_approval_request_id IS NOT NULL),
  CONSTRAINT ck_financial_period_lock_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_financial_period_lock_tid UNIQUE (tenant_id, period_lock_id)
);
COMMENT ON TABLE app.financial_period_lock IS 'Locked month; unlock needs Principal (University Accountant: Finance Director) approval [FE-20, Q5].';
COMMENT ON COLUMN app.financial_period_lock.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.financial_period_lock.period_lock_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.financial_period_lock.institution_id IS 'NULL = university-level (University Accountant). [§6.1]';
COMMENT ON COLUMN app.financial_period_lock.period_month IS 'First day of month.';
COMMENT ON COLUMN app.financial_period_lock.lock_state IS 'State (relock rule OD-31). (OD-31)';
COMMENT ON COLUMN app.financial_period_lock.locked_by IS 'Accountant.';
COMMENT ON COLUMN app.financial_period_lock.locked_at IS 'Time.';
COMMENT ON COLUMN app.financial_period_lock.unlock_reason IS 'Required for UNLOCK_REQUESTED. Length 1–500.';
COMMENT ON COLUMN app.financial_period_lock.unlock_approval_request_id IS 'Required to reach UNLOCKED.';
COMMENT ON COLUMN app.financial_period_lock.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.financial_period_lock.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.financial_period_lock.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.financial_period_lock.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.financial_period_lock.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.financial_period_lock_event (
  tenant_id uuid NOT NULL,
  financial_period_lock_event_id uuid NOT NULL,
  institution_id uuid,
  period_lock_id uuid NOT NULL,
  event_type text NOT NULL,
  actor_user_id uuid NOT NULL,
  note text,
  occurred_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_financial_period_lock_event PRIMARY KEY (tenant_id, financial_period_lock_event_id),
  CONSTRAINT ck_financial_period_lock_event_event_type CHECK (event_type IN ('LOCK', 'UNLOCK_REQUEST', 'UNLOCK_APPROVED', 'UNLOCK_REJECTED', 'RELOCK')),
  CONSTRAINT ck_financial_period_lock_event_note CHECK (char_length(note) BETWEEN 1 AND 500),
  CONSTRAINT ck_financial_period_lock_event_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_financial_period_lock_event_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_financial_period_lock_event_tid UNIQUE (tenant_id, financial_period_lock_event_id)
);
COMMENT ON TABLE app.financial_period_lock_event IS 'Append-only history of month locks.';
COMMENT ON COLUMN app.financial_period_lock_event.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.financial_period_lock_event.financial_period_lock_event_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.financial_period_lock_event.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.financial_period_lock_event.period_lock_id IS 'Lock.';
COMMENT ON COLUMN app.financial_period_lock_event.event_type IS 'Event.';
COMMENT ON COLUMN app.financial_period_lock_event.actor_user_id IS 'Actor.';
COMMENT ON COLUMN app.financial_period_lock_event.note IS 'Reason / remark. Length 1–500.';
COMMENT ON COLUMN app.financial_period_lock_event.occurred_at IS 'Time.';
COMMENT ON COLUMN app.financial_period_lock_event.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.financial_period_lock_event.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.financial_period_lock_event.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.financial_period_lock_event.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.financial_period_lock_event.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.assignment (
  tenant_id uuid NOT NULL,
  assignment_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  course_offering_id uuid NOT NULL,
  section_id uuid,
  subject_group_id uuid,
  created_by_user_id uuid NOT NULL,
  assignment_kind text NOT NULL,
  title text NOT NULL,
  instructions text NOT NULL,
  due_at timestamptz NOT NULL,
  max_marks numeric(6,2),
  allowed_file_types text[],
  status text NOT NULL DEFAULT 'DRAFT',
  closed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_assignment PRIMARY KEY (tenant_id, assignment_id),
  CONSTRAINT ck_assignment_assignment_kind CHECK (assignment_kind IN ('ASSIGNMENT', 'HOMEWORK')),
  CONSTRAINT ck_assignment_title CHECK (char_length(title) BETWEEN 1 AND 200),
  CONSTRAINT ck_assignment_instructions CHECK (char_length(instructions) BETWEEN 1 AND 5000),
  CONSTRAINT ck_assignment_max_marks CHECK (max_marks IS NULL OR max_marks > 0),
  CONSTRAINT ck_assignment_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
  CONSTRAINT ck_assignment_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_assignment_r1 CHECK (num_nonnulls(section_id, subject_group_id) = 1),
  CONSTRAINT ck_assignment_r2 CHECK ((assignment_kind = 'ASSIGNMENT') = (allowed_file_types IS NOT NULL)),
  CONSTRAINT ck_assignment_r3 CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
  CONSTRAINT ck_assignment_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_assignment_tid UNIQUE (tenant_id, assignment_id),
  CONSTRAINT uq_assignment_tiid UNIQUE (tenant_id, institution_id, assignment_id)
);
COMMENT ON TABLE app.assignment IS 'Assignment (college, students upload) or homework (school, teacher marks status) [MD-01..04].';
COMMENT ON COLUMN app.assignment.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.assignment.assignment_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.assignment.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.assignment.course_offering_id IS 'Offering.';
COMMENT ON COLUMN app.assignment.section_id IS 'Section.';
COMMENT ON COLUMN app.assignment.subject_group_id IS 'Group.';
COMMENT ON COLUMN app.assignment.created_by_user_id IS 'Assigned teacher (service via subject_allocation).';
COMMENT ON COLUMN app.assignment.assignment_kind IS 'Must match institution type (trigger).';
COMMENT ON COLUMN app.assignment.title IS 'Title. Length 1–200.';
COMMENT ON COLUMN app.assignment.instructions IS 'Instructions. Length 1–5000.';
COMMENT ON COLUMN app.assignment.due_at IS 'Due.';
COMMENT ON COLUMN app.assignment.max_marks IS 'Optional.';
COMMENT ON COLUMN app.assignment.allowed_file_types IS 'Required for ASSIGNMENT from the safe list [MD-02, TE-11].';
COMMENT ON COLUMN app.assignment.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.assignment.closed_at IS 'Late allowed until closed [MD-01].';
COMMENT ON COLUMN app.assignment.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.assignment.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.assignment.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.assignment.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.assignment.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.assignment_submission (
  tenant_id uuid NOT NULL,
  submission_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  assignment_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  submitted_at timestamptz NOT NULL DEFAULT now(),
  is_late boolean NOT NULL DEFAULT false,
  status text NOT NULL DEFAULT 'SUBMITTED',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_assignment_submission PRIMARY KEY (tenant_id, submission_id),
  CONSTRAINT ck_assignment_submission_status CHECK (status IN ('SUBMITTED', 'EVALUATED')),
  CONSTRAINT ck_assignment_submission_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_assignment_submission_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_assignment_submission_tid UNIQUE (tenant_id, submission_id),
  CONSTRAINT uq_assignment_submission_tiid UNIQUE (tenant_id, institution_id, submission_id)
);
COMMENT ON TABLE app.assignment_submission IS 'College student submission (one per student; re-submission OD-37).';
COMMENT ON COLUMN app.assignment_submission.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.assignment_submission.submission_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.assignment_submission.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.assignment_submission.assignment_id IS 'Assignment (must be PUBLISHED, trigger).';
COMMENT ON COLUMN app.assignment_submission.enrollment_id IS 'Student in the target.';
COMMENT ON COLUMN app.assignment_submission.submitted_at IS 'Time.';
COMMENT ON COLUMN app.assignment_submission.is_late IS 'submitted_at > due_at (trigger).';
COMMENT ON COLUMN app.assignment_submission.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.assignment_submission.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.assignment_submission.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.assignment_submission.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.assignment_submission.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.assignment_submission.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.assignment_submission_file (
  tenant_id uuid NOT NULL,
  assignment_submission_file_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  submission_id uuid NOT NULL,
  file_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_assignment_submission_file PRIMARY KEY (tenant_id, assignment_submission_file_id),
  CONSTRAINT ck_assignment_submission_file_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_assignment_submission_file_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_assignment_submission_file_tid UNIQUE (tenant_id, assignment_submission_file_id),
  CONSTRAINT uq_assignment_submission_file_tiid UNIQUE (tenant_id, institution_id, assignment_submission_file_id)
);
COMMENT ON TABLE app.assignment_submission_file IS 'File of a submission (type must be allowed).';
COMMENT ON COLUMN app.assignment_submission_file.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.assignment_submission_file.assignment_submission_file_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.assignment_submission_file.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.assignment_submission_file.submission_id IS 'Submission.';
COMMENT ON COLUMN app.assignment_submission_file.file_id IS 'File.';
COMMENT ON COLUMN app.assignment_submission_file.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.assignment_submission_file.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.assignment_submission_file.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.assignment_submission_file.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.assignment_submission_file.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.assignment_feedback (
  tenant_id uuid NOT NULL,
  feedback_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  submission_id uuid NOT NULL,
  marks_awarded numeric(6,2),
  feedback_text text,
  evaluated_by uuid NOT NULL,
  evaluated_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_assignment_feedback PRIMARY KEY (tenant_id, feedback_id),
  CONSTRAINT ck_assignment_feedback_marks_awarded CHECK (marks_awarded IS NULL OR marks_awarded >= 0),
  CONSTRAINT ck_assignment_feedback_feedback_text CHECK (feedback_text IS NULL OR char_length(feedback_text) <= 5000),
  CONSTRAINT ck_assignment_feedback_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_assignment_feedback_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_assignment_feedback_tid UNIQUE (tenant_id, feedback_id),
  CONSTRAINT uq_assignment_feedback_tiid UNIQUE (tenant_id, institution_id, feedback_id)
);
COMMENT ON TABLE app.assignment_feedback IS 'Teacher marks and feedback (ASSIGNMENT_EVALUATE).';
COMMENT ON COLUMN app.assignment_feedback.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.assignment_feedback.feedback_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.assignment_feedback.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.assignment_feedback.submission_id IS 'Submission (one feedback).';
COMMENT ON COLUMN app.assignment_feedback.marks_awarded IS '≤ assignment max (trigger).';
COMMENT ON COLUMN app.assignment_feedback.feedback_text IS 'Feedback. Length 0–5000.';
COMMENT ON COLUMN app.assignment_feedback.evaluated_by IS 'Teacher.';
COMMENT ON COLUMN app.assignment_feedback.evaluated_at IS 'Time.';
COMMENT ON COLUMN app.assignment_feedback.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.assignment_feedback.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.assignment_feedback.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.assignment_feedback.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.assignment_feedback.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.homework_status (
  tenant_id uuid NOT NULL,
  homework_status_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  assignment_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  status text NOT NULL,
  teacher_remark text,
  marked_by uuid NOT NULL,
  marked_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_homework_status PRIMARY KEY (tenant_id, homework_status_id),
  CONSTRAINT ck_homework_status_status CHECK (status IN ('COMPLETED', 'PENDING', 'NOT_SUBMITTED')),
  CONSTRAINT ck_homework_status_teacher_remark CHECK (char_length(teacher_remark) BETWEEN 1 AND 1000),
  CONSTRAINT ck_homework_status_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_homework_status_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_homework_status_tid UNIQUE (tenant_id, homework_status_id),
  CONSTRAINT uq_homework_status_tiid UNIQUE (tenant_id, institution_id, homework_status_id)
);
COMMENT ON TABLE app.homework_status IS 'School homework status per student [MD-04].';
COMMENT ON COLUMN app.homework_status.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.homework_status.homework_status_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.homework_status.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.homework_status.assignment_id IS 'HOMEWORK only (trigger).';
COMMENT ON COLUMN app.homework_status.enrollment_id IS 'Student.';
COMMENT ON COLUMN app.homework_status.status IS 'Status.';
COMMENT ON COLUMN app.homework_status.teacher_remark IS 'Remark. Length 1–1000.';
COMMENT ON COLUMN app.homework_status.marked_by IS 'Teacher.';
COMMENT ON COLUMN app.homework_status.marked_at IS 'Time.';
COMMENT ON COLUMN app.homework_status.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.homework_status.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.homework_status.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.homework_status.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.homework_status.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.study_material (
  tenant_id uuid NOT NULL,
  study_material_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  course_offering_id uuid NOT NULL,
  section_id uuid,
  subject_group_id uuid,
  title text NOT NULL,
  description text,
  file_id uuid NOT NULL,
  uploaded_by uuid NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_study_material PRIMARY KEY (tenant_id, study_material_id),
  CONSTRAINT ck_study_material_title CHECK (char_length(title) BETWEEN 1 AND 200),
  CONSTRAINT ck_study_material_description CHECK (description IS NULL OR char_length(description) <= 2000),
  CONSTRAINT ck_study_material_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  CONSTRAINT ck_study_material_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_study_material_r1 CHECK (num_nonnulls(section_id, subject_group_id) = 1),
  CONSTRAINT ck_study_material_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_study_material_tid UNIQUE (tenant_id, study_material_id),
  CONSTRAINT uq_study_material_tiid UNIQUE (tenant_id, institution_id, study_material_id)
);
COMMENT ON TABLE app.study_material IS 'Study material (STUDY_MATERIAL_UPLOAD); no download tracking (later phase [OB-14]).';
COMMENT ON COLUMN app.study_material.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.study_material.study_material_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.study_material.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.study_material.course_offering_id IS 'Offering.';
COMMENT ON COLUMN app.study_material.section_id IS 'Section.';
COMMENT ON COLUMN app.study_material.subject_group_id IS 'Group.';
COMMENT ON COLUMN app.study_material.title IS 'Title. Length 1–200.';
COMMENT ON COLUMN app.study_material.description IS 'Text. Length 0–2000.';
COMMENT ON COLUMN app.study_material.file_id IS 'File.';
COMMENT ON COLUMN app.study_material.uploaded_by IS 'Teacher / HOD.';
COMMENT ON COLUMN app.study_material.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.study_material.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.study_material.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.study_material.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.study_material.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.study_material.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.notice (
  tenant_id uuid NOT NULL,
  notice_id uuid NOT NULL,
  notice_level text NOT NULL,
  institution_id uuid,
  department_id uuid,
  section_id uuid,
  notice_kind text NOT NULL,
  title text NOT NULL,
  body text NOT NULL,
  authored_by_user_id uuid NOT NULL,
  authored_active_role text NOT NULL,
  publish_path text NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT',
  approval_request_id uuid,
  published_at timestamptz,
  published_by_user_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_notice PRIMARY KEY (tenant_id, notice_id),
  CONSTRAINT ck_notice_notice_level CHECK (notice_level IN ('UNIVERSITY', 'INSTITUTION', 'DEPARTMENT', 'CLASS')),
  CONSTRAINT ck_notice_notice_kind CHECK (notice_kind IN ('GENERAL', 'FINANCIAL', 'PLACEMENT', 'MEETING', 'INSTRUCTION', 'EMERGENCY')),
  CONSTRAINT ck_notice_title CHECK (char_length(title) BETWEEN 1 AND 200),
  CONSTRAINT ck_notice_body CHECK (char_length(body) BETWEEN 1 AND 10000),
  CONSTRAINT ck_notice_authored_active_role CHECK (char_length(authored_active_role) BETWEEN 2 AND 60),
  CONSTRAINT ck_notice_publish_path CHECK (publish_path IN ('DIRECT', 'APPROVAL')),
  CONSTRAINT ck_notice_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'PUBLISHED', 'REJECTED')),
  CONSTRAINT ck_notice_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_notice_r1 CHECK ((notice_level = 'UNIVERSITY') = (institution_id IS NULL)),
  CONSTRAINT ck_notice_r2 CHECK ((notice_level = 'DEPARTMENT') = (department_id IS NOT NULL)),
  CONSTRAINT ck_notice_r3 CHECK ((notice_level = 'CLASS') = (section_id IS NOT NULL)),
  CONSTRAINT ck_notice_r4 CHECK (publish_path = 'DIRECT' OR status = 'DRAFT' OR approval_request_id IS NOT NULL),
  CONSTRAINT ck_notice_r5 CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL AND published_by_user_id IS NOT NULL)),
  CONSTRAINT ck_notice_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_notice_tid UNIQUE (tenant_id, notice_id)
);
COMMENT ON TABLE app.notice IS 'Notice; Admin / Principal publish directly, others via Principal approval [§7.1, Q2].';
COMMENT ON COLUMN app.notice.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.notice.notice_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.notice.notice_level IS 'Level.';
COMMENT ON COLUMN app.notice.institution_id IS 'NULL only for UNIVERSITY. [§6.1]';
COMMENT ON COLUMN app.notice.department_id IS 'DEPARTMENT level.';
COMMENT ON COLUMN app.notice.section_id IS 'CLASS level.';
COMMENT ON COLUMN app.notice.notice_kind IS 'Kind.';
COMMENT ON COLUMN app.notice.title IS 'Only text sent by SMS [CM-15]. Length 1–200.';
COMMENT ON COLUMN app.notice.body IS 'Body. Length 1–10000.';
COMMENT ON COLUMN app.notice.authored_by_user_id IS 'Author.';
COMMENT ON COLUMN app.notice.authored_active_role IS 'Role. Length 2–60.';
COMMENT ON COLUMN app.notice.publish_path IS 'Decided by the service from the author''s actions.';
COMMENT ON COLUMN app.notice.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.notice.approval_request_id IS 'Required on the APPROVAL path.';
COMMENT ON COLUMN app.notice.published_at IS 'Time.';
COMMENT ON COLUMN app.notice.published_by_user_id IS 'Publisher.';
COMMENT ON COLUMN app.notice.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.notice.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.notice.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.notice.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.notice.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.notice_audience (
  tenant_id uuid NOT NULL,
  notice_audience_id uuid NOT NULL,
  notice_id uuid NOT NULL,
  audience_group text NOT NULL,
  role_definition_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_notice_audience PRIMARY KEY (tenant_id, notice_audience_id),
  CONSTRAINT ck_notice_audience_audience_group CHECK (audience_group IN ('STAFF', 'STUDENTS', 'PARENTS', 'ALL')),
  CONSTRAINT ck_notice_audience_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_notice_audience_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_notice_audience_tid UNIQUE (tenant_id, notice_audience_id)
);
COMMENT ON TABLE app.notice_audience IS 'Audience groups resolved on the server at publish time (PTD-47).';
COMMENT ON COLUMN app.notice_audience.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.notice_audience.notice_audience_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.notice_audience.notice_id IS 'Notice.';
COMMENT ON COLUMN app.notice_audience.audience_group IS 'Group.';
COMMENT ON COLUMN app.notice_audience.role_definition_id IS 'Narrow to one role.';
COMMENT ON COLUMN app.notice_audience.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.notice_audience.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.notice_audience.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.notice_audience.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.notice_audience.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.message_thread (
  tenant_id uuid NOT NULL,
  thread_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  thread_kind text NOT NULL,
  subject text,
  about_enrollment_id uuid,
  broadcast_section_id uuid,
  origin_broadcast_thread_id uuid,
  office_handler_user_id uuid,
  passed_to_principal_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_message_thread PRIMARY KEY (tenant_id, thread_id),
  CONSTRAINT ck_message_thread_thread_kind CHECK (thread_kind IN ('DIRECT', 'OFFICE', 'CLASS_BROADCAST')),
  CONSTRAINT ck_message_thread_subject CHECK (char_length(subject) BETWEEN 1 AND 200),
  CONSTRAINT ck_message_thread_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_message_thread_r1 CHECK ((thread_kind = 'CLASS_BROADCAST') = (broadcast_section_id IS NOT NULL)),
  CONSTRAINT ck_message_thread_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_message_thread_tid UNIQUE (tenant_id, thread_id),
  CONSTRAINT uq_message_thread_tiid UNIQUE (tenant_id, institution_id, thread_id)
);
COMMENT ON TABLE app.message_thread IS 'Private conversation [CM-01..05]; never deleted [§7.10].';
COMMENT ON COLUMN app.message_thread.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.message_thread.thread_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.message_thread.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.message_thread.thread_kind IS 'Kind.';
COMMENT ON COLUMN app.message_thread.subject IS 'Subject. Length 1–200.';
COMMENT ON COLUMN app.message_thread.about_enrollment_id IS 'Student the conversation is about.';
COMMENT ON COLUMN app.message_thread.broadcast_section_id IS 'Required for CLASS_BROADCAST (own classes [CM-05]).';
COMMENT ON COLUMN app.message_thread.origin_broadcast_thread_id IS 'Replies to a broadcast are new DIRECT threads.';
COMMENT ON COLUMN app.message_thread.office_handler_user_id IS 'Office Staff / Admin (MESSAGE_OFFICE_HANDLE).';
COMMENT ON COLUMN app.message_thread.passed_to_principal_at IS 'Parents reach the Principal only through the office [CM-03].';
COMMENT ON COLUMN app.message_thread.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.message_thread.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.message_thread.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.message_thread.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.message_thread.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.message_thread_participant (
  tenant_id uuid NOT NULL,
  message_thread_participant_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  thread_id uuid NOT NULL,
  user_account_id uuid NOT NULL,
  participant_kind text NOT NULL,
  joined_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_message_thread_participant PRIMARY KEY (tenant_id, message_thread_participant_id),
  CONSTRAINT ck_message_thread_participant_participant_kind CHECK (participant_kind IN ('SENDER', 'RECIPIENT', 'OFFICE_HANDLER', 'PRINCIPAL_VIA_OFFICE')),
  CONSTRAINT ck_message_thread_participant_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_message_thread_participant_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_message_thread_participant_tid UNIQUE (tenant_id, message_thread_participant_id),
  CONSTRAINT uq_message_thread_participant_tiid UNIQUE (tenant_id, institution_id, message_thread_participant_id)
);
COMMENT ON TABLE app.message_thread_participant IS 'Participant of a thread.';
COMMENT ON COLUMN app.message_thread_participant.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.message_thread_participant.message_thread_participant_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.message_thread_participant.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.message_thread_participant.thread_id IS 'Thread.';
COMMENT ON COLUMN app.message_thread_participant.user_account_id IS 'User.';
COMMENT ON COLUMN app.message_thread_participant.participant_kind IS 'Role in thread.';
COMMENT ON COLUMN app.message_thread_participant.joined_at IS 'Time.';
COMMENT ON COLUMN app.message_thread_participant.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.message_thread_participant.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.message_thread_participant.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.message_thread_participant.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.message_thread_participant.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.message (
  tenant_id uuid NOT NULL,
  message_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  thread_id uuid NOT NULL,
  sender_user_id uuid NOT NULL,
  sender_active_role text NOT NULL,
  body text NOT NULL,
  sent_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_message PRIMARY KEY (tenant_id, message_id),
  CONSTRAINT ck_message_sender_active_role CHECK (char_length(sender_active_role) BETWEEN 2 AND 60),
  CONSTRAINT ck_message_body CHECK (char_length(body) BETWEEN 1 AND 5000),
  CONSTRAINT ck_message_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_message_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_message_tid UNIQUE (tenant_id, message_id),
  CONSTRAINT uq_message_tiid UNIQUE (tenant_id, institution_id, message_id)
);
COMMENT ON TABLE app.message IS 'Message; no UPDATE / DELETE [§7.10]; quiet hours 21:00–07:00 refuse sends [CM-02].';
COMMENT ON COLUMN app.message.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.message.message_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.message.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.message.thread_id IS 'Thread.';
COMMENT ON COLUMN app.message.sender_user_id IS 'Sender.';
COMMENT ON COLUMN app.message.sender_active_role IS 'Role. Length 2–60.';
COMMENT ON COLUMN app.message.body IS 'Body (limit OD-45). Length 1–5000. (OD-45)';
COMMENT ON COLUMN app.message.sent_at IS 'Time.';
COMMENT ON COLUMN app.message.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.message.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.message.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.message.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.message.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.message_attachment (
  tenant_id uuid NOT NULL,
  message_attachment_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  message_id uuid NOT NULL,
  file_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_message_attachment PRIMARY KEY (tenant_id, message_attachment_id),
  CONSTRAINT ck_message_attachment_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_message_attachment_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_message_attachment_tid UNIQUE (tenant_id, message_attachment_id),
  CONSTRAINT uq_message_attachment_tiid UNIQUE (tenant_id, institution_id, message_attachment_id)
);
COMMENT ON TABLE app.message_attachment IS 'Attachment (PDF, JPG, PNG ≤ 10 MB, virus-scanned [CM-06]).';
COMMENT ON COLUMN app.message_attachment.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.message_attachment.message_attachment_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.message_attachment.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.message_attachment.message_id IS 'Message.';
COMMENT ON COLUMN app.message_attachment.file_id IS 'File with purpose MESSAGE_ATTACHMENT.';
COMMENT ON COLUMN app.message_attachment.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.message_attachment.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.message_attachment.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.message_attachment.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.message_attachment.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.notification (
  tenant_id uuid NOT NULL,
  notification_id uuid NOT NULL,
  recipient_user_id uuid NOT NULL,
  institution_id uuid,
  trigger_code text NOT NULL,
  title text NOT NULL,
  body text,
  entity_type text,
  entity_id uuid,
  notice_id uuid,
  created_by_user_id uuid,
  priority_class text NOT NULL DEFAULT 'NORMAL',
  deliver_after timestamptz,
  dedupe_key text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_notification PRIMARY KEY (tenant_id, notification_id),
  CONSTRAINT ck_notification_title CHECK (char_length(title) BETWEEN 1 AND 200),
  CONSTRAINT ck_notification_body CHECK (char_length(body) BETWEEN 1 AND 2000),
  CONSTRAINT ck_notification_entity_type CHECK (char_length(entity_type) BETWEEN 1 AND 60),
  CONSTRAINT ck_notification_priority_class CHECK (priority_class IN ('NORMAL', 'SECURITY', 'EMERGENCY')),
  CONSTRAINT ck_notification_dedupe_key CHECK (char_length(dedupe_key) BETWEEN 1 AND 200),
  CONSTRAINT ck_notification_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_notification_r1 CHECK (priority_class = 'NORMAL' OR deliver_after IS NULL),
  CONSTRAINT ck_notification_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_notification_tid UNIQUE (tenant_id, notification_id)
);
COMMENT ON TABLE app.notification IS 'Notification to one user; quiet hours hold normal alerts to 07:00 [CM-02]; users cannot switch them off [CM-14].';
COMMENT ON COLUMN app.notification.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.notification.notification_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.notification.recipient_user_id IS 'Recipient.';
COMMENT ON COLUMN app.notification.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.notification.trigger_code IS 'Trigger.';
COMMENT ON COLUMN app.notification.title IS 'SMS carries only this [CM-15]. Length 1–200.';
COMMENT ON COLUMN app.notification.body IS 'Body. Length 1–2000.';
COMMENT ON COLUMN app.notification.entity_type IS 'Deep link type. Length 1–60.';
COMMENT ON COLUMN app.notification.entity_id IS 'Deep link id.';
COMMENT ON COLUMN app.notification.notice_id IS 'Source notice.';
COMMENT ON COLUMN app.notification.created_by_user_id IS 'Sender; NULL for system alerts.';
COMMENT ON COLUMN app.notification.priority_class IS 'Class.';
COMMENT ON COLUMN app.notification.deliver_after IS 'Hold time for normal alerts in quiet hours.';
COMMENT ON COLUMN app.notification.dedupe_key IS 'Idempotent creation (PTD-41). Length 1–200.';
COMMENT ON COLUMN app.notification.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.notification.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.notification.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.notification.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.notification.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.notification_delivery (
  tenant_id uuid NOT NULL,
  delivery_id uuid NOT NULL,
  institution_id uuid,
  notification_id uuid NOT NULL,
  channel text NOT NULL,
  status text NOT NULL DEFAULT 'QUEUED',
  attempt_count smallint NOT NULL DEFAULT 0,
  next_attempt_at timestamptz,
  failure_class text,
  contact_masked text,
  billable_units smallint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_notification_delivery PRIMARY KEY (tenant_id, delivery_id),
  CONSTRAINT ck_notification_delivery_channel CHECK (channel IN ('IN_APP', 'PUSH', 'SMS', 'EMAIL')),
  CONSTRAINT ck_notification_delivery_status CHECK (status IN ('QUEUED', 'HELD', 'SENT', 'DELIVERED', 'FAILED', 'SKIPPED')),
  CONSTRAINT ck_notification_delivery_attempt_count CHECK (attempt_count >= 0),
  CONSTRAINT ck_notification_delivery_failure_class CHECK (failure_class IN ('INVALID_CONTACT', 'PROVIDER_ERROR', 'RATE_LIMITED', 'UNKNOWN')),
  CONSTRAINT ck_notification_delivery_contact_masked CHECK (char_length(contact_masked) BETWEEN 1 AND 40),
  CONSTRAINT ck_notification_delivery_billable_units CHECK (billable_units >= 0),
  CONSTRAINT ck_notification_delivery_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_notification_delivery_r1 CHECK (status <> 'FAILED' OR failure_class IS NOT NULL),
  CONSTRAINT ck_notification_delivery_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_notification_delivery_tid UNIQUE (tenant_id, delivery_id)
);
COMMENT ON TABLE app.notification_delivery IS 'Per-channel delivery of a notification [PQ-55].';
COMMENT ON COLUMN app.notification_delivery.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.notification_delivery.delivery_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.notification_delivery.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.notification_delivery.notification_id IS 'Notification.';
COMMENT ON COLUMN app.notification_delivery.channel IS 'No WHATSAPP in v1 [CM-07, PQ-54].';
COMMENT ON COLUMN app.notification_delivery.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.notification_delivery.attempt_count IS 'Attempts (limit OD-38).';
COMMENT ON COLUMN app.notification_delivery.next_attempt_at IS 'Retry time.';
COMMENT ON COLUMN app.notification_delivery.failure_class IS 'Required when FAILED.';
COMMENT ON COLUMN app.notification_delivery.contact_masked IS 'e.g. ******5678; wrong-number list [CM-17]. Length 1–40.';
COMMENT ON COLUMN app.notification_delivery.billable_units IS 'SMS segments counted into platform.tenant_usage_monthly (finding 13).';
COMMENT ON COLUMN app.notification_delivery.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.notification_delivery.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.notification_delivery.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.notification_delivery.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.notification_delivery.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.notification_delivery_attempt (
  tenant_id uuid NOT NULL,
  attempt_id uuid NOT NULL,
  institution_id uuid,
  delivery_id uuid NOT NULL,
  attempt_no smallint NOT NULL,
  attempted_at timestamptz NOT NULL DEFAULT now(),
  outcome text NOT NULL,
  provider text NOT NULL,
  provider_reference text,
  provider_code text,
  provider_message text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_notification_delivery_attempt PRIMARY KEY (tenant_id, attempt_id),
  CONSTRAINT ck_notification_delivery_attempt_attempt_no CHECK (attempt_no >= 1),
  CONSTRAINT ck_notification_delivery_attempt_outcome CHECK (outcome IN ('ACCEPTED', 'DELIVERED', 'REJECTED', 'TIMEOUT', 'ERROR')),
  CONSTRAINT ck_notification_delivery_attempt_provider CHECK (char_length(provider) BETWEEN 1 AND 60),
  CONSTRAINT ck_notification_delivery_attempt_provider_reference CHECK (char_length(provider_reference) BETWEEN 1 AND 200),
  CONSTRAINT ck_notification_delivery_attempt_provider_code CHECK (char_length(provider_code) BETWEEN 1 AND 60),
  CONSTRAINT ck_notification_delivery_attempt_provider_message CHECK (provider_message IS NULL OR char_length(provider_message) <= 500),
  CONSTRAINT ck_notification_delivery_attempt_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_notification_delivery_attempt_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_notification_delivery_attempt_tid UNIQUE (tenant_id, attempt_id)
);
COMMENT ON TABLE app.notification_delivery_attempt IS 'Append-only provider attempts [PQ-55].';
COMMENT ON COLUMN app.notification_delivery_attempt.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.notification_delivery_attempt.attempt_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.notification_delivery_attempt.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.notification_delivery_attempt.delivery_id IS 'Delivery.';
COMMENT ON COLUMN app.notification_delivery_attempt.attempt_no IS 'Number.';
COMMENT ON COLUMN app.notification_delivery_attempt.attempted_at IS 'Time.';
COMMENT ON COLUMN app.notification_delivery_attempt.outcome IS 'Outcome.';
COMMENT ON COLUMN app.notification_delivery_attempt.provider IS 'Provider. Length 1–60.';
COMMENT ON COLUMN app.notification_delivery_attempt.provider_reference IS 'Ref. Length 1–200.';
COMMENT ON COLUMN app.notification_delivery_attempt.provider_code IS 'Code. Length 1–60.';
COMMENT ON COLUMN app.notification_delivery_attempt.provider_message IS 'Masked. Length 0–500.';
COMMENT ON COLUMN app.notification_delivery_attempt.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.notification_delivery_attempt.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.notification_delivery_attempt.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.notification_delivery_attempt.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.notification_delivery_attempt.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.push_subscription (
  tenant_id uuid NOT NULL,
  push_subscription_id uuid NOT NULL,
  user_device_id uuid NOT NULL,
  endpoint_ref text NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_push_subscription PRIMARY KEY (tenant_id, push_subscription_id),
  CONSTRAINT ck_push_subscription_endpoint_ref CHECK (char_length(endpoint_ref) BETWEEN 1 AND 1000),
  CONSTRAINT ck_push_subscription_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
  CONSTRAINT ck_push_subscription_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_push_subscription_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_push_subscription_tid UNIQUE (tenant_id, push_subscription_id)
);
COMMENT ON TABLE app.push_subscription IS 'Web-push subscription of a device [OB-13].';
COMMENT ON COLUMN app.push_subscription.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.push_subscription.push_subscription_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.push_subscription.user_device_id IS 'Device.';
COMMENT ON COLUMN app.push_subscription.endpoint_ref IS 'Encrypted endpoint / reference. Length 1–1000.';
COMMENT ON COLUMN app.push_subscription.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.push_subscription.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.push_subscription.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.push_subscription.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.push_subscription.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.push_subscription.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.channel_config (
  tenant_id uuid NOT NULL,
  channel_config_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  channel text NOT NULL,
  integration_type_code text NOT NULL,
  sms_sender_id text,
  dlt_entity_id text,
  secret_ref text NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT',
  last_health_check_at timestamptz,
  last_health_outcome text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_channel_config PRIMARY KEY (tenant_id, channel_config_id),
  CONSTRAINT ck_channel_config_channel CHECK (channel IN ('SMS', 'EMAIL', 'MEETING_ZOOM', 'MEETING_GOOGLE_MEET', 'MEETING_MS_TEAMS')),
  CONSTRAINT ck_channel_config_integration_type_code CHECK (char_length(integration_type_code) BETWEEN 1 AND 60),
  CONSTRAINT ck_channel_config_sms_sender_id CHECK (char_length(sms_sender_id) BETWEEN 1 AND 20),
  CONSTRAINT ck_channel_config_dlt_entity_id CHECK (char_length(dlt_entity_id) BETWEEN 1 AND 40),
  CONSTRAINT ck_channel_config_secret_ref CHECK (char_length(secret_ref) BETWEEN 1 AND 300),
  CONSTRAINT ck_channel_config_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DISABLED')),
  CONSTRAINT ck_channel_config_last_health_outcome CHECK (last_health_outcome IN ('OK', 'FAILED')),
  CONSTRAINT ck_channel_config_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_channel_config_r1 CHECK (channel <> 'SMS' OR sms_sender_id IS NOT NULL),
  CONSTRAINT ck_channel_config_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_channel_config_tid UNIQUE (tenant_id, channel_config_id),
  CONSTRAINT uq_channel_config_tiid UNIQUE (tenant_id, institution_id, channel_config_id)
);
COMMENT ON TABLE app.channel_config IS 'SMS (DLT), email and meeting-tool configuration; secrets by reference only [CM-08, MX-02]. Read-only status view for the System Operator (INTEGRATION_SETTINGS_VIEW, V3.3).';
COMMENT ON COLUMN app.channel_config.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.channel_config.channel_config_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.channel_config.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.channel_config.channel IS 'Channel.';
COMMENT ON COLUMN app.channel_config.integration_type_code IS 'Allowed type (platform.integration_type; no cross-DB FK). Length 1–60.';
COMMENT ON COLUMN app.channel_config.sms_sender_id IS 'Required for SMS. Length 1–20.';
COMMENT ON COLUMN app.channel_config.dlt_entity_id IS 'DLT entity. Length 1–40.';
COMMENT ON COLUMN app.channel_config.secret_ref IS 'Vault reference; never returned by any API. Length 1–300.';
COMMENT ON COLUMN app.channel_config.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.channel_config.last_health_check_at IS 'Status shown read-only to the System Operator.';
COMMENT ON COLUMN app.channel_config.last_health_outcome IS 'Status.';
COMMENT ON COLUMN app.channel_config.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.channel_config.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.channel_config.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.channel_config.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.channel_config.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.sms_template (
  tenant_id uuid NOT NULL,
  sms_template_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  trigger_code text NOT NULL,
  dlt_template_id text NOT NULL,
  template_text text NOT NULL,
  status text NOT NULL DEFAULT 'PENDING_REGISTRATION',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_sms_template PRIMARY KEY (tenant_id, sms_template_id),
  CONSTRAINT ck_sms_template_dlt_template_id CHECK (char_length(dlt_template_id) BETWEEN 1 AND 40),
  CONSTRAINT ck_sms_template_template_text CHECK (char_length(template_text) BETWEEN 1 AND 500),
  CONSTRAINT ck_sms_template_status CHECK (status IN ('PENDING_REGISTRATION', 'ACTIVE', 'RETIRED')),
  CONSTRAINT ck_sms_template_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_sms_template_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_sms_template_tid UNIQUE (tenant_id, sms_template_id),
  CONSTRAINT uq_sms_template_tiid UNIQUE (tenant_id, institution_id, sms_template_id)
);
COMMENT ON TABLE app.sms_template IS 'DLT-registered SMS template (heading only [CM-15]).';
COMMENT ON COLUMN app.sms_template.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.sms_template.sms_template_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.sms_template.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.sms_template.trigger_code IS 'Trigger.';
COMMENT ON COLUMN app.sms_template.dlt_template_id IS 'DLT id. Length 1–40.';
COMMENT ON COLUMN app.sms_template.template_text IS 'Heading only. Length 1–500.';
COMMENT ON COLUMN app.sms_template.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.sms_template.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.sms_template.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.sms_template.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.sms_template.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.sms_template.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.community_post (
  tenant_id uuid NOT NULL,
  post_id uuid NOT NULL,
  institution_id uuid,
  author_user_id uuid NOT NULL,
  post_kind text NOT NULL,
  body text NOT NULL,
  publish_path text NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT',
  approval_request_id uuid,
  published_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_community_post PRIMARY KEY (tenant_id, post_id),
  CONSTRAINT ck_community_post_post_kind CHECK (post_kind IN ('EVENT_NEWS', 'ACHIEVEMENT', 'CULTURAL', 'SPORTS', 'PLACEMENT_NEWS', 'ANNOUNCEMENT')),
  CONSTRAINT ck_community_post_body CHECK (char_length(body) BETWEEN 1 AND 5000),
  CONSTRAINT ck_community_post_publish_path CHECK (publish_path IN ('DIRECT', 'APPROVAL')),
  CONSTRAINT ck_community_post_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'PUBLISHED', 'REJECTED')),
  CONSTRAINT ck_community_post_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_community_post_r1 CHECK (publish_path = 'DIRECT' OR status = 'DRAFT' OR approval_request_id IS NOT NULL),
  CONSTRAINT ck_community_post_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_community_post_tid UNIQUE (tenant_id, post_id)
);
COMMENT ON TABLE app.community_post IS 'Community post; like and comment only, no share [R2-30]; school students no access [R2-39].';
COMMENT ON COLUMN app.community_post.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.community_post.post_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.community_post.institution_id IS 'NULL = university scope (University Admin, COMMUNITY_POST_DIRECT, V3.3 #111). [§6.1]';
COMMENT ON COLUMN app.community_post.author_user_id IS 'Author.';
COMMENT ON COLUMN app.community_post.post_kind IS 'Kind.';
COMMENT ON COLUMN app.community_post.body IS 'Body. Length 1–5000.';
COMMENT ON COLUMN app.community_post.publish_path IS 'Admin / Principal / University Admin direct; others Principal approval.';
COMMENT ON COLUMN app.community_post.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.community_post.approval_request_id IS 'Required on APPROVAL path.';
COMMENT ON COLUMN app.community_post.published_at IS 'Time; approved posts notify parents.';
COMMENT ON COLUMN app.community_post.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.community_post.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.community_post.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.community_post.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.community_post.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.community_post_media (
  tenant_id uuid NOT NULL,
  community_post_media_id uuid NOT NULL,
  post_id uuid NOT NULL,
  file_id uuid NOT NULL,
  media_type text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_community_post_media PRIMARY KEY (tenant_id, community_post_media_id),
  CONSTRAINT ck_community_post_media_media_type CHECK (media_type IN ('PHOTO', 'VIDEO')),
  CONSTRAINT ck_community_post_media_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_community_post_media_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_community_post_media_tid UNIQUE (tenant_id, community_post_media_id)
);
COMMENT ON TABLE app.community_post_media IS 'Photo / video of a post (consent later phase [CM-09]; limits OD-35).';
COMMENT ON COLUMN app.community_post_media.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.community_post_media.community_post_media_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.community_post_media.post_id IS 'Post.';
COMMENT ON COLUMN app.community_post_media.file_id IS 'File.';
COMMENT ON COLUMN app.community_post_media.media_type IS 'Kind.';
COMMENT ON COLUMN app.community_post_media.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.community_post_media.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.community_post_media.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.community_post_media.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.community_post_media.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.post_comment (
  tenant_id uuid NOT NULL,
  comment_id uuid NOT NULL,
  post_id uuid NOT NULL,
  author_user_id uuid NOT NULL,
  body text NOT NULL,
  status text NOT NULL DEFAULT 'VISIBLE',
  hidden_by uuid,
  hidden_at timestamptz,
  hide_reason text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_post_comment PRIMARY KEY (tenant_id, comment_id),
  CONSTRAINT ck_post_comment_body CHECK (char_length(body) BETWEEN 1 AND 1000),
  CONSTRAINT ck_post_comment_status CHECK (status IN ('VISIBLE', 'HIDDEN')),
  CONSTRAINT ck_post_comment_hide_reason CHECK (char_length(hide_reason) BETWEEN 1 AND 500),
  CONSTRAINT ck_post_comment_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_post_comment_r1 CHECK ((status = 'HIDDEN') = (hidden_by IS NOT NULL AND hidden_at IS NOT NULL AND hide_reason IS NOT NULL)),
  CONSTRAINT ck_post_comment_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_post_comment_tid UNIQUE (tenant_id, comment_id)
);
COMMENT ON TABLE app.post_comment IS 'Comment; filtered before insert; hidden by Admin / Principal with reason [CM-10].';
COMMENT ON COLUMN app.post_comment.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.post_comment.comment_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.post_comment.post_id IS 'Post.';
COMMENT ON COLUMN app.post_comment.author_user_id IS 'Author.';
COMMENT ON COLUMN app.post_comment.body IS 'Body (limit OD-45). Length 1–1000.';
COMMENT ON COLUMN app.post_comment.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.post_comment.hidden_by IS 'Admin / Principal (COMMUNITY_COMMENT_HIDE).';
COMMENT ON COLUMN app.post_comment.hidden_at IS 'Time.';
COMMENT ON COLUMN app.post_comment.hide_reason IS 'Reason. Length 1–500.';
COMMENT ON COLUMN app.post_comment.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.post_comment.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.post_comment.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.post_comment.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.post_comment.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.post_like (
  tenant_id uuid NOT NULL,
  post_like_id uuid NOT NULL,
  post_id uuid NOT NULL,
  user_account_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_post_like PRIMARY KEY (tenant_id, post_like_id),
  CONSTRAINT ck_post_like_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_post_like_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_post_like_tid UNIQUE (tenant_id, post_like_id)
);
COMMENT ON TABLE app.post_like IS 'Like on a post.';
COMMENT ON COLUMN app.post_like.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.post_like.post_like_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.post_like.post_id IS 'Post.';
COMMENT ON COLUMN app.post_like.user_account_id IS 'User.';
COMMENT ON COLUMN app.post_like.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.post_like.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.post_like.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.post_like.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.post_like.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.event (
  tenant_id uuid NOT NULL,
  event_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  proposed_by_user_id uuid NOT NULL,
  club_name text,
  title text NOT NULL,
  description text,
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  venue text,
  status text NOT NULL DEFAULT 'PROPOSED',
  approval_request_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_event PRIMARY KEY (tenant_id, event_id),
  CONSTRAINT ck_event_club_name CHECK (char_length(club_name) BETWEEN 1 AND 100),
  CONSTRAINT ck_event_title CHECK (char_length(title) BETWEEN 1 AND 200),
  CONSTRAINT ck_event_description CHECK (description IS NULL OR char_length(description) <= 5000),
  CONSTRAINT ck_event_venue CHECK (char_length(venue) BETWEEN 1 AND 200),
  CONSTRAINT ck_event_status CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED', 'CANCELLED')),
  CONSTRAINT ck_event_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_event_r1 CHECK (ends_at >= starts_at),
  CONSTRAINT ck_event_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_event_tid UNIQUE (tenant_id, event_id),
  CONSTRAINT uq_event_tiid UNIQUE (tenant_id, institution_id, event_id)
);
COMMENT ON TABLE app.event IS 'Event proposed by staff, approved by the Principal [AP-18]; RSVP only [MD-23].';
COMMENT ON COLUMN app.event.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.event.event_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.event.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.event.proposed_by_user_id IS 'Staff proposer.';
COMMENT ON COLUMN app.event.club_name IS 'Student club (through a faculty coordinator). Length 1–100.';
COMMENT ON COLUMN app.event.title IS 'Title. Length 1–200.';
COMMENT ON COLUMN app.event.description IS 'Text. Length 0–5000.';
COMMENT ON COLUMN app.event.starts_at IS 'Start.';
COMMENT ON COLUMN app.event.ends_at IS 'End.';
COMMENT ON COLUMN app.event.venue IS 'Venue. Length 1–200.';
COMMENT ON COLUMN app.event.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.event.approval_request_id IS 'Principal approval.';
COMMENT ON COLUMN app.event.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.event.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.event.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.event.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.event.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.event_rsvp (
  tenant_id uuid NOT NULL,
  event_rsvp_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  event_id uuid NOT NULL,
  user_account_id uuid NOT NULL,
  status text NOT NULL DEFAULT 'REGISTERED',
  registered_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_event_rsvp PRIMARY KEY (tenant_id, event_rsvp_id),
  CONSTRAINT ck_event_rsvp_status CHECK (status IN ('REGISTERED', 'CANCELLED')),
  CONSTRAINT ck_event_rsvp_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_event_rsvp_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_event_rsvp_tid UNIQUE (tenant_id, event_rsvp_id),
  CONSTRAINT uq_event_rsvp_tiid UNIQUE (tenant_id, institution_id, event_rsvp_id)
);
COMMENT ON TABLE app.event_rsvp IS 'Registration for an approved event.';
COMMENT ON COLUMN app.event_rsvp.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.event_rsvp.event_rsvp_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.event_rsvp.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.event_rsvp.event_id IS 'Event (APPROVED, trigger).';
COMMENT ON COLUMN app.event_rsvp.user_account_id IS 'User.';
COMMENT ON COLUMN app.event_rsvp.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.event_rsvp.registered_at IS 'Time.';
COMMENT ON COLUMN app.event_rsvp.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.event_rsvp.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.event_rsvp.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.event_rsvp.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.event_rsvp.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.complaint (
  tenant_id uuid NOT NULL,
  complaint_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  raised_by_user_id uuid NOT NULL,
  about_enrollment_id uuid,
  description text NOT NULL,
  status text NOT NULL DEFAULT 'OPEN',
  current_owner_user_id uuid,
  escalated_to_university boolean NOT NULL DEFAULT false,
  resolved_at timestamptz,
  resolution_note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_complaint PRIMARY KEY (tenant_id, complaint_id),
  CONSTRAINT ck_complaint_description CHECK (char_length(description) BETWEEN 1 AND 5000),
  CONSTRAINT ck_complaint_status CHECK (status IN ('OPEN', 'ASSIGNED', 'ESCALATED', 'RESOLVED')),
  CONSTRAINT ck_complaint_resolution_note CHECK (char_length(resolution_note) BETWEEN 1 AND 5000),
  CONSTRAINT ck_complaint_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_complaint_r1 CHECK (status NOT IN ('ASSIGNED','ESCALATED') OR current_owner_user_id IS NOT NULL),
  CONSTRAINT ck_complaint_r2 CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL AND resolution_note IS NOT NULL)),
  CONSTRAINT ck_complaint_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_complaint_tid UNIQUE (tenant_id, complaint_id),
  CONSTRAINT uq_complaint_tiid UNIQUE (tenant_id, institution_id, complaint_id)
);
COMMENT ON TABLE app.complaint IS 'Complaint by a student or parent; no categories, time limits, anonymity, reopen or rating [CP-01..05].';
COMMENT ON COLUMN app.complaint.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.complaint.complaint_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.complaint.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.complaint.raised_by_user_id IS 'Student or parent.';
COMMENT ON COLUMN app.complaint.about_enrollment_id IS 'Student concerned.';
COMMENT ON COLUMN app.complaint.description IS 'Description. Length 1–5000.';
COMMENT ON COLUMN app.complaint.status IS 'RESOLVED is final (trigger).';
COMMENT ON COLUMN app.complaint.current_owner_user_id IS 'Required for ASSIGNED / ESCALATED.';
COMMENT ON COLUMN app.complaint.escalated_to_university IS 'Visible to the University Admin (COMPLAINT_ESCALATED_VIEW).';
COMMENT ON COLUMN app.complaint.resolved_at IS 'Time.';
COMMENT ON COLUMN app.complaint.resolution_note IS 'Note. Length 1–5000.';
COMMENT ON COLUMN app.complaint.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.complaint.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.complaint.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.complaint.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.complaint.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.complaint_action (
  tenant_id uuid NOT NULL,
  complaint_action_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  complaint_id uuid NOT NULL,
  action_type text NOT NULL,
  actor_user_id uuid NOT NULL,
  to_user_id uuid,
  note text,
  acted_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_complaint_action PRIMARY KEY (tenant_id, complaint_action_id),
  CONSTRAINT ck_complaint_action_action_type CHECK (action_type IN ('ASSIGN', 'RESOLVE', 'ESCALATE')),
  CONSTRAINT ck_complaint_action_note CHECK (char_length(note) BETWEEN 1 AND 2000),
  CONSTRAINT ck_complaint_action_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_complaint_action_r1 CHECK (action_type = 'RESOLVE' OR to_user_id IS NOT NULL),
  CONSTRAINT ck_complaint_action_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_complaint_action_tid UNIQUE (tenant_id, complaint_action_id),
  CONSTRAINT uq_complaint_action_tiid UNIQUE (tenant_id, institution_id, complaint_action_id)
);
COMMENT ON TABLE app.complaint_action IS 'Append-only complaint history.';
COMMENT ON COLUMN app.complaint_action.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.complaint_action.complaint_action_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.complaint_action.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.complaint_action.complaint_id IS 'Complaint.';
COMMENT ON COLUMN app.complaint_action.action_type IS 'Action.';
COMMENT ON COLUMN app.complaint_action.actor_user_id IS 'Actor.';
COMMENT ON COLUMN app.complaint_action.to_user_id IS 'Required for ASSIGN / ESCALATE.';
COMMENT ON COLUMN app.complaint_action.note IS 'Note. Length 1–2000.';
COMMENT ON COLUMN app.complaint_action.acted_at IS 'Time.';
COMMENT ON COLUMN app.complaint_action.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.complaint_action.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.complaint_action.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.complaint_action.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.complaint_action.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.student_note (
  tenant_id uuid NOT NULL,
  student_note_id uuid NOT NULL,
  student_profile_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  note_type text NOT NULL,
  body text NOT NULL,
  added_by_user_id uuid NOT NULL,
  added_active_role text NOT NULL,
  corrects_note_id uuid,
  delete_after date,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_student_note PRIMARY KEY (tenant_id, student_note_id),
  CONSTRAINT ck_student_note_note_type CHECK (note_type IN ('DISCIPLINE', 'HEALTH')),
  CONSTRAINT ck_student_note_body CHECK (char_length(body) BETWEEN 1 AND 5000),
  CONSTRAINT ck_student_note_added_active_role CHECK (char_length(added_active_role) BETWEEN 2 AND 60),
  CONSTRAINT ck_student_note_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_student_note_r1 CHECK (corrects_note_id IS NULL OR corrects_note_id <> student_note_id),
  CONSTRAINT ck_student_note_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_student_note_tid UNIQUE (tenant_id, student_note_id),
  CONSTRAINT uq_student_note_tiid UNIQUE (tenant_id, institution_id, student_note_id)
);
COMMENT ON TABLE app.student_note IS 'Discipline or health note; never edited or deleted by users [MD-19]; deleted 1 year after the student leaves unless on hold [MD-22].';
COMMENT ON COLUMN app.student_note.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.student_note.student_note_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.student_note.student_profile_id IS 'Student.';
COMMENT ON COLUMN app.student_note.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.student_note.note_type IS 'Kind.';
COMMENT ON COLUMN app.student_note.body IS 'Text (field-level encryption candidate, OD-53). Length 1–5000. (OD-53)';
COMMENT ON COLUMN app.student_note.added_by_user_id IS 'Class / Subject Teacher, faculty or Principal [WF-38].';
COMMENT ON COLUMN app.student_note.added_active_role IS 'Role. Length 2–60.';
COMMENT ON COLUMN app.student_note.corrects_note_id IS 'Correction link; original stays [MD-19].';
COMMENT ON COLUMN app.student_note.delete_after IS 'Leave date + 1 year unless on legal hold [MD-22].';
COMMENT ON COLUMN app.student_note.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.student_note.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.student_note.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.student_note.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.student_note.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.policy (
  tenant_id uuid NOT NULL,
  policy_id uuid NOT NULL,
  institution_id uuid,
  title text NOT NULL,
  version_no integer NOT NULL,
  body_file_id uuid NOT NULL,
  drafted_by uuid NOT NULL,
  drafted_active_role text NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT',
  approval_request_id uuid,
  published_by uuid,
  published_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_policy PRIMARY KEY (tenant_id, policy_id),
  CONSTRAINT ck_policy_title CHECK (char_length(title) BETWEEN 1 AND 200),
  CONSTRAINT ck_policy_version_no CHECK (version_no >= 1),
  CONSTRAINT ck_policy_drafted_active_role CHECK (char_length(drafted_active_role) BETWEEN 2 AND 60),
  CONSTRAINT ck_policy_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'PUBLISHED', 'SUPERSEDED')),
  CONSTRAINT ck_policy_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_policy_r1 CHECK (status IN ('DRAFT') OR approval_request_id IS NOT NULL),
  CONSTRAINT ck_policy_r2 CHECK ((status IN ('PUBLISHED','SUPERSEDED')) = (published_at IS NOT NULL AND published_by IS NOT NULL)),
  CONSTRAINT ck_policy_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_policy_tid UNIQUE (tenant_id, policy_id)
);
COMMENT ON TABLE app.policy IS 'Policy with V3.3 approval: university – University Admin / Registrar draft → Vice-Chancellor approves → University Admin publishes [C2]; standalone – Admin drafts → Principal approves (Principal-drafted → Management; no Management: open item 22) → Admin publishes [answer 1].';
COMMENT ON COLUMN app.policy.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.policy.policy_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.policy.institution_id IS 'NULL = university policy applying to all institutions. [§6.1]';
COMMENT ON COLUMN app.policy.title IS 'Title. Length 1–200.';
COMMENT ON COLUMN app.policy.version_no IS 'Version.';
COMMENT ON COLUMN app.policy.body_file_id IS 'Document.';
COMMENT ON COLUMN app.policy.drafted_by IS 'Drafter (POLICY_DRAFT).';
COMMENT ON COLUMN app.policy.drafted_active_role IS 'Drafter role; decides the approver (Principal-drafted → Management). Length 2–60.';
COMMENT ON COLUMN app.policy.status IS 'Published only after approval (POLICY_PUBLISH) [C2]. [C2]';
COMMENT ON COLUMN app.policy.approval_request_id IS 'Chain UNIVERSITY_POLICY or STANDALONE_POLICY.';
COMMENT ON COLUMN app.policy.published_by IS 'Publisher.';
COMMENT ON COLUMN app.policy.published_at IS 'Time.';
COMMENT ON COLUMN app.policy.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.policy.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.policy.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.policy.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.policy.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.policy_acceptance (
  tenant_id uuid NOT NULL,
  policy_acceptance_id uuid NOT NULL,
  policy_id uuid NOT NULL,
  user_account_id uuid NOT NULL,
  accepted_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_policy_acceptance PRIMARY KEY (tenant_id, policy_acceptance_id),
  CONSTRAINT ck_policy_acceptance_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_policy_acceptance_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_policy_acceptance_tid UNIQUE (tenant_id, policy_acceptance_id)
);
COMMENT ON TABLE app.policy_acceptance IS 'Staff read-and-accept of a policy [MD-24]; Management does not accept (not staff) [C9].';
COMMENT ON COLUMN app.policy_acceptance.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.policy_acceptance.policy_acceptance_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.policy_acceptance.policy_id IS 'Policy.';
COMMENT ON COLUMN app.policy_acceptance.user_account_id IS 'Staff member.';
COMMENT ON COLUMN app.policy_acceptance.accepted_at IS 'Time.';
COMMENT ON COLUMN app.policy_acceptance.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.policy_acceptance.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.policy_acceptance.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.policy_acceptance.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.policy_acceptance.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.department_document (
  tenant_id uuid NOT NULL,
  department_document_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  department_id uuid NOT NULL,
  title text NOT NULL,
  file_id uuid NOT NULL,
  uploaded_by uuid NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_department_document PRIMARY KEY (tenant_id, department_document_id),
  CONSTRAINT ck_department_document_title CHECK (char_length(title) BETWEEN 1 AND 200),
  CONSTRAINT ck_department_document_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  CONSTRAINT ck_department_document_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_department_document_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_department_document_tid UNIQUE (tenant_id, department_document_id),
  CONSTRAINT uq_department_document_tiid UNIQUE (tenant_id, institution_id, department_document_id)
);
COMMENT ON TABLE app.department_document IS 'Department document uploaded by the HOD (DEPT_DOCUMENT_UPLOAD).';
COMMENT ON COLUMN app.department_document.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.department_document.department_document_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.department_document.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.department_document.department_id IS 'Department.';
COMMENT ON COLUMN app.department_document.title IS 'Title. Length 1–200.';
COMMENT ON COLUMN app.department_document.file_id IS 'File.';
COMMENT ON COLUMN app.department_document.uploaded_by IS 'HOD.';
COMMENT ON COLUMN app.department_document.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.department_document.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.department_document.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.department_document.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.department_document.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.department_document.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.compliance_report (
  tenant_id uuid NOT NULL,
  compliance_report_id uuid NOT NULL,
  institution_id uuid,
  title text NOT NULL,
  period_label text,
  report_kind text NOT NULL,
  file_id uuid NOT NULL,
  prepared_by uuid NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT',
  approval_request_id uuid,
  approved_by uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_compliance_report PRIMARY KEY (tenant_id, compliance_report_id),
  CONSTRAINT ck_compliance_report_title CHECK (char_length(title) BETWEEN 1 AND 200),
  CONSTRAINT ck_compliance_report_period_label CHECK (char_length(period_label) BETWEEN 1 AND 40),
  CONSTRAINT ck_compliance_report_report_kind CHECK (report_kind IN ('COMPLIANCE_REPORT', 'STATUTORY_RETURN')),
  CONSTRAINT ck_compliance_report_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
  CONSTRAINT ck_compliance_report_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_compliance_report_r1 CHECK (status = 'DRAFT' OR approval_request_id IS NOT NULL),
  CONSTRAINT ck_compliance_report_r2 CHECK (approved_by IS NULL OR approved_by <> prepared_by),
  CONSTRAINT ck_compliance_report_r3 CHECK ((status = 'APPROVED') = (approved_by IS NOT NULL)),
  CONSTRAINT ck_compliance_report_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_compliance_report_tid UNIQUE (tenant_id, compliance_report_id)
);
COMMENT ON TABLE app.compliance_report IS 'Compliance report or statutory return: Compliance Officer prepares → Registrar approves (university); Admin prepares → Principal approves (standalone) [C3, answer 1]. Preparer never approves.';
COMMENT ON COLUMN app.compliance_report.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.compliance_report.compliance_report_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.compliance_report.institution_id IS 'NULL = university. [§6.1]';
COMMENT ON COLUMN app.compliance_report.title IS 'Title. Length 1–200.';
COMMENT ON COLUMN app.compliance_report.period_label IS 'Period. Length 1–40.';
COMMENT ON COLUMN app.compliance_report.report_kind IS 'AISHE, UDISE+ and the like are statutory returns.';
COMMENT ON COLUMN app.compliance_report.file_id IS 'Document.';
COMMENT ON COLUMN app.compliance_report.prepared_by IS 'Preparer.';
COMMENT ON COLUMN app.compliance_report.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.compliance_report.approval_request_id IS 'Chain COMPLIANCE_REPORT.';
COMMENT ON COLUMN app.compliance_report.approved_by IS 'Registrar / Principal; never the preparer.';
COMMENT ON COLUMN app.compliance_report.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.compliance_report.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.compliance_report.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.compliance_report.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.compliance_report.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.accreditation_criterion (
  tenant_id uuid NOT NULL,
  criterion_id uuid NOT NULL,
  institution_id uuid,
  criterion_label text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_accreditation_criterion PRIMARY KEY (tenant_id, criterion_id),
  CONSTRAINT ck_accreditation_criterion_criterion_label CHECK (char_length(criterion_label) BETWEEN 1 AND 300),
  CONSTRAINT ck_accreditation_criterion_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_accreditation_criterion_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_accreditation_criterion_tid UNIQUE (tenant_id, criterion_id)
);
COMMENT ON TABLE app.accreditation_criterion IS 'Accreditation criterion entered by the coordinator (templates later [OB-17]).';
COMMENT ON COLUMN app.accreditation_criterion.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.accreditation_criterion.criterion_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.accreditation_criterion.institution_id IS 'NULL = university. [§6.1]';
COMMENT ON COLUMN app.accreditation_criterion.criterion_label IS 'Label. Length 1–300.';
COMMENT ON COLUMN app.accreditation_criterion.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.accreditation_criterion.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.accreditation_criterion.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.accreditation_criterion.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.accreditation_criterion.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.accreditation_document (
  tenant_id uuid NOT NULL,
  accreditation_document_id uuid NOT NULL,
  institution_id uuid,
  title text NOT NULL,
  file_id uuid NOT NULL,
  uploaded_by uuid NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT',
  approval_request_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_accreditation_document PRIMARY KEY (tenant_id, accreditation_document_id),
  CONSTRAINT ck_accreditation_document_title CHECK (char_length(title) BETWEEN 1 AND 200),
  CONSTRAINT ck_accreditation_document_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
  CONSTRAINT ck_accreditation_document_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_accreditation_document_r1 CHECK (status = 'DRAFT' OR approval_request_id IS NOT NULL),
  CONSTRAINT ck_accreditation_document_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_accreditation_document_tid UNIQUE (tenant_id, accreditation_document_id)
);
COMMENT ON TABLE app.accreditation_document IS 'Accreditation evidence: university – Accreditation Coordinator (University) uploads → Compliance Officer approves; college – Accreditation Coordinator (College) → Principal; school – School Admin → School Principal [C3, answer 1].';
COMMENT ON COLUMN app.accreditation_document.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.accreditation_document.accreditation_document_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.accreditation_document.institution_id IS 'NULL = university. [§6.1]';
COMMENT ON COLUMN app.accreditation_document.title IS 'Title. Length 1–200.';
COMMENT ON COLUMN app.accreditation_document.file_id IS 'File.';
COMMENT ON COLUMN app.accreditation_document.uploaded_by IS 'Preparer.';
COMMENT ON COLUMN app.accreditation_document.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.accreditation_document.approval_request_id IS 'Chain ACCREDITATION_ITEM.';
COMMENT ON COLUMN app.accreditation_document.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.accreditation_document.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.accreditation_document.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.accreditation_document.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.accreditation_document.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.accreditation_evidence_map (
  tenant_id uuid NOT NULL,
  accreditation_evidence_map_id uuid NOT NULL,
  accreditation_document_id uuid NOT NULL,
  criterion_id uuid NOT NULL,
  mapped_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_accreditation_evidence_map PRIMARY KEY (tenant_id, accreditation_evidence_map_id),
  CONSTRAINT ck_accreditation_evidence_map_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_accreditation_evidence_map_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_accreditation_evidence_map_tid UNIQUE (tenant_id, accreditation_evidence_map_id)
);
COMMENT ON TABLE app.accreditation_evidence_map IS 'Document ↔ criterion mapping.';
COMMENT ON COLUMN app.accreditation_evidence_map.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.accreditation_evidence_map.accreditation_evidence_map_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.accreditation_evidence_map.accreditation_document_id IS 'Document.';
COMMENT ON COLUMN app.accreditation_evidence_map.criterion_id IS 'Criterion.';
COMMENT ON COLUMN app.accreditation_evidence_map.mapped_by IS 'Coordinator.';
COMMENT ON COLUMN app.accreditation_evidence_map.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.accreditation_evidence_map.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.accreditation_evidence_map.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.accreditation_evidence_map.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.accreditation_evidence_map.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.governance_item (
  tenant_id uuid NOT NULL,
  governance_item_id uuid NOT NULL,
  institution_id uuid,
  area text NOT NULL,
  title text NOT NULL,
  description text,
  submitted_by uuid NOT NULL,
  status text NOT NULL DEFAULT 'PENDING',
  approval_request_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_governance_item PRIMARY KEY (tenant_id, governance_item_id),
  CONSTRAINT ck_governance_item_area CHECK (area IN ('QUALITY', 'LEGAL')),
  CONSTRAINT ck_governance_item_title CHECK (char_length(title) BETWEEN 1 AND 200),
  CONSTRAINT ck_governance_item_description CHECK (description IS NULL OR char_length(description) <= 5000),
  CONSTRAINT ck_governance_item_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  CONSTRAINT ck_governance_item_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_governance_item_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_governance_item_tid UNIQUE (tenant_id, governance_item_id)
);
COMMENT ON TABLE app.governance_item IS 'Generic item in a Quality Assurance or Legal area, approved by the officer of that area [WF-19, answer 4]. Who raises items has no action code (OD-121, finding 31).';
COMMENT ON COLUMN app.governance_item.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.governance_item.governance_item_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.governance_item.institution_id IS 'NULL = university. [§6.1]';
COMMENT ON COLUMN app.governance_item.area IS 'Area (compliance and accreditation now have their own tables, V3.3 C3).';
COMMENT ON COLUMN app.governance_item.title IS 'Title. Length 1–200.';
COMMENT ON COLUMN app.governance_item.description IS 'Text. Length 0–5000.';
COMMENT ON COLUMN app.governance_item.submitted_by IS 'Raiser (action OD-121). (OD-121)';
COMMENT ON COLUMN app.governance_item.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.governance_item.approval_request_id IS 'Chain GOVERNANCE_ITEM.';
COMMENT ON COLUMN app.governance_item.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.governance_item.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.governance_item.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.governance_item.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.governance_item.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.audit_finding (
  tenant_id uuid NOT NULL,
  audit_finding_id uuid NOT NULL,
  institution_id uuid,
  description text NOT NULL,
  assigned_owner_user_id uuid,
  status text NOT NULL DEFAULT 'OPEN',
  recorded_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_audit_finding PRIMARY KEY (tenant_id, audit_finding_id),
  CONSTRAINT ck_audit_finding_description CHECK (char_length(description) BETWEEN 1 AND 5000),
  CONSTRAINT ck_audit_finding_status CHECK (status IN ('OPEN', 'ASSIGNED', 'CLOSED')),
  CONSTRAINT ck_audit_finding_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_audit_finding_r1 CHECK (status <> 'ASSIGNED' OR assigned_owner_user_id IS NOT NULL),
  CONSTRAINT ck_audit_finding_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_audit_finding_tid UNIQUE (tenant_id, audit_finding_id)
);
COMMENT ON TABLE app.audit_finding IS 'Finding recorded by an Audit Officer (AUDIT_FINDING_RECORD); fields OD-47.';
COMMENT ON COLUMN app.audit_finding.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.audit_finding.audit_finding_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.audit_finding.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.audit_finding.description IS 'Finding. Length 1–5000.';
COMMENT ON COLUMN app.audit_finding.assigned_owner_user_id IS 'Owner.';
COMMENT ON COLUMN app.audit_finding.status IS 'Proposed values (OD-47). (OD-47)';
COMMENT ON COLUMN app.audit_finding.recorded_by IS 'Auditor.';
COMMENT ON COLUMN app.audit_finding.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.audit_finding.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.audit_finding.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.audit_finding.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.audit_finding.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.data_request (
  tenant_id uuid NOT NULL,
  data_request_id uuid NOT NULL,
  request_number text NOT NULL,
  request_type text NOT NULL,
  raised_by_user_id uuid NOT NULL,
  subject_kind text NOT NULL,
  subject_id uuid NOT NULL,
  description text NOT NULL,
  status text NOT NULL DEFAULT 'RECEIVED',
  owner_user_id uuid,
  identity_verified_at timestamptz,
  identity_verified_by uuid,
  retention_check_result text,
  outcome_note text,
  completed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_data_request PRIMARY KEY (tenant_id, data_request_id),
  CONSTRAINT ck_data_request_request_number CHECK (char_length(request_number) BETWEEN 1 AND 30),
  CONSTRAINT ck_data_request_request_type CHECK (request_type IN ('ACCESS', 'CORRECTION', 'DELETION')),
  CONSTRAINT ck_data_request_subject_kind CHECK (subject_kind IN ('STUDENT', 'STAFF', 'GUARDIAN')),
  CONSTRAINT ck_data_request_description CHECK (char_length(description) BETWEEN 1 AND 5000),
  CONSTRAINT ck_data_request_status CHECK (status IN ('RECEIVED', 'IDENTITY_VERIFIED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED')),
  CONSTRAINT ck_data_request_retention_check_result CHECK (retention_check_result IN ('ALLOWED', 'BLOCKED_RETENTION', 'BLOCKED_LEGAL_HOLD')),
  CONSTRAINT ck_data_request_outcome_note CHECK (char_length(outcome_note) BETWEEN 1 AND 5000),
  CONSTRAINT ck_data_request_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_data_request_r1 CHECK (status = 'RECEIVED' OR owner_user_id IS NOT NULL),
  CONSTRAINT ck_data_request_r2 CHECK (status NOT IN ('IN_PROGRESS','COMPLETED') OR (identity_verified_at IS NOT NULL AND identity_verified_by IS NOT NULL)),
  CONSTRAINT ck_data_request_r3 CHECK (status <> 'COMPLETED' OR request_type = 'ACCESS' OR retention_check_result IS NOT NULL),
  CONSTRAINT ck_data_request_r4 CHECK (status NOT IN ('COMPLETED','REJECTED') OR outcome_note IS NOT NULL),
  CONSTRAINT ck_data_request_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_data_request_tid UNIQUE (tenant_id, data_request_id)
);
COMMENT ON TABLE app.data_request IS 'Individual access / correction / deletion request handled by the Data Protection Contact [R2-25, PQ-67].';
COMMENT ON COLUMN app.data_request.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.data_request.data_request_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.data_request.request_number IS 'Tracking id. Length 1–30.';
COMMENT ON COLUMN app.data_request.request_type IS 'Kind (meaning of DELETION vs no-hard-delete: OD-122). (OD-122)';
COMMENT ON COLUMN app.data_request.raised_by_user_id IS 'Self or parent for own children.';
COMMENT ON COLUMN app.data_request.subject_kind IS 'Subject kind.';
COMMENT ON COLUMN app.data_request.subject_id IS 'Profile id.';
COMMENT ON COLUMN app.data_request.description IS 'Request. Length 1–5000.';
COMMENT ON COLUMN app.data_request.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.data_request.owner_user_id IS 'DPC; required once not RECEIVED.';
COMMENT ON COLUMN app.data_request.identity_verified_at IS 'Before IN_PROGRESS.';
COMMENT ON COLUMN app.data_request.identity_verified_by IS 'Verifier.';
COMMENT ON COLUMN app.data_request.retention_check_result IS 'Required before a DELETION / CORRECTION completes.';
COMMENT ON COLUMN app.data_request.outcome_note IS 'Required for COMPLETED / REJECTED. Length 1–5000.';
COMMENT ON COLUMN app.data_request.completed_at IS 'Time.';
COMMENT ON COLUMN app.data_request.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.data_request.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.data_request.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.data_request.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.data_request.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.data_request_event (
  tenant_id uuid NOT NULL,
  data_request_event_id uuid NOT NULL,
  data_request_id uuid NOT NULL,
  event_type text NOT NULL,
  actor_user_id uuid NOT NULL,
  note text,
  occurred_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_data_request_event PRIMARY KEY (tenant_id, data_request_event_id),
  CONSTRAINT ck_data_request_event_event_type CHECK (event_type IN ('CREATED', 'OWNER_ASSIGNED', 'IDENTITY_VERIFIED', 'STATUS_CHANGED', 'ACTION_ARRANGED', 'NOTE_ADDED')),
  CONSTRAINT ck_data_request_event_note CHECK (char_length(note) BETWEEN 1 AND 2000),
  CONSTRAINT ck_data_request_event_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_data_request_event_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_data_request_event_tid UNIQUE (tenant_id, data_request_event_id)
);
COMMENT ON TABLE app.data_request_event IS 'Append-only history of a data request.';
COMMENT ON COLUMN app.data_request_event.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.data_request_event.data_request_event_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.data_request_event.data_request_id IS 'Request.';
COMMENT ON COLUMN app.data_request_event.event_type IS 'Event.';
COMMENT ON COLUMN app.data_request_event.actor_user_id IS 'Actor.';
COMMENT ON COLUMN app.data_request_event.note IS 'Note. Length 1–2000.';
COMMENT ON COLUMN app.data_request_event.occurred_at IS 'Time.';
COMMENT ON COLUMN app.data_request_event.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.data_request_event.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.data_request_event.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.data_request_event.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.data_request_event.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.legal_hold (
  tenant_id uuid NOT NULL,
  legal_hold_id uuid NOT NULL,
  subject_kind text NOT NULL,
  subject_id uuid NOT NULL,
  reason text NOT NULL,
  placed_by uuid NOT NULL,
  placed_at timestamptz NOT NULL DEFAULT now(),
  released_by uuid,
  released_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_legal_hold PRIMARY KEY (tenant_id, legal_hold_id),
  CONSTRAINT ck_legal_hold_subject_kind CHECK (subject_kind IN ('STUDENT', 'STAFF', 'GUARDIAN', 'INSTITUTION', 'TENANT')),
  CONSTRAINT ck_legal_hold_reason CHECK (char_length(reason) BETWEEN 1 AND 1000),
  CONSTRAINT ck_legal_hold_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_legal_hold_r1 CHECK ((released_by IS NULL) = (released_at IS NULL)),
  CONSTRAINT ck_legal_hold_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_legal_hold_tid UNIQUE (tenant_id, legal_hold_id)
);
COMMENT ON TABLE app.legal_hold IS 'Legal hold blocking retention deletion and DELETION requests [MD-22, PQ-29, PQ-67]. Who places / releases: OD-34 (no action code; finding 33).';
COMMENT ON COLUMN app.legal_hold.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.legal_hold.legal_hold_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.legal_hold.subject_kind IS 'Kind.';
COMMENT ON COLUMN app.legal_hold.subject_id IS 'Id.';
COMMENT ON COLUMN app.legal_hold.reason IS 'Reason. Length 1–1000.';
COMMENT ON COLUMN app.legal_hold.placed_by IS 'Who (OD-34). (OD-34)';
COMMENT ON COLUMN app.legal_hold.placed_at IS 'Time.';
COMMENT ON COLUMN app.legal_hold.released_by IS 'Who.';
COMMENT ON COLUMN app.legal_hold.released_at IS 'Time.';
COMMENT ON COLUMN app.legal_hold.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.legal_hold.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.legal_hold.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.legal_hold.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.legal_hold.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.file_object (
  tenant_id uuid NOT NULL,
  file_id uuid NOT NULL,
  institution_id uuid,
  purpose text NOT NULL,
  storage_key text NOT NULL,
  original_filename text NOT NULL,
  content_type text NOT NULL,
  size_bytes bigint NOT NULL,
  sha256 char(64) NOT NULL,
  scan_status text NOT NULL DEFAULT 'PENDING',
  status text NOT NULL DEFAULT 'ACTIVE',
  uploaded_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_file_object PRIMARY KEY (tenant_id, file_id),
  CONSTRAINT ck_file_object_purpose CHECK (purpose IN ('MESSAGE_ATTACHMENT', 'ASSIGNMENT_SUBMISSION', 'STUDY_MATERIAL', 'STAFF_DOCUMENT', 'POLICY', 'DEPARTMENT_DOCUMENT', 'ACCREDITATION', 'COMPLIANCE', 'LEAVE_DOCUMENT', 'HALL_TICKET', 'COMMUNITY_MEDIA', 'IMPORT_SOURCE', 'EXPORT', 'PROFILE_PHOTO', 'BRANDING', 'ATTENDANCE_UPLOAD', 'MARKS_SOURCE', 'SYLLABUS', 'CERTIFICATE', 'CONSENT_FORM', 'SERVICE_RECORD')),
  CONSTRAINT ck_file_object_storage_key CHECK (char_length(storage_key) BETWEEN 1 AND 1000),
  CONSTRAINT ck_file_object_original_filename CHECK (char_length(original_filename) BETWEEN 1 AND 255),
  CONSTRAINT ck_file_object_content_type CHECK (char_length(content_type) BETWEEN 3 AND 100),
  CONSTRAINT ck_file_object_size_bytes CHECK (size_bytes > 0),
  CONSTRAINT ck_file_object_scan_status CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED', 'SCAN_FAILED')),
  CONSTRAINT ck_file_object_status CHECK (status IN ('ACTIVE', 'QUARANTINED', 'DELETED')),
  CONSTRAINT ck_file_object_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_file_object_r1 CHECK (purpose <> 'MESSAGE_ATTACHMENT' OR (content_type IN ('application/pdf','image/jpeg','image/png') AND size_bytes <= 10485760)),
  CONSTRAINT ck_file_object_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_file_object_tid UNIQUE (tenant_id, file_id)
);
COMMENT ON TABLE app.file_object IS 'Stored file; storage key never leaves the server; download only when the scan is CLEAN [TE-11].';
COMMENT ON COLUMN app.file_object.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.file_object.file_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.file_object.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.file_object.purpose IS 'Purpose; limits per purpose in ref.safe_file_type.';
COMMENT ON COLUMN app.file_object.storage_key IS 'Object-store key prefixed tenants/{tenant_id}/ (PTD-40); never returned. Length 1–1000.';
COMMENT ON COLUMN app.file_object.original_filename IS 'Sanitised name. Length 1–255.';
COMMENT ON COLUMN app.file_object.content_type IS 'MIME, checked against ref.safe_file_type. Length 3–100.';
COMMENT ON COLUMN app.file_object.size_bytes IS 'Size.';
COMMENT ON COLUMN app.file_object.sha256 IS 'Checksum.';
COMMENT ON COLUMN app.file_object.scan_status IS 'Virus scan.';
COMMENT ON COLUMN app.file_object.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.file_object.uploaded_by IS 'Uploader.';
COMMENT ON COLUMN app.file_object.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.file_object.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.file_object.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.file_object.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.file_object.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.audit_outbox (
  tenant_id uuid NOT NULL,
  audit_event_id uuid NOT NULL,
  institution_id uuid,
  occurred_at timestamptz NOT NULL DEFAULT now(),
  actor_kind text NOT NULL,
  actor_user_id uuid,
  active_role_code text,
  support_grant_id uuid,
  operation text NOT NULL,
  action_code text,
  entity_type text NOT NULL,
  entity_id uuid,
  record_count integer,
  sensitivity_class text,
  outcome text NOT NULL,
  old_values jsonb,
  new_values jsonb,
  correlation_id text NOT NULL,
  ship_status text NOT NULL DEFAULT 'PENDING',
  shipped_at timestamptz,
  store_object_key text,
  payload_hash char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_audit_outbox PRIMARY KEY (tenant_id, audit_event_id),
  CONSTRAINT ck_audit_outbox_actor_kind CHECK (actor_kind IN ('USER', 'SYSTEM', 'SUPPORT_UNDER_GRANT')),
  CONSTRAINT ck_audit_outbox_active_role_code CHECK (char_length(active_role_code) BETWEEN 2 AND 60),
  CONSTRAINT ck_audit_outbox_operation CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE', 'APPROVE', 'REJECT', 'LOGIN', 'EXPORT', 'DOWNLOAD', 'DENIED', 'VIEW_SENSITIVE', 'CONFIG_CHANGE')),
  CONSTRAINT ck_audit_outbox_entity_type CHECK (char_length(entity_type) BETWEEN 1 AND 60),
  CONSTRAINT ck_audit_outbox_record_count CHECK (record_count IS NULL OR record_count >= 0),
  CONSTRAINT ck_audit_outbox_sensitivity_class CHECK (sensitivity_class IN ('N', 'P', 'F', 'H', 'K')),
  CONSTRAINT ck_audit_outbox_outcome CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILED')),
  CONSTRAINT ck_audit_outbox_correlation_id CHECK (char_length(correlation_id) BETWEEN 1 AND 100),
  CONSTRAINT ck_audit_outbox_ship_status CHECK (ship_status IN ('PENDING', 'SHIPPED', 'FAILED')),
  CONSTRAINT ck_audit_outbox_store_object_key CHECK (char_length(store_object_key) BETWEEN 1 AND 500),
  CONSTRAINT ck_audit_outbox_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_audit_outbox_r1 CHECK (actor_kind = 'SYSTEM' OR actor_user_id IS NOT NULL),
  CONSTRAINT ck_audit_outbox_r2 CHECK (actor_kind <> 'USER' OR active_role_code IS NOT NULL),
  CONSTRAINT ck_audit_outbox_r3 CHECK ((actor_kind = 'SUPPORT_UNDER_GRANT') = (support_grant_id IS NOT NULL)),
  CONSTRAINT ck_audit_outbox_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_audit_outbox_tid UNIQUE (tenant_id, audit_event_id)
);
COMMENT ON TABLE app.audit_outbox IS 'Tenant audit events written in the business transaction and shipped to the write-once store [AU-06, PQ-65] (PTD-39).';
COMMENT ON COLUMN app.audit_outbox.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.audit_outbox.audit_event_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.audit_outbox.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.audit_outbox.occurred_at IS 'Time.';
COMMENT ON COLUMN app.audit_outbox.actor_kind IS 'Actor kind (Doc 2 P5-8).';
COMMENT ON COLUMN app.audit_outbox.actor_user_id IS 'Tenant user, or platform_user under a support grant; NULL only for SYSTEM.';
COMMENT ON COLUMN app.audit_outbox.active_role_code IS 'Required for USER [HI-15]. Length 2–60.';
COMMENT ON COLUMN app.audit_outbox.support_grant_id IS 'Required for SUPPORT_UNDER_GRANT [WF-11].';
COMMENT ON COLUMN app.audit_outbox.operation IS 'Operation.';
COMMENT ON COLUMN app.audit_outbox.action_code IS 'Action.';
COMMENT ON COLUMN app.audit_outbox.entity_type IS 'Entity. Length 1–60.';
COMMENT ON COLUMN app.audit_outbox.entity_id IS 'Entity id (NULL for list views).';
COMMENT ON COLUMN app.audit_outbox.record_count IS 'Records touched / viewed (granularity OD-59). (OD-59)';
COMMENT ON COLUMN app.audit_outbox.sensitivity_class IS 'Class of the data involved.';
COMMENT ON COLUMN app.audit_outbox.outcome IS 'Outcome.';
COMMENT ON COLUMN app.audit_outbox.old_values IS 'Masked [AU-04].';
COMMENT ON COLUMN app.audit_outbox.new_values IS 'Masked [AU-04].';
COMMENT ON COLUMN app.audit_outbox.correlation_id IS 'Request id shared with logs and API errors. Length 1–100.';
COMMENT ON COLUMN app.audit_outbox.ship_status IS 'Shipper.';
COMMENT ON COLUMN app.audit_outbox.shipped_at IS 'Ack.';
COMMENT ON COLUMN app.audit_outbox.store_object_key IS 'Store key. Length 1–500.';
COMMENT ON COLUMN app.audit_outbox.payload_hash IS 'SHA-256 (method OD-09).';
COMMENT ON COLUMN app.audit_outbox.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.audit_outbox.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.audit_outbox.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.audit_outbox.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.audit_outbox.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.outbox_event (
  tenant_id uuid NOT NULL,
  outbox_event_id uuid NOT NULL,
  event_type text NOT NULL,
  aggregate_type text NOT NULL,
  aggregate_id uuid NOT NULL,
  payload jsonb NOT NULL,
  status text NOT NULL DEFAULT 'PENDING',
  attempts smallint NOT NULL DEFAULT 0,
  next_attempt_at timestamptz,
  processed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_outbox_event PRIMARY KEY (tenant_id, outbox_event_id),
  CONSTRAINT ck_outbox_event_event_type CHECK (char_length(event_type) BETWEEN 3 AND 100),
  CONSTRAINT ck_outbox_event_aggregate_type CHECK (char_length(aggregate_type) BETWEEN 1 AND 60),
  CONSTRAINT ck_outbox_event_status CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
  CONSTRAINT ck_outbox_event_attempts CHECK (attempts >= 0),
  CONSTRAINT ck_outbox_event_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_outbox_event_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_outbox_event_tid UNIQUE (tenant_id, outbox_event_id)
);
COMMENT ON TABLE app.outbox_event IS 'Transactional outbox for domain events [TE-21] (PTD-11).';
COMMENT ON COLUMN app.outbox_event.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.outbox_event.outbox_event_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.outbox_event.event_type IS 'e.g. payment.confirmed. Length 3–100.';
COMMENT ON COLUMN app.outbox_event.aggregate_type IS 'Aggregate. Length 1–60.';
COMMENT ON COLUMN app.outbox_event.aggregate_id IS 'Id.';
COMMENT ON COLUMN app.outbox_event.payload IS 'IDs and codes only.';
COMMENT ON COLUMN app.outbox_event.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.outbox_event.attempts IS 'Attempts (OD-38).';
COMMENT ON COLUMN app.outbox_event.next_attempt_at IS 'Retry.';
COMMENT ON COLUMN app.outbox_event.processed_at IS 'Done.';
COMMENT ON COLUMN app.outbox_event.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.outbox_event.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.outbox_event.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.outbox_event.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.outbox_event.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.import_job (
  tenant_id uuid NOT NULL,
  import_job_id uuid NOT NULL,
  institution_id uuid,
  import_kind text NOT NULL,
  uploaded_by uuid NOT NULL,
  source_file_id uuid NOT NULL,
  status text NOT NULL DEFAULT 'UPLOADED',
  import_mode text,
  total_rows integer,
  valid_rows integer,
  error_rows integer,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_import_job PRIMARY KEY (tenant_id, import_job_id),
  CONSTRAINT ck_import_job_import_kind CHECK (import_kind IN ('STUDENTS', 'STAFF', 'PARENTS', 'FEE_DUES', 'PAST_RESULTS', 'USERS', 'ATTENDANCE', 'EXTERNAL_MARKS')),
  CONSTRAINT ck_import_job_status CHECK (status IN ('UPLOADED', 'VALIDATING', 'VALIDATED', 'IMPORTING', 'COMPLETED', 'FAILED', 'CANCELLED')),
  CONSTRAINT ck_import_job_import_mode CHECK (import_mode IN ('CLEAN_ONLY', 'GOOD_ROWS_ONLY')),
  CONSTRAINT ck_import_job_total_rows CHECK (total_rows IS NULL OR total_rows >= 0),
  CONSTRAINT ck_import_job_valid_rows CHECK (valid_rows IS NULL OR valid_rows >= 0),
  CONSTRAINT ck_import_job_error_rows CHECK (error_rows IS NULL OR error_rows >= 0),
  CONSTRAINT ck_import_job_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_import_job_r1 CHECK (status NOT IN ('VALIDATED','IMPORTING','COMPLETED') OR valid_rows + error_rows = total_rows),
  CONSTRAINT ck_import_job_r2 CHECK (status NOT IN ('IMPORTING','COMPLETED') OR import_mode IS NOT NULL),
  CONSTRAINT ck_import_job_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_import_job_tid UNIQUE (tenant_id, import_job_id)
);
COMMENT ON TABLE app.import_job IS 'Checked bulk import (whole file first; clean file or good rows only) [MD-26, MD-27].';
COMMENT ON COLUMN app.import_job.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.import_job.import_job_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.import_job.institution_id IS 'NULL = university-level import. [§6.1]';
COMMENT ON COLUMN app.import_job.import_kind IS 'Kind.';
COMMENT ON COLUMN app.import_job.uploaded_by IS 'Uploader.';
COMMENT ON COLUMN app.import_job.source_file_id IS 'File.';
COMMENT ON COLUMN app.import_job.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.import_job.import_mode IS 'Chosen after validation.';
COMMENT ON COLUMN app.import_job.total_rows IS 'Rows.';
COMMENT ON COLUMN app.import_job.valid_rows IS 'Valid.';
COMMENT ON COLUMN app.import_job.error_rows IS 'Errors.';
COMMENT ON COLUMN app.import_job.started_at IS 'Start.';
COMMENT ON COLUMN app.import_job.finished_at IS 'End.';
COMMENT ON COLUMN app.import_job.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.import_job.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.import_job.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.import_job.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.import_job.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.import_row_error (
  tenant_id uuid NOT NULL,
  import_row_error_id uuid NOT NULL,
  import_job_id uuid NOT NULL,
  row_number integer NOT NULL,
  field_name text,
  error_code text NOT NULL,
  error_message text NOT NULL,
  row_data jsonb NOT NULL,
  fixed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_import_row_error PRIMARY KEY (tenant_id, import_row_error_id),
  CONSTRAINT ck_import_row_error_row_number CHECK (row_number >= 1),
  CONSTRAINT ck_import_row_error_field_name CHECK (char_length(field_name) BETWEEN 1 AND 100),
  CONSTRAINT ck_import_row_error_error_code CHECK (char_length(error_code) BETWEEN 1 AND 60),
  CONSTRAINT ck_import_row_error_error_message CHECK (char_length(error_message) BETWEEN 1 AND 500),
  CONSTRAINT ck_import_row_error_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_import_row_error_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_import_row_error_tid UNIQUE (tenant_id, import_row_error_id)
);
COMMENT ON TABLE app.import_row_error IS 'Rejected row kept to fix and add later [MD-27].';
COMMENT ON COLUMN app.import_row_error.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.import_row_error.import_row_error_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.import_row_error.import_job_id IS 'Job.';
COMMENT ON COLUMN app.import_row_error.row_number IS 'Row.';
COMMENT ON COLUMN app.import_row_error.field_name IS 'Field. Length 1–100.';
COMMENT ON COLUMN app.import_row_error.error_code IS 'Code. Length 1–60.';
COMMENT ON COLUMN app.import_row_error.error_message IS 'Message. Length 1–500.';
COMMENT ON COLUMN app.import_row_error.row_data IS 'Original row, ID numbers masked.';
COMMENT ON COLUMN app.import_row_error.fixed_at IS 'Fixed.';
COMMENT ON COLUMN app.import_row_error.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.import_row_error.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.import_row_error.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.import_row_error.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.import_row_error.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.bulk_correction_batch (
  tenant_id uuid NOT NULL,
  bulk_correction_batch_id uuid NOT NULL,
  institution_id uuid,
  submitted_by uuid NOT NULL,
  source_file_id uuid,
  reason text NOT NULL,
  status text NOT NULL DEFAULT 'STAGED',
  item_count integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_bulk_correction_batch PRIMARY KEY (tenant_id, bulk_correction_batch_id),
  CONSTRAINT ck_bulk_correction_batch_reason CHECK (char_length(reason) BETWEEN 1 AND 1000),
  CONSTRAINT ck_bulk_correction_batch_status CHECK (status IN ('STAGED', 'VALIDATED', 'SUBMITTED', 'PARTIALLY_APPLIED', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT ck_bulk_correction_batch_item_count CHECK (item_count >= 0),
  CONSTRAINT ck_bulk_correction_batch_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_bulk_correction_batch_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_bulk_correction_batch_tid UNIQUE (tenant_id, bulk_correction_batch_id)
);
COMMENT ON TABLE app.bulk_correction_batch IS 'Bulk correction of many records (BULK_CORRECT): attendance, marks and fee changes enter their own approval chains; other fields are applied with audit [R2-32]. Doc 1 had no design (OD-71, findings 9 / 26).';
COMMENT ON COLUMN app.bulk_correction_batch.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.bulk_correction_batch.bulk_correction_batch_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.bulk_correction_batch.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.bulk_correction_batch.submitted_by IS 'Admin.';
COMMENT ON COLUMN app.bulk_correction_batch.source_file_id IS 'Optional file.';
COMMENT ON COLUMN app.bulk_correction_batch.reason IS 'Reason. Length 1–1000.';
COMMENT ON COLUMN app.bulk_correction_batch.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.bulk_correction_batch.item_count IS 'Items.';
COMMENT ON COLUMN app.bulk_correction_batch.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.bulk_correction_batch.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.bulk_correction_batch.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.bulk_correction_batch.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.bulk_correction_batch.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.bulk_correction_item (
  tenant_id uuid NOT NULL,
  bulk_correction_item_id uuid NOT NULL,
  bulk_correction_batch_id uuid NOT NULL,
  item_kind text NOT NULL,
  target_entity_type text NOT NULL,
  target_entity_id uuid NOT NULL,
  field_name text,
  old_value jsonb,
  new_value jsonb NOT NULL,
  route text NOT NULL,
  child_entity_type text,
  child_entity_id uuid,
  status text NOT NULL DEFAULT 'PENDING',
  error_code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_bulk_correction_item PRIMARY KEY (tenant_id, bulk_correction_item_id),
  CONSTRAINT ck_bulk_correction_item_item_kind CHECK (item_kind IN ('ATTENDANCE', 'MARKS', 'FEE', 'STUDENT_FIELD', 'STAFF_FIELD', 'GUARDIAN_FIELD')),
  CONSTRAINT ck_bulk_correction_item_target_entity_type CHECK (char_length(target_entity_type) BETWEEN 1 AND 60),
  CONSTRAINT ck_bulk_correction_item_field_name CHECK (char_length(field_name) BETWEEN 1 AND 100),
  CONSTRAINT ck_bulk_correction_item_route CHECK (route IN ('ATTENDANCE_CORRECTION_CHAIN', 'MARKS_RESUBMISSION', 'FEE_CHAIN', 'DIRECT_WITH_AUDIT')),
  CONSTRAINT ck_bulk_correction_item_child_entity_type CHECK (char_length(child_entity_type) BETWEEN 1 AND 60),
  CONSTRAINT ck_bulk_correction_item_status CHECK (status IN ('PENDING', 'ROUTED', 'APPLIED', 'REJECTED', 'FAILED')),
  CONSTRAINT ck_bulk_correction_item_error_code CHECK (char_length(error_code) BETWEEN 1 AND 60),
  CONSTRAINT ck_bulk_correction_item_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_bulk_correction_item_r1 CHECK (item_kind NOT IN ('ATTENDANCE','MARKS','FEE') OR route <> 'DIRECT_WITH_AUDIT'),
  CONSTRAINT ck_bulk_correction_item_r2 CHECK (item_kind IN ('ATTENDANCE','MARKS','FEE') OR route = 'DIRECT_WITH_AUDIT'),
  CONSTRAINT ck_bulk_correction_item_r3 CHECK (status <> 'ROUTED' OR child_entity_id IS NOT NULL),
  CONSTRAINT ck_bulk_correction_item_r4 CHECK (field_name IS NULL OR field_name NOT IN ('student_code','status','staff_code','tenant_id','institution_id')),
  CONSTRAINT ck_bulk_correction_item_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_bulk_correction_item_tid UNIQUE (tenant_id, bulk_correction_item_id)
);
COMMENT ON TABLE app.bulk_correction_item IS 'One change inside a bulk correction; chain-governed kinds create their own request and never bypass it.';
COMMENT ON COLUMN app.bulk_correction_item.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.bulk_correction_item.bulk_correction_item_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.bulk_correction_item.bulk_correction_batch_id IS 'Batch.';
COMMENT ON COLUMN app.bulk_correction_item.item_kind IS 'Kind.';
COMMENT ON COLUMN app.bulk_correction_item.target_entity_type IS 'Target entity. Length 1–60.';
COMMENT ON COLUMN app.bulk_correction_item.target_entity_id IS 'Target id.';
COMMENT ON COLUMN app.bulk_correction_item.field_name IS 'Field for *_FIELD kinds (allow-list; never IDs or status [answer 8]). Length 1–100.';
COMMENT ON COLUMN app.bulk_correction_item.old_value IS 'Before.';
COMMENT ON COLUMN app.bulk_correction_item.new_value IS 'Requested value.';
COMMENT ON COLUMN app.bulk_correction_item.route IS 'Chain-governed kinds never use DIRECT_WITH_AUDIT (CHECK).';
COMMENT ON COLUMN app.bulk_correction_item.child_entity_type IS 'Created request type (e.g. ATTENDANCE_CORRECTION_REQUEST). Length 1–60.';
COMMENT ON COLUMN app.bulk_correction_item.child_entity_id IS 'Created request id.';
COMMENT ON COLUMN app.bulk_correction_item.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.bulk_correction_item.error_code IS 'Validation error. Length 1–60.';
COMMENT ON COLUMN app.bulk_correction_item.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.bulk_correction_item.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.bulk_correction_item.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.bulk_correction_item.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.bulk_correction_item.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.report_export_job (
  tenant_id uuid NOT NULL,
  report_export_job_id uuid NOT NULL,
  requested_by_user_id uuid NOT NULL,
  requested_active_role text NOT NULL,
  report_code text NOT NULL,
  scope_snapshot jsonb NOT NULL,
  parameters jsonb NOT NULL,
  format text NOT NULL,
  status text NOT NULL DEFAULT 'QUEUED',
  file_id uuid,
  link_expires_at timestamptz,
  attempts smallint NOT NULL DEFAULT 0,
  last_error_code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_report_export_job PRIMARY KEY (tenant_id, report_export_job_id),
  CONSTRAINT ck_report_export_job_requested_active_role CHECK (char_length(requested_active_role) BETWEEN 2 AND 60),
  CONSTRAINT ck_report_export_job_format CHECK (format IN ('PDF', 'XLSX', 'CSV')),
  CONSTRAINT ck_report_export_job_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED')),
  CONSTRAINT ck_report_export_job_attempts CHECK (attempts >= 0),
  CONSTRAINT ck_report_export_job_last_error_code CHECK (char_length(last_error_code) BETWEEN 1 AND 60),
  CONSTRAINT ck_report_export_job_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_report_export_job_r1 CHECK (status <> 'SUCCEEDED' OR (file_id IS NOT NULL AND link_expires_at IS NOT NULL)),
  CONSTRAINT ck_report_export_job_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_report_export_job_tid UNIQUE (tenant_id, report_export_job_id)
);
COMMENT ON TABLE app.report_export_job IS 'Report / statutory / ID-card export; rights re-checked at run [PQ-60]; link 24 h [TE-12].';
COMMENT ON COLUMN app.report_export_job.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.report_export_job.report_export_job_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.report_export_job.requested_by_user_id IS 'Requester.';
COMMENT ON COLUMN app.report_export_job.requested_active_role IS 'Role. Length 2–60.';
COMMENT ON COLUMN app.report_export_job.report_code IS 'Report.';
COMMENT ON COLUMN app.report_export_job.scope_snapshot IS 'Scope at request; never trusted again.';
COMMENT ON COLUMN app.report_export_job.parameters IS 'Validated filters.';
COMMENT ON COLUMN app.report_export_job.format IS 'CSV only where a template needs it (OD-48).';
COMMENT ON COLUMN app.report_export_job.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.report_export_job.file_id IS 'Required when SUCCEEDED.';
COMMENT ON COLUMN app.report_export_job.link_expires_at IS '24 h after success.';
COMMENT ON COLUMN app.report_export_job.attempts IS 'Attempts.';
COMMENT ON COLUMN app.report_export_job.last_error_code IS 'e.g. AUTHORIZATION_LOST. Length 1–60.';
COMMENT ON COLUMN app.report_export_job.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.report_export_job.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.report_export_job.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.report_export_job.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.report_export_job.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.background_job (
  tenant_id uuid NOT NULL,
  background_job_id uuid NOT NULL,
  job_type text NOT NULL,
  state text NOT NULL DEFAULT 'QUEUED',
  run_after timestamptz NOT NULL DEFAULT now(),
  attempts smallint NOT NULL DEFAULT 0,
  max_attempts smallint NOT NULL,
  lease_owner text,
  lease_expires_at timestamptz,
  dedupe_key text,
  requested_by_user_id uuid,
  requested_active_role text,
  payload jsonb NOT NULL,
  last_error_code text,
  finished_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_background_job PRIMARY KEY (tenant_id, background_job_id),
  CONSTRAINT ck_background_job_state CHECK (state IN ('QUEUED', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED', 'DEAD')),
  CONSTRAINT ck_background_job_attempts CHECK (attempts >= 0),
  CONSTRAINT ck_background_job_max_attempts CHECK (max_attempts >= 1),
  CONSTRAINT ck_background_job_lease_owner CHECK (char_length(lease_owner) BETWEEN 1 AND 100),
  CONSTRAINT ck_background_job_dedupe_key CHECK (char_length(dedupe_key) BETWEEN 1 AND 200),
  CONSTRAINT ck_background_job_requested_active_role CHECK (char_length(requested_active_role) BETWEEN 2 AND 60),
  CONSTRAINT ck_background_job_last_error_code CHECK (char_length(last_error_code) BETWEEN 1 AND 60),
  CONSTRAINT ck_background_job_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_background_job_r1 CHECK (attempts <= max_attempts),
  CONSTRAINT ck_background_job_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_background_job_tid UNIQUE (tenant_id, background_job_id)
);
COMMENT ON TABLE app.background_job IS 'Scheduled / async job with lease (PTD-43).';
COMMENT ON COLUMN app.background_job.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.background_job.background_job_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.background_job.job_type IS 'Type.';
COMMENT ON COLUMN app.background_job.state IS 'State.';
COMMENT ON COLUMN app.background_job.run_after IS 'Earliest run.';
COMMENT ON COLUMN app.background_job.attempts IS 'Attempts.';
COMMENT ON COLUMN app.background_job.max_attempts IS 'From ref.job_type (OD-38).';
COMMENT ON COLUMN app.background_job.lease_owner IS 'Worker. Length 1–100.';
COMMENT ON COLUMN app.background_job.lease_expires_at IS 'Lease.';
COMMENT ON COLUMN app.background_job.dedupe_key IS 'Unique per type. Length 1–200.';
COMMENT ON COLUMN app.background_job.requested_by_user_id IS 'NULL for scheduled jobs.';
COMMENT ON COLUMN app.background_job.requested_active_role IS 'Role. Length 2–60.';
COMMENT ON COLUMN app.background_job.payload IS 'Tenant-scoped ids only.';
COMMENT ON COLUMN app.background_job.last_error_code IS 'Error. Length 1–60.';
COMMENT ON COLUMN app.background_job.finished_at IS 'End.';
COMMENT ON COLUMN app.background_job.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.background_job.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.background_job.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.background_job.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.background_job.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.idempotency_key (
  tenant_id uuid NOT NULL,
  idempotency_key_id uuid NOT NULL,
  scope text NOT NULL,
  request_key text NOT NULL,
  caller_user_id uuid NOT NULL,
  request_hash char(64) NOT NULL,
  status text NOT NULL DEFAULT 'IN_PROGRESS',
  response_status smallint,
  response_ref text,
  completed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_idempotency_key PRIMARY KEY (tenant_id, idempotency_key_id),
  CONSTRAINT ck_idempotency_key_request_key CHECK (char_length(request_key) BETWEEN 8 AND 100),
  CONSTRAINT ck_idempotency_key_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
  CONSTRAINT ck_idempotency_key_response_ref CHECK (char_length(response_ref) BETWEEN 1 AND 500),
  CONSTRAINT ck_idempotency_key_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_idempotency_key_r1 CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL AND response_status IS NOT NULL)),
  CONSTRAINT ck_idempotency_key_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_idempotency_key_tid UNIQUE (tenant_id, idempotency_key_id)
);
COMMENT ON TABLE app.idempotency_key IS 'Stored outcome of an idempotent command; scope from ref.idempotency_scope (fixes finding 40).';
COMMENT ON COLUMN app.idempotency_key.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.idempotency_key.idempotency_key_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.idempotency_key.scope IS 'Scope.';
COMMENT ON COLUMN app.idempotency_key.request_key IS 'Idempotency-Key header. Length 8–100.';
COMMENT ON COLUMN app.idempotency_key.caller_user_id IS 'Key is scoped to the caller as well (prevents cross-user replay).';
COMMENT ON COLUMN app.idempotency_key.request_hash IS 'Same key + different body = conflict (PTD-44).';
COMMENT ON COLUMN app.idempotency_key.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.idempotency_key.response_status IS 'HTTP status.';
COMMENT ON COLUMN app.idempotency_key.response_ref IS 'Reference to the stored result. Length 1–500.';
COMMENT ON COLUMN app.idempotency_key.completed_at IS 'Time.';
COMMENT ON COLUMN app.idempotency_key.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.idempotency_key.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.idempotency_key.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.idempotency_key.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.idempotency_key.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.setting_value (
  tenant_id uuid NOT NULL,
  setting_value_id uuid NOT NULL,
  setting_key text NOT NULL,
  scope_level text NOT NULL,
  institution_id uuid,
  value jsonb NOT NULL,
  version_no integer NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE',
  effective_from timestamptz NOT NULL DEFAULT now(),
  change_reason text,
  restored_from_version_no integer,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_setting_value PRIMARY KEY (tenant_id, setting_value_id),
  CONSTRAINT ck_setting_value_scope_level CHECK (scope_level IN ('TENANT', 'INSTITUTION')),
  CONSTRAINT ck_setting_value_version_no CHECK (version_no >= 1),
  CONSTRAINT ck_setting_value_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
  CONSTRAINT ck_setting_value_change_reason CHECK (char_length(change_reason) BETWEEN 1 AND 500),
  CONSTRAINT ck_setting_value_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_setting_value_r1 CHECK ((scope_level = 'INSTITUTION') = (institution_id IS NOT NULL)),
  CONSTRAINT ck_setting_value_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_setting_value_tid UNIQUE (tenant_id, setting_value_id)
);
COMMENT ON TABLE app.setting_value IS 'Tenant / institution setting value, versioned; rollback is a new version [PQ-06, PQ-74] (precedence OD-08).';
COMMENT ON COLUMN app.setting_value.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.setting_value.setting_value_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.setting_value.setting_key IS 'Setting.';
COMMENT ON COLUMN app.setting_value.scope_level IS 'Level.';
COMMENT ON COLUMN app.setting_value.institution_id IS 'Required for INSTITUTION. [§6.1]';
COMMENT ON COLUMN app.setting_value.value IS 'Validated against the definition.';
COMMENT ON COLUMN app.setting_value.version_no IS 'Version.';
COMMENT ON COLUMN app.setting_value.status IS 'Lifecycle status; transitions in §4.26.';
COMMENT ON COLUMN app.setting_value.effective_from IS 'Start.';
COMMENT ON COLUMN app.setting_value.change_reason IS 'Reason. Length 1–500.';
COMMENT ON COLUMN app.setting_value.restored_from_version_no IS 'Rollback source.';
COMMENT ON COLUMN app.setting_value.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.setting_value.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.setting_value.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.setting_value.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.setting_value.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.saved_filter (
  tenant_id uuid NOT NULL,
  saved_filter_id uuid NOT NULL,
  user_account_id uuid NOT NULL,
  report_code text NOT NULL,
  filter_name text NOT NULL,
  filter_values jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_saved_filter PRIMARY KEY (tenant_id, saved_filter_id),
  CONSTRAINT ck_saved_filter_filter_name CHECK (char_length(filter_name) BETWEEN 1 AND 100),
  CONSTRAINT ck_saved_filter_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_saved_filter_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_saved_filter_tid UNIQUE (tenant_id, saved_filter_id)
);
COMMENT ON TABLE app.saved_filter IS 'Personal saved report filter; never changes report definitions or permissions [PQ-62].';
COMMENT ON COLUMN app.saved_filter.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.saved_filter.saved_filter_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.saved_filter.user_account_id IS 'Owner.';
COMMENT ON COLUMN app.saved_filter.report_code IS 'Report.';
COMMENT ON COLUMN app.saved_filter.filter_name IS 'Name. Length 1–100.';
COMMENT ON COLUMN app.saved_filter.filter_values IS 'Re-validated against scope at each run.';
COMMENT ON COLUMN app.saved_filter.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.saved_filter.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.saved_filter.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.saved_filter.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.saved_filter.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.dashboard_preference (
  tenant_id uuid NOT NULL,
  dashboard_preference_id uuid NOT NULL,
  user_account_id uuid NOT NULL,
  preferences jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_dashboard_preference PRIMARY KEY (tenant_id, dashboard_preference_id),
  CONSTRAINT ck_dashboard_preference_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_dashboard_preference_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_dashboard_preference_tid UNIQUE (tenant_id, dashboard_preference_id)
);
COMMENT ON TABLE app.dashboard_preference IS 'Dashboard layout only (no scope or role fields) [PQ-62].';
COMMENT ON COLUMN app.dashboard_preference.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.dashboard_preference.dashboard_preference_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.dashboard_preference.user_account_id IS 'Owner.';
COMMENT ON COLUMN app.dashboard_preference.preferences IS 'Layout JSON.';
COMMENT ON COLUMN app.dashboard_preference.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.dashboard_preference.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.dashboard_preference.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.dashboard_preference.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.dashboard_preference.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.usage_metric_daily (
  tenant_id uuid NOT NULL,
  usage_metric_daily_id uuid NOT NULL,
  institution_id uuid,
  usage_date date NOT NULL,
  metric_code text NOT NULL,
  value bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_usage_metric_daily PRIMARY KEY (tenant_id, usage_metric_daily_id),
  CONSTRAINT ck_usage_metric_daily_metric_code CHECK (metric_code IN ('LOGINS', 'ACTIVE_USERS', 'ATTENDANCE_MARKED', 'FEES_PAID_ONLINE', 'NOTICES_PUBLISHED', 'SMS_SENT')),
  CONSTRAINT ck_usage_metric_daily_value CHECK (value >= 0),
  CONSTRAINT ck_usage_metric_daily_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_usage_metric_daily_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_usage_metric_daily_tid UNIQUE (tenant_id, usage_metric_daily_id)
);
COMMENT ON TABLE app.usage_metric_daily IS 'Tenant usage counts for own scope (USAGE_ANALYTICS_VIEW [MX-21]); Doc 1 had no tenant-level store (OD-70, findings 8 / 29). Proposed metric list.';
COMMENT ON COLUMN app.usage_metric_daily.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.usage_metric_daily.usage_metric_daily_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.usage_metric_daily.institution_id IS 'NULL = tenant total. [§6.1]';
COMMENT ON COLUMN app.usage_metric_daily.usage_date IS 'Day.';
COMMENT ON COLUMN app.usage_metric_daily.metric_code IS 'Metric (list proposed, OD-70). (OD-70)';
COMMENT ON COLUMN app.usage_metric_daily.value IS 'Count.';
COMMENT ON COLUMN app.usage_metric_daily.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.usage_metric_daily.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.usage_metric_daily.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.usage_metric_daily.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.usage_metric_daily.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE TABLE app.certificate_request (
  tenant_id uuid NOT NULL,
  certificate_request_id uuid NOT NULL,
  student_profile_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  requested_by_user_id uuid NOT NULL,
  requester_kind text NOT NULL,
  request_type text NOT NULL,
  details text,
  correction_payload jsonb,
  no_dues_result text NOT NULL,
  no_dues_override_by uuid,
  no_dues_override_at timestamptz,
  no_dues_override_reason text,
  approval_request_id uuid,
  status text NOT NULL DEFAULT 'REQUESTED',
  prepared_by_user_id uuid,
  prepared_at timestamptz,
  file_id uuid,
  applied_by_user_id uuid,
  applied_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL,
  row_version integer NOT NULL DEFAULT 1,
  CONSTRAINT pk_certificate_request PRIMARY KEY (tenant_id, certificate_request_id),
  CONSTRAINT ck_certificate_request_requester_kind CHECK (requester_kind IN ('STUDENT_SELF', 'SCHOOL_PARENT')),
  CONSTRAINT ck_certificate_request_request_type CHECK (request_type IN ('BONAFIDE', 'TRANSFER_CERTIFICATE', 'RECORD_CORRECTION', 'OTHER')),
  CONSTRAINT ck_certificate_request_details CHECK (char_length(details) BETWEEN 1 AND 2000),
  CONSTRAINT ck_certificate_request_no_dues_result CHECK (no_dues_result IN ('CLEAR', 'DUES_PENDING')),
  CONSTRAINT ck_certificate_request_no_dues_override_reason CHECK (char_length(no_dues_override_reason) BETWEEN 1 AND 500),
  CONSTRAINT ck_certificate_request_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'PREPARED', 'APPLIED', 'CANCELLED')),
  CONSTRAINT ck_certificate_request_row_version CHECK (row_version >= 1),
  CONSTRAINT ck_certificate_request_r1 CHECK ((request_type = 'RECORD_CORRECTION') = (correction_payload IS NOT NULL)),
  CONSTRAINT ck_certificate_request_r2 CHECK ((no_dues_override_by IS NULL) = (no_dues_override_at IS NULL) AND (no_dues_override_by IS NULL) = (no_dues_override_reason IS NULL)),
  CONSTRAINT ck_certificate_request_r3 CHECK (status <> 'PREPARED' OR (request_type <> 'RECORD_CORRECTION' AND prepared_by_user_id IS NOT NULL AND prepared_at IS NOT NULL AND file_id IS NOT NULL)),
  CONSTRAINT ck_certificate_request_r4 CHECK (status <> 'APPLIED' OR (request_type = 'RECORD_CORRECTION' AND applied_by_user_id IS NOT NULL AND applied_at IS NOT NULL)),
  CONSTRAINT ck_certificate_request_r5 CHECK (status NOT IN ('APPROVED','PREPARED','APPLIED') OR no_dues_result = 'CLEAR' OR no_dues_override_by IS NOT NULL),
  CONSTRAINT ck_certificate_request_upd CHECK (updated_at >= created_at),
  CONSTRAINT uq_certificate_request_tid UNIQUE (tenant_id, certificate_request_id),
  CONSTRAINT uq_certificate_request_tiid UNIQUE (tenant_id, institution_id, certificate_request_id)
);
COMMENT ON TABLE app.certificate_request IS 'Certificate, TC or record-correction request (Phase 1 records requests; generation later [OB-11]). Record corrections are applied by the College / School Admin after approval (RECORD_CORRECTION_APPLY, V3.3).';
COMMENT ON COLUMN app.certificate_request.tenant_id IS 'Owning tenant. RLS key; first column of every composite FK (PTD-02, PTD-03). Never accepted from a request body. [PQ-64, PQ-70]';
COMMENT ON COLUMN app.certificate_request.certificate_request_id IS 'Primary key. UUID v7 generated by the application (PTD-01). [PQ-23]';
COMMENT ON COLUMN app.certificate_request.student_profile_id IS 'Student.';
COMMENT ON COLUMN app.certificate_request.institution_id IS 'Owning institution (college or school). [§6.1]';
COMMENT ON COLUMN app.certificate_request.requested_by_user_id IS 'College student (self) or school parent [Q11].';
COMMENT ON COLUMN app.certificate_request.requester_kind IS 'College parents cannot request [Q11].';
COMMENT ON COLUMN app.certificate_request.request_type IS 'Types (list OD-56). (OD-56)';
COMMENT ON COLUMN app.certificate_request.details IS 'Details. Length 1–2000.';
COMMENT ON COLUMN app.certificate_request.correction_payload IS 'Field + new value from an allow-list; required for RECORD_CORRECTION.';
COMMENT ON COLUMN app.certificate_request.no_dues_result IS 'Overdue check at request [FE-24].';
COMMENT ON COLUMN app.certificate_request.no_dues_override_by IS 'Principal (NO_DUES_OVERRIDE).';
COMMENT ON COLUMN app.certificate_request.no_dues_override_at IS 'Time.';
COMMENT ON COLUMN app.certificate_request.no_dues_override_reason IS 'Reason. Length 1–500.';
COMMENT ON COLUMN app.certificate_request.approval_request_id IS 'Chain CERTIFICATE_REQUEST (Principal).';
COMMENT ON COLUMN app.certificate_request.status IS 'PREPARED (certificates, Records Officer) or APPLIED (record corrections, RECORD_CORRECTION_APPLY).';
COMMENT ON COLUMN app.certificate_request.prepared_by_user_id IS 'Records Officer.';
COMMENT ON COLUMN app.certificate_request.prepared_at IS 'Time.';
COMMENT ON COLUMN app.certificate_request.file_id IS 'Prepared document.';
COMMENT ON COLUMN app.certificate_request.applied_by_user_id IS 'College / School Admin.';
COMMENT ON COLUMN app.certificate_request.applied_at IS 'Time.';
COMMENT ON COLUMN app.certificate_request.created_at IS 'Server time of insert (UTC). [PTD-05, PTD-09]';
COMMENT ON COLUMN app.certificate_request.created_by IS 'user_account_id of the actor; reserved SYSTEM user for jobs (PTD-23). No FK, so bootstrap rows can be written. [PTD-09]';
COMMENT ON COLUMN app.certificate_request.updated_at IS 'Server time of last change; set by trigger trg_touch. [PTD-09]';
COMMENT ON COLUMN app.certificate_request.updated_by IS 'Actor of the last change. [PTD-09]';
COMMENT ON COLUMN app.certificate_request.row_version IS 'Optimistic-concurrency counter; incremented by trigger trg_touch. Exposed to API clients as ETag/version. [PTD-08, TE-09]';
CREATE UNIQUE INDEX ux_module_dependency_1 ON ref.module_dependency (module_code, requires_module_code);
CREATE UNIQUE INDEX ux_standard_role_action_1 ON ref.standard_role_action (role_code, action_code);
CREATE UNIQUE INDEX ux_permission_matrix_cell_1 ON ref.permission_matrix_cell (role_code, feature_area_code);
CREATE UNIQUE INDEX ux_approval_chain_step_1 ON ref.approval_chain_step (process_code, step_no);
CREATE UNIQUE INDEX ux_safe_file_type_1 ON ref.safe_file_type (purpose, mime_type);
CREATE UNIQUE INDEX ux_plan_entitlement_1 ON platform.plan_entitlement (plan_id, module_code, feature_code) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_tenant_entitlement_1 ON platform.tenant_entitlement (tenant_id, module_code, feature_code) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_tenant_retention_policy_1 ON platform.tenant_retention_policy (tenant_id, data_class_code) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX ux_tenant_usage_monthly_1 ON platform.tenant_usage_monthly (tenant_id, usage_month, metric_code);
CREATE UNIQUE INDEX ux_usage_metric_daily_1 ON platform.usage_metric_daily (usage_date, metric_code);
CREATE UNIQUE INDEX ux_setting_value_1 ON platform.setting_value (setting_key) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX ux_setting_value_2 ON platform.setting_value (setting_key, version_no);
CREATE UNIQUE INDEX ux_tenant_provision_step_1 ON platform.tenant_provision_step (tenant_id, step_code);
CREATE UNIQUE INDEX ux_schema_release_1 ON platform.schema_release (release_id, schema_version);
CREATE UNIQUE INDEX ux_tenant_relocation_1 ON platform.tenant_relocation (tenant_id) WHERE step NOT IN ('SOURCE_PURGED','ABORTED');
CREATE UNIQUE INDEX ux_payment_route_1 ON platform.payment_route (provider, gateway_order_id);
CREATE UNIQUE INDEX ux_payment_unmatched_event_1 ON platform.payment_unmatched_event (provider, provider_event_id);
CREATE UNIQUE INDEX ux_institution_1 ON app.institution (tenant_id, institution_code);
CREATE UNIQUE INDEX ux_institution_2 ON app.institution (tenant_id, institution_name);
CREATE UNIQUE INDEX ux_institution_3 ON app.institution (tenant_id, institution_id, institution_type);
CREATE UNIQUE INDEX ux_leave_approver_setting_1 ON app.leave_approver_setting (tenant_id, subject_role, institution_id) NULLS NOT DISTINCT WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX ux_academic_session_1 ON app.academic_session (tenant_id, institution_id, session_label) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_department_1 ON app.department (tenant_id, institution_id, department_code);
CREATE UNIQUE INDEX ux_program_1 ON app.program (tenant_id, institution_id, program_code);
CREATE UNIQUE INDEX ux_program_rule_set_1 ON app.program_rule_set (tenant_id, institution_id, program_id, version_no) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_program_year_1 ON app.program_year (tenant_id, program_id, year_number);
CREATE UNIQUE INDEX ux_semester_1 ON app.semester (tenant_id, program_year_id, semester_number);
CREATE UNIQUE INDEX ux_school_class_1 ON app.school_class (tenant_id, institution_id, class_name);
CREATE UNIQUE INDEX ux_section_1 ON app.section (tenant_id, academic_session_id, semester_id, class_id, section_name) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_subject_1 ON app.subject (tenant_id, institution_id, subject_code);
CREATE UNIQUE INDEX ux_syllabus_1 ON app.syllabus (tenant_id, subject_id, academic_session_id, version_no) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_course_offering_1 ON app.course_offering (tenant_id, subject_id, academic_session_id, semester_id, class_id) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_subject_group_1 ON app.subject_group (tenant_id, course_offering_id, group_name);
CREATE UNIQUE INDEX ux_subject_group_member_1 ON app.subject_group_member (tenant_id, subject_group_id, enrollment_id);
CREATE UNIQUE INDEX ux_user_account_1 ON app.user_account (tenant_id, email) WHERE user_type IN ('STAFF','GOVERNANCE') AND email IS NOT NULL;
CREATE UNIQUE INDEX ux_user_account_2 ON app.user_account (tenant_id, mobile) WHERE user_type = 'GUARDIAN';
CREATE UNIQUE INDEX ux_user_identity_link_1 ON app.user_identity_link (tenant_id, identity_realm, identity_subject);
CREATE UNIQUE INDEX ux_user_identity_link_2 ON app.user_identity_link (tenant_id, user_account_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX ux_user_device_1 ON app.user_device (tenant_id, user_account_id, device_class) WHERE revoked_at IS NULL;
CREATE UNIQUE INDEX ux_staff_profile_1 ON app.staff_profile (tenant_id, staff_code);
CREATE UNIQUE INDEX ux_staff_profile_2 ON app.staff_profile (tenant_id, user_account_id) WHERE user_account_id IS NOT NULL;
CREATE UNIQUE INDEX ux_role_definition_1 ON app.role_definition (tenant_id, role_code);
CREATE UNIQUE INDEX ux_role_action_1 ON app.role_action (tenant_id, role_definition_id, action_code);
CREATE UNIQUE INDEX ux_user_role_assignment_1 ON app.user_role_assignment (tenant_id, user_account_id, role_definition_id, scope_type, institution_id, department_id) NULLS NOT DISTINCT WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX ux_approval_request_1 ON app.approval_request (tenant_id, subject_type, subject_id) WHERE status IN ('PENDING','BLOCKED');
CREATE UNIQUE INDEX ux_approval_step_1 ON app.approval_step (tenant_id, approval_request_id, step_no);
CREATE UNIQUE INDEX ux_approval_step_2 ON app.approval_step (tenant_id, approval_request_id, acted_by_user_id) WHERE decision IN ('APPROVED','REJECTED','FORWARDED');
CREATE UNIQUE INDEX ux_student_profile_1 ON app.student_profile (tenant_id, student_code);
CREATE UNIQUE INDEX ux_student_profile_2 ON app.student_profile (tenant_id, user_account_id) WHERE user_account_id IS NOT NULL;
CREATE UNIQUE INDEX ux_student_code_sequence_1 ON app.student_code_sequence (tenant_id, institution_id);
CREATE UNIQUE INDEX ux_duplicate_review_1 ON app.duplicate_review (tenant_id, record_type, record_a_id, record_b_id);
CREATE UNIQUE INDEX ux_guardian_profile_1 ON app.guardian_profile (tenant_id, user_account_id);
CREATE UNIQUE INDEX ux_guardian_profile_2 ON app.guardian_profile (tenant_id, mobile);
CREATE UNIQUE INDEX ux_student_guardian_link_1 ON app.student_guardian_link (tenant_id, student_profile_id, guardian_profile_id);
CREATE UNIQUE INDEX ux_student_guardian_link_2 ON app.student_guardian_link (tenant_id, student_profile_id) WHERE is_primary AND status = 'ACTIVE';
CREATE UNIQUE INDEX ux_student_enrollment_1 ON app.student_enrollment (tenant_id, student_profile_id, academic_session_id);
CREATE UNIQUE INDEX ux_student_enrollment_2 ON app.student_enrollment (tenant_id, section_id, roll_number) WHERE roll_number IS NOT NULL;
CREATE UNIQUE INDEX ux_student_enrollment_3 ON app.student_enrollment (tenant_id, student_profile_id) WHERE outcome = 'IN_PROGRESS';
CREATE UNIQUE INDEX ux_promotion_run_1 ON app.promotion_run (tenant_id, institution_id, from_session_id) WHERE status <> 'REJECTED';
CREATE UNIQUE INDEX ux_promotion_run_item_1 ON app.promotion_run_item (tenant_id, promotion_run_id, enrollment_id);
CREATE UNIQUE INDEX ux_subject_allocation_1 ON app.subject_allocation (tenant_id, course_offering_id, section_id, subject_group_id, staff_assignment_id) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_mentor_assignment_1 ON app.mentor_assignment (tenant_id, section_id) WHERE effective_to IS NULL;
CREATE UNIQUE INDEX ux_class_teacher_assignment_1 ON app.class_teacher_assignment (tenant_id, section_id) WHERE effective_to IS NULL;
CREATE UNIQUE INDEX ux_meeting_link_1 ON app.meeting_link (tenant_id, timetable_slot_id, occurrence_date);
CREATE UNIQUE INDEX ux_attendance_session_1 ON app.attendance_session (tenant_id, section_id, subject_group_id, timetable_slot_id, session_kind, attendance_date) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_attendance_session_2 ON app.attendance_session (tenant_id, client_draft_id) WHERE client_draft_id IS NOT NULL;
CREATE UNIQUE INDEX ux_attendance_sync_conflict_1 ON app.attendance_sync_conflict (tenant_id, client_draft_id);
CREATE UNIQUE INDEX ux_attendance_entry_1 ON app.attendance_entry (tenant_id, attendance_session_id, enrollment_id, academic_year);
CREATE UNIQUE INDEX ux_attendance_correction_1 ON app.attendance_correction (tenant_id, attendance_correction_request_id, attendance_entry_id);
CREATE UNIQUE INDEX ux_term_lock_1 ON app.term_lock (tenant_id, institution_id, academic_session_id, semester_id) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_risk_flag_1 ON app.risk_flag (tenant_id, enrollment_id);
CREATE UNIQUE INDEX ux_leave_type_1 ON app.leave_type (tenant_id, institution_id, audience, leave_code);
CREATE UNIQUE INDEX ux_leave_balance_1 ON app.leave_balance (tenant_id, staff_profile_id, leave_type_id, leave_year);
CREATE UNIQUE INDEX ux_grading_scheme_version_1 ON app.grading_scheme_version (tenant_id, institution_id, scheme_name, version_no) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_grade_band_1 ON app.grade_band (tenant_id, grading_scheme_version_id, lower_percent);
CREATE UNIQUE INDEX ux_grade_band_2 ON app.grade_band (tenant_id, grading_scheme_version_id, grade_label);
CREATE UNIQUE INDEX ux_exam_rule_1 ON app.exam_rule (tenant_id, institution_id, rule_name, version_no) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_exam_pattern_1 ON app.exam_pattern (tenant_id, institution_id, pattern_name, version_no);
CREATE UNIQUE INDEX ux_exam_pattern_component_1 ON app.exam_pattern_component (tenant_id, exam_pattern_id, component_name);
CREATE UNIQUE INDEX ux_exam_1 ON app.exam (tenant_id, institution_id, academic_session_id, exam_name);
CREATE UNIQUE INDEX ux_exam_schedule_item_1 ON app.exam_schedule_item (tenant_id, exam_id, course_offering_id);
CREATE UNIQUE INDEX ux_hall_ticket_1 ON app.hall_ticket (tenant_id, exam_id, enrollment_id);
CREATE UNIQUE INDEX ux_marks_submission_1 ON app.marks_submission (tenant_id, exam_id, course_offering_id, section_id, subject_group_id) NULLS NOT DISTINCT WHERE status NOT IN ('REJECTED','WITHDRAWN');
CREATE UNIQUE INDEX ux_marks_entry_1 ON app.marks_entry (tenant_id, marks_submission_id, enrollment_id, component_id);
CREATE UNIQUE INDEX ux_backlog_1 ON app.backlog (tenant_id, student_profile_id, subject_id) WHERE status <> 'CLEARED';
CREATE UNIQUE INDEX ux_supplementary_registration_1 ON app.supplementary_registration (tenant_id, backlog_id, exam_id);
CREATE UNIQUE INDEX ux_result_1 ON app.result (tenant_id, exam_id, enrollment_id, version_no);
CREATE UNIQUE INDEX ux_result_line_1 ON app.result_line (tenant_id, result_id, subject_id);
CREATE UNIQUE INDEX ux_result_publication_1 ON app.result_publication (tenant_id, exam_id, section_id) NULLS NOT DISTINCT WHERE status = 'PUBLISHED';
CREATE UNIQUE INDEX ux_result_withdrawal_request_1 ON app.result_withdrawal_request (tenant_id, result_publication_id) WHERE status IN ('REQUESTED','APPROVED','EXECUTED');
CREATE UNIQUE INDEX ux_fee_head_1 ON app.fee_head (tenant_id, fee_head_code);
CREATE UNIQUE INDEX ux_fee_policy_1 ON app.fee_policy (tenant_id, institution_id, version_no) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_fee_head_rule_1 ON app.fee_head_rule (tenant_id, fee_policy_id, fee_head_id);
CREATE UNIQUE INDEX ux_fee_structure_1 ON app.fee_structure (tenant_id, institution_id, academic_session_id, program_year_id, class_id, version_no) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_fee_structure_2 ON app.fee_structure (tenant_id, institution_id, academic_session_id, program_year_id, class_id) NULLS NOT DISTINCT WHERE status = 'LIVE';
CREATE UNIQUE INDEX ux_fee_structure_line_1 ON app.fee_structure_line (tenant_id, fee_structure_id, fee_head_id);
CREATE UNIQUE INDEX ux_fee_structure_installment_1 ON app.fee_structure_installment (tenant_id, fee_structure_id, installment_no);
CREATE UNIQUE INDEX ux_student_fee_account_1 ON app.student_fee_account (tenant_id, student_profile_id);
CREATE UNIQUE INDEX ux_student_installment_1 ON app.student_installment (tenant_id, fee_assignment_id, installment_no);
CREATE UNIQUE INDEX ux_ledger_entry_1 ON app.ledger_entry (tenant_id, reverses_entry_id) WHERE reverses_entry_id IS NOT NULL;
CREATE UNIQUE INDEX ux_payment_1 ON app.payment (tenant_id, request_key);
CREATE UNIQUE INDEX ux_payment_2 ON app.payment (tenant_id, student_fee_account_id) WHERE method = 'GATEWAY' AND status IN ('STARTED','PENDING');
CREATE UNIQUE INDEX ux_payment_intent_item_1 ON app.payment_intent_item (tenant_id, payment_id, student_installment_id);
CREATE UNIQUE INDEX ux_gateway_order_1 ON app.gateway_order (tenant_id, payment_id);
CREATE UNIQUE INDEX ux_gateway_order_2 ON app.gateway_order (tenant_id, provider, provider_order_id);
CREATE UNIQUE INDEX ux_gateway_transaction_1 ON app.gateway_transaction (tenant_id, provider_payment_id);
CREATE UNIQUE INDEX ux_gateway_transaction_2 ON app.gateway_transaction (tenant_id, gateway_order_id) WHERE status = 'CAPTURED';
CREATE UNIQUE INDEX ux_payment_gateway_event_1 ON app.payment_gateway_event (tenant_id, provider, provider_event_id);
CREATE UNIQUE INDEX ux_payment_allocation_1 ON app.payment_allocation (tenant_id, student_installment_id);
CREATE UNIQUE INDEX ux_credit_application_1 ON app.credit_application (tenant_id, student_installment_id);
CREATE UNIQUE INDEX ux_receipt_sequence_1 ON app.receipt_sequence (tenant_id, institution_id, financial_year);
CREATE UNIQUE INDEX ux_receipt_1 ON app.receipt (tenant_id, institution_id, financial_year, receipt_number);
CREATE UNIQUE INDEX ux_receipt_2 ON app.receipt (tenant_id, payment_id) WHERE status = 'ISSUED';
CREATE UNIQUE INDEX ux_scholarship_scheme_1 ON app.scholarship_scheme (tenant_id, institution_id, academic_session_id, scheme_name);
CREATE UNIQUE INDEX ux_scholarship_award_1 ON app.scholarship_award (tenant_id, list_id, enrollment_id);
CREATE UNIQUE INDEX ux_late_fee_waiver_1 ON app.late_fee_waiver (tenant_id, late_fee_ledger_entry_id);
CREATE UNIQUE INDEX ux_financial_period_lock_1 ON app.financial_period_lock (tenant_id, institution_id, period_month) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_assignment_submission_1 ON app.assignment_submission (tenant_id, assignment_id, enrollment_id);
CREATE UNIQUE INDEX ux_assignment_submission_file_1 ON app.assignment_submission_file (tenant_id, submission_id, file_id);
CREATE UNIQUE INDEX ux_assignment_feedback_1 ON app.assignment_feedback (tenant_id, submission_id);
CREATE UNIQUE INDEX ux_homework_status_1 ON app.homework_status (tenant_id, assignment_id, enrollment_id);
CREATE UNIQUE INDEX ux_notice_audience_1 ON app.notice_audience (tenant_id, notice_id, audience_group, role_definition_id) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_message_thread_participant_1 ON app.message_thread_participant (tenant_id, thread_id, user_account_id);
CREATE UNIQUE INDEX ux_message_attachment_1 ON app.message_attachment (tenant_id, message_id, file_id);
CREATE UNIQUE INDEX ux_notification_1 ON app.notification (tenant_id, recipient_user_id, dedupe_key) WHERE dedupe_key IS NOT NULL;
CREATE UNIQUE INDEX ux_notification_delivery_1 ON app.notification_delivery (tenant_id, notification_id, channel);
CREATE UNIQUE INDEX ux_notification_delivery_attempt_1 ON app.notification_delivery_attempt (tenant_id, delivery_id, attempt_no);
CREATE UNIQUE INDEX ux_push_subscription_1 ON app.push_subscription (tenant_id, user_device_id);
CREATE UNIQUE INDEX ux_channel_config_1 ON app.channel_config (tenant_id, institution_id, channel);
CREATE UNIQUE INDEX ux_sms_template_1 ON app.sms_template (tenant_id, institution_id, trigger_code, dlt_template_id);
CREATE UNIQUE INDEX ux_community_post_media_1 ON app.community_post_media (tenant_id, post_id, file_id);
CREATE UNIQUE INDEX ux_post_like_1 ON app.post_like (tenant_id, post_id, user_account_id);
CREATE UNIQUE INDEX ux_event_rsvp_1 ON app.event_rsvp (tenant_id, event_id, user_account_id);
CREATE UNIQUE INDEX ux_policy_1 ON app.policy (tenant_id, institution_id, title, version_no) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_policy_acceptance_1 ON app.policy_acceptance (tenant_id, policy_id, user_account_id);
CREATE UNIQUE INDEX ux_accreditation_criterion_1 ON app.accreditation_criterion (tenant_id, institution_id, criterion_label) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_accreditation_evidence_map_1 ON app.accreditation_evidence_map (tenant_id, accreditation_document_id, criterion_id);
CREATE UNIQUE INDEX ux_data_request_1 ON app.data_request (tenant_id, request_number);
CREATE UNIQUE INDEX ux_import_row_error_1 ON app.import_row_error (tenant_id, import_job_id, row_number);
CREATE UNIQUE INDEX ux_background_job_1 ON app.background_job (tenant_id, job_type, dedupe_key) WHERE dedupe_key IS NOT NULL;
CREATE UNIQUE INDEX ux_idempotency_key_1 ON app.idempotency_key (tenant_id, scope, caller_user_id, request_key);
CREATE UNIQUE INDEX ux_setting_value_1 ON app.setting_value (tenant_id, setting_key, scope_level, institution_id) NULLS NOT DISTINCT WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX ux_setting_value_2 ON app.setting_value (tenant_id, setting_key, scope_level, institution_id, version_no) NULLS NOT DISTINCT;
CREATE UNIQUE INDEX ux_saved_filter_1 ON app.saved_filter (tenant_id, user_account_id, report_code, filter_name);
CREATE UNIQUE INDEX ux_dashboard_preference_1 ON app.dashboard_preference (tenant_id, user_account_id);
CREATE UNIQUE INDEX ux_usage_metric_daily_1 ON app.usage_metric_daily (tenant_id, institution_id, usage_date, metric_code) NULLS NOT DISTINCT;
ALTER TABLE ref.feature_catalog ADD CONSTRAINT fk_feature_catalog_module_code FOREIGN KEY (module_code) REFERENCES ref.module_catalog (module_code) ON DELETE RESTRICT;
ALTER TABLE ref.module_dependency ADD CONSTRAINT fk_module_dependency_module_code FOREIGN KEY (module_code) REFERENCES ref.module_catalog (module_code) ON DELETE RESTRICT;
ALTER TABLE ref.module_dependency ADD CONSTRAINT fk_module_dependency_requires_module_code FOREIGN KEY (requires_module_code) REFERENCES ref.module_catalog (module_code) ON DELETE RESTRICT;
ALTER TABLE ref.standard_role_action ADD CONSTRAINT fk_standard_role_action_role_code FOREIGN KEY (role_code) REFERENCES ref.standard_role (role_code) ON DELETE RESTRICT;
ALTER TABLE ref.standard_role_action ADD CONSTRAINT fk_standard_role_action_action_code FOREIGN KEY (action_code) REFERENCES ref.action_catalog (action_code) ON DELETE RESTRICT;
ALTER TABLE ref.permission_matrix_cell ADD CONSTRAINT fk_permission_matrix_cell_role_code FOREIGN KEY (role_code) REFERENCES ref.standard_role (role_code) ON DELETE RESTRICT;
ALTER TABLE ref.permission_matrix_cell ADD CONSTRAINT fk_permission_matrix_cell_feature_area_code FOREIGN KEY (feature_area_code) REFERENCES ref.feature_area (feature_area_code) ON DELETE RESTRICT;
ALTER TABLE ref.approval_chain_step ADD CONSTRAINT fk_approval_chain_step_process_code FOREIGN KEY (process_code) REFERENCES ref.approval_process (process_code) ON DELETE RESTRICT;
ALTER TABLE ref.approval_chain_step ADD CONSTRAINT fk_approval_chain_step_action_code FOREIGN KEY (action_code) REFERENCES ref.action_catalog (action_code) ON DELETE RESTRICT;
ALTER TABLE ref.report_catalog ADD CONSTRAINT fk_report_catalog_module_code FOREIGN KEY (module_code) REFERENCES ref.module_catalog (module_code) ON DELETE RESTRICT;
ALTER TABLE ref.report_catalog ADD CONSTRAINT fk_report_catalog_required_action_code FOREIGN KEY (required_action_code) REFERENCES ref.action_catalog (action_code) ON DELETE RESTRICT;
ALTER TABLE ref.setting_definition ADD CONSTRAINT fk_setting_definition_owner_action_code FOREIGN KEY (owner_action_code) REFERENCES ref.action_catalog (action_code) ON DELETE RESTRICT;
ALTER TABLE platform.plan_entitlement ADD CONSTRAINT fk_plan_entitlement_plan_id FOREIGN KEY (plan_id) REFERENCES platform.plan (plan_id) ON DELETE RESTRICT;
ALTER TABLE platform.plan_entitlement ADD CONSTRAINT fk_plan_entitlement_module_code FOREIGN KEY (module_code) REFERENCES ref.module_catalog (module_code) ON DELETE RESTRICT;
ALTER TABLE platform.plan_entitlement ADD CONSTRAINT fk_plan_entitlement_feature_code FOREIGN KEY (feature_code) REFERENCES ref.feature_catalog (feature_code) ON DELETE RESTRICT;
ALTER TABLE platform.tenant ADD CONSTRAINT fk_tenant_plan_id FOREIGN KEY (plan_id) REFERENCES platform.plan (plan_id) ON DELETE RESTRICT;
ALTER TABLE platform.tenant_entitlement ADD CONSTRAINT fk_tenant_entitlement_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.tenant_entitlement ADD CONSTRAINT fk_tenant_entitlement_module_code FOREIGN KEY (module_code) REFERENCES ref.module_catalog (module_code) ON DELETE RESTRICT;
ALTER TABLE platform.tenant_entitlement ADD CONSTRAINT fk_tenant_entitlement_feature_code FOREIGN KEY (feature_code) REFERENCES ref.feature_catalog (feature_code) ON DELETE RESTRICT;
ALTER TABLE platform.tenant_retention_policy ADD CONSTRAINT fk_tenant_retention_policy_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.tenant_retention_policy ADD CONSTRAINT fk_tenant_retention_policy_data_class_code FOREIGN KEY (data_class_code) REFERENCES ref.data_class (data_class_code) ON DELETE RESTRICT;
ALTER TABLE platform.retention_change_request ADD CONSTRAINT fk_retention_change_request_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.retention_change_request ADD CONSTRAINT fk_retention_change_request_data_class_code FOREIGN KEY (data_class_code) REFERENCES ref.data_class (data_class_code) ON DELETE RESTRICT;
ALTER TABLE platform.retention_change_request ADD CONSTRAINT fk_retention_change_request_requested_by FOREIGN KEY (requested_by) REFERENCES platform.platform_user (platform_user_id) ON DELETE RESTRICT;
ALTER TABLE platform.retention_change_request ADD CONSTRAINT fk_retention_change_request_decided_by FOREIGN KEY (decided_by) REFERENCES platform.platform_user (platform_user_id) ON DELETE RESTRICT;
ALTER TABLE platform.invoice ADD CONSTRAINT fk_invoice_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.invoice ADD CONSTRAINT fk_invoice_discount_applied_by FOREIGN KEY (discount_applied_by) REFERENCES platform.platform_user (platform_user_id) ON DELETE RESTRICT;
ALTER TABLE platform.invoice_line ADD CONSTRAINT fk_invoice_line_invoice_id FOREIGN KEY (invoice_id) REFERENCES platform.invoice (invoice_id) ON DELETE RESTRICT;
ALTER TABLE platform.invoice_line ADD CONSTRAINT fk_invoice_line_tenant_usage_monthly_id FOREIGN KEY (tenant_usage_monthly_id) REFERENCES platform.tenant_usage_monthly (tenant_usage_monthly_id) ON DELETE RESTRICT;
ALTER TABLE platform.tenant_usage_monthly ADD CONSTRAINT fk_tenant_usage_monthly_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.support_ticket ADD CONSTRAINT fk_support_ticket_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.support_ticket ADD CONSTRAINT fk_support_ticket_assigned_to FOREIGN KEY (assigned_to) REFERENCES platform.platform_user (platform_user_id) ON DELETE RESTRICT;
ALTER TABLE platform.support_access_grant ADD CONSTRAINT fk_support_access_grant_ticket_id FOREIGN KEY (ticket_id) REFERENCES platform.support_ticket (support_ticket_id) ON DELETE RESTRICT;
ALTER TABLE platform.support_access_grant ADD CONSTRAINT fk_support_access_grant_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.export_request ADD CONSTRAINT fk_export_request_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.export_request ADD CONSTRAINT fk_export_request_decided_by FOREIGN KEY (decided_by) REFERENCES platform.platform_user (platform_user_id) ON DELETE RESTRICT;
ALTER TABLE platform.deletion_request ADD CONSTRAINT fk_deletion_request_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.deletion_request ADD CONSTRAINT fk_deletion_request_po_confirmed_by FOREIGN KEY (po_confirmed_by) REFERENCES platform.platform_user (platform_user_id) ON DELETE RESTRICT;
ALTER TABLE platform.deletion_request ADD CONSTRAINT fk_deletion_request_export_request_id FOREIGN KEY (export_request_id) REFERENCES platform.export_request (export_request_id) ON DELETE RESTRICT;
ALTER TABLE platform.deletion_request ADD CONSTRAINT fk_deletion_request_dpo_decided_by FOREIGN KEY (dpo_decided_by) REFERENCES platform.platform_user (platform_user_id) ON DELETE RESTRICT;
ALTER TABLE platform.deletion_certificate ADD CONSTRAINT fk_deletion_certificate_deletion_request_id FOREIGN KEY (deletion_request_id) REFERENCES platform.deletion_request (deletion_request_id) ON DELETE RESTRICT;
ALTER TABLE platform.deletion_certificate ADD CONSTRAINT fk_deletion_certificate_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.deletion_certificate ADD CONSTRAINT fk_deletion_certificate_approved_by FOREIGN KEY (approved_by) REFERENCES platform.platform_user (platform_user_id) ON DELETE RESTRICT;
ALTER TABLE platform.tenant_backup_status ADD CONSTRAINT fk_tenant_backup_status_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.setting_value ADD CONSTRAINT fk_setting_value_setting_key FOREIGN KEY (setting_key) REFERENCES ref.setting_definition (setting_key) ON DELETE RESTRICT;
ALTER TABLE platform.audit_outbox ADD CONSTRAINT fk_audit_outbox_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.audit_outbox ADD CONSTRAINT fk_audit_outbox_actor_user_id FOREIGN KEY (actor_user_id) REFERENCES platform.platform_user (platform_user_id) ON DELETE RESTRICT;
ALTER TABLE platform.audit_outbox ADD CONSTRAINT fk_audit_outbox_action_code FOREIGN KEY (action_code) REFERENCES ref.action_catalog (action_code) ON DELETE RESTRICT;
ALTER TABLE platform.tenant_provision_step ADD CONSTRAINT fk_tenant_provision_step_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.schema_release ADD CONSTRAINT fk_schema_release_release_id FOREIGN KEY (release_id) REFERENCES platform.release (release_id) ON DELETE RESTRICT;
ALTER TABLE platform.tenant_migration_state ADD CONSTRAINT fk_tenant_migration_state_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.migration_run ADD CONSTRAINT fk_migration_run_release_id FOREIGN KEY (release_id) REFERENCES platform.release (release_id) ON DELETE RESTRICT;
ALTER TABLE platform.migration_run ADD CONSTRAINT fk_migration_run_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.tenant_relocation ADD CONSTRAINT fk_tenant_relocation_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.payment_route ADD CONSTRAINT fk_payment_route_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.security_incident ADD CONSTRAINT fk_security_incident_tenant_id FOREIGN KEY (tenant_id) REFERENCES platform.tenant (tenant_id) ON DELETE RESTRICT;
ALTER TABLE platform.security_incident ADD CONSTRAINT fk_security_incident_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES platform.platform_user (platform_user_id) ON DELETE RESTRICT;
ALTER TABLE app.institution ADD CONSTRAINT fk_institution_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_approver_setting ADD CONSTRAINT fk_leave_approver_setting_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_approver_setting ADD CONSTRAINT fk_leave_approver_setting_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_approver_setting ADD CONSTRAINT fk_leave_approver_setting_approver_user_id FOREIGN KEY (tenant_id, approver_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.academic_session ADD CONSTRAINT fk_academic_session_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.academic_session ADD CONSTRAINT fk_academic_session_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.academic_calendar_event ADD CONSTRAINT fk_academic_calendar_event_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.academic_calendar_event ADD CONSTRAINT fk_academic_calendar_event_academic_session_id FOREIGN KEY (tenant_id, academic_session_id) REFERENCES app.academic_session (tenant_id, academic_session_id) ON DELETE RESTRICT;
ALTER TABLE app.academic_calendar_event ADD CONSTRAINT fk_academic_calendar_event_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.weekly_off_rule ADD CONSTRAINT fk_weekly_off_rule_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.weekly_off_rule ADD CONSTRAINT fk_weekly_off_rule_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.department ADD CONSTRAINT fk_department_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.department ADD CONSTRAINT fk_department_institution_typed FOREIGN KEY (tenant_id, institution_id, institution_type) REFERENCES app.institution (tenant_id, institution_id, institution_type) ON DELETE RESTRICT;
ALTER TABLE app.department ADD CONSTRAINT fk_department_hod_staff_assignment_id FOREIGN KEY (tenant_id, hod_staff_assignment_id) REFERENCES app.staff_assignment (tenant_id, staff_assignment_id) ON DELETE RESTRICT;
ALTER TABLE app.program ADD CONSTRAINT fk_program_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.program ADD CONSTRAINT fk_program_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.program ADD CONSTRAINT fk_program_department_id FOREIGN KEY (tenant_id, institution_id, department_id) REFERENCES app.department (tenant_id, institution_id, department_id) ON DELETE RESTRICT;
ALTER TABLE app.program_rule_set ADD CONSTRAINT fk_program_rule_set_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.program_rule_set ADD CONSTRAINT fk_program_rule_set_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.program_rule_set ADD CONSTRAINT fk_program_rule_set_program_id FOREIGN KEY (tenant_id, program_id) REFERENCES app.program (tenant_id, program_id) ON DELETE RESTRICT;
ALTER TABLE app.program_year ADD CONSTRAINT fk_program_year_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.program_year ADD CONSTRAINT fk_program_year_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.program_year ADD CONSTRAINT fk_program_year_program_id FOREIGN KEY (tenant_id, institution_id, program_id) REFERENCES app.program (tenant_id, institution_id, program_id) ON DELETE RESTRICT;
ALTER TABLE app.semester ADD CONSTRAINT fk_semester_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.semester ADD CONSTRAINT fk_semester_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.semester ADD CONSTRAINT fk_semester_program_year_id FOREIGN KEY (tenant_id, institution_id, program_year_id) REFERENCES app.program_year (tenant_id, institution_id, program_year_id) ON DELETE RESTRICT;
ALTER TABLE app.school_class ADD CONSTRAINT fk_school_class_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.school_class ADD CONSTRAINT fk_school_class_institution_typed FOREIGN KEY (tenant_id, institution_id, institution_type) REFERENCES app.institution (tenant_id, institution_id, institution_type) ON DELETE RESTRICT;
ALTER TABLE app.section ADD CONSTRAINT fk_section_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.section ADD CONSTRAINT fk_section_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.section ADD CONSTRAINT fk_section_academic_session_id FOREIGN KEY (tenant_id, academic_session_id) REFERENCES app.academic_session (tenant_id, academic_session_id) ON DELETE RESTRICT;
ALTER TABLE app.section ADD CONSTRAINT fk_section_semester_id FOREIGN KEY (tenant_id, institution_id, semester_id) REFERENCES app.semester (tenant_id, institution_id, semester_id) ON DELETE RESTRICT;
ALTER TABLE app.section ADD CONSTRAINT fk_section_class_id FOREIGN KEY (tenant_id, institution_id, class_id) REFERENCES app.school_class (tenant_id, institution_id, class_id) ON DELETE RESTRICT;
ALTER TABLE app.subject ADD CONSTRAINT fk_subject_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.subject ADD CONSTRAINT fk_subject_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.syllabus ADD CONSTRAINT fk_syllabus_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.syllabus ADD CONSTRAINT fk_syllabus_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.syllabus ADD CONSTRAINT fk_syllabus_subject_id FOREIGN KEY (tenant_id, institution_id, subject_id) REFERENCES app.subject (tenant_id, institution_id, subject_id) ON DELETE RESTRICT;
ALTER TABLE app.syllabus ADD CONSTRAINT fk_syllabus_academic_session_id FOREIGN KEY (tenant_id, academic_session_id) REFERENCES app.academic_session (tenant_id, academic_session_id) ON DELETE RESTRICT;
ALTER TABLE app.syllabus ADD CONSTRAINT fk_syllabus_file_id FOREIGN KEY (tenant_id, file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.course_offering ADD CONSTRAINT fk_course_offering_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.course_offering ADD CONSTRAINT fk_course_offering_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.course_offering ADD CONSTRAINT fk_course_offering_subject_id FOREIGN KEY (tenant_id, institution_id, subject_id) REFERENCES app.subject (tenant_id, institution_id, subject_id) ON DELETE RESTRICT;
ALTER TABLE app.course_offering ADD CONSTRAINT fk_course_offering_academic_session_id FOREIGN KEY (tenant_id, academic_session_id) REFERENCES app.academic_session (tenant_id, academic_session_id) ON DELETE RESTRICT;
ALTER TABLE app.course_offering ADD CONSTRAINT fk_course_offering_semester_id FOREIGN KEY (tenant_id, institution_id, semester_id) REFERENCES app.semester (tenant_id, institution_id, semester_id) ON DELETE RESTRICT;
ALTER TABLE app.course_offering ADD CONSTRAINT fk_course_offering_class_id FOREIGN KEY (tenant_id, institution_id, class_id) REFERENCES app.school_class (tenant_id, institution_id, class_id) ON DELETE RESTRICT;
ALTER TABLE app.subject_group ADD CONSTRAINT fk_subject_group_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.subject_group ADD CONSTRAINT fk_subject_group_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.subject_group ADD CONSTRAINT fk_subject_group_course_offering_id FOREIGN KEY (tenant_id, institution_id, course_offering_id) REFERENCES app.course_offering (tenant_id, institution_id, course_offering_id) ON DELETE RESTRICT;
ALTER TABLE app.subject_group_member ADD CONSTRAINT fk_subject_group_member_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.subject_group_member ADD CONSTRAINT fk_subject_group_member_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.subject_group_member ADD CONSTRAINT fk_subject_group_member_subject_group_id FOREIGN KEY (tenant_id, institution_id, subject_group_id) REFERENCES app.subject_group (tenant_id, institution_id, subject_group_id) ON DELETE RESTRICT;
ALTER TABLE app.subject_group_member ADD CONSTRAINT fk_subject_group_member_enrollment_id FOREIGN KEY (tenant_id, institution_id, enrollment_id) REFERENCES app.student_enrollment (tenant_id, institution_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.user_account ADD CONSTRAINT fk_user_account_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.user_identity_link ADD CONSTRAINT fk_user_identity_link_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.user_identity_link ADD CONSTRAINT fk_user_identity_link_user_account_id FOREIGN KEY (tenant_id, user_account_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.user_device ADD CONSTRAINT fk_user_device_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.user_device ADD CONSTRAINT fk_user_device_user_account_id FOREIGN KEY (tenant_id, user_account_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.staff_profile ADD CONSTRAINT fk_staff_profile_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.staff_profile ADD CONSTRAINT fk_staff_profile_user_account_id FOREIGN KEY (tenant_id, user_account_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.staff_profile ADD CONSTRAINT fk_staff_profile_photo_file_id FOREIGN KEY (tenant_id, photo_file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.staff_service_record ADD CONSTRAINT fk_staff_service_record_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.staff_service_record ADD CONSTRAINT fk_staff_service_record_staff_profile_id FOREIGN KEY (tenant_id, staff_profile_id) REFERENCES app.staff_profile (tenant_id, staff_profile_id) ON DELETE RESTRICT;
ALTER TABLE app.staff_service_record ADD CONSTRAINT fk_staff_service_record_staff_assignment_id FOREIGN KEY (tenant_id, staff_assignment_id) REFERENCES app.staff_assignment (tenant_id, staff_assignment_id) ON DELETE RESTRICT;
ALTER TABLE app.staff_service_record ADD CONSTRAINT fk_staff_service_record_document_file_id FOREIGN KEY (tenant_id, document_file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.staff_assignment ADD CONSTRAINT fk_staff_assignment_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.staff_assignment ADD CONSTRAINT fk_staff_assignment_staff_profile_id FOREIGN KEY (tenant_id, staff_profile_id) REFERENCES app.staff_profile (tenant_id, staff_profile_id) ON DELETE RESTRICT;
ALTER TABLE app.staff_assignment ADD CONSTRAINT fk_staff_assignment_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.staff_assignment ADD CONSTRAINT fk_staff_assignment_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES app.department (tenant_id, department_id) ON DELETE RESTRICT;
ALTER TABLE app.staff_document ADD CONSTRAINT fk_staff_document_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.staff_document ADD CONSTRAINT fk_staff_document_staff_profile_id FOREIGN KEY (tenant_id, staff_profile_id) REFERENCES app.staff_profile (tenant_id, staff_profile_id) ON DELETE RESTRICT;
ALTER TABLE app.staff_document ADD CONSTRAINT fk_staff_document_file_id FOREIGN KEY (tenant_id, file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.role_definition ADD CONSTRAINT fk_role_definition_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.role_definition ADD CONSTRAINT fk_role_definition_base_standard_role FOREIGN KEY (base_standard_role) REFERENCES ref.standard_role (role_code) ON DELETE RESTRICT;
ALTER TABLE app.role_action ADD CONSTRAINT fk_role_action_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.role_action ADD CONSTRAINT fk_role_action_role_definition_id FOREIGN KEY (tenant_id, role_definition_id) REFERENCES app.role_definition (tenant_id, role_definition_id) ON DELETE RESTRICT;
ALTER TABLE app.role_action ADD CONSTRAINT fk_role_action_action_code FOREIGN KEY (action_code) REFERENCES ref.action_catalog (action_code) ON DELETE RESTRICT;
ALTER TABLE app.user_role_assignment ADD CONSTRAINT fk_user_role_assignment_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.user_role_assignment ADD CONSTRAINT fk_user_role_assignment_user_account_id FOREIGN KEY (tenant_id, user_account_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.user_role_assignment ADD CONSTRAINT fk_user_role_assignment_role_definition_id FOREIGN KEY (tenant_id, role_definition_id) REFERENCES app.role_definition (tenant_id, role_definition_id) ON DELETE RESTRICT;
ALTER TABLE app.user_role_assignment ADD CONSTRAINT fk_user_role_assignment_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.user_role_assignment ADD CONSTRAINT fk_user_role_assignment_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES app.department (tenant_id, department_id) ON DELETE RESTRICT;
ALTER TABLE app.delegation ADD CONSTRAINT fk_delegation_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.delegation ADD CONSTRAINT fk_delegation_delegator_user_id FOREIGN KEY (tenant_id, delegator_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.delegation ADD CONSTRAINT fk_delegation_delegate_user_id FOREIGN KEY (tenant_id, delegate_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.delegation ADD CONSTRAINT fk_delegation_process_code FOREIGN KEY (process_code) REFERENCES ref.approval_process (process_code) ON DELETE RESTRICT;
ALTER TABLE app.approval_request ADD CONSTRAINT fk_approval_request_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.approval_request ADD CONSTRAINT fk_approval_request_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.approval_request ADD CONSTRAINT fk_approval_request_process_code FOREIGN KEY (process_code) REFERENCES ref.approval_process (process_code) ON DELETE RESTRICT;
ALTER TABLE app.approval_request ADD CONSTRAINT fk_approval_request_requested_by_user_id FOREIGN KEY (tenant_id, requested_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.approval_batch ADD CONSTRAINT fk_approval_batch_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.approval_batch ADD CONSTRAINT fk_approval_batch_acted_by_user_id FOREIGN KEY (tenant_id, acted_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.approval_step ADD CONSTRAINT fk_approval_step_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.approval_step ADD CONSTRAINT fk_approval_step_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.approval_step ADD CONSTRAINT fk_approval_step_approver_user_id FOREIGN KEY (tenant_id, approver_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.approval_step ADD CONSTRAINT fk_approval_step_acted_by_user_id FOREIGN KEY (tenant_id, acted_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.approval_step ADD CONSTRAINT fk_approval_step_acting_via_delegation_id FOREIGN KEY (tenant_id, acting_via_delegation_id) REFERENCES app.delegation (tenant_id, delegation_id) ON DELETE RESTRICT;
ALTER TABLE app.approval_step ADD CONSTRAINT fk_approval_step_approval_batch_id FOREIGN KEY (tenant_id, approval_batch_id) REFERENCES app.approval_batch (tenant_id, approval_batch_id) ON DELETE RESTRICT;
ALTER TABLE app.student_profile ADD CONSTRAINT fk_student_profile_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.student_profile ADD CONSTRAINT fk_student_profile_user_account_id FOREIGN KEY (tenant_id, user_account_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.student_profile ADD CONSTRAINT fk_student_profile_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.student_profile ADD CONSTRAINT fk_student_profile_photo_file_id FOREIGN KEY (tenant_id, photo_file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.student_code_sequence ADD CONSTRAINT fk_student_code_sequence_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.student_code_sequence ADD CONSTRAINT fk_student_code_sequence_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.duplicate_review ADD CONSTRAINT fk_duplicate_review_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.guardian_profile ADD CONSTRAINT fk_guardian_profile_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.guardian_profile ADD CONSTRAINT fk_guardian_profile_user_account_id FOREIGN KEY (tenant_id, user_account_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.student_guardian_link ADD CONSTRAINT fk_student_guardian_link_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.student_guardian_link ADD CONSTRAINT fk_student_guardian_link_student_profile_id FOREIGN KEY (tenant_id, student_profile_id) REFERENCES app.student_profile (tenant_id, student_profile_id) ON DELETE RESTRICT;
ALTER TABLE app.student_guardian_link ADD CONSTRAINT fk_student_guardian_link_guardian_profile_id FOREIGN KEY (tenant_id, guardian_profile_id) REFERENCES app.guardian_profile (tenant_id, guardian_profile_id) ON DELETE RESTRICT;
ALTER TABLE app.student_guardian_link ADD CONSTRAINT fk_student_guardian_link_change_approval_request_id FOREIGN KEY (tenant_id, change_approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.parent_consent ADD CONSTRAINT fk_parent_consent_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.parent_consent ADD CONSTRAINT fk_parent_consent_student_profile_id FOREIGN KEY (tenant_id, student_profile_id) REFERENCES app.student_profile (tenant_id, student_profile_id) ON DELETE RESTRICT;
ALTER TABLE app.parent_consent ADD CONSTRAINT fk_parent_consent_guardian_profile_id FOREIGN KEY (tenant_id, guardian_profile_id) REFERENCES app.guardian_profile (tenant_id, guardian_profile_id) ON DELETE RESTRICT;
ALTER TABLE app.parent_consent ADD CONSTRAINT fk_parent_consent_paper_file_id FOREIGN KEY (tenant_id, paper_file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.student_enrollment ADD CONSTRAINT fk_student_enrollment_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.student_enrollment ADD CONSTRAINT fk_student_enrollment_student_profile_id FOREIGN KEY (tenant_id, student_profile_id) REFERENCES app.student_profile (tenant_id, student_profile_id) ON DELETE RESTRICT;
ALTER TABLE app.student_enrollment ADD CONSTRAINT fk_student_enrollment_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.student_enrollment ADD CONSTRAINT fk_student_enrollment_academic_session_id FOREIGN KEY (tenant_id, academic_session_id) REFERENCES app.academic_session (tenant_id, academic_session_id) ON DELETE RESTRICT;
ALTER TABLE app.student_enrollment ADD CONSTRAINT fk_student_enrollment_section_id FOREIGN KEY (tenant_id, institution_id, section_id) REFERENCES app.section (tenant_id, institution_id, section_id) ON DELETE RESTRICT;
ALTER TABLE app.student_enrollment ADD CONSTRAINT fk_student_enrollment_promoted_from_enrollment_id FOREIGN KEY (tenant_id, promoted_from_enrollment_id) REFERENCES app.student_enrollment (tenant_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.promotion_run ADD CONSTRAINT fk_promotion_run_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.promotion_run ADD CONSTRAINT fk_promotion_run_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.promotion_run ADD CONSTRAINT fk_promotion_run_from_session_id FOREIGN KEY (tenant_id, from_session_id) REFERENCES app.academic_session (tenant_id, academic_session_id) ON DELETE RESTRICT;
ALTER TABLE app.promotion_run ADD CONSTRAINT fk_promotion_run_to_session_id FOREIGN KEY (tenant_id, to_session_id) REFERENCES app.academic_session (tenant_id, academic_session_id) ON DELETE RESTRICT;
ALTER TABLE app.promotion_run ADD CONSTRAINT fk_promotion_run_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.promotion_run_item ADD CONSTRAINT fk_promotion_run_item_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.promotion_run_item ADD CONSTRAINT fk_promotion_run_item_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.promotion_run_item ADD CONSTRAINT fk_promotion_run_item_promotion_run_id FOREIGN KEY (tenant_id, institution_id, promotion_run_id) REFERENCES app.promotion_run (tenant_id, institution_id, promotion_run_id) ON DELETE RESTRICT;
ALTER TABLE app.promotion_run_item ADD CONSTRAINT fk_promotion_run_item_enrollment_id FOREIGN KEY (tenant_id, institution_id, enrollment_id) REFERENCES app.student_enrollment (tenant_id, institution_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.promotion_run_item ADD CONSTRAINT fk_promotion_run_item_target_section_id FOREIGN KEY (tenant_id, institution_id, target_section_id) REFERENCES app.section (tenant_id, institution_id, section_id) ON DELETE RESTRICT;
ALTER TABLE app.subject_allocation ADD CONSTRAINT fk_subject_allocation_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.subject_allocation ADD CONSTRAINT fk_subject_allocation_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.subject_allocation ADD CONSTRAINT fk_subject_allocation_course_offering_id FOREIGN KEY (tenant_id, institution_id, course_offering_id) REFERENCES app.course_offering (tenant_id, institution_id, course_offering_id) ON DELETE RESTRICT;
ALTER TABLE app.subject_allocation ADD CONSTRAINT fk_subject_allocation_section_id FOREIGN KEY (tenant_id, institution_id, section_id) REFERENCES app.section (tenant_id, institution_id, section_id) ON DELETE RESTRICT;
ALTER TABLE app.subject_allocation ADD CONSTRAINT fk_subject_allocation_subject_group_id FOREIGN KEY (tenant_id, institution_id, subject_group_id) REFERENCES app.subject_group (tenant_id, institution_id, subject_group_id) ON DELETE RESTRICT;
ALTER TABLE app.subject_allocation ADD CONSTRAINT fk_subject_allocation_staff_assignment_id FOREIGN KEY (tenant_id, staff_assignment_id) REFERENCES app.staff_assignment (tenant_id, staff_assignment_id) ON DELETE RESTRICT;
ALTER TABLE app.mentor_assignment ADD CONSTRAINT fk_mentor_assignment_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.mentor_assignment ADD CONSTRAINT fk_mentor_assignment_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.mentor_assignment ADD CONSTRAINT fk_mentor_assignment_section_id FOREIGN KEY (tenant_id, institution_id, section_id) REFERENCES app.section (tenant_id, institution_id, section_id) ON DELETE RESTRICT;
ALTER TABLE app.mentor_assignment ADD CONSTRAINT fk_mentor_assignment_staff_assignment_id FOREIGN KEY (tenant_id, staff_assignment_id) REFERENCES app.staff_assignment (tenant_id, staff_assignment_id) ON DELETE RESTRICT;
ALTER TABLE app.class_teacher_assignment ADD CONSTRAINT fk_class_teacher_assignment_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.class_teacher_assignment ADD CONSTRAINT fk_class_teacher_assignment_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.class_teacher_assignment ADD CONSTRAINT fk_class_teacher_assignment_section_id FOREIGN KEY (tenant_id, institution_id, section_id) REFERENCES app.section (tenant_id, institution_id, section_id) ON DELETE RESTRICT;
ALTER TABLE app.class_teacher_assignment ADD CONSTRAINT fk_class_teacher_assignment_staff_assignment_id FOREIGN KEY (tenant_id, staff_assignment_id) REFERENCES app.staff_assignment (tenant_id, staff_assignment_id) ON DELETE RESTRICT;
ALTER TABLE app.timetable_slot ADD CONSTRAINT fk_timetable_slot_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.timetable_slot ADD CONSTRAINT fk_timetable_slot_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.timetable_slot ADD CONSTRAINT fk_timetable_slot_academic_session_id FOREIGN KEY (tenant_id, academic_session_id) REFERENCES app.academic_session (tenant_id, academic_session_id) ON DELETE RESTRICT;
ALTER TABLE app.timetable_slot ADD CONSTRAINT fk_timetable_slot_section_id FOREIGN KEY (tenant_id, institution_id, section_id) REFERENCES app.section (tenant_id, institution_id, section_id) ON DELETE RESTRICT;
ALTER TABLE app.timetable_slot ADD CONSTRAINT fk_timetable_slot_subject_group_id FOREIGN KEY (tenant_id, institution_id, subject_group_id) REFERENCES app.subject_group (tenant_id, institution_id, subject_group_id) ON DELETE RESTRICT;
ALTER TABLE app.timetable_slot ADD CONSTRAINT fk_timetable_slot_subject_allocation_id FOREIGN KEY (tenant_id, institution_id, subject_allocation_id) REFERENCES app.subject_allocation (tenant_id, institution_id, subject_allocation_id) ON DELETE RESTRICT;
ALTER TABLE app.timetable_slot ADD CONSTRAINT fk_timetable_slot_supersedes_slot_id FOREIGN KEY (tenant_id, institution_id, supersedes_slot_id) REFERENCES app.timetable_slot (tenant_id, institution_id, timetable_slot_id) ON DELETE RESTRICT;
ALTER TABLE app.meeting_link ADD CONSTRAINT fk_meeting_link_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.meeting_link ADD CONSTRAINT fk_meeting_link_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.meeting_link ADD CONSTRAINT fk_meeting_link_timetable_slot_id FOREIGN KEY (tenant_id, institution_id, timetable_slot_id) REFERENCES app.timetable_slot (tenant_id, institution_id, timetable_slot_id) ON DELETE RESTRICT;
ALTER TABLE app.substitute_assignment ADD CONSTRAINT fk_substitute_assignment_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.substitute_assignment ADD CONSTRAINT fk_substitute_assignment_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.substitute_assignment ADD CONSTRAINT fk_substitute_assignment_subject_allocation_id FOREIGN KEY (tenant_id, institution_id, subject_allocation_id) REFERENCES app.subject_allocation (tenant_id, institution_id, subject_allocation_id) ON DELETE RESTRICT;
ALTER TABLE app.substitute_assignment ADD CONSTRAINT fk_substitute_assignment_substitute_staff_assignment_id FOREIGN KEY (tenant_id, substitute_staff_assignment_id) REFERENCES app.staff_assignment (tenant_id, staff_assignment_id) ON DELETE RESTRICT;
ALTER TABLE app.substitute_assignment ADD CONSTRAINT fk_substitute_assignment_leave_request_id FOREIGN KEY (tenant_id, leave_request_id) REFERENCES app.leave_request (tenant_id, leave_request_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_session ADD CONSTRAINT fk_attendance_session_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_session ADD CONSTRAINT fk_attendance_session_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_session ADD CONSTRAINT fk_attendance_session_section_id FOREIGN KEY (tenant_id, institution_id, section_id) REFERENCES app.section (tenant_id, institution_id, section_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_session ADD CONSTRAINT fk_attendance_session_subject_group_id FOREIGN KEY (tenant_id, institution_id, subject_group_id) REFERENCES app.subject_group (tenant_id, institution_id, subject_group_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_session ADD CONSTRAINT fk_attendance_session_timetable_slot_id FOREIGN KEY (tenant_id, institution_id, timetable_slot_id) REFERENCES app.timetable_slot (tenant_id, institution_id, timetable_slot_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_session ADD CONSTRAINT fk_attendance_session_subject_allocation_id FOREIGN KEY (tenant_id, institution_id, subject_allocation_id) REFERENCES app.subject_allocation (tenant_id, institution_id, subject_allocation_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_session ADD CONSTRAINT fk_attendance_session_marked_by_user_id FOREIGN KEY (tenant_id, marked_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_session ADD CONSTRAINT fk_attendance_session_substitute_assignment_id FOREIGN KEY (tenant_id, institution_id, substitute_assignment_id) REFERENCES app.substitute_assignment (tenant_id, institution_id, substitute_assignment_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_sync_conflict ADD CONSTRAINT fk_attendance_sync_conflict_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_sync_conflict ADD CONSTRAINT fk_attendance_sync_conflict_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_sync_conflict ADD CONSTRAINT fk_attendance_sync_conflict_existing_attendance_session_id FOREIGN KEY (tenant_id, institution_id, existing_attendance_session_id) REFERENCES app.attendance_session (tenant_id, institution_id, attendance_session_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_sync_conflict ADD CONSTRAINT fk_attendance_sync_conflict_submitted_by_user_id FOREIGN KEY (tenant_id, submitted_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_entry ADD CONSTRAINT fk_attendance_entry_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_entry ADD CONSTRAINT fk_attendance_entry_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_entry ADD CONSTRAINT fk_attendance_entry_attendance_session_id FOREIGN KEY (tenant_id, institution_id, attendance_session_id) REFERENCES app.attendance_session (tenant_id, institution_id, attendance_session_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_entry ADD CONSTRAINT fk_attendance_entry_enrollment_id FOREIGN KEY (tenant_id, institution_id, enrollment_id) REFERENCES app.student_enrollment (tenant_id, institution_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_entry ADD CONSTRAINT fk_attendance_entry_leave_request_id FOREIGN KEY (tenant_id, leave_request_id) REFERENCES app.leave_request (tenant_id, leave_request_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_entry ADD CONSTRAINT fk_attendance_entry_recorded_by_user_id FOREIGN KEY (tenant_id, recorded_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_correction_request ADD CONSTRAINT fk_attendance_correction_request_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_correction_request ADD CONSTRAINT fk_attendance_correction_request_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_correction_request ADD CONSTRAINT fk_attendance_correction_request_attendance_session_id FOREIGN KEY (tenant_id, institution_id, attendance_session_id) REFERENCES app.attendance_session (tenant_id, institution_id, attendance_session_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_correction_request ADD CONSTRAINT fk_attendance_correction_request_requested_by_user_id FOREIGN KEY (tenant_id, requested_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_correction_request ADD CONSTRAINT fk_attendance_correction_request_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_correction_request ADD CONSTRAINT fk_attendance_correction_request_bulk_correction_item_id FOREIGN KEY (tenant_id, bulk_correction_item_id) REFERENCES app.bulk_correction_item (tenant_id, bulk_correction_item_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_correction ADD CONSTRAINT fk_attendance_correction_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_correction ADD CONSTRAINT fk_attendance_correction_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_correction ADD CONSTRAINT fk_attendance_correction_attendance_correction_request_id FOREIGN KEY (tenant_id, institution_id, attendance_correction_request_id) REFERENCES app.attendance_correction_request (tenant_id, institution_id, attendance_correction_request_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_bulk_upload ADD CONSTRAINT fk_attendance_bulk_upload_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_bulk_upload ADD CONSTRAINT fk_attendance_bulk_upload_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_bulk_upload ADD CONSTRAINT fk_attendance_bulk_upload_import_job_id FOREIGN KEY (tenant_id, import_job_id) REFERENCES app.import_job (tenant_id, import_job_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_bulk_upload ADD CONSTRAINT fk_attendance_bulk_upload_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.term_lock ADD CONSTRAINT fk_term_lock_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.term_lock ADD CONSTRAINT fk_term_lock_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.term_lock ADD CONSTRAINT fk_term_lock_academic_session_id FOREIGN KEY (tenant_id, academic_session_id) REFERENCES app.academic_session (tenant_id, academic_session_id) ON DELETE RESTRICT;
ALTER TABLE app.term_lock ADD CONSTRAINT fk_term_lock_semester_id FOREIGN KEY (tenant_id, institution_id, semester_id) REFERENCES app.semester (tenant_id, institution_id, semester_id) ON DELETE RESTRICT;
ALTER TABLE app.term_lock_event ADD CONSTRAINT fk_term_lock_event_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.term_lock_event ADD CONSTRAINT fk_term_lock_event_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.term_lock_event ADD CONSTRAINT fk_term_lock_event_term_lock_id FOREIGN KEY (tenant_id, institution_id, term_lock_id) REFERENCES app.term_lock (tenant_id, institution_id, term_lock_id) ON DELETE RESTRICT;
ALTER TABLE app.term_lock_event ADD CONSTRAINT fk_term_lock_event_actor_user_id FOREIGN KEY (tenant_id, actor_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.risk_flag ADD CONSTRAINT fk_risk_flag_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.risk_flag ADD CONSTRAINT fk_risk_flag_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.risk_flag ADD CONSTRAINT fk_risk_flag_enrollment_id FOREIGN KEY (tenant_id, institution_id, enrollment_id) REFERENCES app.student_enrollment (tenant_id, institution_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.risk_remark ADD CONSTRAINT fk_risk_remark_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.risk_remark ADD CONSTRAINT fk_risk_remark_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.risk_remark ADD CONSTRAINT fk_risk_remark_enrollment_id FOREIGN KEY (tenant_id, institution_id, enrollment_id) REFERENCES app.student_enrollment (tenant_id, institution_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_type ADD CONSTRAINT fk_leave_type_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_type ADD CONSTRAINT fk_leave_type_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_balance ADD CONSTRAINT fk_leave_balance_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_balance ADD CONSTRAINT fk_leave_balance_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_balance ADD CONSTRAINT fk_leave_balance_staff_profile_id FOREIGN KEY (tenant_id, staff_profile_id) REFERENCES app.staff_profile (tenant_id, staff_profile_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_balance ADD CONSTRAINT fk_leave_balance_leave_type_id FOREIGN KEY (tenant_id, leave_type_id) REFERENCES app.leave_type (tenant_id, leave_type_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_request ADD CONSTRAINT fk_leave_request_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_request ADD CONSTRAINT fk_leave_request_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_request ADD CONSTRAINT fk_leave_request_requested_by_user_id FOREIGN KEY (tenant_id, requested_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_request ADD CONSTRAINT fk_leave_request_for_enrollment_id FOREIGN KEY (tenant_id, for_enrollment_id) REFERENCES app.student_enrollment (tenant_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_request ADD CONSTRAINT fk_leave_request_for_staff_profile_id FOREIGN KEY (tenant_id, for_staff_profile_id) REFERENCES app.staff_profile (tenant_id, staff_profile_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_request ADD CONSTRAINT fk_leave_request_leave_type_id FOREIGN KEY (tenant_id, leave_type_id) REFERENCES app.leave_type (tenant_id, leave_type_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_request ADD CONSTRAINT fk_leave_request_document_file_id FOREIGN KEY (tenant_id, document_file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.leave_request ADD CONSTRAINT fk_leave_request_process_code FOREIGN KEY (process_code) REFERENCES ref.approval_process (process_code) ON DELETE RESTRICT;
ALTER TABLE app.leave_request ADD CONSTRAINT fk_leave_request_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.grading_scheme_version ADD CONSTRAINT fk_grading_scheme_version_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.grading_scheme_version ADD CONSTRAINT fk_grading_scheme_version_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.grade_band ADD CONSTRAINT fk_grade_band_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.grade_band ADD CONSTRAINT fk_grade_band_grading_scheme_version_id FOREIGN KEY (tenant_id, grading_scheme_version_id) REFERENCES app.grading_scheme_version (tenant_id, grading_scheme_version_id) ON DELETE RESTRICT;
ALTER TABLE app.exam_rule ADD CONSTRAINT fk_exam_rule_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.exam_rule ADD CONSTRAINT fk_exam_rule_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.exam_rule ADD CONSTRAINT fk_exam_rule_grading_scheme_version_id FOREIGN KEY (tenant_id, grading_scheme_version_id) REFERENCES app.grading_scheme_version (tenant_id, grading_scheme_version_id) ON DELETE RESTRICT;
ALTER TABLE app.exam_pattern ADD CONSTRAINT fk_exam_pattern_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.exam_pattern ADD CONSTRAINT fk_exam_pattern_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.exam_pattern ADD CONSTRAINT fk_exam_pattern_exam_rule_id FOREIGN KEY (tenant_id, exam_rule_id) REFERENCES app.exam_rule (tenant_id, exam_rule_id) ON DELETE RESTRICT;
ALTER TABLE app.exam_pattern ADD CONSTRAINT fk_exam_pattern_grading_scheme_version_id FOREIGN KEY (tenant_id, grading_scheme_version_id) REFERENCES app.grading_scheme_version (tenant_id, grading_scheme_version_id) ON DELETE RESTRICT;
ALTER TABLE app.exam_pattern_component ADD CONSTRAINT fk_exam_pattern_component_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.exam_pattern_component ADD CONSTRAINT fk_exam_pattern_component_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.exam_pattern_component ADD CONSTRAINT fk_exam_pattern_component_exam_pattern_id FOREIGN KEY (tenant_id, institution_id, exam_pattern_id) REFERENCES app.exam_pattern (tenant_id, institution_id, exam_pattern_id) ON DELETE RESTRICT;
ALTER TABLE app.exam ADD CONSTRAINT fk_exam_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.exam ADD CONSTRAINT fk_exam_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.exam ADD CONSTRAINT fk_exam_academic_session_id FOREIGN KEY (tenant_id, academic_session_id) REFERENCES app.academic_session (tenant_id, academic_session_id) ON DELETE RESTRICT;
ALTER TABLE app.exam ADD CONSTRAINT fk_exam_semester_id FOREIGN KEY (tenant_id, institution_id, semester_id) REFERENCES app.semester (tenant_id, institution_id, semester_id) ON DELETE RESTRICT;
ALTER TABLE app.exam ADD CONSTRAINT fk_exam_class_id FOREIGN KEY (tenant_id, institution_id, class_id) REFERENCES app.school_class (tenant_id, institution_id, class_id) ON DELETE RESTRICT;
ALTER TABLE app.exam ADD CONSTRAINT fk_exam_exam_pattern_id FOREIGN KEY (tenant_id, institution_id, exam_pattern_id) REFERENCES app.exam_pattern (tenant_id, institution_id, exam_pattern_id) ON DELETE RESTRICT;
ALTER TABLE app.exam ADD CONSTRAINT fk_exam_schedule_approval_request_id FOREIGN KEY (tenant_id, schedule_approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.exam_schedule_item ADD CONSTRAINT fk_exam_schedule_item_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.exam_schedule_item ADD CONSTRAINT fk_exam_schedule_item_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.exam_schedule_item ADD CONSTRAINT fk_exam_schedule_item_exam_id FOREIGN KEY (tenant_id, institution_id, exam_id) REFERENCES app.exam (tenant_id, institution_id, exam_id) ON DELETE RESTRICT;
ALTER TABLE app.exam_schedule_item ADD CONSTRAINT fk_exam_schedule_item_course_offering_id FOREIGN KEY (tenant_id, institution_id, course_offering_id) REFERENCES app.course_offering (tenant_id, institution_id, course_offering_id) ON DELETE RESTRICT;
ALTER TABLE app.hall_ticket ADD CONSTRAINT fk_hall_ticket_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.hall_ticket ADD CONSTRAINT fk_hall_ticket_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.hall_ticket ADD CONSTRAINT fk_hall_ticket_exam_id FOREIGN KEY (tenant_id, institution_id, exam_id) REFERENCES app.exam (tenant_id, institution_id, exam_id) ON DELETE RESTRICT;
ALTER TABLE app.hall_ticket ADD CONSTRAINT fk_hall_ticket_enrollment_id FOREIGN KEY (tenant_id, institution_id, enrollment_id) REFERENCES app.student_enrollment (tenant_id, institution_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.hall_ticket ADD CONSTRAINT fk_hall_ticket_override_by_user_id FOREIGN KEY (tenant_id, override_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.hall_ticket ADD CONSTRAINT fk_hall_ticket_file_id FOREIGN KEY (tenant_id, file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_submission ADD CONSTRAINT fk_marks_submission_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_submission ADD CONSTRAINT fk_marks_submission_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_submission ADD CONSTRAINT fk_marks_submission_exam_id FOREIGN KEY (tenant_id, institution_id, exam_id) REFERENCES app.exam (tenant_id, institution_id, exam_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_submission ADD CONSTRAINT fk_marks_submission_course_offering_id FOREIGN KEY (tenant_id, institution_id, course_offering_id) REFERENCES app.course_offering (tenant_id, institution_id, course_offering_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_submission ADD CONSTRAINT fk_marks_submission_section_id FOREIGN KEY (tenant_id, institution_id, section_id) REFERENCES app.section (tenant_id, institution_id, section_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_submission ADD CONSTRAINT fk_marks_submission_subject_group_id FOREIGN KEY (tenant_id, institution_id, subject_group_id) REFERENCES app.subject_group (tenant_id, institution_id, subject_group_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_submission ADD CONSTRAINT fk_marks_submission_entered_by_user_id FOREIGN KEY (tenant_id, entered_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_submission ADD CONSTRAINT fk_marks_submission_source_file_id FOREIGN KEY (tenant_id, source_file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_submission ADD CONSTRAINT fk_marks_submission_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_submission ADD CONSTRAINT fk_marks_submission_supersedes_submission_id FOREIGN KEY (tenant_id, institution_id, supersedes_submission_id) REFERENCES app.marks_submission (tenant_id, institution_id, marks_submission_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_entry ADD CONSTRAINT fk_marks_entry_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_entry ADD CONSTRAINT fk_marks_entry_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_entry ADD CONSTRAINT fk_marks_entry_marks_submission_id FOREIGN KEY (tenant_id, institution_id, marks_submission_id) REFERENCES app.marks_submission (tenant_id, institution_id, marks_submission_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_entry ADD CONSTRAINT fk_marks_entry_enrollment_id FOREIGN KEY (tenant_id, institution_id, enrollment_id) REFERENCES app.student_enrollment (tenant_id, institution_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.marks_entry ADD CONSTRAINT fk_marks_entry_component_id FOREIGN KEY (tenant_id, institution_id, component_id) REFERENCES app.exam_pattern_component (tenant_id, institution_id, component_id) ON DELETE RESTRICT;
ALTER TABLE app.backlog ADD CONSTRAINT fk_backlog_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.backlog ADD CONSTRAINT fk_backlog_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.backlog ADD CONSTRAINT fk_backlog_student_profile_id FOREIGN KEY (tenant_id, student_profile_id) REFERENCES app.student_profile (tenant_id, student_profile_id) ON DELETE RESTRICT;
ALTER TABLE app.backlog ADD CONSTRAINT fk_backlog_subject_id FOREIGN KEY (tenant_id, institution_id, subject_id) REFERENCES app.subject (tenant_id, institution_id, subject_id) ON DELETE RESTRICT;
ALTER TABLE app.backlog ADD CONSTRAINT fk_backlog_origin_result_line_id FOREIGN KEY (tenant_id, origin_result_line_id) REFERENCES app.result_line (tenant_id, result_line_id) ON DELETE RESTRICT;
ALTER TABLE app.backlog ADD CONSTRAINT fk_backlog_cleared_result_line_id FOREIGN KEY (tenant_id, cleared_result_line_id) REFERENCES app.result_line (tenant_id, result_line_id) ON DELETE RESTRICT;
ALTER TABLE app.supplementary_registration ADD CONSTRAINT fk_supplementary_registration_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.supplementary_registration ADD CONSTRAINT fk_supplementary_registration_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.supplementary_registration ADD CONSTRAINT fk_supplementary_registration_backlog_id FOREIGN KEY (tenant_id, institution_id, backlog_id) REFERENCES app.backlog (tenant_id, institution_id, backlog_id) ON DELETE RESTRICT;
ALTER TABLE app.supplementary_registration ADD CONSTRAINT fk_supplementary_registration_exam_id FOREIGN KEY (tenant_id, institution_id, exam_id) REFERENCES app.exam (tenant_id, institution_id, exam_id) ON DELETE RESTRICT;
ALTER TABLE app.supplementary_registration ADD CONSTRAINT fk_supplementary_registration_registered_by FOREIGN KEY (tenant_id, registered_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.result ADD CONSTRAINT fk_result_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.result ADD CONSTRAINT fk_result_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.result ADD CONSTRAINT fk_result_exam_id FOREIGN KEY (tenant_id, institution_id, exam_id) REFERENCES app.exam (tenant_id, institution_id, exam_id) ON DELETE RESTRICT;
ALTER TABLE app.result ADD CONSTRAINT fk_result_enrollment_id FOREIGN KEY (tenant_id, institution_id, enrollment_id) REFERENCES app.student_enrollment (tenant_id, institution_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.result ADD CONSTRAINT fk_result_grading_scheme_version_id FOREIGN KEY (tenant_id, grading_scheme_version_id) REFERENCES app.grading_scheme_version (tenant_id, grading_scheme_version_id) ON DELETE RESTRICT;
ALTER TABLE app.result_line ADD CONSTRAINT fk_result_line_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.result_line ADD CONSTRAINT fk_result_line_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.result_line ADD CONSTRAINT fk_result_line_result_id FOREIGN KEY (tenant_id, institution_id, result_id) REFERENCES app.result (tenant_id, institution_id, result_id) ON DELETE RESTRICT;
ALTER TABLE app.result_line ADD CONSTRAINT fk_result_line_subject_id FOREIGN KEY (tenant_id, institution_id, subject_id) REFERENCES app.subject (tenant_id, institution_id, subject_id) ON DELETE RESTRICT;
ALTER TABLE app.result_publication ADD CONSTRAINT fk_result_publication_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.result_publication ADD CONSTRAINT fk_result_publication_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.result_publication ADD CONSTRAINT fk_result_publication_exam_id FOREIGN KEY (tenant_id, institution_id, exam_id) REFERENCES app.exam (tenant_id, institution_id, exam_id) ON DELETE RESTRICT;
ALTER TABLE app.result_publication ADD CONSTRAINT fk_result_publication_section_id FOREIGN KEY (tenant_id, institution_id, section_id) REFERENCES app.section (tenant_id, institution_id, section_id) ON DELETE RESTRICT;
ALTER TABLE app.result_publication ADD CONSTRAINT fk_result_publication_published_by_user_id FOREIGN KEY (tenant_id, published_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.result_publication ADD CONSTRAINT fk_result_publication_withdrawal_request_id FOREIGN KEY (tenant_id, institution_id, withdrawal_request_id) REFERENCES app.result_withdrawal_request (tenant_id, institution_id, result_withdrawal_request_id) ON DELETE RESTRICT;
ALTER TABLE app.result_withdrawal_request ADD CONSTRAINT fk_result_withdrawal_request_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.result_withdrawal_request ADD CONSTRAINT fk_result_withdrawal_request_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.result_withdrawal_request ADD CONSTRAINT fk_result_withdrawal_request_result_publication_id FOREIGN KEY (tenant_id, institution_id, result_publication_id) REFERENCES app.result_publication (tenant_id, institution_id, result_publication_id) ON DELETE RESTRICT;
ALTER TABLE app.result_withdrawal_request ADD CONSTRAINT fk_result_withdrawal_request_requested_by_user_id FOREIGN KEY (tenant_id, requested_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.result_withdrawal_request ADD CONSTRAINT fk_result_withdrawal_request_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_head ADD CONSTRAINT fk_fee_head_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_policy ADD CONSTRAINT fk_fee_policy_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_policy ADD CONSTRAINT fk_fee_policy_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_head_rule ADD CONSTRAINT fk_fee_head_rule_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_head_rule ADD CONSTRAINT fk_fee_head_rule_fee_policy_id FOREIGN KEY (tenant_id, fee_policy_id) REFERENCES app.fee_policy (tenant_id, fee_policy_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_head_rule ADD CONSTRAINT fk_fee_head_rule_fee_head_id FOREIGN KEY (tenant_id, fee_head_id) REFERENCES app.fee_head (tenant_id, fee_head_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure ADD CONSTRAINT fk_fee_structure_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure ADD CONSTRAINT fk_fee_structure_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure ADD CONSTRAINT fk_fee_structure_academic_session_id FOREIGN KEY (tenant_id, academic_session_id) REFERENCES app.academic_session (tenant_id, academic_session_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure ADD CONSTRAINT fk_fee_structure_program_year_id FOREIGN KEY (tenant_id, institution_id, program_year_id) REFERENCES app.program_year (tenant_id, institution_id, program_year_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure ADD CONSTRAINT fk_fee_structure_class_id FOREIGN KEY (tenant_id, institution_id, class_id) REFERENCES app.school_class (tenant_id, institution_id, class_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure ADD CONSTRAINT fk_fee_structure_fee_policy_id FOREIGN KEY (tenant_id, fee_policy_id) REFERENCES app.fee_policy (tenant_id, fee_policy_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure ADD CONSTRAINT fk_fee_structure_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure_line ADD CONSTRAINT fk_fee_structure_line_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure_line ADD CONSTRAINT fk_fee_structure_line_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure_line ADD CONSTRAINT fk_fee_structure_line_fee_structure_id FOREIGN KEY (tenant_id, institution_id, fee_structure_id) REFERENCES app.fee_structure (tenant_id, institution_id, fee_structure_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure_line ADD CONSTRAINT fk_fee_structure_line_fee_head_id FOREIGN KEY (tenant_id, fee_head_id) REFERENCES app.fee_head (tenant_id, fee_head_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure_line ADD CONSTRAINT fk_fee_structure_line_fee_exception_id FOREIGN KEY (tenant_id, institution_id, fee_exception_id) REFERENCES app.fee_exception (tenant_id, institution_id, fee_exception_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure_installment ADD CONSTRAINT fk_fee_structure_installment_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure_installment ADD CONSTRAINT fk_fee_structure_installment_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_structure_installment ADD CONSTRAINT fk_fee_structure_installment_fee_structure_id FOREIGN KEY (tenant_id, institution_id, fee_structure_id) REFERENCES app.fee_structure (tenant_id, institution_id, fee_structure_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_exception ADD CONSTRAINT fk_fee_exception_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_exception ADD CONSTRAINT fk_fee_exception_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_exception ADD CONSTRAINT fk_fee_exception_fee_structure_id FOREIGN KEY (tenant_id, institution_id, fee_structure_id) REFERENCES app.fee_structure (tenant_id, institution_id, fee_structure_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_exception ADD CONSTRAINT fk_fee_exception_fee_head_id FOREIGN KEY (tenant_id, fee_head_id) REFERENCES app.fee_head (tenant_id, fee_head_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_exception ADD CONSTRAINT fk_fee_exception_requested_by FOREIGN KEY (tenant_id, requested_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_exception ADD CONSTRAINT fk_fee_exception_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.student_fee_account ADD CONSTRAINT fk_student_fee_account_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.student_fee_account ADD CONSTRAINT fk_student_fee_account_student_profile_id FOREIGN KEY (tenant_id, student_profile_id) REFERENCES app.student_profile (tenant_id, student_profile_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_assignment ADD CONSTRAINT fk_fee_assignment_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_assignment ADD CONSTRAINT fk_fee_assignment_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_assignment ADD CONSTRAINT fk_fee_assignment_student_fee_account_id FOREIGN KEY (tenant_id, student_fee_account_id) REFERENCES app.student_fee_account (tenant_id, student_fee_account_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_assignment ADD CONSTRAINT fk_fee_assignment_enrollment_id FOREIGN KEY (tenant_id, institution_id, enrollment_id) REFERENCES app.student_enrollment (tenant_id, institution_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.fee_assignment ADD CONSTRAINT fk_fee_assignment_fee_structure_id FOREIGN KEY (tenant_id, institution_id, fee_structure_id) REFERENCES app.fee_structure (tenant_id, institution_id, fee_structure_id) ON DELETE RESTRICT;
ALTER TABLE app.student_installment ADD CONSTRAINT fk_student_installment_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.student_installment ADD CONSTRAINT fk_student_installment_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.student_installment ADD CONSTRAINT fk_student_installment_student_fee_account_id FOREIGN KEY (tenant_id, student_fee_account_id) REFERENCES app.student_fee_account (tenant_id, student_fee_account_id) ON DELETE RESTRICT;
ALTER TABLE app.student_installment ADD CONSTRAINT fk_student_installment_fee_assignment_id FOREIGN KEY (tenant_id, institution_id, fee_assignment_id) REFERENCES app.fee_assignment (tenant_id, institution_id, fee_assignment_id) ON DELETE RESTRICT;
ALTER TABLE app.ledger_entry ADD CONSTRAINT fk_ledger_entry_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.ledger_entry ADD CONSTRAINT fk_ledger_entry_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.ledger_entry ADD CONSTRAINT fk_ledger_entry_student_fee_account_id FOREIGN KEY (tenant_id, student_fee_account_id) REFERENCES app.student_fee_account (tenant_id, student_fee_account_id) ON DELETE RESTRICT;
ALTER TABLE app.ledger_entry ADD CONSTRAINT fk_ledger_entry_academic_session_id FOREIGN KEY (tenant_id, academic_session_id) REFERENCES app.academic_session (tenant_id, academic_session_id) ON DELETE RESTRICT;
ALTER TABLE app.ledger_entry ADD CONSTRAINT fk_ledger_entry_fee_head_id FOREIGN KEY (tenant_id, fee_head_id) REFERENCES app.fee_head (tenant_id, fee_head_id) ON DELETE RESTRICT;
ALTER TABLE app.ledger_entry ADD CONSTRAINT fk_ledger_entry_student_installment_id FOREIGN KEY (tenant_id, institution_id, student_installment_id) REFERENCES app.student_installment (tenant_id, institution_id, student_installment_id) ON DELETE RESTRICT;
ALTER TABLE app.ledger_entry ADD CONSTRAINT fk_ledger_entry_payment_id FOREIGN KEY (tenant_id, institution_id, payment_id) REFERENCES app.payment (tenant_id, institution_id, payment_id) ON DELETE RESTRICT;
ALTER TABLE app.ledger_entry ADD CONSTRAINT fk_ledger_entry_scholarship_award_id FOREIGN KEY (tenant_id, institution_id, scholarship_award_id) REFERENCES app.scholarship_award (tenant_id, institution_id, scholarship_award_id) ON DELETE RESTRICT;
ALTER TABLE app.ledger_entry ADD CONSTRAINT fk_ledger_entry_concession_id FOREIGN KEY (tenant_id, institution_id, concession_id) REFERENCES app.concession (tenant_id, institution_id, concession_id) ON DELETE RESTRICT;
ALTER TABLE app.ledger_entry ADD CONSTRAINT fk_ledger_entry_late_fee_waiver_id FOREIGN KEY (tenant_id, institution_id, late_fee_waiver_id) REFERENCES app.late_fee_waiver (tenant_id, institution_id, late_fee_waiver_id) ON DELETE RESTRICT;
ALTER TABLE app.ledger_entry ADD CONSTRAINT fk_ledger_entry_credit_application_id FOREIGN KEY (tenant_id, institution_id, credit_application_id) REFERENCES app.credit_application (tenant_id, institution_id, credit_application_id) ON DELETE RESTRICT;
ALTER TABLE app.ledger_entry ADD CONSTRAINT fk_ledger_entry_reverses_entry_id FOREIGN KEY (tenant_id, institution_id, reverses_entry_id) REFERENCES app.ledger_entry (tenant_id, institution_id, ledger_entry_id) ON DELETE RESTRICT;
ALTER TABLE app.payment ADD CONSTRAINT fk_payment_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.payment ADD CONSTRAINT fk_payment_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.payment ADD CONSTRAINT fk_payment_student_fee_account_id FOREIGN KEY (tenant_id, student_fee_account_id) REFERENCES app.student_fee_account (tenant_id, student_fee_account_id) ON DELETE RESTRICT;
ALTER TABLE app.payment ADD CONSTRAINT fk_payment_paid_by_user_id FOREIGN KEY (tenant_id, paid_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.payment ADD CONSTRAINT fk_payment_gateway_transaction_id FOREIGN KEY (tenant_id, institution_id, gateway_transaction_id) REFERENCES app.gateway_transaction (tenant_id, institution_id, gateway_transaction_id) ON DELETE RESTRICT;
ALTER TABLE app.payment ADD CONSTRAINT fk_payment_marked_by_user_id FOREIGN KEY (tenant_id, marked_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.payment_intent_item ADD CONSTRAINT fk_payment_intent_item_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.payment_intent_item ADD CONSTRAINT fk_payment_intent_item_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.payment_intent_item ADD CONSTRAINT fk_payment_intent_item_payment_id FOREIGN KEY (tenant_id, institution_id, payment_id) REFERENCES app.payment (tenant_id, institution_id, payment_id) ON DELETE RESTRICT;
ALTER TABLE app.payment_intent_item ADD CONSTRAINT fk_payment_intent_item_student_installment_id FOREIGN KEY (tenant_id, institution_id, student_installment_id) REFERENCES app.student_installment (tenant_id, institution_id, student_installment_id) ON DELETE RESTRICT;
ALTER TABLE app.gateway_order ADD CONSTRAINT fk_gateway_order_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.gateway_order ADD CONSTRAINT fk_gateway_order_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.gateway_order ADD CONSTRAINT fk_gateway_order_payment_id FOREIGN KEY (tenant_id, institution_id, payment_id) REFERENCES app.payment (tenant_id, institution_id, payment_id) ON DELETE RESTRICT;
ALTER TABLE app.gateway_transaction ADD CONSTRAINT fk_gateway_transaction_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.gateway_transaction ADD CONSTRAINT fk_gateway_transaction_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.gateway_transaction ADD CONSTRAINT fk_gateway_transaction_gateway_order_id FOREIGN KEY (tenant_id, institution_id, gateway_order_id) REFERENCES app.gateway_order (tenant_id, institution_id, gateway_order_id) ON DELETE RESTRICT;
ALTER TABLE app.payment_gateway_event ADD CONSTRAINT fk_payment_gateway_event_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.payment_gateway_event ADD CONSTRAINT fk_payment_gateway_event_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.payment_gateway_event ADD CONSTRAINT fk_payment_gateway_event_gateway_order_id FOREIGN KEY (tenant_id, gateway_order_id) REFERENCES app.gateway_order (tenant_id, gateway_order_id) ON DELETE RESTRICT;
ALTER TABLE app.payment_gateway_event ADD CONSTRAINT fk_payment_gateway_event_gateway_transaction_id FOREIGN KEY (tenant_id, gateway_transaction_id) REFERENCES app.gateway_transaction (tenant_id, gateway_transaction_id) ON DELETE RESTRICT;
ALTER TABLE app.payment_allocation ADD CONSTRAINT fk_payment_allocation_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.payment_allocation ADD CONSTRAINT fk_payment_allocation_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.payment_allocation ADD CONSTRAINT fk_payment_allocation_payment_id FOREIGN KEY (tenant_id, institution_id, payment_id) REFERENCES app.payment (tenant_id, institution_id, payment_id) ON DELETE RESTRICT;
ALTER TABLE app.payment_allocation ADD CONSTRAINT fk_payment_allocation_student_installment_id FOREIGN KEY (tenant_id, institution_id, student_installment_id) REFERENCES app.student_installment (tenant_id, institution_id, student_installment_id) ON DELETE RESTRICT;
ALTER TABLE app.credit_application ADD CONSTRAINT fk_credit_application_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.credit_application ADD CONSTRAINT fk_credit_application_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.credit_application ADD CONSTRAINT fk_credit_application_student_fee_account_id FOREIGN KEY (tenant_id, student_fee_account_id) REFERENCES app.student_fee_account (tenant_id, student_fee_account_id) ON DELETE RESTRICT;
ALTER TABLE app.credit_application ADD CONSTRAINT fk_credit_application_student_installment_id FOREIGN KEY (tenant_id, institution_id, student_installment_id) REFERENCES app.student_installment (tenant_id, institution_id, student_installment_id) ON DELETE RESTRICT;
ALTER TABLE app.reconciliation_case ADD CONSTRAINT fk_reconciliation_case_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.reconciliation_case ADD CONSTRAINT fk_reconciliation_case_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.reconciliation_case ADD CONSTRAINT fk_reconciliation_case_payment_id FOREIGN KEY (tenant_id, institution_id, payment_id) REFERENCES app.payment (tenant_id, institution_id, payment_id) ON DELETE RESTRICT;
ALTER TABLE app.reconciliation_case ADD CONSTRAINT fk_reconciliation_case_payment_gateway_event_id FOREIGN KEY (tenant_id, payment_gateway_event_id) REFERENCES app.payment_gateway_event (tenant_id, payment_gateway_event_id) ON DELETE RESTRICT;
ALTER TABLE app.reconciliation_case ADD CONSTRAINT fk_reconciliation_case_owner_user_id FOREIGN KEY (tenant_id, owner_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.receipt_sequence ADD CONSTRAINT fk_receipt_sequence_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.receipt_sequence ADD CONSTRAINT fk_receipt_sequence_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.receipt ADD CONSTRAINT fk_receipt_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.receipt ADD CONSTRAINT fk_receipt_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.receipt ADD CONSTRAINT fk_receipt_payment_id FOREIGN KEY (tenant_id, institution_id, payment_id) REFERENCES app.payment (tenant_id, institution_id, payment_id) ON DELETE RESTRICT;
ALTER TABLE app.receipt ADD CONSTRAINT fk_receipt_void_approval_request_id FOREIGN KEY (tenant_id, void_approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.receipt ADD CONSTRAINT fk_receipt_replaced_by_receipt_id FOREIGN KEY (tenant_id, institution_id, replaced_by_receipt_id) REFERENCES app.receipt (tenant_id, institution_id, receipt_id) ON DELETE RESTRICT;
ALTER TABLE app.scholarship_scheme ADD CONSTRAINT fk_scholarship_scheme_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.scholarship_scheme ADD CONSTRAINT fk_scholarship_scheme_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.scholarship_scheme ADD CONSTRAINT fk_scholarship_scheme_fee_policy_id FOREIGN KEY (tenant_id, fee_policy_id) REFERENCES app.fee_policy (tenant_id, fee_policy_id) ON DELETE RESTRICT;
ALTER TABLE app.scholarship_scheme ADD CONSTRAINT fk_scholarship_scheme_academic_session_id FOREIGN KEY (tenant_id, academic_session_id) REFERENCES app.academic_session (tenant_id, academic_session_id) ON DELETE RESTRICT;
ALTER TABLE app.scholarship_scheme ADD CONSTRAINT fk_scholarship_scheme_fee_head_id FOREIGN KEY (tenant_id, fee_head_id) REFERENCES app.fee_head (tenant_id, fee_head_id) ON DELETE RESTRICT;
ALTER TABLE app.scholarship_award_list ADD CONSTRAINT fk_scholarship_award_list_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.scholarship_award_list ADD CONSTRAINT fk_scholarship_award_list_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.scholarship_award_list ADD CONSTRAINT fk_scholarship_award_list_scholarship_scheme_id FOREIGN KEY (tenant_id, institution_id, scholarship_scheme_id) REFERENCES app.scholarship_scheme (tenant_id, institution_id, scholarship_scheme_id) ON DELETE RESTRICT;
ALTER TABLE app.scholarship_award_list ADD CONSTRAINT fk_scholarship_award_list_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.scholarship_award ADD CONSTRAINT fk_scholarship_award_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.scholarship_award ADD CONSTRAINT fk_scholarship_award_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.scholarship_award ADD CONSTRAINT fk_scholarship_award_list_id FOREIGN KEY (tenant_id, institution_id, list_id) REFERENCES app.scholarship_award_list (tenant_id, institution_id, list_id) ON DELETE RESTRICT;
ALTER TABLE app.scholarship_award ADD CONSTRAINT fk_scholarship_award_enrollment_id FOREIGN KEY (tenant_id, institution_id, enrollment_id) REFERENCES app.student_enrollment (tenant_id, institution_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.concession ADD CONSTRAINT fk_concession_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.concession ADD CONSTRAINT fk_concession_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.concession ADD CONSTRAINT fk_concession_student_fee_account_id FOREIGN KEY (tenant_id, student_fee_account_id) REFERENCES app.student_fee_account (tenant_id, student_fee_account_id) ON DELETE RESTRICT;
ALTER TABLE app.concession ADD CONSTRAINT fk_concession_student_installment_id FOREIGN KEY (tenant_id, institution_id, student_installment_id) REFERENCES app.student_installment (tenant_id, institution_id, student_installment_id) ON DELETE RESTRICT;
ALTER TABLE app.concession ADD CONSTRAINT fk_concession_fee_head_id FOREIGN KEY (tenant_id, fee_head_id) REFERENCES app.fee_head (tenant_id, fee_head_id) ON DELETE RESTRICT;
ALTER TABLE app.concession ADD CONSTRAINT fk_concession_requested_by FOREIGN KEY (tenant_id, requested_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.concession ADD CONSTRAINT fk_concession_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.late_fee_rule ADD CONSTRAINT fk_late_fee_rule_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.late_fee_rule ADD CONSTRAINT fk_late_fee_rule_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.late_fee_waiver ADD CONSTRAINT fk_late_fee_waiver_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.late_fee_waiver ADD CONSTRAINT fk_late_fee_waiver_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.late_fee_waiver ADD CONSTRAINT fk_late_fee_waiver_late_fee_ledger_entry_id FOREIGN KEY (tenant_id, institution_id, late_fee_ledger_entry_id) REFERENCES app.ledger_entry (tenant_id, institution_id, ledger_entry_id) ON DELETE RESTRICT;
ALTER TABLE app.late_fee_waiver ADD CONSTRAINT fk_late_fee_waiver_requested_by FOREIGN KEY (tenant_id, requested_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.late_fee_waiver ADD CONSTRAINT fk_late_fee_waiver_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.financial_period_lock ADD CONSTRAINT fk_financial_period_lock_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.financial_period_lock ADD CONSTRAINT fk_financial_period_lock_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.financial_period_lock ADD CONSTRAINT fk_financial_period_lock_unlock_approval_request_id FOREIGN KEY (tenant_id, unlock_approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.financial_period_lock_event ADD CONSTRAINT fk_financial_period_lock_event_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.financial_period_lock_event ADD CONSTRAINT fk_financial_period_lock_event_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.financial_period_lock_event ADD CONSTRAINT fk_financial_period_lock_event_period_lock_id FOREIGN KEY (tenant_id, period_lock_id) REFERENCES app.financial_period_lock (tenant_id, period_lock_id) ON DELETE RESTRICT;
ALTER TABLE app.financial_period_lock_event ADD CONSTRAINT fk_financial_period_lock_event_actor_user_id FOREIGN KEY (tenant_id, actor_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment ADD CONSTRAINT fk_assignment_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment ADD CONSTRAINT fk_assignment_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment ADD CONSTRAINT fk_assignment_course_offering_id FOREIGN KEY (tenant_id, institution_id, course_offering_id) REFERENCES app.course_offering (tenant_id, institution_id, course_offering_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment ADD CONSTRAINT fk_assignment_section_id FOREIGN KEY (tenant_id, institution_id, section_id) REFERENCES app.section (tenant_id, institution_id, section_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment ADD CONSTRAINT fk_assignment_subject_group_id FOREIGN KEY (tenant_id, institution_id, subject_group_id) REFERENCES app.subject_group (tenant_id, institution_id, subject_group_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment ADD CONSTRAINT fk_assignment_created_by_user_id FOREIGN KEY (tenant_id, created_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment_submission ADD CONSTRAINT fk_assignment_submission_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment_submission ADD CONSTRAINT fk_assignment_submission_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment_submission ADD CONSTRAINT fk_assignment_submission_assignment_id FOREIGN KEY (tenant_id, institution_id, assignment_id) REFERENCES app.assignment (tenant_id, institution_id, assignment_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment_submission ADD CONSTRAINT fk_assignment_submission_enrollment_id FOREIGN KEY (tenant_id, institution_id, enrollment_id) REFERENCES app.student_enrollment (tenant_id, institution_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment_submission_file ADD CONSTRAINT fk_assignment_submission_file_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment_submission_file ADD CONSTRAINT fk_assignment_submission_file_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment_submission_file ADD CONSTRAINT fk_assignment_submission_file_submission_id FOREIGN KEY (tenant_id, institution_id, submission_id) REFERENCES app.assignment_submission (tenant_id, institution_id, submission_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment_submission_file ADD CONSTRAINT fk_assignment_submission_file_file_id FOREIGN KEY (tenant_id, file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment_feedback ADD CONSTRAINT fk_assignment_feedback_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment_feedback ADD CONSTRAINT fk_assignment_feedback_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment_feedback ADD CONSTRAINT fk_assignment_feedback_submission_id FOREIGN KEY (tenant_id, institution_id, submission_id) REFERENCES app.assignment_submission (tenant_id, institution_id, submission_id) ON DELETE RESTRICT;
ALTER TABLE app.assignment_feedback ADD CONSTRAINT fk_assignment_feedback_evaluated_by FOREIGN KEY (tenant_id, evaluated_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.homework_status ADD CONSTRAINT fk_homework_status_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.homework_status ADD CONSTRAINT fk_homework_status_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.homework_status ADD CONSTRAINT fk_homework_status_assignment_id FOREIGN KEY (tenant_id, institution_id, assignment_id) REFERENCES app.assignment (tenant_id, institution_id, assignment_id) ON DELETE RESTRICT;
ALTER TABLE app.homework_status ADD CONSTRAINT fk_homework_status_enrollment_id FOREIGN KEY (tenant_id, institution_id, enrollment_id) REFERENCES app.student_enrollment (tenant_id, institution_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.homework_status ADD CONSTRAINT fk_homework_status_marked_by FOREIGN KEY (tenant_id, marked_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.study_material ADD CONSTRAINT fk_study_material_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.study_material ADD CONSTRAINT fk_study_material_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.study_material ADD CONSTRAINT fk_study_material_course_offering_id FOREIGN KEY (tenant_id, institution_id, course_offering_id) REFERENCES app.course_offering (tenant_id, institution_id, course_offering_id) ON DELETE RESTRICT;
ALTER TABLE app.study_material ADD CONSTRAINT fk_study_material_section_id FOREIGN KEY (tenant_id, institution_id, section_id) REFERENCES app.section (tenant_id, institution_id, section_id) ON DELETE RESTRICT;
ALTER TABLE app.study_material ADD CONSTRAINT fk_study_material_subject_group_id FOREIGN KEY (tenant_id, institution_id, subject_group_id) REFERENCES app.subject_group (tenant_id, institution_id, subject_group_id) ON DELETE RESTRICT;
ALTER TABLE app.study_material ADD CONSTRAINT fk_study_material_file_id FOREIGN KEY (tenant_id, file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.study_material ADD CONSTRAINT fk_study_material_uploaded_by FOREIGN KEY (tenant_id, uploaded_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.notice ADD CONSTRAINT fk_notice_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.notice ADD CONSTRAINT fk_notice_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.notice ADD CONSTRAINT fk_notice_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES app.department (tenant_id, department_id) ON DELETE RESTRICT;
ALTER TABLE app.notice ADD CONSTRAINT fk_notice_section_id FOREIGN KEY (tenant_id, section_id) REFERENCES app.section (tenant_id, section_id) ON DELETE RESTRICT;
ALTER TABLE app.notice ADD CONSTRAINT fk_notice_authored_by_user_id FOREIGN KEY (tenant_id, authored_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.notice ADD CONSTRAINT fk_notice_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.notice ADD CONSTRAINT fk_notice_published_by_user_id FOREIGN KEY (tenant_id, published_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.notice_audience ADD CONSTRAINT fk_notice_audience_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.notice_audience ADD CONSTRAINT fk_notice_audience_notice_id FOREIGN KEY (tenant_id, notice_id) REFERENCES app.notice (tenant_id, notice_id) ON DELETE RESTRICT;
ALTER TABLE app.notice_audience ADD CONSTRAINT fk_notice_audience_role_definition_id FOREIGN KEY (tenant_id, role_definition_id) REFERENCES app.role_definition (tenant_id, role_definition_id) ON DELETE RESTRICT;
ALTER TABLE app.message_thread ADD CONSTRAINT fk_message_thread_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.message_thread ADD CONSTRAINT fk_message_thread_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.message_thread ADD CONSTRAINT fk_message_thread_about_enrollment_id FOREIGN KEY (tenant_id, institution_id, about_enrollment_id) REFERENCES app.student_enrollment (tenant_id, institution_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.message_thread ADD CONSTRAINT fk_message_thread_broadcast_section_id FOREIGN KEY (tenant_id, institution_id, broadcast_section_id) REFERENCES app.section (tenant_id, institution_id, section_id) ON DELETE RESTRICT;
ALTER TABLE app.message_thread ADD CONSTRAINT fk_message_thread_origin_broadcast_thread_id FOREIGN KEY (tenant_id, institution_id, origin_broadcast_thread_id) REFERENCES app.message_thread (tenant_id, institution_id, thread_id) ON DELETE RESTRICT;
ALTER TABLE app.message_thread ADD CONSTRAINT fk_message_thread_office_handler_user_id FOREIGN KEY (tenant_id, office_handler_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.message_thread_participant ADD CONSTRAINT fk_message_thread_participant_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.message_thread_participant ADD CONSTRAINT fk_message_thread_participant_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.message_thread_participant ADD CONSTRAINT fk_message_thread_participant_thread_id FOREIGN KEY (tenant_id, institution_id, thread_id) REFERENCES app.message_thread (tenant_id, institution_id, thread_id) ON DELETE RESTRICT;
ALTER TABLE app.message_thread_participant ADD CONSTRAINT fk_message_thread_participant_user_account_id FOREIGN KEY (tenant_id, user_account_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.message ADD CONSTRAINT fk_message_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.message ADD CONSTRAINT fk_message_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.message ADD CONSTRAINT fk_message_thread_id FOREIGN KEY (tenant_id, institution_id, thread_id) REFERENCES app.message_thread (tenant_id, institution_id, thread_id) ON DELETE RESTRICT;
ALTER TABLE app.message ADD CONSTRAINT fk_message_sender_user_id FOREIGN KEY (tenant_id, sender_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.message_attachment ADD CONSTRAINT fk_message_attachment_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.message_attachment ADD CONSTRAINT fk_message_attachment_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.message_attachment ADD CONSTRAINT fk_message_attachment_message_id FOREIGN KEY (tenant_id, institution_id, message_id) REFERENCES app.message (tenant_id, institution_id, message_id) ON DELETE RESTRICT;
ALTER TABLE app.message_attachment ADD CONSTRAINT fk_message_attachment_file_id FOREIGN KEY (tenant_id, file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.notification ADD CONSTRAINT fk_notification_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.notification ADD CONSTRAINT fk_notification_recipient_user_id FOREIGN KEY (tenant_id, recipient_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.notification ADD CONSTRAINT fk_notification_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.notification ADD CONSTRAINT fk_notification_trigger_code FOREIGN KEY (trigger_code) REFERENCES ref.notification_template (trigger_code) ON DELETE RESTRICT;
ALTER TABLE app.notification ADD CONSTRAINT fk_notification_notice_id FOREIGN KEY (tenant_id, notice_id) REFERENCES app.notice (tenant_id, notice_id) ON DELETE RESTRICT;
ALTER TABLE app.notification ADD CONSTRAINT fk_notification_created_by_user_id FOREIGN KEY (tenant_id, created_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.notification_delivery ADD CONSTRAINT fk_notification_delivery_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.notification_delivery ADD CONSTRAINT fk_notification_delivery_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.notification_delivery ADD CONSTRAINT fk_notification_delivery_notification_id FOREIGN KEY (tenant_id, notification_id) REFERENCES app.notification (tenant_id, notification_id) ON DELETE RESTRICT;
ALTER TABLE app.notification_delivery_attempt ADD CONSTRAINT fk_notification_delivery_attempt_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.notification_delivery_attempt ADD CONSTRAINT fk_notification_delivery_attempt_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.notification_delivery_attempt ADD CONSTRAINT fk_notification_delivery_attempt_delivery_id FOREIGN KEY (tenant_id, delivery_id) REFERENCES app.notification_delivery (tenant_id, delivery_id) ON DELETE RESTRICT;
ALTER TABLE app.push_subscription ADD CONSTRAINT fk_push_subscription_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.push_subscription ADD CONSTRAINT fk_push_subscription_user_device_id FOREIGN KEY (tenant_id, user_device_id) REFERENCES app.user_device (tenant_id, user_device_id) ON DELETE RESTRICT;
ALTER TABLE app.channel_config ADD CONSTRAINT fk_channel_config_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.channel_config ADD CONSTRAINT fk_channel_config_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.sms_template ADD CONSTRAINT fk_sms_template_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.sms_template ADD CONSTRAINT fk_sms_template_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.sms_template ADD CONSTRAINT fk_sms_template_trigger_code FOREIGN KEY (trigger_code) REFERENCES ref.notification_template (trigger_code) ON DELETE RESTRICT;
ALTER TABLE app.community_post ADD CONSTRAINT fk_community_post_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.community_post ADD CONSTRAINT fk_community_post_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.community_post ADD CONSTRAINT fk_community_post_author_user_id FOREIGN KEY (tenant_id, author_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.community_post ADD CONSTRAINT fk_community_post_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.community_post_media ADD CONSTRAINT fk_community_post_media_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.community_post_media ADD CONSTRAINT fk_community_post_media_post_id FOREIGN KEY (tenant_id, post_id) REFERENCES app.community_post (tenant_id, post_id) ON DELETE RESTRICT;
ALTER TABLE app.community_post_media ADD CONSTRAINT fk_community_post_media_file_id FOREIGN KEY (tenant_id, file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.post_comment ADD CONSTRAINT fk_post_comment_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.post_comment ADD CONSTRAINT fk_post_comment_post_id FOREIGN KEY (tenant_id, post_id) REFERENCES app.community_post (tenant_id, post_id) ON DELETE RESTRICT;
ALTER TABLE app.post_comment ADD CONSTRAINT fk_post_comment_author_user_id FOREIGN KEY (tenant_id, author_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.post_comment ADD CONSTRAINT fk_post_comment_hidden_by FOREIGN KEY (tenant_id, hidden_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.post_like ADD CONSTRAINT fk_post_like_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.post_like ADD CONSTRAINT fk_post_like_post_id FOREIGN KEY (tenant_id, post_id) REFERENCES app.community_post (tenant_id, post_id) ON DELETE RESTRICT;
ALTER TABLE app.post_like ADD CONSTRAINT fk_post_like_user_account_id FOREIGN KEY (tenant_id, user_account_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.event ADD CONSTRAINT fk_event_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.event ADD CONSTRAINT fk_event_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.event ADD CONSTRAINT fk_event_proposed_by_user_id FOREIGN KEY (tenant_id, proposed_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.event ADD CONSTRAINT fk_event_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.event_rsvp ADD CONSTRAINT fk_event_rsvp_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.event_rsvp ADD CONSTRAINT fk_event_rsvp_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.event_rsvp ADD CONSTRAINT fk_event_rsvp_event_id FOREIGN KEY (tenant_id, institution_id, event_id) REFERENCES app.event (tenant_id, institution_id, event_id) ON DELETE RESTRICT;
ALTER TABLE app.event_rsvp ADD CONSTRAINT fk_event_rsvp_user_account_id FOREIGN KEY (tenant_id, user_account_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.complaint ADD CONSTRAINT fk_complaint_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.complaint ADD CONSTRAINT fk_complaint_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.complaint ADD CONSTRAINT fk_complaint_raised_by_user_id FOREIGN KEY (tenant_id, raised_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.complaint ADD CONSTRAINT fk_complaint_about_enrollment_id FOREIGN KEY (tenant_id, institution_id, about_enrollment_id) REFERENCES app.student_enrollment (tenant_id, institution_id, enrollment_id) ON DELETE RESTRICT;
ALTER TABLE app.complaint ADD CONSTRAINT fk_complaint_current_owner_user_id FOREIGN KEY (tenant_id, current_owner_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.complaint_action ADD CONSTRAINT fk_complaint_action_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.complaint_action ADD CONSTRAINT fk_complaint_action_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.complaint_action ADD CONSTRAINT fk_complaint_action_complaint_id FOREIGN KEY (tenant_id, institution_id, complaint_id) REFERENCES app.complaint (tenant_id, institution_id, complaint_id) ON DELETE RESTRICT;
ALTER TABLE app.complaint_action ADD CONSTRAINT fk_complaint_action_actor_user_id FOREIGN KEY (tenant_id, actor_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.complaint_action ADD CONSTRAINT fk_complaint_action_to_user_id FOREIGN KEY (tenant_id, to_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.student_note ADD CONSTRAINT fk_student_note_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.student_note ADD CONSTRAINT fk_student_note_student_profile_id FOREIGN KEY (tenant_id, student_profile_id) REFERENCES app.student_profile (tenant_id, student_profile_id) ON DELETE RESTRICT;
ALTER TABLE app.student_note ADD CONSTRAINT fk_student_note_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.student_note ADD CONSTRAINT fk_student_note_added_by_user_id FOREIGN KEY (tenant_id, added_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.student_note ADD CONSTRAINT fk_student_note_corrects_note_id FOREIGN KEY (tenant_id, institution_id, corrects_note_id) REFERENCES app.student_note (tenant_id, institution_id, student_note_id) ON DELETE RESTRICT;
ALTER TABLE app.policy ADD CONSTRAINT fk_policy_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.policy ADD CONSTRAINT fk_policy_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.policy ADD CONSTRAINT fk_policy_body_file_id FOREIGN KEY (tenant_id, body_file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.policy ADD CONSTRAINT fk_policy_drafted_by FOREIGN KEY (tenant_id, drafted_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.policy ADD CONSTRAINT fk_policy_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.policy ADD CONSTRAINT fk_policy_published_by FOREIGN KEY (tenant_id, published_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.policy_acceptance ADD CONSTRAINT fk_policy_acceptance_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.policy_acceptance ADD CONSTRAINT fk_policy_acceptance_policy_id FOREIGN KEY (tenant_id, policy_id) REFERENCES app.policy (tenant_id, policy_id) ON DELETE RESTRICT;
ALTER TABLE app.policy_acceptance ADD CONSTRAINT fk_policy_acceptance_user_account_id FOREIGN KEY (tenant_id, user_account_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.department_document ADD CONSTRAINT fk_department_document_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.department_document ADD CONSTRAINT fk_department_document_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.department_document ADD CONSTRAINT fk_department_document_department_id FOREIGN KEY (tenant_id, institution_id, department_id) REFERENCES app.department (tenant_id, institution_id, department_id) ON DELETE RESTRICT;
ALTER TABLE app.department_document ADD CONSTRAINT fk_department_document_file_id FOREIGN KEY (tenant_id, file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.department_document ADD CONSTRAINT fk_department_document_uploaded_by FOREIGN KEY (tenant_id, uploaded_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.compliance_report ADD CONSTRAINT fk_compliance_report_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.compliance_report ADD CONSTRAINT fk_compliance_report_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.compliance_report ADD CONSTRAINT fk_compliance_report_file_id FOREIGN KEY (tenant_id, file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.compliance_report ADD CONSTRAINT fk_compliance_report_prepared_by FOREIGN KEY (tenant_id, prepared_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.compliance_report ADD CONSTRAINT fk_compliance_report_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.compliance_report ADD CONSTRAINT fk_compliance_report_approved_by FOREIGN KEY (tenant_id, approved_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.accreditation_criterion ADD CONSTRAINT fk_accreditation_criterion_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.accreditation_criterion ADD CONSTRAINT fk_accreditation_criterion_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.accreditation_document ADD CONSTRAINT fk_accreditation_document_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.accreditation_document ADD CONSTRAINT fk_accreditation_document_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.accreditation_document ADD CONSTRAINT fk_accreditation_document_file_id FOREIGN KEY (tenant_id, file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.accreditation_document ADD CONSTRAINT fk_accreditation_document_uploaded_by FOREIGN KEY (tenant_id, uploaded_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.accreditation_document ADD CONSTRAINT fk_accreditation_document_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.accreditation_evidence_map ADD CONSTRAINT fk_accreditation_evidence_map_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.accreditation_evidence_map ADD CONSTRAINT fk_accreditation_evidence_map_accreditation_document_id FOREIGN KEY (tenant_id, accreditation_document_id) REFERENCES app.accreditation_document (tenant_id, accreditation_document_id) ON DELETE RESTRICT;
ALTER TABLE app.accreditation_evidence_map ADD CONSTRAINT fk_accreditation_evidence_map_criterion_id FOREIGN KEY (tenant_id, criterion_id) REFERENCES app.accreditation_criterion (tenant_id, criterion_id) ON DELETE RESTRICT;
ALTER TABLE app.accreditation_evidence_map ADD CONSTRAINT fk_accreditation_evidence_map_mapped_by FOREIGN KEY (tenant_id, mapped_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.governance_item ADD CONSTRAINT fk_governance_item_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.governance_item ADD CONSTRAINT fk_governance_item_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.governance_item ADD CONSTRAINT fk_governance_item_submitted_by FOREIGN KEY (tenant_id, submitted_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.governance_item ADD CONSTRAINT fk_governance_item_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.audit_finding ADD CONSTRAINT fk_audit_finding_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.audit_finding ADD CONSTRAINT fk_audit_finding_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.audit_finding ADD CONSTRAINT fk_audit_finding_assigned_owner_user_id FOREIGN KEY (tenant_id, assigned_owner_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.audit_finding ADD CONSTRAINT fk_audit_finding_recorded_by FOREIGN KEY (tenant_id, recorded_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.data_request ADD CONSTRAINT fk_data_request_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.data_request ADD CONSTRAINT fk_data_request_raised_by_user_id FOREIGN KEY (tenant_id, raised_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.data_request ADD CONSTRAINT fk_data_request_owner_user_id FOREIGN KEY (tenant_id, owner_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.data_request ADD CONSTRAINT fk_data_request_identity_verified_by FOREIGN KEY (tenant_id, identity_verified_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.data_request_event ADD CONSTRAINT fk_data_request_event_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.data_request_event ADD CONSTRAINT fk_data_request_event_data_request_id FOREIGN KEY (tenant_id, data_request_id) REFERENCES app.data_request (tenant_id, data_request_id) ON DELETE RESTRICT;
ALTER TABLE app.data_request_event ADD CONSTRAINT fk_data_request_event_actor_user_id FOREIGN KEY (tenant_id, actor_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.legal_hold ADD CONSTRAINT fk_legal_hold_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.legal_hold ADD CONSTRAINT fk_legal_hold_placed_by FOREIGN KEY (tenant_id, placed_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.legal_hold ADD CONSTRAINT fk_legal_hold_released_by FOREIGN KEY (tenant_id, released_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.file_object ADD CONSTRAINT fk_file_object_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.file_object ADD CONSTRAINT fk_file_object_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.file_object ADD CONSTRAINT fk_file_object_uploaded_by FOREIGN KEY (tenant_id, uploaded_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.audit_outbox ADD CONSTRAINT fk_audit_outbox_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.audit_outbox ADD CONSTRAINT fk_audit_outbox_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.audit_outbox ADD CONSTRAINT fk_audit_outbox_action_code FOREIGN KEY (action_code) REFERENCES ref.action_catalog (action_code) ON DELETE RESTRICT;
ALTER TABLE app.outbox_event ADD CONSTRAINT fk_outbox_event_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.import_job ADD CONSTRAINT fk_import_job_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.import_job ADD CONSTRAINT fk_import_job_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.import_job ADD CONSTRAINT fk_import_job_uploaded_by FOREIGN KEY (tenant_id, uploaded_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.import_job ADD CONSTRAINT fk_import_job_source_file_id FOREIGN KEY (tenant_id, source_file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.import_row_error ADD CONSTRAINT fk_import_row_error_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.import_row_error ADD CONSTRAINT fk_import_row_error_import_job_id FOREIGN KEY (tenant_id, import_job_id) REFERENCES app.import_job (tenant_id, import_job_id) ON DELETE RESTRICT;
ALTER TABLE app.bulk_correction_batch ADD CONSTRAINT fk_bulk_correction_batch_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.bulk_correction_batch ADD CONSTRAINT fk_bulk_correction_batch_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.bulk_correction_batch ADD CONSTRAINT fk_bulk_correction_batch_submitted_by FOREIGN KEY (tenant_id, submitted_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.bulk_correction_batch ADD CONSTRAINT fk_bulk_correction_batch_source_file_id FOREIGN KEY (tenant_id, source_file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.bulk_correction_item ADD CONSTRAINT fk_bulk_correction_item_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.bulk_correction_item ADD CONSTRAINT fk_bulk_correction_item_bulk_correction_batch_id FOREIGN KEY (tenant_id, bulk_correction_batch_id) REFERENCES app.bulk_correction_batch (tenant_id, bulk_correction_batch_id) ON DELETE RESTRICT;
ALTER TABLE app.report_export_job ADD CONSTRAINT fk_report_export_job_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.report_export_job ADD CONSTRAINT fk_report_export_job_requested_by_user_id FOREIGN KEY (tenant_id, requested_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.report_export_job ADD CONSTRAINT fk_report_export_job_report_code FOREIGN KEY (report_code) REFERENCES ref.report_catalog (report_code) ON DELETE RESTRICT;
ALTER TABLE app.report_export_job ADD CONSTRAINT fk_report_export_job_file_id FOREIGN KEY (tenant_id, file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.background_job ADD CONSTRAINT fk_background_job_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.background_job ADD CONSTRAINT fk_background_job_job_type FOREIGN KEY (job_type) REFERENCES ref.job_type (job_type) ON DELETE RESTRICT;
ALTER TABLE app.background_job ADD CONSTRAINT fk_background_job_requested_by_user_id FOREIGN KEY (tenant_id, requested_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.idempotency_key ADD CONSTRAINT fk_idempotency_key_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.idempotency_key ADD CONSTRAINT fk_idempotency_key_scope FOREIGN KEY (scope) REFERENCES ref.idempotency_scope (scope) ON DELETE RESTRICT;
ALTER TABLE app.idempotency_key ADD CONSTRAINT fk_idempotency_key_caller_user_id FOREIGN KEY (tenant_id, caller_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.setting_value ADD CONSTRAINT fk_setting_value_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.setting_value ADD CONSTRAINT fk_setting_value_setting_key FOREIGN KEY (setting_key) REFERENCES ref.setting_definition (setting_key) ON DELETE RESTRICT;
ALTER TABLE app.setting_value ADD CONSTRAINT fk_setting_value_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.saved_filter ADD CONSTRAINT fk_saved_filter_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.saved_filter ADD CONSTRAINT fk_saved_filter_user_account_id FOREIGN KEY (tenant_id, user_account_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.saved_filter ADD CONSTRAINT fk_saved_filter_report_code FOREIGN KEY (report_code) REFERENCES ref.report_catalog (report_code) ON DELETE RESTRICT;
ALTER TABLE app.dashboard_preference ADD CONSTRAINT fk_dashboard_preference_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.dashboard_preference ADD CONSTRAINT fk_dashboard_preference_user_account_id FOREIGN KEY (tenant_id, user_account_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.usage_metric_daily ADD CONSTRAINT fk_usage_metric_daily_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.usage_metric_daily ADD CONSTRAINT fk_usage_metric_daily_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.certificate_request ADD CONSTRAINT fk_certificate_request_tenant_id FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT;
ALTER TABLE app.certificate_request ADD CONSTRAINT fk_certificate_request_student_profile_id FOREIGN KEY (tenant_id, student_profile_id) REFERENCES app.student_profile (tenant_id, student_profile_id) ON DELETE RESTRICT;
ALTER TABLE app.certificate_request ADD CONSTRAINT fk_certificate_request_institution_id FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT;
ALTER TABLE app.certificate_request ADD CONSTRAINT fk_certificate_request_requested_by_user_id FOREIGN KEY (tenant_id, requested_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.certificate_request ADD CONSTRAINT fk_certificate_request_no_dues_override_by FOREIGN KEY (tenant_id, no_dues_override_by) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.certificate_request ADD CONSTRAINT fk_certificate_request_approval_request_id FOREIGN KEY (tenant_id, approval_request_id) REFERENCES app.approval_request (tenant_id, approval_request_id) ON DELETE RESTRICT;
ALTER TABLE app.certificate_request ADD CONSTRAINT fk_certificate_request_prepared_by_user_id FOREIGN KEY (tenant_id, prepared_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.certificate_request ADD CONSTRAINT fk_certificate_request_file_id FOREIGN KEY (tenant_id, file_id) REFERENCES app.file_object (tenant_id, file_id) ON DELETE RESTRICT;
ALTER TABLE app.certificate_request ADD CONSTRAINT fk_certificate_request_applied_by_user_id FOREIGN KEY (tenant_id, applied_by_user_id) REFERENCES app.user_account (tenant_id, user_account_id) ON DELETE RESTRICT;
ALTER TABLE app.attendance_correction ADD CONSTRAINT fk_attendance_correction_entry FOREIGN KEY (tenant_id, attendance_entry_id, academic_year) REFERENCES app.attendance_entry (tenant_id, attendance_entry_id, academic_year);
CREATE INDEX ix_plan_entitlement_plan_id ON platform.plan_entitlement (plan_id);
CREATE INDEX ix_plan_entitlement_module_code ON platform.plan_entitlement (module_code);
CREATE INDEX ix_plan_entitlement_feature_code ON platform.plan_entitlement (feature_code);
CREATE INDEX ix_tenant_plan_id ON platform.tenant (plan_id);
CREATE INDEX ix_tenant_entitlement_tenant_id ON platform.tenant_entitlement (tenant_id);
CREATE INDEX ix_tenant_entitlement_module_code ON platform.tenant_entitlement (module_code);
CREATE INDEX ix_tenant_entitlement_feature_code ON platform.tenant_entitlement (feature_code);
CREATE INDEX ix_tenant_retention_policy_tenant_id ON platform.tenant_retention_policy (tenant_id);
CREATE INDEX ix_tenant_retention_policy_data_class_code ON platform.tenant_retention_policy (data_class_code);
CREATE INDEX ix_retention_change_request_tenant_id ON platform.retention_change_request (tenant_id);
CREATE INDEX ix_retention_change_request_data_class_code ON platform.retention_change_request (data_class_code);
CREATE INDEX ix_retention_change_request_requested_by ON platform.retention_change_request (requested_by);
CREATE INDEX ix_retention_change_request_decided_by ON platform.retention_change_request (decided_by);
CREATE INDEX ix_invoice_tenant_id ON platform.invoice (tenant_id);
CREATE INDEX ix_invoice_discount_applied_by ON platform.invoice (discount_applied_by);
CREATE INDEX ix_invoice_line_invoice_id ON platform.invoice_line (invoice_id);
CREATE INDEX ix_invoice_line_tenant_usage_monthly_id ON platform.invoice_line (tenant_usage_monthly_id);
CREATE INDEX ix_tenant_usage_monthly_tenant_id ON platform.tenant_usage_monthly (tenant_id);
CREATE INDEX ix_support_ticket_tenant_id ON platform.support_ticket (tenant_id);
CREATE INDEX ix_support_ticket_assigned_to ON platform.support_ticket (assigned_to);
CREATE INDEX ix_support_access_grant_ticket_id ON platform.support_access_grant (ticket_id);
CREATE INDEX ix_support_access_grant_tenant_id ON platform.support_access_grant (tenant_id);
CREATE INDEX ix_export_request_tenant_id ON platform.export_request (tenant_id);
CREATE INDEX ix_export_request_decided_by ON platform.export_request (decided_by);
CREATE INDEX ix_deletion_request_tenant_id ON platform.deletion_request (tenant_id);
CREATE INDEX ix_deletion_request_po_confirmed_by ON platform.deletion_request (po_confirmed_by);
CREATE INDEX ix_deletion_request_export_request_id ON platform.deletion_request (export_request_id);
CREATE INDEX ix_deletion_request_dpo_decided_by ON platform.deletion_request (dpo_decided_by);
CREATE INDEX ix_deletion_certificate_deletion_request_id ON platform.deletion_certificate (deletion_request_id);
CREATE INDEX ix_deletion_certificate_tenant_id ON platform.deletion_certificate (tenant_id);
CREATE INDEX ix_deletion_certificate_approved_by ON platform.deletion_certificate (approved_by);
CREATE INDEX ix_tenant_backup_status_tenant_id ON platform.tenant_backup_status (tenant_id);
CREATE INDEX ix_setting_value_setting_key ON platform.setting_value (setting_key);
CREATE INDEX ix_audit_outbox_tenant_id ON platform.audit_outbox (tenant_id);
CREATE INDEX ix_audit_outbox_actor_user_id ON platform.audit_outbox (actor_user_id);
CREATE INDEX ix_audit_outbox_action_code ON platform.audit_outbox (action_code);
CREATE INDEX ix_tenant_provision_step_tenant_id ON platform.tenant_provision_step (tenant_id);
CREATE INDEX ix_schema_release_release_id ON platform.schema_release (release_id);
CREATE INDEX ix_tenant_migration_state_tenant_id ON platform.tenant_migration_state (tenant_id);
CREATE INDEX ix_migration_run_release_id ON platform.migration_run (release_id);
CREATE INDEX ix_migration_run_tenant_id ON platform.migration_run (tenant_id);
CREATE INDEX ix_tenant_relocation_tenant_id ON platform.tenant_relocation (tenant_id);
CREATE INDEX ix_payment_route_tenant_id ON platform.payment_route (tenant_id);
CREATE INDEX ix_security_incident_tenant_id ON platform.security_incident (tenant_id);
CREATE INDEX ix_security_incident_owner_user_id ON platform.security_incident (owner_user_id);
CREATE INDEX ix_institution_tenant_id ON app.institution (tenant_id);
CREATE INDEX ix_leave_approver_setting_tenant_id ON app.leave_approver_setting (tenant_id);
CREATE INDEX ix_leave_approver_setting_institution_id ON app.leave_approver_setting (tenant_id, institution_id);
CREATE INDEX ix_leave_approver_setting_approver_user_id ON app.leave_approver_setting (tenant_id, approver_user_id);
CREATE INDEX ix_academic_session_tenant_id ON app.academic_session (tenant_id);
CREATE INDEX ix_academic_session_institution_id ON app.academic_session (tenant_id, institution_id);
CREATE INDEX ix_academic_calendar_event_tenant_id ON app.academic_calendar_event (tenant_id);
CREATE INDEX ix_academic_calendar_event_academic_session_id ON app.academic_calendar_event (tenant_id, academic_session_id);
CREATE INDEX ix_academic_calendar_event_institution_id ON app.academic_calendar_event (tenant_id, institution_id);
CREATE INDEX ix_weekly_off_rule_tenant_id ON app.weekly_off_rule (tenant_id);
CREATE INDEX ix_weekly_off_rule_institution_id ON app.weekly_off_rule (tenant_id, institution_id);
CREATE INDEX ix_department_tenant_id ON app.department (tenant_id);
CREATE INDEX ix_department_institution_id ON app.department (tenant_id, institution_id);
CREATE INDEX ix_department_hod_staff_assignment_id ON app.department (tenant_id, hod_staff_assignment_id);
CREATE INDEX ix_program_tenant_id ON app.program (tenant_id);
CREATE INDEX ix_program_institution_id ON app.program (tenant_id, institution_id);
CREATE INDEX ix_program_department_id ON app.program (tenant_id, department_id);
CREATE INDEX ix_program_rule_set_tenant_id ON app.program_rule_set (tenant_id);
CREATE INDEX ix_program_rule_set_institution_id ON app.program_rule_set (tenant_id, institution_id);
CREATE INDEX ix_program_rule_set_program_id ON app.program_rule_set (tenant_id, program_id);
CREATE INDEX ix_program_year_tenant_id ON app.program_year (tenant_id);
CREATE INDEX ix_program_year_institution_id ON app.program_year (tenant_id, institution_id);
CREATE INDEX ix_program_year_program_id ON app.program_year (tenant_id, program_id);
CREATE INDEX ix_semester_tenant_id ON app.semester (tenant_id);
CREATE INDEX ix_semester_institution_id ON app.semester (tenant_id, institution_id);
CREATE INDEX ix_semester_program_year_id ON app.semester (tenant_id, program_year_id);
CREATE INDEX ix_school_class_tenant_id ON app.school_class (tenant_id);
CREATE INDEX ix_school_class_institution_id ON app.school_class (tenant_id, institution_id);
CREATE INDEX ix_section_tenant_id ON app.section (tenant_id);
CREATE INDEX ix_section_institution_id ON app.section (tenant_id, institution_id);
CREATE INDEX ix_section_academic_session_id ON app.section (tenant_id, academic_session_id);
CREATE INDEX ix_section_semester_id ON app.section (tenant_id, semester_id);
CREATE INDEX ix_section_class_id ON app.section (tenant_id, class_id);
CREATE INDEX ix_subject_tenant_id ON app.subject (tenant_id);
CREATE INDEX ix_subject_institution_id ON app.subject (tenant_id, institution_id);
CREATE INDEX ix_syllabus_tenant_id ON app.syllabus (tenant_id);
CREATE INDEX ix_syllabus_institution_id ON app.syllabus (tenant_id, institution_id);
CREATE INDEX ix_syllabus_subject_id ON app.syllabus (tenant_id, subject_id);
CREATE INDEX ix_syllabus_academic_session_id ON app.syllabus (tenant_id, academic_session_id);
CREATE INDEX ix_syllabus_file_id ON app.syllabus (tenant_id, file_id);
CREATE INDEX ix_course_offering_tenant_id ON app.course_offering (tenant_id);
CREATE INDEX ix_course_offering_institution_id ON app.course_offering (tenant_id, institution_id);
CREATE INDEX ix_course_offering_subject_id ON app.course_offering (tenant_id, subject_id);
CREATE INDEX ix_course_offering_academic_session_id ON app.course_offering (tenant_id, academic_session_id);
CREATE INDEX ix_course_offering_semester_id ON app.course_offering (tenant_id, semester_id);
CREATE INDEX ix_course_offering_class_id ON app.course_offering (tenant_id, class_id);
CREATE INDEX ix_subject_group_tenant_id ON app.subject_group (tenant_id);
CREATE INDEX ix_subject_group_institution_id ON app.subject_group (tenant_id, institution_id);
CREATE INDEX ix_subject_group_course_offering_id ON app.subject_group (tenant_id, course_offering_id);
CREATE INDEX ix_subject_group_member_tenant_id ON app.subject_group_member (tenant_id);
CREATE INDEX ix_subject_group_member_institution_id ON app.subject_group_member (tenant_id, institution_id);
CREATE INDEX ix_subject_group_member_subject_group_id ON app.subject_group_member (tenant_id, subject_group_id);
CREATE INDEX ix_subject_group_member_enrollment_id ON app.subject_group_member (tenant_id, enrollment_id);
CREATE INDEX ix_user_account_tenant_id ON app.user_account (tenant_id);
CREATE INDEX ix_user_identity_link_tenant_id ON app.user_identity_link (tenant_id);
CREATE INDEX ix_user_identity_link_user_account_id ON app.user_identity_link (tenant_id, user_account_id);
CREATE INDEX ix_user_device_tenant_id ON app.user_device (tenant_id);
CREATE INDEX ix_user_device_user_account_id ON app.user_device (tenant_id, user_account_id);
CREATE INDEX ix_staff_profile_tenant_id ON app.staff_profile (tenant_id);
CREATE INDEX ix_staff_profile_user_account_id ON app.staff_profile (tenant_id, user_account_id);
CREATE INDEX ix_staff_profile_photo_file_id ON app.staff_profile (tenant_id, photo_file_id);
CREATE INDEX ix_staff_service_record_tenant_id ON app.staff_service_record (tenant_id);
CREATE INDEX ix_staff_service_record_staff_profile_id ON app.staff_service_record (tenant_id, staff_profile_id);
CREATE INDEX ix_staff_service_record_staff_assignment_id ON app.staff_service_record (tenant_id, staff_assignment_id);
CREATE INDEX ix_staff_service_record_document_file_id ON app.staff_service_record (tenant_id, document_file_id);
CREATE INDEX ix_staff_assignment_tenant_id ON app.staff_assignment (tenant_id);
CREATE INDEX ix_staff_assignment_staff_profile_id ON app.staff_assignment (tenant_id, staff_profile_id);
CREATE INDEX ix_staff_assignment_institution_id ON app.staff_assignment (tenant_id, institution_id);
CREATE INDEX ix_staff_assignment_department_id ON app.staff_assignment (tenant_id, department_id);
CREATE INDEX ix_staff_document_tenant_id ON app.staff_document (tenant_id);
CREATE INDEX ix_staff_document_staff_profile_id ON app.staff_document (tenant_id, staff_profile_id);
CREATE INDEX ix_staff_document_file_id ON app.staff_document (tenant_id, file_id);
CREATE INDEX ix_role_definition_tenant_id ON app.role_definition (tenant_id);
CREATE INDEX ix_role_definition_base_standard_role ON app.role_definition (base_standard_role);
CREATE INDEX ix_role_action_tenant_id ON app.role_action (tenant_id);
CREATE INDEX ix_role_action_role_definition_id ON app.role_action (tenant_id, role_definition_id);
CREATE INDEX ix_role_action_action_code ON app.role_action (action_code);
CREATE INDEX ix_user_role_assignment_tenant_id ON app.user_role_assignment (tenant_id);
CREATE INDEX ix_user_role_assignment_user_account_id ON app.user_role_assignment (tenant_id, user_account_id);
CREATE INDEX ix_user_role_assignment_role_definition_id ON app.user_role_assignment (tenant_id, role_definition_id);
CREATE INDEX ix_user_role_assignment_institution_id ON app.user_role_assignment (tenant_id, institution_id);
CREATE INDEX ix_user_role_assignment_department_id ON app.user_role_assignment (tenant_id, department_id);
CREATE INDEX ix_delegation_tenant_id ON app.delegation (tenant_id);
CREATE INDEX ix_delegation_delegator_user_id ON app.delegation (tenant_id, delegator_user_id);
CREATE INDEX ix_delegation_delegate_user_id ON app.delegation (tenant_id, delegate_user_id);
CREATE INDEX ix_delegation_process_code ON app.delegation (process_code);
CREATE INDEX ix_approval_request_tenant_id ON app.approval_request (tenant_id);
CREATE INDEX ix_approval_request_institution_id ON app.approval_request (tenant_id, institution_id);
CREATE INDEX ix_approval_request_process_code ON app.approval_request (process_code);
CREATE INDEX ix_approval_request_requested_by_user_id ON app.approval_request (tenant_id, requested_by_user_id);
CREATE INDEX ix_approval_batch_tenant_id ON app.approval_batch (tenant_id);
CREATE INDEX ix_approval_batch_acted_by_user_id ON app.approval_batch (tenant_id, acted_by_user_id);
CREATE INDEX ix_approval_step_tenant_id ON app.approval_step (tenant_id);
CREATE INDEX ix_approval_step_approval_request_id ON app.approval_step (tenant_id, approval_request_id);
CREATE INDEX ix_approval_step_approver_user_id ON app.approval_step (tenant_id, approver_user_id);
CREATE INDEX ix_approval_step_acted_by_user_id ON app.approval_step (tenant_id, acted_by_user_id);
CREATE INDEX ix_approval_step_acting_via_delegation_id ON app.approval_step (tenant_id, acting_via_delegation_id);
CREATE INDEX ix_approval_step_approval_batch_id ON app.approval_step (tenant_id, approval_batch_id);
CREATE INDEX ix_student_profile_tenant_id ON app.student_profile (tenant_id);
CREATE INDEX ix_student_profile_user_account_id ON app.student_profile (tenant_id, user_account_id);
CREATE INDEX ix_student_profile_institution_id ON app.student_profile (tenant_id, institution_id);
CREATE INDEX ix_student_profile_photo_file_id ON app.student_profile (tenant_id, photo_file_id);
CREATE INDEX ix_student_code_sequence_tenant_id ON app.student_code_sequence (tenant_id);
CREATE INDEX ix_student_code_sequence_institution_id ON app.student_code_sequence (tenant_id, institution_id);
CREATE INDEX ix_duplicate_review_tenant_id ON app.duplicate_review (tenant_id);
CREATE INDEX ix_guardian_profile_tenant_id ON app.guardian_profile (tenant_id);
CREATE INDEX ix_guardian_profile_user_account_id ON app.guardian_profile (tenant_id, user_account_id);
CREATE INDEX ix_student_guardian_link_tenant_id ON app.student_guardian_link (tenant_id);
CREATE INDEX ix_student_guardian_link_student_profile_id ON app.student_guardian_link (tenant_id, student_profile_id);
CREATE INDEX ix_student_guardian_link_guardian_profile_id ON app.student_guardian_link (tenant_id, guardian_profile_id);
CREATE INDEX ix_student_guardian_link_change_approval_request_id ON app.student_guardian_link (tenant_id, change_approval_request_id);
CREATE INDEX ix_parent_consent_tenant_id ON app.parent_consent (tenant_id);
CREATE INDEX ix_parent_consent_student_profile_id ON app.parent_consent (tenant_id, student_profile_id);
CREATE INDEX ix_parent_consent_guardian_profile_id ON app.parent_consent (tenant_id, guardian_profile_id);
CREATE INDEX ix_parent_consent_paper_file_id ON app.parent_consent (tenant_id, paper_file_id);
CREATE INDEX ix_student_enrollment_tenant_id ON app.student_enrollment (tenant_id);
CREATE INDEX ix_student_enrollment_student_profile_id ON app.student_enrollment (tenant_id, student_profile_id);
CREATE INDEX ix_student_enrollment_institution_id ON app.student_enrollment (tenant_id, institution_id);
CREATE INDEX ix_student_enrollment_academic_session_id ON app.student_enrollment (tenant_id, academic_session_id);
CREATE INDEX ix_student_enrollment_section_id ON app.student_enrollment (tenant_id, section_id);
CREATE INDEX ix_student_enrollment_promoted_from_enrollment_id ON app.student_enrollment (tenant_id, promoted_from_enrollment_id);
CREATE INDEX ix_promotion_run_tenant_id ON app.promotion_run (tenant_id);
CREATE INDEX ix_promotion_run_institution_id ON app.promotion_run (tenant_id, institution_id);
CREATE INDEX ix_promotion_run_from_session_id ON app.promotion_run (tenant_id, from_session_id);
CREATE INDEX ix_promotion_run_to_session_id ON app.promotion_run (tenant_id, to_session_id);
CREATE INDEX ix_promotion_run_approval_request_id ON app.promotion_run (tenant_id, approval_request_id);
CREATE INDEX ix_promotion_run_item_tenant_id ON app.promotion_run_item (tenant_id);
CREATE INDEX ix_promotion_run_item_institution_id ON app.promotion_run_item (tenant_id, institution_id);
CREATE INDEX ix_promotion_run_item_promotion_run_id ON app.promotion_run_item (tenant_id, promotion_run_id);
CREATE INDEX ix_promotion_run_item_enrollment_id ON app.promotion_run_item (tenant_id, enrollment_id);
CREATE INDEX ix_promotion_run_item_target_section_id ON app.promotion_run_item (tenant_id, target_section_id);
CREATE INDEX ix_subject_allocation_tenant_id ON app.subject_allocation (tenant_id);
CREATE INDEX ix_subject_allocation_institution_id ON app.subject_allocation (tenant_id, institution_id);
CREATE INDEX ix_subject_allocation_course_offering_id ON app.subject_allocation (tenant_id, course_offering_id);
CREATE INDEX ix_subject_allocation_section_id ON app.subject_allocation (tenant_id, section_id);
CREATE INDEX ix_subject_allocation_subject_group_id ON app.subject_allocation (tenant_id, subject_group_id);
CREATE INDEX ix_subject_allocation_staff_assignment_id ON app.subject_allocation (tenant_id, staff_assignment_id);
CREATE INDEX ix_mentor_assignment_tenant_id ON app.mentor_assignment (tenant_id);
CREATE INDEX ix_mentor_assignment_institution_id ON app.mentor_assignment (tenant_id, institution_id);
CREATE INDEX ix_mentor_assignment_section_id ON app.mentor_assignment (tenant_id, section_id);
CREATE INDEX ix_mentor_assignment_staff_assignment_id ON app.mentor_assignment (tenant_id, staff_assignment_id);
CREATE INDEX ix_class_teacher_assignment_tenant_id ON app.class_teacher_assignment (tenant_id);
CREATE INDEX ix_class_teacher_assignment_institution_id ON app.class_teacher_assignment (tenant_id, institution_id);
CREATE INDEX ix_class_teacher_assignment_section_id ON app.class_teacher_assignment (tenant_id, section_id);
CREATE INDEX ix_class_teacher_assignment_staff_assignment_id ON app.class_teacher_assignment (tenant_id, staff_assignment_id);
CREATE INDEX ix_timetable_slot_tenant_id ON app.timetable_slot (tenant_id);
CREATE INDEX ix_timetable_slot_institution_id ON app.timetable_slot (tenant_id, institution_id);
CREATE INDEX ix_timetable_slot_academic_session_id ON app.timetable_slot (tenant_id, academic_session_id);
CREATE INDEX ix_timetable_slot_section_id ON app.timetable_slot (tenant_id, section_id);
CREATE INDEX ix_timetable_slot_subject_group_id ON app.timetable_slot (tenant_id, subject_group_id);
CREATE INDEX ix_timetable_slot_subject_allocation_id ON app.timetable_slot (tenant_id, subject_allocation_id);
CREATE INDEX ix_timetable_slot_supersedes_slot_id ON app.timetable_slot (tenant_id, supersedes_slot_id);
CREATE INDEX ix_meeting_link_tenant_id ON app.meeting_link (tenant_id);
CREATE INDEX ix_meeting_link_institution_id ON app.meeting_link (tenant_id, institution_id);
CREATE INDEX ix_meeting_link_timetable_slot_id ON app.meeting_link (tenant_id, timetable_slot_id);
CREATE INDEX ix_substitute_assignment_tenant_id ON app.substitute_assignment (tenant_id);
CREATE INDEX ix_substitute_assignment_institution_id ON app.substitute_assignment (tenant_id, institution_id);
CREATE INDEX ix_substitute_assignment_subject_allocation_id ON app.substitute_assignment (tenant_id, subject_allocation_id);
CREATE INDEX ix_substitute_assignment_substitute_staff_assignment_id ON app.substitute_assignment (tenant_id, substitute_staff_assignment_id);
CREATE INDEX ix_substitute_assignment_leave_request_id ON app.substitute_assignment (tenant_id, leave_request_id);
CREATE INDEX ix_attendance_session_tenant_id ON app.attendance_session (tenant_id);
CREATE INDEX ix_attendance_session_institution_id ON app.attendance_session (tenant_id, institution_id);
CREATE INDEX ix_attendance_session_section_id ON app.attendance_session (tenant_id, section_id);
CREATE INDEX ix_attendance_session_subject_group_id ON app.attendance_session (tenant_id, subject_group_id);
CREATE INDEX ix_attendance_session_timetable_slot_id ON app.attendance_session (tenant_id, timetable_slot_id);
CREATE INDEX ix_attendance_session_subject_allocation_id ON app.attendance_session (tenant_id, subject_allocation_id);
CREATE INDEX ix_attendance_session_marked_by_user_id ON app.attendance_session (tenant_id, marked_by_user_id);
CREATE INDEX ix_attendance_session_substitute_assignment_id ON app.attendance_session (tenant_id, substitute_assignment_id);
CREATE INDEX ix_attendance_sync_conflict_tenant_id ON app.attendance_sync_conflict (tenant_id);
CREATE INDEX ix_attendance_sync_conflict_institution_id ON app.attendance_sync_conflict (tenant_id, institution_id);
CREATE INDEX ix_attendance_sync_conflict_existing_attendance_session_id ON app.attendance_sync_conflict (tenant_id, existing_attendance_session_id);
CREATE INDEX ix_attendance_sync_conflict_submitted_by_user_id ON app.attendance_sync_conflict (tenant_id, submitted_by_user_id);
CREATE INDEX ix_attendance_entry_tenant_id ON app.attendance_entry (tenant_id);
CREATE INDEX ix_attendance_entry_institution_id ON app.attendance_entry (tenant_id, institution_id);
CREATE INDEX ix_attendance_entry_attendance_session_id ON app.attendance_entry (tenant_id, attendance_session_id);
CREATE INDEX ix_attendance_entry_enrollment_id ON app.attendance_entry (tenant_id, enrollment_id);
CREATE INDEX ix_attendance_entry_leave_request_id ON app.attendance_entry (tenant_id, leave_request_id);
CREATE INDEX ix_attendance_entry_recorded_by_user_id ON app.attendance_entry (tenant_id, recorded_by_user_id);
CREATE INDEX ix_attendance_correction_request_tenant_id ON app.attendance_correction_request (tenant_id);
CREATE INDEX ix_attendance_correction_request_institution_id ON app.attendance_correction_request (tenant_id, institution_id);
CREATE INDEX ix_attendance_correction_request_attendance_session_id ON app.attendance_correction_request (tenant_id, attendance_session_id);
CREATE INDEX ix_attendance_correction_request_requested_by_user_id ON app.attendance_correction_request (tenant_id, requested_by_user_id);
CREATE INDEX ix_attendance_correction_request_approval_request_id ON app.attendance_correction_request (tenant_id, approval_request_id);
CREATE INDEX ix_attendance_correction_request_bulk_correction_item_id ON app.attendance_correction_request (tenant_id, bulk_correction_item_id);
CREATE INDEX ix_attendance_correction_tenant_id ON app.attendance_correction (tenant_id);
CREATE INDEX ix_attendance_correction_institution_id ON app.attendance_correction (tenant_id, institution_id);
CREATE INDEX ix_attendance_correction_attendance_correction_request_id ON app.attendance_correction (tenant_id, attendance_correction_request_id);
CREATE INDEX ix_attendance_bulk_upload_tenant_id ON app.attendance_bulk_upload (tenant_id);
CREATE INDEX ix_attendance_bulk_upload_institution_id ON app.attendance_bulk_upload (tenant_id, institution_id);
CREATE INDEX ix_attendance_bulk_upload_import_job_id ON app.attendance_bulk_upload (tenant_id, import_job_id);
CREATE INDEX ix_attendance_bulk_upload_approval_request_id ON app.attendance_bulk_upload (tenant_id, approval_request_id);
CREATE INDEX ix_term_lock_tenant_id ON app.term_lock (tenant_id);
CREATE INDEX ix_term_lock_institution_id ON app.term_lock (tenant_id, institution_id);
CREATE INDEX ix_term_lock_academic_session_id ON app.term_lock (tenant_id, academic_session_id);
CREATE INDEX ix_term_lock_semester_id ON app.term_lock (tenant_id, semester_id);
CREATE INDEX ix_term_lock_event_tenant_id ON app.term_lock_event (tenant_id);
CREATE INDEX ix_term_lock_event_institution_id ON app.term_lock_event (tenant_id, institution_id);
CREATE INDEX ix_term_lock_event_term_lock_id ON app.term_lock_event (tenant_id, term_lock_id);
CREATE INDEX ix_term_lock_event_actor_user_id ON app.term_lock_event (tenant_id, actor_user_id);
CREATE INDEX ix_risk_flag_tenant_id ON app.risk_flag (tenant_id);
CREATE INDEX ix_risk_flag_institution_id ON app.risk_flag (tenant_id, institution_id);
CREATE INDEX ix_risk_flag_enrollment_id ON app.risk_flag (tenant_id, enrollment_id);
CREATE INDEX ix_risk_remark_tenant_id ON app.risk_remark (tenant_id);
CREATE INDEX ix_risk_remark_institution_id ON app.risk_remark (tenant_id, institution_id);
CREATE INDEX ix_risk_remark_enrollment_id ON app.risk_remark (tenant_id, enrollment_id);
CREATE INDEX ix_leave_type_tenant_id ON app.leave_type (tenant_id);
CREATE INDEX ix_leave_type_institution_id ON app.leave_type (tenant_id, institution_id);
CREATE INDEX ix_leave_balance_tenant_id ON app.leave_balance (tenant_id);
CREATE INDEX ix_leave_balance_institution_id ON app.leave_balance (tenant_id, institution_id);
CREATE INDEX ix_leave_balance_staff_profile_id ON app.leave_balance (tenant_id, staff_profile_id);
CREATE INDEX ix_leave_balance_leave_type_id ON app.leave_balance (tenant_id, leave_type_id);
CREATE INDEX ix_leave_request_tenant_id ON app.leave_request (tenant_id);
CREATE INDEX ix_leave_request_institution_id ON app.leave_request (tenant_id, institution_id);
CREATE INDEX ix_leave_request_requested_by_user_id ON app.leave_request (tenant_id, requested_by_user_id);
CREATE INDEX ix_leave_request_for_enrollment_id ON app.leave_request (tenant_id, for_enrollment_id);
CREATE INDEX ix_leave_request_for_staff_profile_id ON app.leave_request (tenant_id, for_staff_profile_id);
CREATE INDEX ix_leave_request_leave_type_id ON app.leave_request (tenant_id, leave_type_id);
CREATE INDEX ix_leave_request_document_file_id ON app.leave_request (tenant_id, document_file_id);
CREATE INDEX ix_leave_request_process_code ON app.leave_request (process_code);
CREATE INDEX ix_leave_request_approval_request_id ON app.leave_request (tenant_id, approval_request_id);
CREATE INDEX ix_grading_scheme_version_tenant_id ON app.grading_scheme_version (tenant_id);
CREATE INDEX ix_grading_scheme_version_institution_id ON app.grading_scheme_version (tenant_id, institution_id);
CREATE INDEX ix_grade_band_tenant_id ON app.grade_band (tenant_id);
CREATE INDEX ix_grade_band_grading_scheme_version_id ON app.grade_band (tenant_id, grading_scheme_version_id);
CREATE INDEX ix_exam_rule_tenant_id ON app.exam_rule (tenant_id);
CREATE INDEX ix_exam_rule_institution_id ON app.exam_rule (tenant_id, institution_id);
CREATE INDEX ix_exam_rule_grading_scheme_version_id ON app.exam_rule (tenant_id, grading_scheme_version_id);
CREATE INDEX ix_exam_pattern_tenant_id ON app.exam_pattern (tenant_id);
CREATE INDEX ix_exam_pattern_institution_id ON app.exam_pattern (tenant_id, institution_id);
CREATE INDEX ix_exam_pattern_exam_rule_id ON app.exam_pattern (tenant_id, exam_rule_id);
CREATE INDEX ix_exam_pattern_grading_scheme_version_id ON app.exam_pattern (tenant_id, grading_scheme_version_id);
CREATE INDEX ix_exam_pattern_component_tenant_id ON app.exam_pattern_component (tenant_id);
CREATE INDEX ix_exam_pattern_component_institution_id ON app.exam_pattern_component (tenant_id, institution_id);
CREATE INDEX ix_exam_pattern_component_exam_pattern_id ON app.exam_pattern_component (tenant_id, exam_pattern_id);
CREATE INDEX ix_exam_tenant_id ON app.exam (tenant_id);
CREATE INDEX ix_exam_institution_id ON app.exam (tenant_id, institution_id);
CREATE INDEX ix_exam_academic_session_id ON app.exam (tenant_id, academic_session_id);
CREATE INDEX ix_exam_semester_id ON app.exam (tenant_id, semester_id);
CREATE INDEX ix_exam_class_id ON app.exam (tenant_id, class_id);
CREATE INDEX ix_exam_exam_pattern_id ON app.exam (tenant_id, exam_pattern_id);
CREATE INDEX ix_exam_schedule_approval_request_id ON app.exam (tenant_id, schedule_approval_request_id);
CREATE INDEX ix_exam_schedule_item_tenant_id ON app.exam_schedule_item (tenant_id);
CREATE INDEX ix_exam_schedule_item_institution_id ON app.exam_schedule_item (tenant_id, institution_id);
CREATE INDEX ix_exam_schedule_item_exam_id ON app.exam_schedule_item (tenant_id, exam_id);
CREATE INDEX ix_exam_schedule_item_course_offering_id ON app.exam_schedule_item (tenant_id, course_offering_id);
CREATE INDEX ix_hall_ticket_tenant_id ON app.hall_ticket (tenant_id);
CREATE INDEX ix_hall_ticket_institution_id ON app.hall_ticket (tenant_id, institution_id);
CREATE INDEX ix_hall_ticket_exam_id ON app.hall_ticket (tenant_id, exam_id);
CREATE INDEX ix_hall_ticket_enrollment_id ON app.hall_ticket (tenant_id, enrollment_id);
CREATE INDEX ix_hall_ticket_override_by_user_id ON app.hall_ticket (tenant_id, override_by_user_id);
CREATE INDEX ix_hall_ticket_file_id ON app.hall_ticket (tenant_id, file_id);
CREATE INDEX ix_marks_submission_tenant_id ON app.marks_submission (tenant_id);
CREATE INDEX ix_marks_submission_institution_id ON app.marks_submission (tenant_id, institution_id);
CREATE INDEX ix_marks_submission_exam_id ON app.marks_submission (tenant_id, exam_id);
CREATE INDEX ix_marks_submission_course_offering_id ON app.marks_submission (tenant_id, course_offering_id);
CREATE INDEX ix_marks_submission_section_id ON app.marks_submission (tenant_id, section_id);
CREATE INDEX ix_marks_submission_subject_group_id ON app.marks_submission (tenant_id, subject_group_id);
CREATE INDEX ix_marks_submission_entered_by_user_id ON app.marks_submission (tenant_id, entered_by_user_id);
CREATE INDEX ix_marks_submission_source_file_id ON app.marks_submission (tenant_id, source_file_id);
CREATE INDEX ix_marks_submission_approval_request_id ON app.marks_submission (tenant_id, approval_request_id);
CREATE INDEX ix_marks_submission_supersedes_submission_id ON app.marks_submission (tenant_id, supersedes_submission_id);
CREATE INDEX ix_marks_entry_tenant_id ON app.marks_entry (tenant_id);
CREATE INDEX ix_marks_entry_institution_id ON app.marks_entry (tenant_id, institution_id);
CREATE INDEX ix_marks_entry_marks_submission_id ON app.marks_entry (tenant_id, marks_submission_id);
CREATE INDEX ix_marks_entry_enrollment_id ON app.marks_entry (tenant_id, enrollment_id);
CREATE INDEX ix_marks_entry_component_id ON app.marks_entry (tenant_id, component_id);
CREATE INDEX ix_backlog_tenant_id ON app.backlog (tenant_id);
CREATE INDEX ix_backlog_institution_id ON app.backlog (tenant_id, institution_id);
CREATE INDEX ix_backlog_student_profile_id ON app.backlog (tenant_id, student_profile_id);
CREATE INDEX ix_backlog_subject_id ON app.backlog (tenant_id, subject_id);
CREATE INDEX ix_backlog_origin_result_line_id ON app.backlog (tenant_id, origin_result_line_id);
CREATE INDEX ix_backlog_cleared_result_line_id ON app.backlog (tenant_id, cleared_result_line_id);
CREATE INDEX ix_supplementary_registration_tenant_id ON app.supplementary_registration (tenant_id);
CREATE INDEX ix_supplementary_registration_institution_id ON app.supplementary_registration (tenant_id, institution_id);
CREATE INDEX ix_supplementary_registration_backlog_id ON app.supplementary_registration (tenant_id, backlog_id);
CREATE INDEX ix_supplementary_registration_exam_id ON app.supplementary_registration (tenant_id, exam_id);
CREATE INDEX ix_supplementary_registration_registered_by ON app.supplementary_registration (tenant_id, registered_by);
CREATE INDEX ix_result_tenant_id ON app.result (tenant_id);
CREATE INDEX ix_result_institution_id ON app.result (tenant_id, institution_id);
CREATE INDEX ix_result_exam_id ON app.result (tenant_id, exam_id);
CREATE INDEX ix_result_enrollment_id ON app.result (tenant_id, enrollment_id);
CREATE INDEX ix_result_grading_scheme_version_id ON app.result (tenant_id, grading_scheme_version_id);
CREATE INDEX ix_result_line_tenant_id ON app.result_line (tenant_id);
CREATE INDEX ix_result_line_institution_id ON app.result_line (tenant_id, institution_id);
CREATE INDEX ix_result_line_result_id ON app.result_line (tenant_id, result_id);
CREATE INDEX ix_result_line_subject_id ON app.result_line (tenant_id, subject_id);
CREATE INDEX ix_result_publication_tenant_id ON app.result_publication (tenant_id);
CREATE INDEX ix_result_publication_institution_id ON app.result_publication (tenant_id, institution_id);
CREATE INDEX ix_result_publication_exam_id ON app.result_publication (tenant_id, exam_id);
CREATE INDEX ix_result_publication_section_id ON app.result_publication (tenant_id, section_id);
CREATE INDEX ix_result_publication_published_by_user_id ON app.result_publication (tenant_id, published_by_user_id);
CREATE INDEX ix_result_publication_withdrawal_request_id ON app.result_publication (tenant_id, withdrawal_request_id);
CREATE INDEX ix_result_withdrawal_request_tenant_id ON app.result_withdrawal_request (tenant_id);
CREATE INDEX ix_result_withdrawal_request_institution_id ON app.result_withdrawal_request (tenant_id, institution_id);
CREATE INDEX ix_result_withdrawal_request_result_publication_id ON app.result_withdrawal_request (tenant_id, result_publication_id);
CREATE INDEX ix_result_withdrawal_request_requested_by_user_id ON app.result_withdrawal_request (tenant_id, requested_by_user_id);
CREATE INDEX ix_result_withdrawal_request_approval_request_id ON app.result_withdrawal_request (tenant_id, approval_request_id);
CREATE INDEX ix_fee_head_tenant_id ON app.fee_head (tenant_id);
CREATE INDEX ix_fee_policy_tenant_id ON app.fee_policy (tenant_id);
CREATE INDEX ix_fee_policy_institution_id ON app.fee_policy (tenant_id, institution_id);
CREATE INDEX ix_fee_head_rule_tenant_id ON app.fee_head_rule (tenant_id);
CREATE INDEX ix_fee_head_rule_fee_policy_id ON app.fee_head_rule (tenant_id, fee_policy_id);
CREATE INDEX ix_fee_head_rule_fee_head_id ON app.fee_head_rule (tenant_id, fee_head_id);
CREATE INDEX ix_fee_structure_tenant_id ON app.fee_structure (tenant_id);
CREATE INDEX ix_fee_structure_institution_id ON app.fee_structure (tenant_id, institution_id);
CREATE INDEX ix_fee_structure_academic_session_id ON app.fee_structure (tenant_id, academic_session_id);
CREATE INDEX ix_fee_structure_program_year_id ON app.fee_structure (tenant_id, program_year_id);
CREATE INDEX ix_fee_structure_class_id ON app.fee_structure (tenant_id, class_id);
CREATE INDEX ix_fee_structure_fee_policy_id ON app.fee_structure (tenant_id, fee_policy_id);
CREATE INDEX ix_fee_structure_approval_request_id ON app.fee_structure (tenant_id, approval_request_id);
CREATE INDEX ix_fee_structure_line_tenant_id ON app.fee_structure_line (tenant_id);
CREATE INDEX ix_fee_structure_line_institution_id ON app.fee_structure_line (tenant_id, institution_id);
CREATE INDEX ix_fee_structure_line_fee_structure_id ON app.fee_structure_line (tenant_id, fee_structure_id);
CREATE INDEX ix_fee_structure_line_fee_head_id ON app.fee_structure_line (tenant_id, fee_head_id);
CREATE INDEX ix_fee_structure_line_fee_exception_id ON app.fee_structure_line (tenant_id, fee_exception_id);
CREATE INDEX ix_fee_structure_installment_tenant_id ON app.fee_structure_installment (tenant_id);
CREATE INDEX ix_fee_structure_installment_institution_id ON app.fee_structure_installment (tenant_id, institution_id);
CREATE INDEX ix_fee_structure_installment_fee_structure_id ON app.fee_structure_installment (tenant_id, fee_structure_id);
CREATE INDEX ix_fee_exception_tenant_id ON app.fee_exception (tenant_id);
CREATE INDEX ix_fee_exception_institution_id ON app.fee_exception (tenant_id, institution_id);
CREATE INDEX ix_fee_exception_fee_structure_id ON app.fee_exception (tenant_id, fee_structure_id);
CREATE INDEX ix_fee_exception_fee_head_id ON app.fee_exception (tenant_id, fee_head_id);
CREATE INDEX ix_fee_exception_requested_by ON app.fee_exception (tenant_id, requested_by);
CREATE INDEX ix_fee_exception_approval_request_id ON app.fee_exception (tenant_id, approval_request_id);
CREATE INDEX ix_student_fee_account_tenant_id ON app.student_fee_account (tenant_id);
CREATE INDEX ix_student_fee_account_student_profile_id ON app.student_fee_account (tenant_id, student_profile_id);
CREATE INDEX ix_fee_assignment_tenant_id ON app.fee_assignment (tenant_id);
CREATE INDEX ix_fee_assignment_institution_id ON app.fee_assignment (tenant_id, institution_id);
CREATE INDEX ix_fee_assignment_student_fee_account_id ON app.fee_assignment (tenant_id, student_fee_account_id);
CREATE INDEX ix_fee_assignment_enrollment_id ON app.fee_assignment (tenant_id, enrollment_id);
CREATE INDEX ix_fee_assignment_fee_structure_id ON app.fee_assignment (tenant_id, fee_structure_id);
CREATE INDEX ix_student_installment_tenant_id ON app.student_installment (tenant_id);
CREATE INDEX ix_student_installment_institution_id ON app.student_installment (tenant_id, institution_id);
CREATE INDEX ix_student_installment_student_fee_account_id ON app.student_installment (tenant_id, student_fee_account_id);
CREATE INDEX ix_student_installment_fee_assignment_id ON app.student_installment (tenant_id, fee_assignment_id);
CREATE INDEX ix_ledger_entry_tenant_id ON app.ledger_entry (tenant_id);
CREATE INDEX ix_ledger_entry_institution_id ON app.ledger_entry (tenant_id, institution_id);
CREATE INDEX ix_ledger_entry_student_fee_account_id ON app.ledger_entry (tenant_id, student_fee_account_id);
CREATE INDEX ix_ledger_entry_academic_session_id ON app.ledger_entry (tenant_id, academic_session_id);
CREATE INDEX ix_ledger_entry_fee_head_id ON app.ledger_entry (tenant_id, fee_head_id);
CREATE INDEX ix_ledger_entry_student_installment_id ON app.ledger_entry (tenant_id, student_installment_id);
CREATE INDEX ix_ledger_entry_payment_id ON app.ledger_entry (tenant_id, payment_id);
CREATE INDEX ix_ledger_entry_scholarship_award_id ON app.ledger_entry (tenant_id, scholarship_award_id);
CREATE INDEX ix_ledger_entry_concession_id ON app.ledger_entry (tenant_id, concession_id);
CREATE INDEX ix_ledger_entry_late_fee_waiver_id ON app.ledger_entry (tenant_id, late_fee_waiver_id);
CREATE INDEX ix_ledger_entry_credit_application_id ON app.ledger_entry (tenant_id, credit_application_id);
CREATE INDEX ix_ledger_entry_reverses_entry_id ON app.ledger_entry (tenant_id, reverses_entry_id);
CREATE INDEX ix_payment_tenant_id ON app.payment (tenant_id);
CREATE INDEX ix_payment_institution_id ON app.payment (tenant_id, institution_id);
CREATE INDEX ix_payment_student_fee_account_id ON app.payment (tenant_id, student_fee_account_id);
CREATE INDEX ix_payment_paid_by_user_id ON app.payment (tenant_id, paid_by_user_id);
CREATE INDEX ix_payment_gateway_transaction_id ON app.payment (tenant_id, gateway_transaction_id);
CREATE INDEX ix_payment_marked_by_user_id ON app.payment (tenant_id, marked_by_user_id);
CREATE INDEX ix_payment_intent_item_tenant_id ON app.payment_intent_item (tenant_id);
CREATE INDEX ix_payment_intent_item_institution_id ON app.payment_intent_item (tenant_id, institution_id);
CREATE INDEX ix_payment_intent_item_payment_id ON app.payment_intent_item (tenant_id, payment_id);
CREATE INDEX ix_payment_intent_item_student_installment_id ON app.payment_intent_item (tenant_id, student_installment_id);
CREATE INDEX ix_gateway_order_tenant_id ON app.gateway_order (tenant_id);
CREATE INDEX ix_gateway_order_institution_id ON app.gateway_order (tenant_id, institution_id);
CREATE INDEX ix_gateway_order_payment_id ON app.gateway_order (tenant_id, payment_id);
CREATE INDEX ix_gateway_transaction_tenant_id ON app.gateway_transaction (tenant_id);
CREATE INDEX ix_gateway_transaction_institution_id ON app.gateway_transaction (tenant_id, institution_id);
CREATE INDEX ix_gateway_transaction_gateway_order_id ON app.gateway_transaction (tenant_id, gateway_order_id);
CREATE INDEX ix_payment_gateway_event_tenant_id ON app.payment_gateway_event (tenant_id);
CREATE INDEX ix_payment_gateway_event_institution_id ON app.payment_gateway_event (tenant_id, institution_id);
CREATE INDEX ix_payment_gateway_event_gateway_order_id ON app.payment_gateway_event (tenant_id, gateway_order_id);
CREATE INDEX ix_payment_gateway_event_gateway_transaction_id ON app.payment_gateway_event (tenant_id, gateway_transaction_id);
CREATE INDEX ix_payment_allocation_tenant_id ON app.payment_allocation (tenant_id);
CREATE INDEX ix_payment_allocation_institution_id ON app.payment_allocation (tenant_id, institution_id);
CREATE INDEX ix_payment_allocation_payment_id ON app.payment_allocation (tenant_id, payment_id);
CREATE INDEX ix_payment_allocation_student_installment_id ON app.payment_allocation (tenant_id, student_installment_id);
CREATE INDEX ix_credit_application_tenant_id ON app.credit_application (tenant_id);
CREATE INDEX ix_credit_application_institution_id ON app.credit_application (tenant_id, institution_id);
CREATE INDEX ix_credit_application_student_fee_account_id ON app.credit_application (tenant_id, student_fee_account_id);
CREATE INDEX ix_credit_application_student_installment_id ON app.credit_application (tenant_id, student_installment_id);
CREATE INDEX ix_reconciliation_case_tenant_id ON app.reconciliation_case (tenant_id);
CREATE INDEX ix_reconciliation_case_institution_id ON app.reconciliation_case (tenant_id, institution_id);
CREATE INDEX ix_reconciliation_case_payment_id ON app.reconciliation_case (tenant_id, payment_id);
CREATE INDEX ix_reconciliation_case_payment_gateway_event_id ON app.reconciliation_case (tenant_id, payment_gateway_event_id);
CREATE INDEX ix_reconciliation_case_owner_user_id ON app.reconciliation_case (tenant_id, owner_user_id);
CREATE INDEX ix_receipt_sequence_tenant_id ON app.receipt_sequence (tenant_id);
CREATE INDEX ix_receipt_sequence_institution_id ON app.receipt_sequence (tenant_id, institution_id);
CREATE INDEX ix_receipt_tenant_id ON app.receipt (tenant_id);
CREATE INDEX ix_receipt_institution_id ON app.receipt (tenant_id, institution_id);
CREATE INDEX ix_receipt_payment_id ON app.receipt (tenant_id, payment_id);
CREATE INDEX ix_receipt_void_approval_request_id ON app.receipt (tenant_id, void_approval_request_id);
CREATE INDEX ix_receipt_replaced_by_receipt_id ON app.receipt (tenant_id, replaced_by_receipt_id);
CREATE INDEX ix_scholarship_scheme_tenant_id ON app.scholarship_scheme (tenant_id);
CREATE INDEX ix_scholarship_scheme_institution_id ON app.scholarship_scheme (tenant_id, institution_id);
CREATE INDEX ix_scholarship_scheme_fee_policy_id ON app.scholarship_scheme (tenant_id, fee_policy_id);
CREATE INDEX ix_scholarship_scheme_academic_session_id ON app.scholarship_scheme (tenant_id, academic_session_id);
CREATE INDEX ix_scholarship_scheme_fee_head_id ON app.scholarship_scheme (tenant_id, fee_head_id);
CREATE INDEX ix_scholarship_award_list_tenant_id ON app.scholarship_award_list (tenant_id);
CREATE INDEX ix_scholarship_award_list_institution_id ON app.scholarship_award_list (tenant_id, institution_id);
CREATE INDEX ix_scholarship_award_list_scholarship_scheme_id ON app.scholarship_award_list (tenant_id, scholarship_scheme_id);
CREATE INDEX ix_scholarship_award_list_approval_request_id ON app.scholarship_award_list (tenant_id, approval_request_id);
CREATE INDEX ix_scholarship_award_tenant_id ON app.scholarship_award (tenant_id);
CREATE INDEX ix_scholarship_award_institution_id ON app.scholarship_award (tenant_id, institution_id);
CREATE INDEX ix_scholarship_award_list_id ON app.scholarship_award (tenant_id, list_id);
CREATE INDEX ix_scholarship_award_enrollment_id ON app.scholarship_award (tenant_id, enrollment_id);
CREATE INDEX ix_concession_tenant_id ON app.concession (tenant_id);
CREATE INDEX ix_concession_institution_id ON app.concession (tenant_id, institution_id);
CREATE INDEX ix_concession_student_fee_account_id ON app.concession (tenant_id, student_fee_account_id);
CREATE INDEX ix_concession_student_installment_id ON app.concession (tenant_id, student_installment_id);
CREATE INDEX ix_concession_fee_head_id ON app.concession (tenant_id, fee_head_id);
CREATE INDEX ix_concession_requested_by ON app.concession (tenant_id, requested_by);
CREATE INDEX ix_concession_approval_request_id ON app.concession (tenant_id, approval_request_id);
CREATE INDEX ix_late_fee_rule_tenant_id ON app.late_fee_rule (tenant_id);
CREATE INDEX ix_late_fee_rule_institution_id ON app.late_fee_rule (tenant_id, institution_id);
CREATE INDEX ix_late_fee_waiver_tenant_id ON app.late_fee_waiver (tenant_id);
CREATE INDEX ix_late_fee_waiver_institution_id ON app.late_fee_waiver (tenant_id, institution_id);
CREATE INDEX ix_late_fee_waiver_late_fee_ledger_entry_id ON app.late_fee_waiver (tenant_id, late_fee_ledger_entry_id);
CREATE INDEX ix_late_fee_waiver_requested_by ON app.late_fee_waiver (tenant_id, requested_by);
CREATE INDEX ix_late_fee_waiver_approval_request_id ON app.late_fee_waiver (tenant_id, approval_request_id);
CREATE INDEX ix_financial_period_lock_tenant_id ON app.financial_period_lock (tenant_id);
CREATE INDEX ix_financial_period_lock_institution_id ON app.financial_period_lock (tenant_id, institution_id);
CREATE INDEX ix_financial_period_lock_unlock_approval_request_id ON app.financial_period_lock (tenant_id, unlock_approval_request_id);
CREATE INDEX ix_financial_period_lock_event_tenant_id ON app.financial_period_lock_event (tenant_id);
CREATE INDEX ix_financial_period_lock_event_institution_id ON app.financial_period_lock_event (tenant_id, institution_id);
CREATE INDEX ix_financial_period_lock_event_period_lock_id ON app.financial_period_lock_event (tenant_id, period_lock_id);
CREATE INDEX ix_financial_period_lock_event_actor_user_id ON app.financial_period_lock_event (tenant_id, actor_user_id);
CREATE INDEX ix_assignment_tenant_id ON app.assignment (tenant_id);
CREATE INDEX ix_assignment_institution_id ON app.assignment (tenant_id, institution_id);
CREATE INDEX ix_assignment_course_offering_id ON app.assignment (tenant_id, course_offering_id);
CREATE INDEX ix_assignment_section_id ON app.assignment (tenant_id, section_id);
CREATE INDEX ix_assignment_subject_group_id ON app.assignment (tenant_id, subject_group_id);
CREATE INDEX ix_assignment_created_by_user_id ON app.assignment (tenant_id, created_by_user_id);
CREATE INDEX ix_assignment_submission_tenant_id ON app.assignment_submission (tenant_id);
CREATE INDEX ix_assignment_submission_institution_id ON app.assignment_submission (tenant_id, institution_id);
CREATE INDEX ix_assignment_submission_assignment_id ON app.assignment_submission (tenant_id, assignment_id);
CREATE INDEX ix_assignment_submission_enrollment_id ON app.assignment_submission (tenant_id, enrollment_id);
CREATE INDEX ix_assignment_submission_file_tenant_id ON app.assignment_submission_file (tenant_id);
CREATE INDEX ix_assignment_submission_file_institution_id ON app.assignment_submission_file (tenant_id, institution_id);
CREATE INDEX ix_assignment_submission_file_submission_id ON app.assignment_submission_file (tenant_id, submission_id);
CREATE INDEX ix_assignment_submission_file_file_id ON app.assignment_submission_file (tenant_id, file_id);
CREATE INDEX ix_assignment_feedback_tenant_id ON app.assignment_feedback (tenant_id);
CREATE INDEX ix_assignment_feedback_institution_id ON app.assignment_feedback (tenant_id, institution_id);
CREATE INDEX ix_assignment_feedback_submission_id ON app.assignment_feedback (tenant_id, submission_id);
CREATE INDEX ix_assignment_feedback_evaluated_by ON app.assignment_feedback (tenant_id, evaluated_by);
CREATE INDEX ix_homework_status_tenant_id ON app.homework_status (tenant_id);
CREATE INDEX ix_homework_status_institution_id ON app.homework_status (tenant_id, institution_id);
CREATE INDEX ix_homework_status_assignment_id ON app.homework_status (tenant_id, assignment_id);
CREATE INDEX ix_homework_status_enrollment_id ON app.homework_status (tenant_id, enrollment_id);
CREATE INDEX ix_homework_status_marked_by ON app.homework_status (tenant_id, marked_by);
CREATE INDEX ix_study_material_tenant_id ON app.study_material (tenant_id);
CREATE INDEX ix_study_material_institution_id ON app.study_material (tenant_id, institution_id);
CREATE INDEX ix_study_material_course_offering_id ON app.study_material (tenant_id, course_offering_id);
CREATE INDEX ix_study_material_section_id ON app.study_material (tenant_id, section_id);
CREATE INDEX ix_study_material_subject_group_id ON app.study_material (tenant_id, subject_group_id);
CREATE INDEX ix_study_material_file_id ON app.study_material (tenant_id, file_id);
CREATE INDEX ix_study_material_uploaded_by ON app.study_material (tenant_id, uploaded_by);
CREATE INDEX ix_notice_tenant_id ON app.notice (tenant_id);
CREATE INDEX ix_notice_institution_id ON app.notice (tenant_id, institution_id);
CREATE INDEX ix_notice_department_id ON app.notice (tenant_id, department_id);
CREATE INDEX ix_notice_section_id ON app.notice (tenant_id, section_id);
CREATE INDEX ix_notice_authored_by_user_id ON app.notice (tenant_id, authored_by_user_id);
CREATE INDEX ix_notice_approval_request_id ON app.notice (tenant_id, approval_request_id);
CREATE INDEX ix_notice_published_by_user_id ON app.notice (tenant_id, published_by_user_id);
CREATE INDEX ix_notice_audience_tenant_id ON app.notice_audience (tenant_id);
CREATE INDEX ix_notice_audience_notice_id ON app.notice_audience (tenant_id, notice_id);
CREATE INDEX ix_notice_audience_role_definition_id ON app.notice_audience (tenant_id, role_definition_id);
CREATE INDEX ix_message_thread_tenant_id ON app.message_thread (tenant_id);
CREATE INDEX ix_message_thread_institution_id ON app.message_thread (tenant_id, institution_id);
CREATE INDEX ix_message_thread_about_enrollment_id ON app.message_thread (tenant_id, about_enrollment_id);
CREATE INDEX ix_message_thread_broadcast_section_id ON app.message_thread (tenant_id, broadcast_section_id);
CREATE INDEX ix_message_thread_origin_broadcast_thread_id ON app.message_thread (tenant_id, origin_broadcast_thread_id);
CREATE INDEX ix_message_thread_office_handler_user_id ON app.message_thread (tenant_id, office_handler_user_id);
CREATE INDEX ix_message_thread_participant_tenant_id ON app.message_thread_participant (tenant_id);
CREATE INDEX ix_message_thread_participant_institution_id ON app.message_thread_participant (tenant_id, institution_id);
CREATE INDEX ix_message_thread_participant_thread_id ON app.message_thread_participant (tenant_id, thread_id);
CREATE INDEX ix_message_thread_participant_user_account_id ON app.message_thread_participant (tenant_id, user_account_id);
CREATE INDEX ix_message_tenant_id ON app.message (tenant_id);
CREATE INDEX ix_message_institution_id ON app.message (tenant_id, institution_id);
CREATE INDEX ix_message_thread_id ON app.message (tenant_id, thread_id);
CREATE INDEX ix_message_sender_user_id ON app.message (tenant_id, sender_user_id);
CREATE INDEX ix_message_attachment_tenant_id ON app.message_attachment (tenant_id);
CREATE INDEX ix_message_attachment_institution_id ON app.message_attachment (tenant_id, institution_id);
CREATE INDEX ix_message_attachment_message_id ON app.message_attachment (tenant_id, message_id);
CREATE INDEX ix_message_attachment_file_id ON app.message_attachment (tenant_id, file_id);
CREATE INDEX ix_notification_tenant_id ON app.notification (tenant_id);
CREATE INDEX ix_notification_recipient_user_id ON app.notification (tenant_id, recipient_user_id);
CREATE INDEX ix_notification_institution_id ON app.notification (tenant_id, institution_id);
CREATE INDEX ix_notification_trigger_code ON app.notification (trigger_code);
CREATE INDEX ix_notification_notice_id ON app.notification (tenant_id, notice_id);
CREATE INDEX ix_notification_created_by_user_id ON app.notification (tenant_id, created_by_user_id);
CREATE INDEX ix_notification_delivery_tenant_id ON app.notification_delivery (tenant_id);
CREATE INDEX ix_notification_delivery_institution_id ON app.notification_delivery (tenant_id, institution_id);
CREATE INDEX ix_notification_delivery_notification_id ON app.notification_delivery (tenant_id, notification_id);
CREATE INDEX ix_notification_delivery_attempt_tenant_id ON app.notification_delivery_attempt (tenant_id);
CREATE INDEX ix_notification_delivery_attempt_institution_id ON app.notification_delivery_attempt (tenant_id, institution_id);
CREATE INDEX ix_notification_delivery_attempt_delivery_id ON app.notification_delivery_attempt (tenant_id, delivery_id);
CREATE INDEX ix_push_subscription_tenant_id ON app.push_subscription (tenant_id);
CREATE INDEX ix_push_subscription_user_device_id ON app.push_subscription (tenant_id, user_device_id);
CREATE INDEX ix_channel_config_tenant_id ON app.channel_config (tenant_id);
CREATE INDEX ix_channel_config_institution_id ON app.channel_config (tenant_id, institution_id);
CREATE INDEX ix_sms_template_tenant_id ON app.sms_template (tenant_id);
CREATE INDEX ix_sms_template_institution_id ON app.sms_template (tenant_id, institution_id);
CREATE INDEX ix_sms_template_trigger_code ON app.sms_template (trigger_code);
CREATE INDEX ix_community_post_tenant_id ON app.community_post (tenant_id);
CREATE INDEX ix_community_post_institution_id ON app.community_post (tenant_id, institution_id);
CREATE INDEX ix_community_post_author_user_id ON app.community_post (tenant_id, author_user_id);
CREATE INDEX ix_community_post_approval_request_id ON app.community_post (tenant_id, approval_request_id);
CREATE INDEX ix_community_post_media_tenant_id ON app.community_post_media (tenant_id);
CREATE INDEX ix_community_post_media_post_id ON app.community_post_media (tenant_id, post_id);
CREATE INDEX ix_community_post_media_file_id ON app.community_post_media (tenant_id, file_id);
CREATE INDEX ix_post_comment_tenant_id ON app.post_comment (tenant_id);
CREATE INDEX ix_post_comment_post_id ON app.post_comment (tenant_id, post_id);
CREATE INDEX ix_post_comment_author_user_id ON app.post_comment (tenant_id, author_user_id);
CREATE INDEX ix_post_comment_hidden_by ON app.post_comment (tenant_id, hidden_by);
CREATE INDEX ix_post_like_tenant_id ON app.post_like (tenant_id);
CREATE INDEX ix_post_like_post_id ON app.post_like (tenant_id, post_id);
CREATE INDEX ix_post_like_user_account_id ON app.post_like (tenant_id, user_account_id);
CREATE INDEX ix_event_tenant_id ON app.event (tenant_id);
CREATE INDEX ix_event_institution_id ON app.event (tenant_id, institution_id);
CREATE INDEX ix_event_proposed_by_user_id ON app.event (tenant_id, proposed_by_user_id);
CREATE INDEX ix_event_approval_request_id ON app.event (tenant_id, approval_request_id);
CREATE INDEX ix_event_rsvp_tenant_id ON app.event_rsvp (tenant_id);
CREATE INDEX ix_event_rsvp_institution_id ON app.event_rsvp (tenant_id, institution_id);
CREATE INDEX ix_event_rsvp_event_id ON app.event_rsvp (tenant_id, event_id);
CREATE INDEX ix_event_rsvp_user_account_id ON app.event_rsvp (tenant_id, user_account_id);
CREATE INDEX ix_complaint_tenant_id ON app.complaint (tenant_id);
CREATE INDEX ix_complaint_institution_id ON app.complaint (tenant_id, institution_id);
CREATE INDEX ix_complaint_raised_by_user_id ON app.complaint (tenant_id, raised_by_user_id);
CREATE INDEX ix_complaint_about_enrollment_id ON app.complaint (tenant_id, about_enrollment_id);
CREATE INDEX ix_complaint_current_owner_user_id ON app.complaint (tenant_id, current_owner_user_id);
CREATE INDEX ix_complaint_action_tenant_id ON app.complaint_action (tenant_id);
CREATE INDEX ix_complaint_action_institution_id ON app.complaint_action (tenant_id, institution_id);
CREATE INDEX ix_complaint_action_complaint_id ON app.complaint_action (tenant_id, complaint_id);
CREATE INDEX ix_complaint_action_actor_user_id ON app.complaint_action (tenant_id, actor_user_id);
CREATE INDEX ix_complaint_action_to_user_id ON app.complaint_action (tenant_id, to_user_id);
CREATE INDEX ix_student_note_tenant_id ON app.student_note (tenant_id);
CREATE INDEX ix_student_note_student_profile_id ON app.student_note (tenant_id, student_profile_id);
CREATE INDEX ix_student_note_institution_id ON app.student_note (tenant_id, institution_id);
CREATE INDEX ix_student_note_added_by_user_id ON app.student_note (tenant_id, added_by_user_id);
CREATE INDEX ix_student_note_corrects_note_id ON app.student_note (tenant_id, corrects_note_id);
CREATE INDEX ix_policy_tenant_id ON app.policy (tenant_id);
CREATE INDEX ix_policy_institution_id ON app.policy (tenant_id, institution_id);
CREATE INDEX ix_policy_body_file_id ON app.policy (tenant_id, body_file_id);
CREATE INDEX ix_policy_drafted_by ON app.policy (tenant_id, drafted_by);
CREATE INDEX ix_policy_approval_request_id ON app.policy (tenant_id, approval_request_id);
CREATE INDEX ix_policy_published_by ON app.policy (tenant_id, published_by);
CREATE INDEX ix_policy_acceptance_tenant_id ON app.policy_acceptance (tenant_id);
CREATE INDEX ix_policy_acceptance_policy_id ON app.policy_acceptance (tenant_id, policy_id);
CREATE INDEX ix_policy_acceptance_user_account_id ON app.policy_acceptance (tenant_id, user_account_id);
CREATE INDEX ix_department_document_tenant_id ON app.department_document (tenant_id);
CREATE INDEX ix_department_document_institution_id ON app.department_document (tenant_id, institution_id);
CREATE INDEX ix_department_document_department_id ON app.department_document (tenant_id, department_id);
CREATE INDEX ix_department_document_file_id ON app.department_document (tenant_id, file_id);
CREATE INDEX ix_department_document_uploaded_by ON app.department_document (tenant_id, uploaded_by);
CREATE INDEX ix_compliance_report_tenant_id ON app.compliance_report (tenant_id);
CREATE INDEX ix_compliance_report_institution_id ON app.compliance_report (tenant_id, institution_id);
CREATE INDEX ix_compliance_report_file_id ON app.compliance_report (tenant_id, file_id);
CREATE INDEX ix_compliance_report_prepared_by ON app.compliance_report (tenant_id, prepared_by);
CREATE INDEX ix_compliance_report_approval_request_id ON app.compliance_report (tenant_id, approval_request_id);
CREATE INDEX ix_compliance_report_approved_by ON app.compliance_report (tenant_id, approved_by);
CREATE INDEX ix_accreditation_criterion_tenant_id ON app.accreditation_criterion (tenant_id);
CREATE INDEX ix_accreditation_criterion_institution_id ON app.accreditation_criterion (tenant_id, institution_id);
CREATE INDEX ix_accreditation_document_tenant_id ON app.accreditation_document (tenant_id);
CREATE INDEX ix_accreditation_document_institution_id ON app.accreditation_document (tenant_id, institution_id);
CREATE INDEX ix_accreditation_document_file_id ON app.accreditation_document (tenant_id, file_id);
CREATE INDEX ix_accreditation_document_uploaded_by ON app.accreditation_document (tenant_id, uploaded_by);
CREATE INDEX ix_accreditation_document_approval_request_id ON app.accreditation_document (tenant_id, approval_request_id);
CREATE INDEX ix_accreditation_evidence_map_tenant_id ON app.accreditation_evidence_map (tenant_id);
CREATE INDEX ix_accreditation_evidence_map_accreditation_document_id ON app.accreditation_evidence_map (tenant_id, accreditation_document_id);
CREATE INDEX ix_accreditation_evidence_map_criterion_id ON app.accreditation_evidence_map (tenant_id, criterion_id);
CREATE INDEX ix_accreditation_evidence_map_mapped_by ON app.accreditation_evidence_map (tenant_id, mapped_by);
CREATE INDEX ix_governance_item_tenant_id ON app.governance_item (tenant_id);
CREATE INDEX ix_governance_item_institution_id ON app.governance_item (tenant_id, institution_id);
CREATE INDEX ix_governance_item_submitted_by ON app.governance_item (tenant_id, submitted_by);
CREATE INDEX ix_governance_item_approval_request_id ON app.governance_item (tenant_id, approval_request_id);
CREATE INDEX ix_audit_finding_tenant_id ON app.audit_finding (tenant_id);
CREATE INDEX ix_audit_finding_institution_id ON app.audit_finding (tenant_id, institution_id);
CREATE INDEX ix_audit_finding_assigned_owner_user_id ON app.audit_finding (tenant_id, assigned_owner_user_id);
CREATE INDEX ix_audit_finding_recorded_by ON app.audit_finding (tenant_id, recorded_by);
CREATE INDEX ix_data_request_tenant_id ON app.data_request (tenant_id);
CREATE INDEX ix_data_request_raised_by_user_id ON app.data_request (tenant_id, raised_by_user_id);
CREATE INDEX ix_data_request_owner_user_id ON app.data_request (tenant_id, owner_user_id);
CREATE INDEX ix_data_request_identity_verified_by ON app.data_request (tenant_id, identity_verified_by);
CREATE INDEX ix_data_request_event_tenant_id ON app.data_request_event (tenant_id);
CREATE INDEX ix_data_request_event_data_request_id ON app.data_request_event (tenant_id, data_request_id);
CREATE INDEX ix_data_request_event_actor_user_id ON app.data_request_event (tenant_id, actor_user_id);
CREATE INDEX ix_legal_hold_tenant_id ON app.legal_hold (tenant_id);
CREATE INDEX ix_legal_hold_placed_by ON app.legal_hold (tenant_id, placed_by);
CREATE INDEX ix_legal_hold_released_by ON app.legal_hold (tenant_id, released_by);
CREATE INDEX ix_file_object_tenant_id ON app.file_object (tenant_id);
CREATE INDEX ix_file_object_institution_id ON app.file_object (tenant_id, institution_id);
CREATE INDEX ix_file_object_uploaded_by ON app.file_object (tenant_id, uploaded_by);
CREATE INDEX ix_audit_outbox_tenant_id ON app.audit_outbox (tenant_id);
CREATE INDEX ix_audit_outbox_institution_id ON app.audit_outbox (tenant_id, institution_id);
CREATE INDEX ix_audit_outbox_action_code ON app.audit_outbox (action_code);
CREATE INDEX ix_outbox_event_tenant_id ON app.outbox_event (tenant_id);
CREATE INDEX ix_import_job_tenant_id ON app.import_job (tenant_id);
CREATE INDEX ix_import_job_institution_id ON app.import_job (tenant_id, institution_id);
CREATE INDEX ix_import_job_uploaded_by ON app.import_job (tenant_id, uploaded_by);
CREATE INDEX ix_import_job_source_file_id ON app.import_job (tenant_id, source_file_id);
CREATE INDEX ix_import_row_error_tenant_id ON app.import_row_error (tenant_id);
CREATE INDEX ix_import_row_error_import_job_id ON app.import_row_error (tenant_id, import_job_id);
CREATE INDEX ix_bulk_correction_batch_tenant_id ON app.bulk_correction_batch (tenant_id);
CREATE INDEX ix_bulk_correction_batch_institution_id ON app.bulk_correction_batch (tenant_id, institution_id);
CREATE INDEX ix_bulk_correction_batch_submitted_by ON app.bulk_correction_batch (tenant_id, submitted_by);
CREATE INDEX ix_bulk_correction_batch_source_file_id ON app.bulk_correction_batch (tenant_id, source_file_id);
CREATE INDEX ix_bulk_correction_item_tenant_id ON app.bulk_correction_item (tenant_id);
CREATE INDEX ix_bulk_correction_item_bulk_correction_batch_id ON app.bulk_correction_item (tenant_id, bulk_correction_batch_id);
CREATE INDEX ix_report_export_job_tenant_id ON app.report_export_job (tenant_id);
CREATE INDEX ix_report_export_job_requested_by_user_id ON app.report_export_job (tenant_id, requested_by_user_id);
CREATE INDEX ix_report_export_job_report_code ON app.report_export_job (report_code);
CREATE INDEX ix_report_export_job_file_id ON app.report_export_job (tenant_id, file_id);
CREATE INDEX ix_background_job_tenant_id ON app.background_job (tenant_id);
CREATE INDEX ix_background_job_job_type ON app.background_job (job_type);
CREATE INDEX ix_background_job_requested_by_user_id ON app.background_job (tenant_id, requested_by_user_id);
CREATE INDEX ix_idempotency_key_tenant_id ON app.idempotency_key (tenant_id);
CREATE INDEX ix_idempotency_key_scope ON app.idempotency_key (scope);
CREATE INDEX ix_idempotency_key_caller_user_id ON app.idempotency_key (tenant_id, caller_user_id);
CREATE INDEX ix_setting_value_tenant_id ON app.setting_value (tenant_id);
CREATE INDEX ix_setting_value_setting_key ON app.setting_value (setting_key);
CREATE INDEX ix_setting_value_institution_id ON app.setting_value (tenant_id, institution_id);
CREATE INDEX ix_saved_filter_tenant_id ON app.saved_filter (tenant_id);
CREATE INDEX ix_saved_filter_user_account_id ON app.saved_filter (tenant_id, user_account_id);
CREATE INDEX ix_saved_filter_report_code ON app.saved_filter (report_code);
CREATE INDEX ix_dashboard_preference_tenant_id ON app.dashboard_preference (tenant_id);
CREATE INDEX ix_dashboard_preference_user_account_id ON app.dashboard_preference (tenant_id, user_account_id);
CREATE INDEX ix_usage_metric_daily_tenant_id ON app.usage_metric_daily (tenant_id);
CREATE INDEX ix_usage_metric_daily_institution_id ON app.usage_metric_daily (tenant_id, institution_id);
CREATE INDEX ix_certificate_request_tenant_id ON app.certificate_request (tenant_id);
CREATE INDEX ix_certificate_request_student_profile_id ON app.certificate_request (tenant_id, student_profile_id);
CREATE INDEX ix_certificate_request_institution_id ON app.certificate_request (tenant_id, institution_id);
CREATE INDEX ix_certificate_request_requested_by_user_id ON app.certificate_request (tenant_id, requested_by_user_id);
CREATE INDEX ix_certificate_request_no_dues_override_by ON app.certificate_request (tenant_id, no_dues_override_by);
CREATE INDEX ix_certificate_request_approval_request_id ON app.certificate_request (tenant_id, approval_request_id);
CREATE INDEX ix_certificate_request_prepared_by_user_id ON app.certificate_request (tenant_id, prepared_by_user_id);
CREATE INDEX ix_certificate_request_file_id ON app.certificate_request (tenant_id, file_id);
CREATE INDEX ix_certificate_request_applied_by_user_id ON app.certificate_request (tenant_id, applied_by_user_id);
