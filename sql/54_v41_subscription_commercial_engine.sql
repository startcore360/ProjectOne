BEGIN;

ALTER TABLE platform.plan
    ADD COLUMN IF NOT EXISTS max_colleges integer,
    ADD COLUMN IF NOT EXISTS max_schools integer,
    ADD COLUMN IF NOT EXISTS max_students integer,
    ADD COLUMN IF NOT EXISTS max_staff integer,
    ADD COLUMN IF NOT EXISTS setup_fee_amount numeric(14,2),
    ADD COLUMN IF NOT EXISTS discount_type text,
    ADD COLUMN IF NOT EXISTS discount_value numeric(14,2),
    ADD COLUMN IF NOT EXISTS contract_duration_months integer,
    ADD COLUMN IF NOT EXISTS grace_period_days integer NOT NULL DEFAULT 15,
    ADD COLUMN IF NOT EXISTS renewal_mode text NOT NULL DEFAULT 'AUTOMATIC',
    ADD COLUMN IF NOT EXISTS pricing_engine_version text NOT NULL DEFAULT 'LEGACY';

ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_pricing_type;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_pricing_type
    CHECK (pricing_type IN ('PER_STUDENT_YEAR', 'FLAT', 'PER_MODULE', 'COMBO', 'CUSTOM'));
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_billing_cycle_months;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_billing_cycle_months
    CHECK (billing_cycle_months BETWEEN 1 AND 36);
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_max_colleges;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_max_colleges
    CHECK (max_colleges IS NULL OR max_colleges > 0);
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_max_schools;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_max_schools
    CHECK (max_schools IS NULL OR max_schools > 0);
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_max_students;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_max_students
    CHECK (max_students IS NULL OR max_students > 0);
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_max_staff;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_max_staff
    CHECK (max_staff IS NULL OR max_staff > 0);
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_setup_fee_amount;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_setup_fee_amount
    CHECK (setup_fee_amount IS NULL OR setup_fee_amount >= 0);
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_discount_type;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_discount_type
    CHECK (discount_type IS NULL OR discount_type IN ('PERCENT', 'FIXED'));
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_discount_value;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_discount_value
    CHECK (discount_value IS NULL OR discount_value >= 0);
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_discount_pair;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_discount_pair
    CHECK ((discount_type IS NULL) = (discount_value IS NULL));
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_discount_percent;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_discount_percent
    CHECK (discount_type <> 'PERCENT' OR discount_value BETWEEN 0 AND 100);
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_contract_duration;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_contract_duration
    CHECK (contract_duration_months IS NULL OR contract_duration_months BETWEEN 1 AND 360);
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_grace_period;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_grace_period
    CHECK (grace_period_days BETWEEN 0 AND 365);
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_renewal_mode;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_renewal_mode
    CHECK (renewal_mode IN ('AUTOMATIC', 'MANUAL'));
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_pricing_engine_version;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_pricing_engine_version
    CHECK (pricing_engine_version IN ('LEGACY', 'V2'));

ALTER TABLE platform.tenant
    ADD COLUMN IF NOT EXISTS max_students integer,
    ADD COLUMN IF NOT EXISTS max_staff integer,
    ADD COLUMN IF NOT EXISTS max_colleges integer,
    ADD COLUMN IF NOT EXISTS max_schools integer,
    ADD COLUMN IF NOT EXISTS subscription_engine_version text NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN IF NOT EXISTS renewal_mode text NOT NULL DEFAULT 'AUTOMATIC',
    ADD COLUMN IF NOT EXISTS contract_duration_months integer,
    ADD COLUMN IF NOT EXISTS contract_end_at date,
    ADD COLUMN IF NOT EXISTS grace_period_days integer NOT NULL DEFAULT 15;

ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_max_students;
ALTER TABLE platform.tenant ADD CONSTRAINT ck_tenant_max_students
    CHECK (max_students IS NULL OR max_students > 0);
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_max_staff;
ALTER TABLE platform.tenant ADD CONSTRAINT ck_tenant_max_staff
    CHECK (max_staff IS NULL OR max_staff > 0);
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_max_colleges;
ALTER TABLE platform.tenant ADD CONSTRAINT ck_tenant_max_colleges
    CHECK (max_colleges IS NULL OR max_colleges > 0);
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_max_schools;
ALTER TABLE platform.tenant ADD CONSTRAINT ck_tenant_max_schools
    CHECK (max_schools IS NULL OR max_schools > 0);
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_subscription_engine_version;
ALTER TABLE platform.tenant ADD CONSTRAINT ck_tenant_subscription_engine_version
    CHECK (subscription_engine_version IN ('LEGACY', 'V2'));
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_renewal_mode;
ALTER TABLE platform.tenant ADD CONSTRAINT ck_tenant_renewal_mode
    CHECK (renewal_mode IN ('AUTOMATIC', 'MANUAL'));
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_contract_duration;
ALTER TABLE platform.tenant ADD CONSTRAINT ck_tenant_contract_duration
    CHECK (contract_duration_months IS NULL OR contract_duration_months BETWEEN 1 AND 360);
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_grace_period;
ALTER TABLE platform.tenant ADD CONSTRAINT ck_tenant_grace_period
    CHECK (grace_period_days BETWEEN 0 AND 365);

CREATE TABLE IF NOT EXISTS platform.commercial_catalog_item (
    catalog_item_id uuid PRIMARY KEY,
    item_code text NOT NULL UNIQUE,
    item_name text NOT NULL,
    category text NOT NULL,
    unit_code text NOT NULL,
    pricing_basis text NOT NULL,
    unit_amount numeric(14,2) NOT NULL DEFAULT 0,
    units_per_quantity integer NOT NULL DEFAULT 1,
    billing_interval_months integer,
    module_code text,
    description text,
    is_available boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT ck_commercial_catalog_item_code CHECK (item_code ~ '^[A-Z][A-Z0-9_-]{1,79}$'),
    CONSTRAINT ck_commercial_catalog_item_name CHECK (char_length(item_name) BETWEEN 2 AND 120),
    CONSTRAINT ck_commercial_catalog_category CHECK (category IN ('CAPACITY', 'MODULE', 'SERVICE')),
    CONSTRAINT ck_commercial_catalog_unit CHECK (unit_code IN ('INSTITUTION', 'COLLEGE', 'SCHOOL', 'STUDENT_PACK', 'STAFF_PACK', 'MODULE', 'SERVICE')),
    CONSTRAINT ck_commercial_catalog_pricing_basis CHECK (pricing_basis IN ('ONE_TIME', 'RECURRING_FIXED', 'PER_UNIT', 'PER_STUDENT_YEAR', 'PER_MODULE_YEAR', 'USAGE')),
    CONSTRAINT ck_commercial_catalog_unit_amount CHECK (unit_amount >= 0),
    CONSTRAINT ck_commercial_catalog_units_per_quantity CHECK (units_per_quantity > 0),
    CONSTRAINT ck_commercial_catalog_interval CHECK (billing_interval_months IS NULL OR billing_interval_months BETWEEN 1 AND 36),
    CONSTRAINT ck_commercial_catalog_module CHECK (category <> 'MODULE' OR module_code IS NOT NULL),
    CONSTRAINT ck_commercial_catalog_row_version CHECK (row_version >= 1),
    CONSTRAINT ck_commercial_catalog_updated CHECK (updated_at >= created_at)
);

CREATE TABLE IF NOT EXISTS platform.plan_component (
    plan_component_id uuid PRIMARY KEY,
    plan_id uuid NOT NULL REFERENCES platform.plan(plan_id) ON DELETE RESTRICT,
    catalog_item_id uuid NOT NULL REFERENCES platform.commercial_catalog_item(catalog_item_id) ON DELETE RESTRICT,
    included_quantity numeric(14,2) NOT NULL DEFAULT 0,
    unit_amount_override numeric(14,2),
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT uq_plan_component_item UNIQUE (plan_id, catalog_item_id),
    CONSTRAINT ck_plan_component_quantity CHECK (included_quantity >= 0),
    CONSTRAINT ck_plan_component_amount CHECK (unit_amount_override IS NULL OR unit_amount_override >= 0),
    CONSTRAINT ck_plan_component_row_version CHECK (row_version >= 1),
    CONSTRAINT ck_plan_component_updated CHECK (updated_at >= created_at)
);

CREATE TABLE IF NOT EXISTS platform.tenant_subscription_addon (
    subscription_addon_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES platform.tenant(tenant_id) ON DELETE RESTRICT,
    catalog_item_id uuid NOT NULL REFERENCES platform.commercial_catalog_item(catalog_item_id) ON DELETE RESTRICT,
    invoice_id uuid REFERENCES platform.invoice(invoice_id) ON DELETE RESTRICT,
    quantity numeric(14,2) NOT NULL,
    unit_amount numeric(14,2) NOT NULL,
    status text NOT NULL DEFAULT 'REQUESTED',
    requested_by_user_id uuid NOT NULL,
    approved_by_platform_user_id uuid REFERENCES platform.platform_user(platform_user_id) ON DELETE RESTRICT,
    approved_at timestamptz,
    effective_from date,
    effective_to date,
    request_reason text,
    decision_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT ck_tenant_subscription_addon_quantity CHECK (quantity > 0),
    CONSTRAINT ck_tenant_subscription_addon_amount CHECK (unit_amount >= 0),
    CONSTRAINT ck_tenant_subscription_addon_status CHECK (status IN ('REQUESTED', 'APPROVED', 'INVOICED', 'ACTIVE', 'REJECTED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_tenant_subscription_addon_approval CHECK ((approved_by_platform_user_id IS NULL) = (approved_at IS NULL)),
    CONSTRAINT ck_tenant_subscription_addon_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_tenant_subscription_addon_row_version CHECK (row_version >= 1),
    CONSTRAINT ck_tenant_subscription_addon_updated CHECK (updated_at >= created_at)
);

CREATE INDEX IF NOT EXISTS ix_tenant_subscription_addon_tenant_status
    ON platform.tenant_subscription_addon(tenant_id, status);
CREATE INDEX IF NOT EXISTS ix_tenant_subscription_addon_invoice
    ON platform.tenant_subscription_addon(invoice_id) WHERE invoice_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_plan_component_plan
    ON platform.plan_component(plan_id) WHERE enabled;

DROP TRIGGER IF EXISTS trg_touch ON platform.commercial_catalog_item;
CREATE TRIGGER trg_touch BEFORE UPDATE ON platform.commercial_catalog_item
    FOR EACH ROW EXECUTE FUNCTION platform.fn_touch();
DROP TRIGGER IF EXISTS trg_touch ON platform.plan_component;
CREATE TRIGGER trg_touch BEFORE UPDATE ON platform.plan_component
    FOR EACH ROW EXECUTE FUNCTION platform.fn_touch();
DROP TRIGGER IF EXISTS trg_touch ON platform.tenant_subscription_addon;
CREATE TRIGGER trg_touch BEFORE UPDATE ON platform.tenant_subscription_addon
    FOR EACH ROW EXECUTE FUNCTION platform.fn_touch();

ALTER TABLE platform.invoice_line ALTER COLUMN quantity TYPE numeric(14,6);
ALTER TABLE platform.invoice_line DROP CONSTRAINT IF EXISTS ck_invoice_line_line_type;
ALTER TABLE platform.invoice_line ADD CONSTRAINT ck_invoice_line_line_type
    CHECK (line_type IN ('PLAN', 'SMS', 'STORAGE', 'SETUP', 'OTHER', 'ADDON', 'MODULE'));
ALTER TABLE platform.invoice_line
    ADD COLUMN IF NOT EXISTS catalog_item_id uuid,
    ADD COLUMN IF NOT EXISTS subscription_addon_id uuid;
ALTER TABLE platform.invoice_line DROP CONSTRAINT IF EXISTS fk_invoice_line_catalog_item;
ALTER TABLE platform.invoice_line ADD CONSTRAINT fk_invoice_line_catalog_item
    FOREIGN KEY (catalog_item_id) REFERENCES platform.commercial_catalog_item(catalog_item_id) ON DELETE RESTRICT;
ALTER TABLE platform.invoice_line DROP CONSTRAINT IF EXISTS fk_invoice_line_subscription_addon;
ALTER TABLE platform.invoice_line ADD CONSTRAINT fk_invoice_line_subscription_addon
    FOREIGN KEY (subscription_addon_id) REFERENCES platform.tenant_subscription_addon(subscription_addon_id) ON DELETE RESTRICT;
ALTER TABLE platform.invoice
    ADD COLUMN IF NOT EXISTS discount_type text,
    ADD COLUMN IF NOT EXISTS discount_value numeric(14,2);
ALTER TABLE platform.invoice DROP CONSTRAINT IF EXISTS ck_invoice_discount_type;
ALTER TABLE platform.invoice ADD CONSTRAINT ck_invoice_discount_type
    CHECK (discount_type IS NULL OR discount_type IN ('PERCENT', 'FIXED'));
ALTER TABLE platform.invoice DROP CONSTRAINT IF EXISTS ck_invoice_discount_value;
ALTER TABLE platform.invoice ADD CONSTRAINT ck_invoice_discount_value
    CHECK (discount_value IS NULL OR discount_value >= 0);
ALTER TABLE platform.invoice DROP CONSTRAINT IF EXISTS ck_invoice_discount_pair;
ALTER TABLE platform.invoice ADD CONSTRAINT ck_invoice_discount_pair
    CHECK ((discount_type IS NULL) = (discount_value IS NULL));
ALTER TABLE platform.invoice DROP CONSTRAINT IF EXISTS ck_invoice_discount_percent;
ALTER TABLE platform.invoice ADD CONSTRAINT ck_invoice_discount_percent
    CHECK (discount_type <> 'PERCENT' OR discount_value BETWEEN 0 AND 100);

ALTER TABLE platform.tenant_usage_monthly DROP CONSTRAINT IF EXISTS ck_tenant_usage_monthly_metric_code;
ALTER TABLE platform.tenant_usage_monthly ADD CONSTRAINT ck_tenant_usage_monthly_metric_code
    CHECK (metric_code IN (
        'SMS_SENT', 'STORAGE_GB_PEAK', 'ACTIVE_USERS_PEAK', 'API_CALLS', 'LOGINS',
        'ATTENDANCE_MARKED', 'FEES_PAID_ONLINE', 'STORAGE_GB', 'ACTIVE_USERS',
        'ACTIVE_STUDENTS', 'ACTIVE_STAFF', 'ACTIVE_COLLEGES', 'ACTIVE_SCHOOLS',
        'BILLABLE_ACTIVE_STUDENTS'
    ));

DO $$
DECLARE
    next_action_no integer;
    next_role_action_id integer;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ref.action_catalog WHERE action_code = 'SUBSCRIPTION_ADDON_REQUEST') THEN
        SELECT COALESCE(MAX(action_no), 0) + 1 INTO next_action_no FROM ref.action_catalog;
        INSERT INTO ref.action_catalog(action_code, action_no, module_name, phase, description, is_approval_action, sensitive_class, changed_in)
        VALUES ('SUBSCRIPTION_ADDON_REQUEST', next_action_no, 'Platform', 1,
                'Request an additional subscription capacity or service item', false, 'Personal – Create / edit', '4.1');
    END IF;
    SELECT COALESCE(MAX(standard_role_action_id), 0) + 1 INTO next_role_action_id FROM ref.standard_role_action;
    INSERT INTO ref.standard_role_action(standard_role_action_id, role_code, action_code, limited_to)
    SELECT next_role_action_id + row_number() OVER (ORDER BY roles.role_code) - 1,
           roles.role_code, 'SUBSCRIPTION_ADDON_REQUEST', 'Own tenant subscription'
      FROM (VALUES ('MANAGEMENT'), ('PRINCIPAL'), ('REGISTRAR'), ('VICE_CHANCELLOR')) roles(role_code)
     WHERE EXISTS (SELECT 1 FROM ref.standard_role WHERE standard_role.role_code = roles.role_code)
       AND NOT EXISTS (
           SELECT 1 FROM ref.standard_role_action existing
            WHERE existing.role_code = roles.role_code
              AND existing.action_code = 'SUBSCRIPTION_ADDON_REQUEST'
       );
END $$;

GRANT SELECT, INSERT, UPDATE ON platform.commercial_catalog_item TO edu_platform;
GRANT SELECT, INSERT, UPDATE ON platform.plan_component TO edu_platform;
GRANT SELECT, INSERT, UPDATE ON platform.tenant_subscription_addon TO edu_platform;
REVOKE ALL ON platform.commercial_catalog_item FROM edu_app;
REVOKE ALL ON platform.plan_component FROM edu_app;
REVOKE ALL ON platform.tenant_subscription_addon FROM edu_app;
GRANT SELECT, INSERT, UPDATE ON platform.tenant TO edu_platform;

COMMENT ON COLUMN platform.plan.max_students IS 'Maximum active student profiles; NULL means unlimited.';
COMMENT ON COLUMN platform.plan.max_staff IS 'Maximum active staff profiles; NULL means unlimited.';
COMMENT ON COLUMN platform.plan.pricing_engine_version IS 'LEGACY preserves existing subscription calculations; V2 enables configurable commercial components.';
COMMENT ON TABLE platform.commercial_catalog_item IS 'Configurable capacity, module and service items used by plans and tenant add-ons.';
COMMENT ON TABLE platform.plan_component IS 'Items and included quantities composing a configurable plan or combo package.';
COMMENT ON TABLE platform.tenant_subscription_addon IS 'Tenant-requested catalog item; capacity and entitlement apply only after invoice payment.';

COMMIT;
