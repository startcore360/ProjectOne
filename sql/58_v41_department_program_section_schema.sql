-- EDU 360 V4.0 / T2 — additive curriculum and section capacity metadata.
-- Canonical equivalents: program_year (cohort/year), semester, school_class, section.
BEGIN;

ALTER TABLE app.department
  ADD COLUMN IF NOT EXISTS settings_json jsonb NOT NULL DEFAULT '{}'::jsonb;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.department'::regclass AND conname='ck_department_settings_object') THEN
    ALTER TABLE app.department ADD CONSTRAINT ck_department_settings_object
      CHECK (jsonb_typeof(settings_json) = 'object');
  END IF;
END $$;

ALTER TABLE app.program
  ADD COLUMN IF NOT EXISTS degree_level text,
  ADD COLUMN IF NOT EXISTS total_credits numeric(7,2),
  ADD COLUMN IF NOT EXISTS curriculum_version integer NOT NULL DEFAULT 1;
DO $ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.program'::regclass AND conname='ck_program_degree_level') THEN
    ALTER TABLE app.program ADD CONSTRAINT ck_program_degree_level
      CHECK (degree_level IS NULL OR degree_level IN ('UG','PG','DIPLOMA','K12_GRADE'));
  END IF;
END $;
DO $ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.program'::regclass AND conname='ck_program_total_credits') THEN
    ALTER TABLE app.program ADD CONSTRAINT ck_program_total_credits
      CHECK (total_credits IS NULL OR total_credits > 0);
  END IF;
END $;
DO $ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.program'::regclass AND conname='ck_program_curriculum_version') THEN
    ALTER TABLE app.program ADD CONSTRAINT ck_program_curriculum_version
      CHECK (curriculum_version >= 1);
  END IF;
END $;

ALTER TABLE app.semester
  ADD COLUMN IF NOT EXISTS start_date date,
  ADD COLUMN IF NOT EXISTS end_date date,
  ADD COLUMN IF NOT EXISTS is_locked boolean NOT NULL DEFAULT false;
DO $ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.semester'::regclass AND conname='ck_semester_date_range') THEN
    ALTER TABLE app.semester ADD CONSTRAINT ck_semester_date_range
      CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date);
  END IF;
END $;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.semester'::regclass AND conname='ex_semester_nonoverlap') THEN
    ALTER TABLE app.semester
      ADD CONSTRAINT ex_semester_nonoverlap
      EXCLUDE USING gist (
        tenant_id WITH =,
        program_year_id WITH =,
        daterange(start_date, end_date + 1, '[)') WITH &&
      )
      WHERE (start_date IS NOT NULL AND end_date IS NOT NULL);
  END IF;
END $$;

ALTER TABLE app.section
  ADD COLUMN IF NOT EXISTS capacity integer,
  ADD COLUMN IF NOT EXISTS class_teacher_user_id uuid;
DO $ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.section'::regclass AND conname='ck_section_capacity') THEN
    ALTER TABLE app.section ADD CONSTRAINT ck_section_capacity
      CHECK (capacity IS NULL OR capacity BETWEEN 1 AND 10000);
  END IF;
END $;

-- Curriculum snapshots are append-only by version; edits create a new version.
CREATE TABLE IF NOT EXISTS app.program_curriculum_version (
  tenant_id uuid NOT NULL,
  program_curriculum_version_id uuid NOT NULL,
  institution_id uuid NOT NULL,
  program_id uuid NOT NULL,
  version_number integer NOT NULL,
  curriculum_json jsonb NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid NOT NULL,
  CONSTRAINT pk_program_curriculum_version PRIMARY KEY (tenant_id, program_curriculum_version_id),
  CONSTRAINT fk_program_curriculum_version_tenant
    FOREIGN KEY (tenant_id) REFERENCES app.tenant_local (tenant_id) ON DELETE RESTRICT,
  CONSTRAINT fk_program_curriculum_version_institution
    FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution (tenant_id, institution_id) ON DELETE RESTRICT,
  CONSTRAINT fk_program_curriculum_version_program
    FOREIGN KEY (tenant_id, program_id) REFERENCES app.program (tenant_id, program_id) ON DELETE RESTRICT,
  CONSTRAINT ck_program_curriculum_version_number CHECK (version_number >= 1),
  CONSTRAINT ck_program_curriculum_version_json CHECK (jsonb_typeof(curriculum_json) = 'object'),
  CONSTRAINT ck_program_curriculum_version_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
  CONSTRAINT uq_program_curriculum_version UNIQUE (tenant_id, program_id, version_number)
);

ALTER TABLE app.program_curriculum_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.program_curriculum_version FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_tenant ON app.program_curriculum_version;
CREATE POLICY p_tenant ON app.program_curriculum_version
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
DROP POLICY IF EXISTS p_institution ON app.program_curriculum_version;
CREATE POLICY p_institution ON app.program_curriculum_version AS RESTRICTIVE
  USING (current_setting('app.institution_scope', true) = 'ALL'
      OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))
  WITH CHECK (current_setting('app.institution_scope', true) = 'ALL'
      OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]));
DROP TRIGGER IF EXISTS trg_program_curriculum_version_append_only ON app.program_curriculum_version;
CREATE TRIGGER trg_program_curriculum_version_append_only
  BEFORE UPDATE OR DELETE ON app.program_curriculum_version
  FOR EACH ROW EXECUTE FUNCTION app.fn_append_only();
GRANT SELECT, INSERT ON app.program_curriculum_version TO edu_app;
GRANT SELECT ON app.program_curriculum_version TO edu_report;

CREATE INDEX IF NOT EXISTS ix_program_curriculum_version_program
  ON app.program_curriculum_version (tenant_id, program_id, version_number DESC);
CREATE INDEX IF NOT EXISTS ix_section_capacity
  ON app.section (tenant_id, institution_id, capacity) WHERE capacity IS NOT NULL;

COMMENT ON COLUMN app.department.settings_json IS
  'Validated department-level configuration object; tenant scope comes from authenticated context.';
COMMENT ON COLUMN app.department.hod_staff_assignment_id IS
  'Canonical HOD link; assignment must resolve to a faculty member of this department (WF-20).';
COMMENT ON COLUMN app.semester.is_locked IS
  'When locked, attendance and marks mutations for the term are rejected by domain services with TERM_LOCKED (HTTP 409).';
COMMENT ON COLUMN app.section.class_teacher_user_id IS
  'Assigned class teacher / mentor user; authorization is checked against the institution and section scope.';
COMMENT ON TABLE app.program_curriculum_version IS
  'Immutable version snapshots of program curriculum; changes create a new version rather than rewriting history.';

COMMIT;
