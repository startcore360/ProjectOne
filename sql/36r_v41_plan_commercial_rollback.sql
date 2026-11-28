-- Roll back E2-S1 plan commercial controls.
ALTER TABLE platform.plan
    DROP CONSTRAINT IF EXISTS ck_plan_storage_unit_amount_per_gb,
    DROP CONSTRAINT IF EXISTS ck_plan_sms_unit_amount,
    DROP CONSTRAINT IF EXISTS ck_plan_billing_cycle_months;
ALTER TABLE platform.plan
    DROP COLUMN IF EXISTS storage_unit_amount_per_gb,
    DROP COLUMN IF EXISTS sms_unit_amount,
    DROP COLUMN IF EXISTS billing_cycle_months,
    DROP COLUMN IF EXISTS branding_colors_allowed,
    DROP COLUMN IF EXISTS custom_domain_allowed;
