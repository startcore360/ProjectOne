BEGIN;
ALTER TABLE app.leave_request
 ADD COLUMN IF NOT EXISTS designated_delegate_staff_profile_id uuid;
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_leave_request_designated_delegate') THEN
  ALTER TABLE app.leave_request ADD CONSTRAINT fk_leave_request_designated_delegate
   FOREIGN KEY (tenant_id, designated_delegate_staff_profile_id)
   REFERENCES app.staff_profile(tenant_id, staff_profile_id) ON DELETE RESTRICT;
 END IF;
END $$;
CREATE TABLE IF NOT EXISTS app.leave_delegation (
 tenant_id uuid NOT NULL, leave_delegation_id uuid NOT NULL, institution_id uuid, leave_request_id uuid NOT NULL,
 delegator_staff_profile_id uuid NOT NULL, delegate_staff_profile_id uuid NOT NULL, start_date date NOT NULL, end_date date NOT NULL, reason text,
 status text NOT NULL DEFAULT 'ACTIVE', created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid NOT NULL, row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_leave_delegation PRIMARY KEY (tenant_id,leave_delegation_id), CONSTRAINT uq_leave_delegation_request UNIQUE (tenant_id,leave_request_id),
 CONSTRAINT ck_leave_delegation_dates CHECK (end_date>=start_date), CONSTRAINT ck_leave_delegation_not_self CHECK (delegator_staff_profile_id<>delegate_staff_profile_id),
 CONSTRAINT ck_leave_delegation_status CHECK (status IN ('ACTIVE','ENDED','CANCELLED')), CONSTRAINT ck_leave_delegation_reason CHECK (reason IS NULL OR char_length(btrim(reason)) BETWEEN 1 AND 1000),
 CONSTRAINT ck_leave_delegation_version CHECK (row_version>=1), CONSTRAINT ck_leave_delegation_updated CHECK (updated_at>=created_at),
 CONSTRAINT fk_leave_delegation_tenant FOREIGN KEY (tenant_id) REFERENCES app.tenant_local(tenant_id) ON DELETE RESTRICT,
 CONSTRAINT fk_leave_delegation_institution FOREIGN KEY (tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id) ON DELETE RESTRICT,
 CONSTRAINT fk_leave_delegation_request FOREIGN KEY (tenant_id,leave_request_id) REFERENCES app.leave_request(tenant_id,leave_request_id) ON DELETE RESTRICT,
 CONSTRAINT fk_leave_delegation_delegator FOREIGN KEY (tenant_id,delegator_staff_profile_id) REFERENCES app.staff_profile(tenant_id,staff_profile_id) ON DELETE RESTRICT,
 CONSTRAINT fk_leave_delegation_delegate FOREIGN KEY (tenant_id,delegate_staff_profile_id) REFERENCES app.staff_profile(tenant_id,staff_profile_id) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS ix_leave_delegation_institution_dates ON app.leave_delegation(tenant_id,institution_id,start_date,end_date) WHERE status='ACTIVE';
CREATE INDEX IF NOT EXISTS ix_leave_delegation_delegate_dates ON app.leave_delegation(tenant_id,delegate_staff_profile_id,start_date,end_date) WHERE status='ACTIVE';
CREATE OR REPLACE FUNCTION app.fn_leave_delegation_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_subject uuid; v_status text; v_start date; v_end date;
BEGIN
 SELECT for_staff_profile_id,status,start_date,end_date INTO v_subject,v_status,v_start,v_end FROM app.leave_request WHERE tenant_id=NEW.tenant_id AND leave_request_id=NEW.leave_request_id;
 IF NOT FOUND OR v_subject IS DISTINCT FROM NEW.delegator_staff_profile_id THEN RAISE EXCEPTION 'LEAVE_DELEGATION_REQUEST_MISMATCH' USING ERRCODE='23514'; END IF;
 IF v_status<>'APPROVED' THEN RAISE EXCEPTION 'LEAVE_DELEGATION_REQUIRES_APPROVED_LEAVE' USING ERRCODE='23514'; END IF;
 IF NEW.start_date<v_start OR NEW.end_date>v_end THEN RAISE EXCEPTION 'LEAVE_DELEGATION_OUTSIDE_LEAVE_DATES' USING ERRCODE='23514'; END IF;
 IF EXISTS (SELECT 1 FROM app.leave_delegation d WHERE d.tenant_id=NEW.tenant_id AND d.leave_delegation_id<>NEW.leave_delegation_id AND d.status='ACTIVE' AND d.delegate_staff_profile_id=NEW.delegator_staff_profile_id AND daterange(d.start_date,d.end_date,'[]') && daterange(NEW.start_date,NEW.end_date,'[]')) THEN RAISE EXCEPTION 'LEAVE_REDELEGATION_PROHIBITED' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_leave_delegation_guard ON app.leave_delegation;
CREATE TRIGGER trg_leave_delegation_guard BEFORE INSERT OR UPDATE ON app.leave_delegation FOR EACH ROW EXECUTE FUNCTION app.fn_leave_delegation_guard();
DROP TRIGGER IF EXISTS trg_touch ON app.leave_delegation;
CREATE TRIGGER trg_touch BEFORE UPDATE ON app.leave_delegation FOR EACH ROW EXECUTE FUNCTION app.fn_touch();
ALTER TABLE app.leave_delegation ENABLE ROW LEVEL SECURITY; ALTER TABLE app.leave_delegation FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_tenant ON app.leave_delegation;
CREATE POLICY p_tenant ON app.leave_delegation USING (tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid) WITH CHECK (tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid);
DROP POLICY IF EXISTS p_institution ON app.leave_delegation;
CREATE POLICY p_institution ON app.leave_delegation AS RESTRICTIVE USING (institution_id IS NULL OR current_setting('app.institution_scope',true)='ALL' OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[])) WITH CHECK (institution_id IS NULL OR current_setting('app.institution_scope',true)='ALL' OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[]));
GRANT SELECT,INSERT,UPDATE,DELETE ON app.leave_delegation TO edu_app; GRANT SELECT ON app.leave_delegation TO edu_report;
COMMENT ON TABLE app.leave_delegation IS 'Operational staff cover for an approved leave request; approval authority delegation remains in app.delegation.';
COMMIT;