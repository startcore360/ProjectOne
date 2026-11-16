-- E2-S1: plan commercial controls and metered allowances.
-- Additive migration; existing plans retain conservative defaults.
ALTER TABLE platform.plan
    ADD COLUMN custom_domain_allowed boolean NOT NULL DEFAULT false,
    ADD COLUMN branding_colors_allowed boolean NOT NULL DEFAULT false,
    ADD COLUMN billing_cycle_months integer NOT NULL DEFAULT 12,
    ADD COLUMN sms_unit_amount numeric(14,2),
    ADD COLUMN storage_unit_amount_per_gb numeric(14,2);

ALTER TABLE platform.plan
    ADD CONSTRAINT ck_plan_billing_cycle_months CHECK (billing_cycle_months IN (1, 3, 6, 12)),
    ADD CONSTRAINT ck_plan_sms_unit_amount CHECK (sms_unit_amount IS NULL OR sms_unit_amount >= 0),
    ADD CONSTRAINT ck_plan_storage_unit_amount_per_gb CHECK (storage_unit_amount_per_gb IS NULL OR storage_unit_amount_per_gb >= 0);

COMMENT ON COLUMN platform.plan.custom_domain_allowed IS 'Whether this plan permits a tenant custom domain [E1-S3, E1-S4].';
COMMENT ON COLUMN platform.plan.branding_colors_allowed IS 'Whether this plan permits tenant branding colour overrides [E1-S4].';
COMMENT ON COLUMN platform.plan.billing_cycle_months IS 'Billing interval in months: 1, 3, 6 or 12 [E2-S1].';
COMMENT ON COLUMN platform.plan.sms_unit_amount IS 'Price per SMS usage unit in INR [E2-S1].';
COMMENT ON COLUMN platform.plan.storage_unit_amount_per_gb IS 'Price per GB storage usage unit in INR [E2-S1].';
