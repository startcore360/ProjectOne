-- T10 / Actions 196-213. Tenant- and institution-scoped notices and dispatch.
BEGIN;
CREATE TABLE IF NOT EXISTS app.notice_bulletin (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 title text NOT NULL, body text NOT NULL, target_roles_json jsonb NOT NULL DEFAULT '[]',
 is_urgent boolean NOT NULL DEFAULT false, requires_acknowledgment boolean NOT NULL DEFAULT false,
 published_at timestamptz, expires_at timestamptz, status text NOT NULL DEFAULT 'DRAFT',
 created_by uuid NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT ck_notice_status CHECK(status IN ('DRAFT','PUBLISHED','ARCHIVED')),
 CONSTRAINT ck_notice_expiry CHECK(expires_at IS NULL OR published_at IS NULL OR expires_at>published_at)
);
CREATE TABLE IF NOT EXISTS app.notice_target (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 notice_id uuid NOT NULL REFERENCES app.notice_bulletin(id) ON DELETE RESTRICT,
 target_kind text NOT NULL, target_id uuid, role_code text,
 created_at timestamptz NOT NULL DEFAULT now(),
 CHECK(target_kind IN ('ROLE','DEPARTMENT','CLASS','INSTITUTION')),
 CHECK((target_kind='ROLE' AND role_code IS NOT NULL) OR (target_kind<>'ROLE' AND target_id IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS ix_notice_target_notice ON app.notice_target(tenant_id,notice_id,target_kind);
CREATE TABLE IF NOT EXISTS app.notice_acknowledgment (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 notice_id uuid NOT NULL REFERENCES app.notice_bulletin(id) ON DELETE RESTRICT,
 user_id uuid NOT NULL, acknowledged_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tenant_id,notice_id,user_id)
);
CREATE TABLE IF NOT EXISTS app.communication_campaign (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 name text NOT NULL, template_id uuid, target_roles_json jsonb NOT NULL DEFAULT '[]',
 channels_json jsonb NOT NULL DEFAULT '["IN_APP"]', status text NOT NULL DEFAULT 'DRAFT',
 created_by uuid NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS app.message_template (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 template_code text NOT NULL, channel text NOT NULL, subject text, body text NOT NULL,
 active boolean NOT NULL DEFAULT true, UNIQUE(tenant_id,institution_id,template_code,channel),
 CHECK(channel IN ('IN_APP','PUSH','SMS','EMAIL'))
);
CREATE TABLE IF NOT EXISTS app.message_dispatch (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 campaign_id uuid REFERENCES app.communication_campaign(id) ON DELETE RESTRICT,
 channel text NOT NULL, attempt_order integer NOT NULL DEFAULT 1, recipient_user_id uuid, phone text, email text,
 message_body text NOT NULL, carrier_message_id text, status text NOT NULL DEFAULT 'QUEUED',
 failure_reason text, sent_at timestamptz, delivered_at timestamptz,
 claimed_at timestamptz, attempt_count integer NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 CHECK(channel IN ('IN_APP','PUSH','SMS','EMAIL')),
 CONSTRAINT ck_message_dispatch_status CHECK(status IN ('QUEUED','WAITING','PROCESSING','SENT','DELIVERED','FAILED','CANCELLED')),
 CHECK(attempt_count>=0), UNIQUE(tenant_id,campaign_id,attempt_order)
);
CREATE INDEX IF NOT EXISTS ix_notice_published_target ON app.notice_bulletin(tenant_id,institution_id,status,published_at DESC);
CREATE INDEX IF NOT EXISTS ix_notice_ack_user ON app.notice_acknowledgment(tenant_id,user_id,notice_id);
CREATE INDEX IF NOT EXISTS ix_dispatch_usage ON app.message_dispatch(tenant_id,created_at) WHERE channel='SMS' AND status IN ('SENT','DELIVERED');
CREATE INDEX IF NOT EXISTS ix_dispatch_campaign ON app.message_dispatch(tenant_id,campaign_id,status,attempt_order);
CREATE INDEX IF NOT EXISTS ix_dispatch_worker_claim ON app.message_dispatch(status,created_at,attempt_order) WHERE status IN ('QUEUED','PROCESSING');
DO $$
DECLARE t text;
BEGIN
 FOREACH t IN ARRAY ARRAY['notice_bulletin','notice_target','notice_acknowledgment','communication_campaign','message_template','message_dispatch'] LOOP
  EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('DROP POLICY IF EXISTS p_tenant ON app.%I',t);
  EXECUTE format('CREATE POLICY p_tenant ON app.%I USING (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid AND (institution_id IS NULL OR current_setting(''app.institution_scope'',true)=''ALL'' OR institution_id=ANY(string_to_array(NULLIF(current_setting(''app.institution_ids'',true),''''),'','')::uuid[]))) WITH CHECK (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid AND (institution_id IS NULL OR current_setting(''app.institution_scope'',true)=''ALL'' OR institution_id=ANY(string_to_array(NULLIF(current_setting(''app.institution_ids'',true),''''),'','')::uuid[])))',t);
  EXECUTE format('GRANT SELECT,INSERT,UPDATE ON app.%I TO edu_app',t);
  EXECUTE format('GRANT SELECT ON app.%I TO edu_report',t);
 END LOOP;
END $$;
COMMIT;
