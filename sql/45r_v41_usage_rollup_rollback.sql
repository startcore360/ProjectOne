-- E6-S1 rollback. Refuse to discard monthly usage rows produced by this story.
BEGIN;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM platform.tenant_usage_monthly
      WHERE metric_code IN ('LOGINS','ATTENDANCE_MARKED','FEES_PAID_ONLINE','STORAGE_GB','ACTIVE_USERS'))
    THEN RAISE EXCEPTION 'E6_S1_USAGE_DATA_EXISTS; export or reconcile usage rows before rollback'; END IF;
END $$;
DROP INDEX IF EXISTS app.uq_app_usage_metric_daily_tenant_date_metric;
DROP INDEX IF EXISTS platform.uq_usage_metric_daily_date_metric;
DROP INDEX IF EXISTS platform.uq_tenant_usage_monthly_tenant_month_metric;
ALTER TABLE platform.tenant_usage_monthly DROP CONSTRAINT IF EXISTS ck_tenant_usage_monthly_metric_code;
ALTER TABLE platform.tenant_usage_monthly ADD CONSTRAINT ck_tenant_usage_monthly_metric_code
CHECK (metric_code IN ('SMS_SENT','STORAGE_GB_PEAK','ACTIVE_USERS_PEAK','API_CALLS'));
ALTER TABLE platform.tenant_usage_monthly DROP COLUMN IF EXISTS measured;
COMMIT;
