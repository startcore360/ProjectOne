-- EDU 360 V4.0 / T2 — additive hierarchy constraints.
-- The V4.0 canonical model is Tenant (university group) -> Institution (college/school).
-- Do not create a second UNIVERSITY institution row or duplicate academic_session as academic_year.
BEGIN;

ALTER TABLE app.institution
  ADD COLUMN IF NOT EXISTS settings_json jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE app.institution
  ADD CONSTRAINT ck_institution_settings_object
  CHECK (jsonb_typeof(settings_json) = 'object');

-- A session is the V4.0 academic-year aggregate. One current session per scope.
ALTER TABLE app.academic_session
  ADD COLUMN IF NOT EXISTS is_current boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX IF NOT EXISTS ux_academic_session_current_scope
  ON app.academic_session (tenant_id, (COALESCE(institution_id, '00000000-0000-0000-0000-000000000000'::uuid)))
  WHERE is_current;

-- Date windows cannot overlap inside the same tenant + institution scope. End date is inclusive.
-- Closed historical sessions remain in the timeline but still participate in exclusion:
-- a closed year is historical, not permission to reuse its dates.
ALTER TABLE app.academic_session
  ADD CONSTRAINT ex_academic_session_nonoverlap
  EXCLUDE USING gist (
    tenant_id WITH =,
    (COALESCE(institution_id, '00000000-0000-0000-0000-000000000000'::uuid)) WITH =,
    daterange(start_date, end_date + 1, '[)') WITH &&
  );

CREATE INDEX IF NOT EXISTS ix_institution_tenant_status
  ON app.institution (tenant_id, status, institution_name);
CREATE INDEX IF NOT EXISTS ix_department_institution_status
  ON app.department (tenant_id, institution_id, status, department_name);
CREATE INDEX IF NOT EXISTS ix_program_department_status
  ON app.program (tenant_id, institution_id, department_id, status, program_name);
CREATE INDEX IF NOT EXISTS ix_program_year_program
  ON app.program_year (tenant_id, institution_id, program_id, year_number);
CREATE INDEX IF NOT EXISTS ix_semester_program_year
  ON app.semester (tenant_id, institution_id, program_year_id, semester_number);
CREATE INDEX IF NOT EXISTS ix_section_session_institution
  ON app.section (tenant_id, institution_id, academic_session_id, status, section_name);

COMMENT ON COLUMN app.institution.settings_json IS
  'Validated institution-level configuration; object only. Tenant scope is never supplied by request payload.';
COMMENT ON COLUMN app.academic_session.is_current IS
  'Exactly one current academic session per tenant-wide or institution-specific calendar scope.';
COMMENT ON CONSTRAINT ex_academic_session_nonoverlap ON app.academic_session IS
  'Prevents overlapping academic-year/session date ranges within a tenant and calendar scope.';

COMMIT;
