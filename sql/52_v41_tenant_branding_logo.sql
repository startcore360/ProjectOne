-- EDU 360 V4.1 - tenant branding logo storage (plan E1-S4, R2-14).
-- The logo is uploaded by platform staff, so it cannot be an app.file_object row: that table
-- requires a tenant user account as uploaded_by (fk_file_object_uploaded_by), and naming a school
-- user as the uploader would be false. The logo is kept through FileStorage like any upload, and its
-- storage key and type live on the tenant row next to branding_logo_file_ref (the file id).
-- Additive and repeatable. Rollback: 52r_v41_tenant_branding_logo_rollback.sql.

ALTER TABLE platform.tenant ADD COLUMN IF NOT EXISTS branding_logo_storage_key text;
ALTER TABLE platform.tenant ADD COLUMN IF NOT EXISTS branding_logo_content_type text;

-- No code ever wrote branding_logo_file_ref before this file, so any value there cannot be shown;
-- clear such dead references so the "all three together" rule below can be added.
UPDATE platform.tenant SET branding_logo_file_ref = NULL
 WHERE branding_logo_file_ref IS NOT NULL AND branding_logo_storage_key IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tenant_branding_logo_content_type') THEN
        ALTER TABLE platform.tenant ADD CONSTRAINT ck_tenant_branding_logo_content_type
            CHECK (branding_logo_content_type IS NULL OR branding_logo_content_type IN ('image/png', 'image/svg+xml'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tenant_branding_logo_complete') THEN
        -- The three logo columns are set together or not at all.
        ALTER TABLE platform.tenant ADD CONSTRAINT ck_tenant_branding_logo_complete
            CHECK ((branding_logo_file_ref IS NULL) = (branding_logo_storage_key IS NULL)
               AND (branding_logo_storage_key IS NULL) = (branding_logo_content_type IS NULL));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tenant_branding_logo_storage_key') THEN
        ALTER TABLE platform.tenant ADD CONSTRAINT ck_tenant_branding_logo_storage_key
            CHECK (branding_logo_storage_key IS NULL OR char_length(branding_logo_storage_key) BETWEEN 1 AND 1000);
    END IF;
END $$;

COMMENT ON COLUMN platform.tenant.branding_logo_storage_key IS
    'FileStorage key of the tenant logo (E1-S4). Never sent to clients.';
COMMENT ON COLUMN platform.tenant.branding_logo_content_type IS
    'image/png or image/svg+xml, checked from the file content, not its name (E1-S4).';
