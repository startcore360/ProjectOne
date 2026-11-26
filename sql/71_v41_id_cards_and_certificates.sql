-- T9 / M09-M10. Additive tenant migration 71.
BEGIN;
CREATE TABLE app.id_card_template (
 tenant_id uuid NOT NULL,id_card_template_id uuid NOT NULL,institution_id uuid NOT NULL,
 name text NOT NULL,layout text NOT NULL,background_image_ref text,fields_config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
 status text NOT NULL DEFAULT 'ACTIVE',created_at timestamptz NOT NULL DEFAULT now(),created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(),updated_by uuid NOT NULL,row_version integer NOT NULL DEFAULT 1,
 PRIMARY KEY(tenant_id,id_card_template_id),CHECK(char_length(name) BETWEEN 1 AND 100),
 CHECK(layout IN ('PORTRAIT','LANDSCAPE')),CHECK(jsonb_typeof(fields_config_json)='object'),
 CHECK(status IN ('ACTIVE','INACTIVE')),CHECK(row_version>=1)
);
CREATE TABLE app.id_card_issuance (
 tenant_id uuid NOT NULL,id_card_issuance_id uuid NOT NULL,institution_id uuid NOT NULL,user_id uuid NOT NULL,
 student_profile_id uuid,staff_profile_id uuid,template_id uuid NOT NULL,card_number text NOT NULL,
 issue_date date NOT NULL,expiry_date date,qr_hash char(64) NOT NULL,status text NOT NULL DEFAULT 'ACTIVE',
 created_at timestamptz NOT NULL DEFAULT now(),created_by uuid NOT NULL,updated_at timestamptz NOT NULL DEFAULT now(),
 updated_by uuid NOT NULL,row_version integer NOT NULL DEFAULT 1,
 PRIMARY KEY(tenant_id,id_card_issuance_id),
 FOREIGN KEY(tenant_id,template_id) REFERENCES app.id_card_template(tenant_id,id_card_template_id),
 FOREIGN KEY(tenant_id,student_profile_id) REFERENCES app.student_profile(tenant_id,student_profile_id),
 FOREIGN KEY(tenant_id,staff_profile_id) REFERENCES app.staff_profile(tenant_id,staff_profile_id),
 CHECK((student_profile_id IS NOT NULL)::int+(staff_profile_id IS NOT NULL)::int=1),
 CHECK(expiry_date IS NULL OR expiry_date>=issue_date),CHECK(qr_hash ~ '^[0-9a-f]{64}$'),
 CHECK(status IN ('ACTIVE','REPLACED','REVOKED')),CHECK(row_version>=1)
);
CREATE UNIQUE INDEX uq_id_card_number ON app.id_card_issuance(tenant_id,card_number);
CREATE UNIQUE INDEX uq_id_card_qr_hash ON app.id_card_issuance(tenant_id,qr_hash);
CREATE UNIQUE INDEX uq_id_card_active_user ON app.id_card_issuance(tenant_id,institution_id,user_id) WHERE status='ACTIVE';
CREATE INDEX ix_id_card_user_status ON app.id_card_issuance(tenant_id,user_id,status);
CREATE TABLE app.id_card_replacement (
 tenant_id uuid NOT NULL,id_card_replacement_id uuid NOT NULL,institution_id uuid NOT NULL,
 original_issuance_id uuid NOT NULL,reason text NOT NULL,fee_receipt_file_id uuid,
 status text NOT NULL DEFAULT 'REQUESTED',reviewed_by_user_id uuid,reviewed_at timestamptz,
 replacement_issuance_id uuid,created_at timestamptz NOT NULL DEFAULT now(),created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(),updated_by uuid NOT NULL,row_version integer NOT NULL DEFAULT 1,
 PRIMARY KEY(tenant_id,id_card_replacement_id),
 FOREIGN KEY(tenant_id,original_issuance_id) REFERENCES app.id_card_issuance(tenant_id,id_card_issuance_id),
 FOREIGN KEY(tenant_id,fee_receipt_file_id) REFERENCES app.file_object(tenant_id,file_id),
 FOREIGN KEY(tenant_id,replacement_issuance_id) REFERENCES app.id_card_issuance(tenant_id,id_card_issuance_id),
 CHECK(char_length(reason) BETWEEN 1 AND 1000),CHECK(status IN ('REQUESTED','APPROVED','REJECTED','COMPLETED')),
 CHECK((status IN ('APPROVED','REJECTED','COMPLETED'))=(reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL)),
 CHECK(row_version>=1)
);
CREATE TABLE app.certificate_template (
 tenant_id uuid NOT NULL,certificate_template_id uuid NOT NULL,institution_id uuid NOT NULL,
 type text NOT NULL,name text NOT NULL,template_html text NOT NULL,status text NOT NULL DEFAULT 'ACTIVE',
 created_at timestamptz NOT NULL DEFAULT now(),created_by uuid NOT NULL,updated_at timestamptz NOT NULL DEFAULT now(),
 updated_by uuid NOT NULL,row_version integer NOT NULL DEFAULT 1,
 PRIMARY KEY(tenant_id,certificate_template_id),
 CHECK(type IN ('TRANSFER_CERTIFICATE','BONAFIDE','CONDUCT','DEGREE')),
 CHECK(char_length(name) BETWEEN 1 AND 100),CHECK(char_length(template_html) BETWEEN 1 AND 50000),
 CHECK(status IN ('ACTIVE','INACTIVE')),CHECK(row_version>=1)
);
CREATE TABLE app.certificate_request (
 tenant_id uuid NOT NULL,certificate_request_id uuid NOT NULL,institution_id uuid NOT NULL,
 student_id uuid NOT NULL,template_id uuid NOT NULL,reason text NOT NULL,status text NOT NULL DEFAULT 'REQUESTED',
 dues_json jsonb NOT NULL DEFAULT '[]'::jsonb,created_at timestamptz NOT NULL DEFAULT now(),created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(),updated_by uuid NOT NULL,row_version integer NOT NULL DEFAULT 1,
 PRIMARY KEY(tenant_id,certificate_request_id),
 FOREIGN KEY(tenant_id,student_id) REFERENCES app.student_profile(tenant_id,student_profile_id),
 FOREIGN KEY(tenant_id,template_id) REFERENCES app.certificate_template(tenant_id,certificate_template_id),
 CHECK(char_length(reason) BETWEEN 1 AND 1000),CHECK(status IN ('REQUESTED','NO_DUES_PENDING','APPROVED','ISSUED','REJECTED')),
 CHECK(jsonb_typeof(dues_json)='array'),CHECK(row_version>=1)
);
CREATE INDEX ix_certificate_request_student ON app.certificate_request(tenant_id,student_id,status);
CREATE TABLE app.certificate_issuance (
 tenant_id uuid NOT NULL,certificate_issuance_id uuid NOT NULL,institution_id uuid NOT NULL,
 certificate_request_id uuid NOT NULL,certificate_number text NOT NULL,verification_code_hash char(64) NOT NULL,certificate_file_id uuid, 
 issued_by_user_id uuid NOT NULL,issued_at timestamptz NOT NULL DEFAULT now(),revoked_at timestamptz,
 revoked_by_user_id uuid,revocation_reason text,created_at timestamptz NOT NULL DEFAULT now(),created_by uuid NOT NULL,
 PRIMARY KEY(tenant_id,certificate_issuance_id),
 FOREIGN KEY(tenant_id,certificate_request_id) REFERENCES app.certificate_request(tenant_id,certificate_request_id),
 FOREIGN KEY(tenant_id,certificate_file_id) REFERENCES app.file_object(tenant_id,file_id),
 UNIQUE(tenant_id,certificate_number),UNIQUE(verification_code_hash),
 CHECK(verification_code_hash ~ '^[0-9a-f]{64}$'),
 CHECK((revoked_at IS NULL)=(revoked_by_user_id IS NULL)),
 CHECK(revocation_reason IS NULL OR char_length(revocation_reason) BETWEEN 1 AND 1000)
);
DO $$
DECLARE t text;
BEGIN
 FOREACH t IN ARRAY ARRAY['id_card_template','id_card_issuance','id_card_replacement','certificate_template','certificate_request'] LOOP
  EXECUTE format('CREATE TRIGGER trg_touch BEFORE UPDATE ON app.%I FOR EACH ROW EXECUTE FUNCTION app.fn_touch()',t);
  EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE app.%I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY p_tenant ON app.%I USING (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid) WITH CHECK (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid)',t);
  EXECUTE format('CREATE POLICY p_institution ON app.%I AS RESTRICTIVE USING (institution_id IS NULL OR current_setting(''app.institution_scope'',true)=''ALL'' OR institution_id=ANY(string_to_array(NULLIF(current_setting(''app.institution_ids'',true),''''),'','')::uuid[])) WITH CHECK (institution_id IS NULL OR current_setting(''app.institution_scope'',true)=''ALL'' OR institution_id=ANY(string_to_array(NULLIF(current_setting(''app.institution_ids'',true),''''),'','')::uuid[]))',t);
  EXECUTE format('GRANT SELECT,INSERT,UPDATE ON app.%I TO edu_app',t);
  EXECUTE format('GRANT SELECT ON app.%I TO edu_report',t);
 END LOOP;
 ALTER TABLE app.certificate_issuance ENABLE ROW LEVEL SECURITY;
 ALTER TABLE app.certificate_issuance FORCE ROW LEVEL SECURITY;
 CREATE POLICY p_tenant ON app.certificate_issuance USING(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid) WITH CHECK(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid);
 CREATE POLICY p_institution ON app.certificate_issuance AS RESTRICTIVE USING(institution_id IS NULL OR current_setting('app.institution_scope',true)='ALL' OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[])) WITH CHECK(institution_id IS NULL OR current_setting('app.institution_scope',true)='ALL' OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[]));
 CREATE POLICY p_institution ON app.certificate_issuance AS RESTRICTIVE
  USING(institution_id IS NULL OR current_setting('app.institution_scope',true)='ALL'
    OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[]))
  WITH CHECK(institution_id IS NULL OR current_setting('app.institution_scope',true)='ALL'
    OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[]));
 GRANT SELECT,INSERT ON app.certificate_issuance TO edu_app;
 GRANT UPDATE(revoked_at,revoked_by_user_id,revocation_reason) ON app.certificate_issuance TO edu_app;
 GRANT SELECT ON app.certificate_issuance TO edu_report;
END $$;

-- Public verification is a deliberate, code-possession disclosure of minimal certificate
-- metadata. row_security=off makes this fail closed unless the migration/function owner has
-- BYPASSRLS (or superuser) privileges; never broaden ordinary app.* table grants.
CREATE OR REPLACE FUNCTION app.fn_public_certificate_verify(p_code_hash char(64))
RETURNS TABLE (
 verification_status text,
 student_name text,
 program_name text,
 certificate_type text,
 issued_at timestamptz,
 institution_name text
)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path=pg_catalog,app
SET row_security=off
AS $$
 SELECT CASE WHEN ci.revoked_at IS NULL THEN 'VALID' ELSE 'REVOKED' END,
        sp.full_name,
        COALESCE(pr.program_name,'Not recorded'),
        ct.type,
        ci.issued_at,
        inst.institution_name
 FROM app.certificate_issuance ci
 JOIN app.certificate_request cr ON cr.tenant_id=ci.tenant_id
  AND cr.certificate_request_id=ci.certificate_request_id
 JOIN app.student_profile sp ON sp.tenant_id=cr.tenant_id
  AND sp.student_profile_id=cr.student_id
 LEFT JOIN app.program pr ON pr.tenant_id=sp.tenant_id AND pr.program_id=sp.program_id
 JOIN app.certificate_template ct ON ct.tenant_id=cr.tenant_id
  AND ct.certificate_template_id=cr.template_id
 JOIN app.institution inst ON inst.tenant_id=ci.tenant_id AND inst.institution_id=ci.institution_id
 WHERE ci.verification_code_hash=p_code_hash
 LIMIT 1
$$;
REVOKE ALL ON FUNCTION app.fn_public_certificate_verify(char(64)) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app.fn_public_certificate_verify(char(64)) TO edu_app;
COMMENT ON FUNCTION app.fn_public_certificate_verify(char(64)) IS
 'Unauthenticated certificate verification by possession of a high-entropy code; returns only certificate status, student name, program, type, issue date and institution. Function owner must have BYPASSRLS; row_security=off otherwise errors closed.';

COMMENT ON TABLE app.id_card_issuance IS 'ID card issuance; qr_hash stores a digest of a signed identifier payload, never signing material.';
COMMENT ON TABLE app.certificate_issuance IS 'Official certificate issuance and revocation ledger; verification code is stored only as a SHA-256 digest.';
COMMIT;
