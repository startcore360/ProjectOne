BEGIN;
DROP INDEX IF EXISTS app.ix_leave_balance_staff_year_type;
DROP INDEX IF EXISTS app.ix_leave_request_staff_status_start;
DROP INDEX IF EXISTS app.ix_staff_document_profile_type_created;
ALTER TABLE app.staff_document DROP CONSTRAINT IF EXISTS ck_staff_document_qualification_complete, DROP CONSTRAINT IF EXISTS ck_staff_document_qualification_level, DROP CONSTRAINT IF EXISTS ck_staff_document_awarding_institution, DROP CONSTRAINT IF EXISTS ck_staff_document_qualification_title;
ALTER TABLE app.staff_document DROP COLUMN IF EXISTS awarded_on, DROP COLUMN IF EXISTS qualification_level, DROP COLUMN IF EXISTS awarding_institution, DROP COLUMN IF EXISTS qualification_title;
COMMIT;