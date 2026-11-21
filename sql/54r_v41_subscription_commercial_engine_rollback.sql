BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM platform.tenant_subscription_addon)
       OR EXISTS (SELECT 1 FROM platform.plan_component)
       OR EXISTS (SELECT 1 FROM platform.commercial_catalog_item) THEN
        RAISE EXCEPTION 'SUBSCRIPTION_ENGINE_ROLLBACK_BLOCKED: commercial data exists';
    END IF;
    IF EXISTS (
        SELECT 1 FROM platform.plan
         WHERE pricing_type IN ('COMBO', 'CUSTOM')
            OR max_colleges IS NOT NULL OR max_schools IS NOT NULL
            OR max_students IS NOT NULL OR max_staff IS NOT NULL
            OR setup_fee_amount IS NOT NULL OR discount_type IS NOT NULL
            OR discount_value IS NOT NULL OR contract_duration_months IS NOT NULL
            OR pricing_engine_version = 'V2'
    ) THEN
        RAISE EXCEPTION 'SUBSCRIPTION_ENGINE_ROLLBACK_BLOCKED: a plan uses new commercial fields';
    END IF;
    IF EXISTS (
        SELECT 1 FROM platform.tenant
         WHERE max_students IS NOT NULL OR max_staff IS NOT NULL
            OR max_colleges IS NOT NULL OR max_schools IS NOT NULL
            OR subscription_engine_version <> 'LEGACY'
    ) THEN
        RAISE EXCEPTION 'SUBSCRIPTION_ENGINE_ROLLBACK_BLOCKED: tenant subscription data exists';
    END IF;
    IF EXISTS (SELECT 1 FROM platform.invoice WHERE discount_type IS NOT NULL OR discount_value IS NOT NULL)
       OR EXISTS (SELECT 1 FROM platform.invoice_line
                  WHERE catalog_item_id IS NOT NULL OR subscription_addon_id IS NOT NULL
                     OR line_type IN ('ADDON', 'MODULE')
                     OR quantity <> round(quantity, 2)) THEN
        RAISE EXCEPTION 'SUBSCRIPTION_ENGINE_ROLLBACK_BLOCKED: invoice history uses new fields';
    END IF;
    IF EXISTS (
        SELECT 1 FROM platform.tenant_usage_monthly
         WHERE metric_code IN (
             'ACTIVE_STUDENTS', 'ACTIVE_STAFF', 'ACTIVE_COLLEGES',
             'ACTIVE_SCHOOLS', 'BILLABLE_ACTIVE_STUDENTS'
         )
    ) THEN
        RAISE EXCEPTION 'SUBSCRIPTION_ENGINE_ROLLBACK_BLOCKED: subscription usage metrics exist';
    END IF;
END $$;

ALTER TABLE platform.tenant_usage_monthly DROP CONSTRAINT IF EXISTS ck_tenant_usage_monthly_metric_code;
ALTER TABLE platform.tenant_usage_monthly ADD CONSTRAINT ck_tenant_usage_monthly_metric_code
    CHECK (metric_code IN (
        'SMS_SENT', 'STORAGE_GB_PEAK', 'ACTIVE_USERS_PEAK', 'API_CALLS', 'LOGINS',
        'ATTENDANCE_MARKED', 'FEES_PAID_ONLINE', 'STORAGE_GB', 'ACTIVE_USERS',
        'ACTIVE_STUDENTS', 'ACTIVE_STAFF', 'ACTIVE_COLLEGES', 'ACTIVE_SCHOOLS',
        'BILLABLE_ACTIVE_STUDENTS'
    ));

ALTER TABLE platform.invoice_line DROP CONSTRAINT IF EXISTS fk_invoice_line_subscription_addon;
ALTER TABLE platform.invoice_line DROP CONSTRAINT IF EXISTS fk_invoice_line_catalog_item;
ALTER TABLE platform.invoice_line DROP COLUMN IF EXISTS subscription_addon_id;
ALTER TABLE platform.invoice_line DROP COLUMN IF EXISTS catalog_item_id;
ALTER TABLE platform.invoice_line DROP CONSTRAINT IF EXISTS ck_invoice_line_line_type;
ALTER TABLE platform.invoice_line ADD CONSTRAINT ck_invoice_line_line_type
    CHECK (line_type IN ('PLAN', 'SMS', 'STORAGE', 'SETUP', 'OTHER'));
ALTER TABLE platform.invoice_line ALTER COLUMN quantity TYPE numeric(14,2);
ALTER TABLE platform.invoice DROP CONSTRAINT IF EXISTS ck_invoice_discount_percent;
ALTER TABLE platform.invoice DROP CONSTRAINT IF EXISTS ck_invoice_discount_pair;
ALTER TABLE platform.invoice DROP CONSTRAINT IF EXISTS ck_invoice_discount_value;
ALTER TABLE platform.invoice DROP CONSTRAINT IF EXISTS ck_invoice_discount_type;
ALTER TABLE platform.invoice DROP COLUMN IF EXISTS discount_value;
ALTER TABLE platform.invoice DROP COLUMN IF EXISTS discount_type;

DROP TABLE IF EXISTS platform.tenant_subscription_addon;
DROP TABLE IF EXISTS platform.plan_component;
DROP TABLE IF EXISTS platform.commercial_catalog_item;

DELETE FROM ref.standard_role_action WHERE action_code = 'SUBSCRIPTION_ADDON_REQUEST';
DELETE FROM ref.action_catalog WHERE action_code = 'SUBSCRIPTION_ADDON_REQUEST';

ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_grace_period;
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_contract_duration;
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_renewal_mode;
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_subscription_engine_version;
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_max_schools;
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_max_colleges;
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_max_staff;
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_max_students;
ALTER TABLE platform.tenant
    DROP COLUMN IF EXISTS grace_period_days,
    DROP COLUMN IF EXISTS contract_end_at,
    DROP COLUMN IF EXISTS contract_duration_months,
    DROP COLUMN IF EXISTS renewal_mode,
    DROP COLUMN IF EXISTS subscription_engine_version,
    DROP COLUMN IF EXISTS max_schools,
    DROP COLUMN IF EXISTS max_colleges,
    DROP COLUMN IF EXISTS max_staff,
    DROP COLUMN IF EXISTS max_students;

ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_pricing_engine_version;
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_renewal_mode;
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_grace_period;
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_contract_duration;
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_discount_percent;
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_discount_pair;
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_discount_value;
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_discount_type;
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_setup_fee_amount;
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_max_staff;
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_max_students;
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_max_schools;
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_max_colleges;
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_billing_cycle_months;
ALTER TABLE platform.plan DROP CONSTRAINT IF EXISTS ck_plan_pricing_type;
ALTER TABLE platform.plan
    DROP COLUMN IF EXISTS pricing_engine_version,
    DROP COLUMN IF EXISTS renewal_mode,
    DROP COLUMN IF EXISTS grace_period_days,
    DROP COLUMN IF EXISTS contract_duration_months,
    DROP COLUMN IF EXISTS discount_value,
    DROP COLUMN IF EXISTS discount_type,
    DROP COLUMN IF EXISTS setup_fee_amount,
    DROP COLUMN IF EXISTS max_staff,
    DROP COLUMN IF EXISTS max_students,
    DROP COLUMN IF EXISTS max_schools,
    DROP COLUMN IF EXISTS max_colleges;
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_pricing_type
    CHECK (pricing_type IN ('PER_STUDENT_YEAR', 'FLAT', 'PER_MODULE'));
ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_billing_cycle_months
    CHECK (billing_cycle_months IN (1, 3, 6, 12));

COMMIT;
