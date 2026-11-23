BEGIN;
ALTER TABLE app.staff_document ADD COLUMN IF NOT EXISTS qualification_title text, ADD COLUMN IF NOT EXISTS awarding_institution text, ADD COLUMN IF NOT EXISTS qualification_level text, ADD COLUMN IF NOT EXISTS awarded_on date;
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.staff_document'::regclass AND conname='ck_staff_document_qualification_title') THEN ALTER TABLE app.staff_document ADD CONSTRAINT ck_staff_document_qualification_title CHECK (qualification_title IS NULL OR char_length(btrim(qualification_title)) BETWEEN 1 AND 150); END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.staff_document'::regclass AND conname='ck_staff_document_awarding_institution') THEN ALTER TABLE app.staff_document ADD CONSTRAINT ck_staff_document_awarding_institution CHECK (awarding_institution IS NULL OR char_length(btrim(awarding_institution)) BETWEEN 1 AND 150); END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.staff_document'::regclass AND conname='ck_staff_document_qualification_level') THEN ALTER TABLE app.staff_document ADD CONSTRAINT ck_staff_document_qualification_level CHECK (qualification_level IS NULL OR qualification_level IN ('CERTIFICATE','DIPLOMA','UNDERGRADUATE','POSTGRADUATE','DOCTORATE','OTHER')); END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.staff_document'::regclass AND conname='ck_staff_document_qualification_complete') THEN ALTER TABLE app.staff_document ADD CONSTRAINT ck_staff_document_qualification_complete CHECK ((qualification_title IS NULL AND awarding_institution IS NULL AND qualification_level IS NULL) OR (qualification_title IS NOT NULL AND awarding_institution IS NOT NULL AND qualification_level IS NOT NULL)); END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_staff_document_profile_type_created ON app.staff_document (tenant_id,staff_profile_id,document_type,created_at DESC);
CREATE INDEX IF NOT EXISTS ix_leave_request_staff_status_start ON app.leave_request (tenant_id,for_staff_profile_id,status,start_date) WHERE for_staff_profile_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_leave_balance_staff_year_type ON app.leave_balance (tenant_id,staff_profile_id,leave_year,leave_type_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_leave_balance_staff_type_year ON app.leave_balance (tenant_id,staff_profile_id,leave_type_id,leave_year);
COMMENT ON COLUMN app.staff_document.qualification_title IS 'Qualification name for DEGREE evidence; stored on the existing scanned staff document record.';
COMMENT ON COLUMN app.staff_document.awarding_institution IS 'Awarding institution for a staff qualification.';
COMMENT ON COLUMN app.staff_document.qualification_level IS 'Qualification level: CERTIFICATE, DIPLOMA, UNDERGRADUATE, POSTGRADUATE, DOCTORATE or OTHER.';
COMMENT ON COLUMN app.staff_document.awarded_on IS 'Award date for qualification evidence, when supplied.';
COMMIT;