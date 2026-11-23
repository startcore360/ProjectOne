-- EDU 360 M15: merit ranking, quota allotment, and database-enforced C12.
BEGIN;
CREATE TABLE IF NOT EXISTS app.merit_list (
 tenant_id uuid NOT NULL,id uuid NOT NULL,cycle_id uuid NOT NULL,institution_id uuid NOT NULL,round_number integer NOT NULL,
 generated_at timestamptz NOT NULL DEFAULT now(),status text NOT NULL DEFAULT 'PROVISIONAL',
 approved_by_user_id uuid,approved_at timestamptz,published_at timestamptz,created_by uuid NOT NULL,
 CONSTRAINT pk_merit_list PRIMARY KEY(tenant_id,id),CONSTRAINT uq_merit_list_tid UNIQUE(tenant_id,id),
 CONSTRAINT fk_merit_list_cycle FOREIGN KEY(tenant_id,cycle_id) REFERENCES app.admission_cycle(tenant_id,id),
 CONSTRAINT fk_merit_list_institution FOREIGN KEY(tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id),
 CONSTRAINT ck_merit_list_round CHECK(round_number>0),CONSTRAINT ck_merit_list_status CHECK(status IN('PROVISIONAL','APPROVED','PUBLISHED')),
 CONSTRAINT ck_merit_list_approval CHECK((status='PROVISIONAL' AND approved_by_user_id IS NULL AND approved_at IS NULL) OR
 (status IN('APPROVED','PUBLISHED') AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL))
);
CREATE TABLE IF NOT EXISTS app.merit_list_item (
 tenant_id uuid NOT NULL,id uuid NOT NULL,merit_list_id uuid NOT NULL,application_id uuid NOT NULL,institution_id uuid NOT NULL,
 merit_rank integer NOT NULL,computed_score numeric(7,3) NOT NULL,allocated_quota text,seat_status text NOT NULL DEFAULT 'OFFERED',
 created_at timestamptz NOT NULL DEFAULT now(),CONSTRAINT pk_merit_list_item PRIMARY KEY(tenant_id,id),
 CONSTRAINT uq_merit_list_item_application UNIQUE(tenant_id,merit_list_id,application_id),
 CONSTRAINT fk_merit_item_list FOREIGN KEY(tenant_id,merit_list_id) REFERENCES app.merit_list(tenant_id,id),
 CONSTRAINT fk_merit_item_application FOREIGN KEY(tenant_id,application_id) REFERENCES app.admission_application(tenant_id,id),
 CONSTRAINT fk_merit_item_institution FOREIGN KEY(tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id),
 CONSTRAINT ck_merit_item_rank CHECK(merit_rank>0),CONSTRAINT ck_merit_item_score CHECK(computed_score BETWEEN 0 AND 100),
 CONSTRAINT ck_merit_item_quota CHECK(allocated_quota IS NULL OR allocated_quota IN('GENERAL','MERIT','SPORTS','MANAGEMENT')),
 CONSTRAINT ck_merit_item_seat_status CHECK(seat_status IN('OFFERED','ACCEPTED','DECLINED','EXPIRED'))
);
CREATE TABLE IF NOT EXISTS app.seat_allotment (
 tenant_id uuid NOT NULL,id uuid NOT NULL,cycle_id uuid NOT NULL,merit_list_id uuid NOT NULL,merit_list_item_id uuid NOT NULL,
 application_id uuid NOT NULL,institution_id uuid NOT NULL,quota_code text NOT NULL,status text NOT NULL DEFAULT 'OFFERED',
 offered_at timestamptz NOT NULL DEFAULT now(),accepted_at timestamptz,admission_fee_confirmed_at timestamptz,
 created_by uuid NOT NULL,updated_by uuid NOT NULL,updated_at timestamptz NOT NULL DEFAULT now(),row_version integer NOT NULL DEFAULT 1,
 CONSTRAINT pk_seat_allotment PRIMARY KEY(tenant_id,id),CONSTRAINT uq_seat_allotment_application UNIQUE(tenant_id,cycle_id,application_id),
 CONSTRAINT fk_seat_allotment_cycle FOREIGN KEY(tenant_id,cycle_id) REFERENCES app.admission_cycle(tenant_id,id),
 CONSTRAINT fk_seat_allotment_list FOREIGN KEY(tenant_id,merit_list_id) REFERENCES app.merit_list(tenant_id,id),
 CONSTRAINT fk_seat_allotment_item FOREIGN KEY(tenant_id,merit_list_item_id) REFERENCES app.merit_list_item(tenant_id,id),
 CONSTRAINT fk_seat_allotment_application FOREIGN KEY(tenant_id,application_id) REFERENCES app.admission_application(tenant_id,id),
 CONSTRAINT fk_seat_allotment_institution FOREIGN KEY(tenant_id,institution_id) REFERENCES app.institution(tenant_id,institution_id),
 CONSTRAINT ck_seat_allotment_quota CHECK(quota_code IN('GENERAL','MERIT','SPORTS','MANAGEMENT')),
 CONSTRAINT ck_seat_allotment_status CHECK(status IN('OFFERED','ACCEPTED','DECLINED','EXPIRED')),
 CONSTRAINT ck_seat_allotment_accepted CHECK(status<>'ACCEPTED' OR accepted_at IS NOT NULL),CONSTRAINT ck_seat_allotment_version CHECK(row_version>=1)
);
CREATE OR REPLACE FUNCTION app.fn_admission_set_institution() RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
BEGIN
 IF TG_TABLE_NAME='merit_list' THEN SELECT institution_id INTO NEW.institution_id FROM app.admission_cycle WHERE tenant_id=NEW.tenant_id AND id=NEW.cycle_id;
 ELSIF TG_TABLE_NAME='merit_list_item' THEN SELECT institution_id INTO NEW.institution_id FROM app.merit_list WHERE tenant_id=NEW.tenant_id AND id=NEW.merit_list_id;
 ELSE SELECT institution_id INTO NEW.institution_id FROM app.admission_cycle WHERE tenant_id=NEW.tenant_id AND id=NEW.cycle_id; END IF;
 IF NEW.institution_id IS NULL THEN RAISE EXCEPTION 'ADMISSION_INSTITUTION_SCOPE_REQUIRED'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_merit_list_institution ON app.merit_list;
CREATE TRIGGER trg_merit_list_institution BEFORE INSERT ON app.merit_list FOR EACH ROW EXECUTE FUNCTION app.fn_admission_set_institution();
DROP TRIGGER IF EXISTS trg_merit_item_institution ON app.merit_list_item;
CREATE TRIGGER trg_merit_item_institution BEFORE INSERT ON app.merit_list_item FOR EACH ROW EXECUTE FUNCTION app.fn_admission_set_institution();
DROP TRIGGER IF EXISTS trg_seat_allotment_institution ON app.seat_allotment;
CREATE TRIGGER trg_seat_allotment_institution BEFORE INSERT ON app.seat_allotment FOR EACH ROW EXECUTE FUNCTION app.fn_admission_set_institution();
CREATE OR REPLACE FUNCTION app.fn_validate_merit_approval() RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
DECLARE v_actor uuid; v_role text;
BEGIN
 IF NEW.status IN('APPROVED','PUBLISHED') AND OLD.status='PROVISIONAL' THEN
  v_actor:=NULLIF(current_setting('app.user_id',true),'')::uuid;
  IF v_actor IS NULL THEN RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='MERIT_APPROVAL_NON_DELEGABLE'; END IF;
  SELECT rd.role_code INTO v_role FROM app.user_role_assignment ura JOIN app.role_definition rd
   ON rd.tenant_id=ura.tenant_id AND rd.role_definition_id=ura.role_definition_id
   WHERE ura.tenant_id=NEW.tenant_id AND ura.user_account_id=v_actor AND ura.status='ACTIVE'
    AND ura.effective_from<=now() AND (ura.effective_to IS NULL OR ura.effective_to>now())
    AND rd.status='ACTIVE' AND rd.role_code IN('PRINCIPAL','COLLEGE_PRINCIPAL','SCHOOL_PRINCIPAL','VICE_CHANCELLOR','PRODUCT_OWNER') LIMIT 1;
  IF v_role IS NULL OR EXISTS(SELECT 1 FROM app.delegation d WHERE d.tenant_id=NEW.tenant_id AND d.delegate_user_id=v_actor
    AND d.status='ACTIVE' AND d.starts_at<=now() AND d.ends_at>now()
    AND (d.process_code IS NULL OR d.process_code IN('ALL','MERIT_LIST_APPROVE')))
    OR NEW.approved_by_user_id IS DISTINCT FROM v_actor THEN
   RAISE EXCEPTION USING ERRCODE='42501',MESSAGE='MERIT_APPROVAL_NON_DELEGABLE';
  END IF;
  NEW.approved_at:=COALESCE(NEW.approved_at,now());
 END IF; RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_merit_approval_guard ON app.merit_list;
CREATE TRIGGER trg_merit_approval_guard BEFORE UPDATE OF status,approved_by_user_id ON app.merit_list FOR EACH ROW EXECUTE FUNCTION app.fn_validate_merit_approval();
CREATE OR REPLACE FUNCTION app.fn_admission_seat_capacity() RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
DECLARE v_total integer; v_quota integer; v_used integer; v_quota_used integer;
BEGIN
 SELECT total_seats,COALESCE((quota_capacity->>NEW.quota_code)::integer,0) INTO v_total,v_quota
 FROM app.admission_cycle WHERE tenant_id=NEW.tenant_id AND id=NEW.cycle_id FOR UPDATE;
 SELECT count(*) INTO v_used FROM app.seat_allotment WHERE tenant_id=NEW.tenant_id AND cycle_id=NEW.cycle_id AND status<>'DECLINED';
 SELECT count(*) INTO v_quota_used FROM app.seat_allotment WHERE tenant_id=NEW.tenant_id AND cycle_id=NEW.cycle_id AND quota_code=NEW.quota_code AND status<>'DECLINED';
 IF v_used>=v_total OR v_quota_used>=v_quota THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='ADMISSION_QUOTA_CAPACITY_EXCEEDED'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_admission_seat_capacity ON app.seat_allotment;
CREATE TRIGGER trg_admission_seat_capacity BEFORE INSERT ON app.seat_allotment FOR EACH ROW EXECUTE FUNCTION app.fn_admission_seat_capacity();
CREATE OR REPLACE FUNCTION app.fn_admission_seat_accepted() RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,app AS $$
BEGIN
 IF NEW.status='ACCEPTED' AND OLD.status IS DISTINCT FROM 'ACCEPTED' THEN
  UPDATE app.admission_application SET status='ADMITTED',updated_by=NEW.updated_by
   WHERE tenant_id=NEW.tenant_id AND id=NEW.application_id AND status IN('SHORTLISTED','SCRUTINIZED');
 END IF; RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_admission_seat_accepted ON app.seat_allotment;
CREATE TRIGGER trg_admission_seat_accepted AFTER UPDATE OF status ON app.seat_allotment FOR EACH ROW EXECUTE FUNCTION app.fn_admission_seat_accepted();
CREATE INDEX IF NOT EXISTS ix_merit_list_cycle ON app.merit_list(tenant_id,cycle_id,round_number DESC);
CREATE INDEX IF NOT EXISTS ix_merit_item_rank ON app.merit_list_item(tenant_id,merit_list_id,merit_rank);
CREATE INDEX IF NOT EXISTS ix_seat_allotment_cycle_status ON app.seat_allotment(tenant_id,cycle_id,status);
ALTER TABLE app.merit_list ENABLE ROW LEVEL SECURITY; ALTER TABLE app.merit_list FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_tenant ON app.merit_list;
CREATE POLICY p_tenant ON app.merit_list USING(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid) WITH CHECK(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid);
DROP POLICY IF EXISTS p_institution ON app.merit_list;
CREATE POLICY p_institution ON app.merit_list AS RESTRICTIVE USING(current_setting('app.institution_scope',true)='ALL' OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[])) WITH CHECK(current_setting('app.institution_scope',true)='ALL' OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[]));
GRANT SELECT,INSERT,UPDATE,DELETE ON app.merit_list TO edu_app; GRANT SELECT ON app.merit_list TO edu_report;
ALTER TABLE app.merit_list_item ENABLE ROW LEVEL SECURITY; ALTER TABLE app.merit_list_item FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_tenant ON app.merit_list_item;
CREATE POLICY p_tenant ON app.merit_list_item USING(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid) WITH CHECK(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid);
DROP POLICY IF EXISTS p_institution ON app.merit_list_item;
CREATE POLICY p_institution ON app.merit_list_item AS RESTRICTIVE USING(current_setting('app.institution_scope',true)='ALL' OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[])) WITH CHECK(current_setting('app.institution_scope',true)='ALL' OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[]));
GRANT SELECT,INSERT,UPDATE,DELETE ON app.merit_list_item TO edu_app; GRANT SELECT ON app.merit_list_item TO edu_report;
ALTER TABLE app.seat_allotment ENABLE ROW LEVEL SECURITY; ALTER TABLE app.seat_allotment FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_tenant ON app.seat_allotment;
CREATE POLICY p_tenant ON app.seat_allotment USING(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid) WITH CHECK(tenant_id=NULLIF(current_setting('app.tenant_id',true),'')::uuid);
DROP POLICY IF EXISTS p_institution ON app.seat_allotment;
CREATE POLICY p_institution ON app.seat_allotment AS RESTRICTIVE USING(current_setting('app.institution_scope',true)='ALL' OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[])) WITH CHECK(current_setting('app.institution_scope',true)='ALL' OR institution_id=ANY(string_to_array(NULLIF(current_setting('app.institution_ids',true),''),',')::uuid[]));
GRANT SELECT,INSERT,UPDATE,DELETE ON app.seat_allotment TO edu_app; GRANT SELECT ON app.seat_allotment TO edu_report;
COMMIT;
