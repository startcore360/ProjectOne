-- E2-S1 migration acceptance checks.
DO $$
DECLARE v_count integer;
BEGIN
    SELECT count(*) INTO v_count
      FROM information_schema.columns
     WHERE table_schema = 'platform' AND table_name = 'plan'
       AND column_name IN ('custom_domain_allowed','branding_colors_allowed','billing_cycle_months','sms_unit_amount','storage_unit_amount_per_gb');
    IF v_count <> 5 THEN
        RAISE EXCEPTION 'E2_S1_PLAN_COMMERCIAL_COLUMNS_MISSING: expected 5, found %', v_count;
    END IF;
    IF EXISTS (SELECT 1 FROM platform.plan
               WHERE custom_domain_allowed IS NULL OR branding_colors_allowed IS NULL
                  OR billing_cycle_months NOT IN (1,3,6,12)) THEN
        RAISE EXCEPTION 'E2_S1_EXISTING_PLAN_DEFAULTS_INVALID';
    END IF;
END $$;
