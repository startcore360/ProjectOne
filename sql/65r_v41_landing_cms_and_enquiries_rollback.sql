BEGIN;

DROP TABLE IF EXISTS platform.landing_enquiry CASCADE;
DROP TABLE IF EXISTS platform.landing_testimonial CASCADE;
DROP TABLE IF EXISTS platform.landing_page_config CASCADE;
DROP TABLE IF EXISTS platform.landing_media CASCADE;

DROP INDEX IF EXISTS platform.ix_tenant_listed_directory;
ALTER TABLE platform.tenant DROP COLUMN IF EXISTS listed_in_directory;

COMMIT;
