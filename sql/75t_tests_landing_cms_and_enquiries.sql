BEGIN;

DO $$
DECLARE
    v_has_col boolean;
    v_cfg_count integer;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'platform'
           AND table_name = 'tenant'
           AND column_name = 'listed_in_directory'
    ) INTO v_has_col;
    IF NOT v_has_col THEN
        RAISE EXCEPTION 'platform.tenant.listed_in_directory column missing';
    END IF;

    SELECT count(*) INTO v_cfg_count FROM platform.landing_page_config;
    IF v_cfg_count < 1 THEN
        RAISE EXCEPTION 'platform.landing_page_config default seed missing';
    END IF;

    IF has_table_privilege('edu_app', 'platform.landing_enquiry', 'SELECT') THEN
        RAISE EXCEPTION 'edu_app must not have access to platform.landing_enquiry';
    END IF;
END $$;

ROLLBACK;
