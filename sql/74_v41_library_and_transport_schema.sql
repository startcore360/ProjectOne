-- T10 / Actions 214-228. Configurable institution library policy and transport capacity.
BEGIN;
CREATE TABLE IF NOT EXISTS app.library_catalog (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 isbn text, title text NOT NULL, author text, category text,
 total_copies integer NOT NULL DEFAULT 0, available_copies integer NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 CHECK(total_copies>=0 AND available_copies>=0 AND available_copies<=total_copies)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_library_isbn ON app.library_catalog(tenant_id,institution_id,isbn) WHERE isbn IS NOT NULL;
CREATE TABLE IF NOT EXISTS app.book_copy (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 book_id uuid NOT NULL REFERENCES app.library_catalog(id) ON DELETE RESTRICT,
 accession_number text NOT NULL, status text NOT NULL DEFAULT 'AVAILABLE',
 UNIQUE(tenant_id,institution_id,accession_number),
 CHECK(status IN ('AVAILABLE','ISSUED','RESERVED','LOST','WITHDRAWN'))
);
CREATE TABLE IF NOT EXISTS app.library_fine_policy (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 grace_period_days integer NOT NULL DEFAULT 0,
 calculation_mode text NOT NULL DEFAULT 'FLAT_DAILY_AMOUNT',
 daily_amount numeric(12,2) NOT NULL DEFAULT 5.00,
 day_basis text NOT NULL DEFAULT 'CALENDAR_DAYS',
 working_days jsonb NOT NULL DEFAULT '[1,2,3,4,5,6]'::jsonb,
 fine_cap numeric(12,2) NOT NULL DEFAULT 500.00,
 is_placeholder_default boolean NOT NULL DEFAULT true,
 updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid,
 UNIQUE(tenant_id,institution_id),
 CHECK(grace_period_days>=0), CHECK(calculation_mode='FLAT_DAILY_AMOUNT'),
 CHECK(daily_amount>=0), CHECK(day_basis IN ('CALENDAR_DAYS','WORKING_DAYS')),
 CHECK(jsonb_typeof(working_days)='array'),
 CHECK(fine_cap>=0)
);
-- PLACEHOLDER ONLY: INR 5/day, INR 500 cap, zero grace, calendar days.
-- Institutions must review and may change this configuration; it is not a ratified business rule.
INSERT INTO app.library_fine_policy(id,tenant_id,institution_id)
 SELECT gen_random_uuid(),tenant_id,institution_id FROM app.institution
 ON CONFLICT DO NOTHING;
CREATE OR REPLACE FUNCTION app.fn_library_fine_amount(p_tenant uuid,p_institution uuid,p_due_date date)
RETURNS numeric(12,2) LANGUAGE plpgsql STABLE AS $
DECLARE pol app.library_fine_policy%ROWTYPE; days_late integer; billable_days integer;
BEGIN
 SELECT * INTO pol FROM app.library_fine_policy
  WHERE tenant_id=p_tenant AND institution_id=p_institution;
 IF NOT FOUND THEN RETURN 0; END IF;
 days_late := GREATEST(0,current_date-p_due_date-pol.grace_period_days);
 IF days_late=0 THEN RETURN 0; END IF;
 IF pol.day_basis='CALENDAR_DAYS' THEN billable_days:=days_late;
 ELSE
  SELECT count(*) INTO billable_days
   FROM generate_series(p_due_date+pol.grace_period_days+1,current_date,interval '1 day') d
   WHERE EXTRACT(ISODOW FROM d)::integer IN
     (SELECT value::integer FROM jsonb_array_elements_text(pol.working_days));
 END IF;
 RETURN LEAST(pol.fine_cap,pol.daily_amount*billable_days)::numeric(12,2);
END $;

CREATE TABLE IF NOT EXISTS app.book_circulation (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 book_id uuid NOT NULL REFERENCES app.library_catalog(id) ON DELETE RESTRICT,
 copy_id uuid REFERENCES app.book_copy(id) ON DELETE RESTRICT,
 borrower_user_id uuid NOT NULL, borrower_role text NOT NULL,
 issued_at timestamptz NOT NULL DEFAULT now(), due_date date NOT NULL, returned_at timestamptz,
 accrued_fine numeric(12,2) NOT NULL DEFAULT 0, status text NOT NULL DEFAULT 'ISSUED',
 CHECK(accrued_fine>=0), CHECK(status IN ('ISSUED','RETURNED','OVERDUE','LOST'))
);
CREATE TABLE IF NOT EXISTS app.library_fine (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 circulation_id uuid NOT NULL REFERENCES app.book_circulation(id) ON DELETE RESTRICT,
 amount numeric(12,2) NOT NULL, amount_paid numeric(12,2) NOT NULL DEFAULT 0,
 status text NOT NULL DEFAULT 'OUTSTANDING', assessed_at timestamptz NOT NULL DEFAULT now(),
 CHECK(amount>=0 AND amount_paid>=0 AND amount_paid<=amount),
 CHECK(status IN ('OUTSTANDING','PAID'))
);
CREATE TABLE IF NOT EXISTS app.library_reservation (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 book_id uuid NOT NULL REFERENCES app.library_catalog(id) ON DELETE RESTRICT,
 borrower_user_id uuid NOT NULL, queue_position bigint NOT NULL,
 status text NOT NULL DEFAULT 'WAITING', created_at timestamptz NOT NULL DEFAULT now(),
 CHECK(status IN ('WAITING','FULFILLED','CANCELLED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_library_reservation_queue
 ON app.library_reservation(tenant_id,institution_id,book_id,queue_position) WHERE status='WAITING';
CREATE TABLE IF NOT EXISTS app.vehicle (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 registration_number text NOT NULL, vehicle_type text NOT NULL DEFAULT 'BUS',
 capacity integer NOT NULL, active boolean NOT NULL DEFAULT true,
 CHECK(capacity>0), UNIQUE(tenant_id,institution_id,registration_number)
);
CREATE TABLE IF NOT EXISTS app.transport_route (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 route_name text NOT NULL, vehicle_id uuid REFERENCES app.vehicle(id) ON DELETE RESTRICT, vehicle_number text NOT NULL, driver_name text NOT NULL,
 driver_phone text, total_capacity integer NOT NULL, transport_fee_code text,
 active boolean NOT NULL DEFAULT true, CHECK(total_capacity>0)
);
CREATE TABLE IF NOT EXISTS app.route_stop (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 route_id uuid NOT NULL REFERENCES app.transport_route(id) ON DELETE RESTRICT,
 stop_name text NOT NULL, pickup_time time, drop_time time, fee_amount numeric(12,2) NOT NULL DEFAULT 0,
 stop_order integer NOT NULL, CHECK(fee_amount>=0), CHECK(stop_order>=0),
 UNIQUE(tenant_id,route_id,stop_order)
);
CREATE TABLE IF NOT EXISTS app.report_definition (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 report_code text NOT NULL, report_name text NOT NULL, active boolean NOT NULL DEFAULT true,
 UNIQUE(tenant_id,institution_id,report_code)
);
CREATE TABLE IF NOT EXISTS app.report_execution (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 report_definition_id uuid REFERENCES app.report_definition(id) ON DELETE RESTRICT,
 requested_by uuid NOT NULL, from_date date NOT NULL, to_date date NOT NULL,
 status text NOT NULL DEFAULT 'RUNNING', started_at timestamptz NOT NULL DEFAULT now(),
 completed_at timestamptz, error_code text,
 CHECK(status IN ('RUNNING','SUCCEEDED','FAILED')),
 CHECK(from_date<=to_date)
);
CREATE TABLE IF NOT EXISTS app.transport_allocation (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, institution_id uuid NOT NULL,
 route_id uuid NOT NULL REFERENCES app.transport_route(id) ON DELETE RESTRICT,
 stop_id uuid NOT NULL REFERENCES app.route_stop(id) ON DELETE RESTRICT,
 passenger_user_id uuid NOT NULL, status text NOT NULL DEFAULT 'ACTIVE',
 allocated_at timestamptz NOT NULL DEFAULT now(), CHECK(status IN ('ACTIVE','CANCELLED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_transport_active_passenger
 ON app.transport_allocation(tenant_id,institution_id,passenger_user_id) WHERE status='ACTIVE';
CREATE OR REPLACE FUNCTION app.fn_transport_capacity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE cap integer; used integer;
BEGIN
 IF NEW.status<>'ACTIVE' THEN RETURN NEW; END IF;
 SELECT total_capacity INTO cap FROM app.transport_route
  WHERE id=NEW.route_id AND tenant_id=NEW.tenant_id AND institution_id=NEW.institution_id FOR UPDATE;
 IF cap IS NULL THEN RAISE EXCEPTION 'TRANSPORT_ROUTE_NOT_FOUND'; END IF;
 SELECT count(*) INTO used FROM app.transport_allocation
  WHERE tenant_id=NEW.tenant_id AND route_id=NEW.route_id AND status='ACTIVE' AND id<>COALESCE(NEW.id,'00000000-0000-0000-0000-000000000000'::uuid);
 IF used>=cap THEN RAISE EXCEPTION 'TRANSPORT_CAPACITY_EXCEEDED' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT 1 FROM app.route_stop WHERE id=NEW.stop_id AND route_id=NEW.route_id AND tenant_id=NEW.tenant_id) THEN
  RAISE EXCEPTION 'TRANSPORT_STOP_ROUTE_MISMATCH'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_transport_capacity_guard ON app.transport_allocation;
CREATE TRIGGER trg_transport_capacity_guard BEFORE INSERT OR UPDATE OF route_id,stop_id,status
 ON app.transport_allocation FOR EACH ROW EXECUTE FUNCTION app.fn_transport_capacity_guard();
-- T9 contract: one row for each student with library activity. No row means no library dues.
CREATE OR REPLACE VIEW app.v_student_library_dues_flag WITH (security_invoker=true) AS
 SELECT c.tenant_id,c.institution_id,c.borrower_user_id AS student_id,
        bool_or(c.returned_at IS NULL OR c.status IN ('ISSUED','OVERDUE','LOST')
          OR COALESCE(f.outstanding,0)>0) AS has_library_dues,
        COALESCE(sum(f.outstanding),0)::numeric(12,2) AS outstanding_fine_balance
 FROM app.book_circulation c
 LEFT JOIN (SELECT circulation_id,sum(amount-amount_paid) AS outstanding
            FROM app.library_fine WHERE status='OUTSTANDING' GROUP BY circulation_id) f
   ON f.circulation_id=c.id
 GROUP BY c.tenant_id,c.institution_id,c.borrower_user_id;
GRANT SELECT ON app.v_student_library_dues_flag TO edu_app,edu_report;
DO $$
DECLARE t text;
BEGIN
 FOREACH t IN ARRAY ARRAY['library_catalog','book_copy','library_fine_policy','book_circulation','library_fine','library_reservation','vehicle','transport_route','route_stop','transport_allocation','report_definition','report_execution'] LOOP
  EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('DROP POLICY IF EXISTS p_tenant ON app.%I',t);
  EXECUTE format('CREATE POLICY p_tenant ON app.%I USING (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid AND (institution_id IS NULL OR current_setting(''app.institution_scope'',true)=''ALL'' OR institution_id=ANY(string_to_array(NULLIF(current_setting(''app.institution_ids'',true),''''),'','')::uuid[]))) WITH CHECK (tenant_id=NULLIF(current_setting(''app.tenant_id'',true),'''')::uuid AND (institution_id IS NULL OR current_setting(''app.institution_scope'',true)=''ALL'' OR institution_id=ANY(string_to_array(NULLIF(current_setting(''app.institution_ids'',true),''''),'','')::uuid[])))',t);
  EXECUTE format('GRANT SELECT,INSERT,UPDATE ON app.%I TO edu_app',t);
  EXECUTE format('GRANT SELECT ON app.%I TO edu_report',t);
 END LOOP;
END $$;
COMMIT;
