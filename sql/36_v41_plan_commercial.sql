-- E2-S1: plan commercial controls and metered allowances.
-- Additive and repeatable: existing plans retain conservative defaults.
ALTER TABLE platform.plan
    ADD COLUMN IF NOT EXISTS custom_domain_allowed boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS branding_colors_allowed boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS billing_cycle_months integer NOT NULL DEFAULT 12,
    ADD COLUMN IF NOT EXISTS sms_unit_amount numeric(14,2),
    ADD COLUMN IF NOT EXISTS storage_unit_amount_per_gb numeric(14,2);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'platform.plan'::regclass AND conname = 'ck_plan_billing_cycle_months') THEN
        ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_billing_cycle_months
            CHECK (billing_cycle_months IN (1, 3, 6, 12));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'platform.plan'::regclass AND conname = 'ck_plan_sms_unit_amount') THEN
        ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_sms_unit_amount
            CHECK (sms_unit_amount IS NULL OR sms_unit_amount >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'platform.plan'::regclass AND conname = 'ck_plan_storage_unit_amount_per_gb') THEN
        ALTER TABLE platform.plan ADD CONSTRAINT ck_plan_storage_unit_amount_per_gb
            CHECK (storage_unit_amount_per_gb IS NULL OR storage_unit_amount_per_gb >= 0);
    END IF;
END $$;

COMMENT ON COLUMN platform.plan.custom_domain_allowed IS 'Whether this plan permits a tenant custom domain [E1-S3, E1-S4].';
COMMENT ON COLUMN platform.plan.branding_colors_allowed IS 'Whether this plan permits tenant branding colour overrides [E1-S4].';
COMMENT ON COLUMN platform.plan.billing_cycle_months IS 'Billing interval in months: 1, 3, 6 or 12 [E2-S1].';
COMMENT ON COLUMN platform.plan.sms_unit_amount IS 'Price per SMS usage unit in INR [E2-S1].';
COMMENT ON COLUMN platform.plan.storage_unit_amount_per_gb IS 'Price per GB storage usage unit in INR [E2-S1].';
