-- Rollback for 52_v41_tenant_branding_logo.sql.
-- Clears the logo reference first, so the stored file ids do not point at keys that no longer exist.
UPDATE platform.tenant SET branding_logo_file_ref = NULL WHERE branding_logo_storage_key IS NOT NULL;
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_branding_logo_complete;
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_branding_logo_storage_key;
ALTER TABLE platform.tenant DROP CONSTRAINT IF EXISTS ck_tenant_branding_logo_content_type;
ALTER TABLE platform.tenant DROP COLUMN IF EXISTS branding_logo_content_type;
ALTER TABLE platform.tenant DROP COLUMN IF EXISTS branding_logo_storage_key;
