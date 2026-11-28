\set ON_ERROR_STOP 1
BEGIN;

DO $$
DECLARE
    column_count integer;
    table_count integer;
BEGIN
    SELECT count(*) INTO column_count
      FROM information_schema.columns
     WHERE table_schema = 'platform' AND table_name = 'plan'
       AND column_name IN ('max_students', 'max_staff', 'max_colleges', 'max_schools',
                           'setup_fee_amount', 'pricing_engine_version');
    IF column_count <> 6 THEN
        RAISE EXCEPTION 'SUBSCRIPTION_ENGINE_PLAN_COLUMNS_MISSING: expected 6, found %', column_count;
    END IF;
    SELECT count(*) INTO table_count
      FROM information_schema.tables
     WHERE table_schema = 'platform'
       AND table_name IN ('commercial_catalog_item', 'plan_component', 'tenant_subscription_addon');
    IF table_count <> 3 THEN
        RAISE EXCEPTION 'SUBSCRIPTION_ENGINE_TABLES_MISSING: expected 3, found %', table_count;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'platform' AND table_name = 'commercial_catalog_item'
           AND column_name = 'units_per_quantity'
    ) THEN
        RAISE EXCEPTION 'SUBSCRIPTION_ENGINE_CAPACITY_PACK_SIZE_MISSING';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'platform.plan'::regclass
           AND conname = 'ck_plan_pricing_type'
           AND pg_get_constraintdef(oid) LIKE '%COMBO%'
           AND pg_get_constraintdef(oid) LIKE '%CUSTOM%'
    ) THEN
        RAISE EXCEPTION 'SUBSCRIPTION_ENGINE_PRICING_TYPES_MISSING';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'platform.tenant_usage_monthly'::regclass
           AND conname = 'ck_tenant_usage_monthly_metric_code'
           AND pg_get_constraintdef(oid) LIKE '%BILLABLE_ACTIVE_STUDENTS%'
    ) THEN
        RAISE EXCEPTION 'SUBSCRIPTION_ENGINE_STUDENT_USAGE_METRIC_MISSING';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM ref.action_catalog WHERE action_code = 'SUBSCRIPTION_ADDON_REQUEST'
    ) THEN
        RAISE EXCEPTION 'SUBSCRIPTION_ENGINE_TENANT_ACTION_MISSING';
    END IF;
END $$;

ROLLBACK;
