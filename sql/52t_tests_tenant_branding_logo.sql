-- Contract checks for migration 52 (E1-S4). Run after 52_v41_tenant_branding_logo.sql.
\set ON_ERROR_STOP 1
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'platform'
                     AND table_name = 'tenant' AND column_name = 'branding_logo_storage_key') THEN
        RAISE EXCEPTION 'BRANDING_LOGO_STORAGE_KEY_MISSING';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'platform'
                     AND table_name = 'tenant' AND column_name = 'branding_logo_content_type') THEN
        RAISE EXCEPTION 'BRANDING_LOGO_CONTENT_TYPE_MISSING';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tenant_branding_logo_content_type')
       OR NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tenant_branding_logo_complete') THEN
        RAISE EXCEPTION 'BRANDING_LOGO_CHECKS_MISSING';
    END IF;
    -- Only PNG and SVG, and the logo columns are all set or all empty.
    IF pg_get_constraintdef((SELECT oid FROM pg_constraint WHERE conname = 'ck_tenant_branding_logo_content_type'))
           NOT LIKE '%image/png%image/svg+xml%' THEN
        RAISE EXCEPTION 'BRANDING_LOGO_TYPES_WRONG';
    END IF;
END $$;
