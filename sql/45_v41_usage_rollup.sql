-- E6-S1: measured monthly usage plus idempotent tenant/day and platform/day rollups.
BEGIN;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM platform.tenant_usage_monthly GROUP BY tenant_id,usage_month,metric_code HAVING count(*)>1)
    THEN RAISE EXCEPTION 'E6_S1_DUPLICATE_TENANT_MONTH_METRIC'; END IF;
    IF EXISTS (SELECT 1 FROM platform.usage_metric_daily GROUP BY usage_date,metric_code HAVING count(*)>1)
    THEN RAISE EXCEPTION 'E6_S1_DUPLICATE_PLATFORM_DAY_METRIC'; END IF;
    IF EXISTS (SELECT 1 FROM app.usage_metric_daily WHERE institution_id IS NULL GROUP BY tenant_id,usage_date,metric_code HAVING count(*)>1)
    THEN RAISE EXCEPTION 'E6_S1_DUPLICATE_TENANT_DAY_METRIC'; END IF;
END $$;
ALTER TABLE platform.tenant_usage_monthly ADD COLUMN IF NOT EXISTS measured boolean NOT NULL DEFAULT true;
ALTER TABLE platform.tenant_usage_monthly DROP CONSTRAINT IF EXISTS ck_tenant_usage_monthly_metric_code;
ALTER TABLE platform.tenant_usage_monthly ADD CONSTRAINT ck_tenant_usage_monthly_metric_code
CHECK (metric_code IN ('SMS_SENT','STORAGE_GB_PEAK','ACTIVE_USERS_PEAK','API_CALLS','LOGINS','ATTENDANCE_MARKED','FEES_PAID_ONLINE','STORAGE_GB','ACTIVE_USERS'));
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_usage_monthly_tenant_month_metric ON platform.tenant_usage_monthly(tenant_id,usage_month,metric_code);
CREATE UNIQUE INDEX IF NOT EXISTS uq_usage_metric_daily_date_metric ON platform.usage_metric_daily(usage_date,metric_code);
COMMENT ON COLUMN platform.tenant_usage_monthly.measured IS
'False means the metric source is not integrated; quantity zero must not be interpreted as measured zero.';
COMMIT;
