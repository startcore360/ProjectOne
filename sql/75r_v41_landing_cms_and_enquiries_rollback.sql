BEGIN;

DROP TABLE IF EXISTS platform.landing_enquiry CASCADE;
DROP TABLE IF EXISTS platform.landing_testimonial CASCADE;
DROP TABLE IF EXISTS platform.landing_page_config CASCADE;
DROP TABLE IF EXISTS platform.landing_media CASCADE;

DROP INDEX IF EXISTS platform.ix_tenant_listed_directory;
ALTER TABLE platform.tenant DROP COLUMN IF EXISTS listed_in_directory;

DELETE FROM ref.standard_role_action WHERE action_code = 'LANDING_CMS_MANAGE';
DELETE FROM ref.action_catalog WHERE action_code = 'LANDING_CMS_MANAGE';

COMMIT;
