DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='platform' AND table_name='tenant_usage_monthly' AND column_name='measured' AND is_nullable='NO')
 THEN RAISE EXCEPTION 'E6_S1_MEASURED_FLAG_MISSING'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='platform.tenant_usage_monthly'::regclass AND conname='ck_tenant_usage_monthly_metric_code' AND pg_get_constraintdef(oid) LIKE '%ACTIVE_USERS%')
 THEN RAISE EXCEPTION 'E6_S1_MONTHLY_METRIC_CODES_MISSING'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='platform' AND indexname='uq_tenant_usage_monthly_tenant_month_metric')
 THEN RAISE EXCEPTION 'E6_S1_MONTHLY_INDEX_MISSING'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='platform' AND indexname='uq_usage_metric_daily_date_metric')
 THEN RAISE EXCEPTION 'E6_S1_PLATFORM_DAILY_INDEX_MISSING'; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='app' AND indexname='uq_app_usage_metric_daily_tenant_date_metric')
 THEN RAISE EXCEPTION 'E6_S1_TENANT_DAILY_INDEX_MISSING'; END IF;
END $$;
