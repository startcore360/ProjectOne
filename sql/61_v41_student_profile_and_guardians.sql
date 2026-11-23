-- EDU 360 V4.1 / T4-S1 + T4-S2. Additive, repeatable migration.
BEGIN;

ALTER TABLE app.student_profile
  ADD COLUMN IF NOT EXISTS admission_id text,
  ADD COLUMN IF NOT EXISTS gender text,
  ADD COLUMN IF NOT EXISTS preferred_name text,
  ADD COLUMN IF NOT EXISTS email text,
  ADD COLUMN IF NOT EXISTS mobile text,
  ADD COLUMN IF NOT EXISTS address_line1 text,
  ADD COLUMN IF NOT EXISTS address_line2 text,
  ADD COLUMN IF NOT EXISTS city text,
  ADD COLUMN IF NOT EXISTS state_region text,
  ADD COLUMN IF NOT EXISTS postal_code text,
  ADD COLUMN IF NOT EXISTS country_code char(2),
  ADD COLUMN IF NOT EXISTS department_id uuid,
  ADD COLUMN IF NOT EXISTS program_id uuid,
  ADD COLUMN IF NOT EXISTS batch_id uuid;

ALTER TABLE app.student_profile DROP CONSTRAINT IF EXISTS ck_student_profile_status;
ALTER TABLE app.student_profile ADD CONSTRAINT ck_student_profile_status
  CHECK (status IN ('ACTIVE','SUSPENDED','WITHDRAWN','ALUMNI','ON_HOLD','TRANSFERRED','DROPPED','DETAINED','GRADUATED','APPLICANT'));
ALTER TABLE app.student_profile DROP CONSTRAINT IF EXISTS ck_student_profile_gender;
ALTER TABLE app.student_profile ADD CONSTRAINT ck_student_profile_gender
  CHECK (gender IS NULL OR gender IN ('FEMALE','MALE','NON_BINARY','UNDISCLOSED'));
ALTER TABLE app.student_profile DROP CONSTRAINT IF EXISTS ck_student_profile_admission_id;
ALTER TABLE app.student_profile ADD CONSTRAINT ck_student_profile_admission_id
  CHECK (admission_id IS NULL OR char_length(admission_id) BETWEEN 1 AND 60);
CREATE UNIQUE INDEX IF NOT EXISTS uq_student_profile_admission_id
  ON app.student_profile(tenant_id, admission_id) WHERE admission_id IS NOT NULL;
CREATE OR REPLACE FUNCTION app.fn_student_profile_no_delete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'STUDENT_HARD_DELETE_PROHIBITED: change the student status instead' USING ERRCODE='42501';
END $$;
DROP TRIGGER IF EXISTS trg_student_profile_no_delete ON app.student_profile;
CREATE TRIGGER trg_student_profile_no_delete BEFORE DELETE ON app.student_profile
  FOR EACH ROW EXECUTE FUNCTION app.fn_student_profile_no_delete();
REVOKE DELETE ON app.student_profile FROM edu_app;

CREATE INDEX IF NOT EXISTS ix_student_profile_directory
  ON app.student_profile(tenant_id, institution_id, status, student_code, student_profile_id);
CREATE INDEX IF NOT EXISTS ix_student_profile_program_department
  ON app.student_profile(tenant_id, department_id, program_id, batch_id);

ALTER TABLE app.student_guardian_link
  ADD COLUMN IF NOT EXISTS contact_authorized boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS emergency_contact boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS local_sponsor_name text;
ALTER TABLE app.student_guardian_link DROP CONSTRAINT IF EXISTS ck_student_guardian_link_relationship;
ALTER TABLE app.student_guardian_link ADD CONSTRAINT ck_student_guardian_link_relationship
  CHECK (relationship IN ('FATHER','MOTHER','LEGAL_GUARDIAN','LOCAL_SPONSOR','OTHER'));

-- A student may have at most three non-blocked guardian relationships.
CREATE OR REPLACE FUNCTION app.fn_guardian_limit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_count integer;
BEGIN
  SELECT count(*) INTO v_count FROM app.student_guardian_link l
   WHERE l.tenant_id = NEW.tenant_id AND l.student_profile_id = NEW.student_profile_id
     AND l.status <> 'BLOCKED' AND l.student_guardian_link_id <> NEW.student_guardian_link_id;
  IF NEW.status <> 'BLOCKED' AND v_count >= 3 THEN
    RAISE EXCEPTION 'GUARDIAN_LIMIT_REACHED: at most 3 guardians' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_guardian_limit ON app.student_guardian_link;
CREATE TRIGGER trg_guardian_limit
  BEFORE INSERT OR UPDATE OF tenant_id, student_profile_id, status ON app.student_guardian_link
  FOR EACH ROW EXECUTE FUNCTION app.fn_guardian_limit();

-- Enforce no more than one primary at all times. Exactly one is required once a student has
-- at least one non-blocked link; deferred checking permits an atomic primary hand-off.
CREATE UNIQUE INDEX IF NOT EXISTS uq_student_guardian_one_primary
  ON app.student_guardian_link(tenant_id, student_profile_id)
  WHERE is_primary AND status <> 'BLOCKED';
CREATE OR REPLACE FUNCTION app.fn_guardian_primary_exactly_one() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_tenant uuid; v_student uuid; v_count integer; v_primary integer;
BEGIN
  v_tenant := COALESCE(NEW.tenant_id, OLD.tenant_id);
  v_student := COALESCE(NEW.student_profile_id, OLD.student_profile_id);
  SELECT count(*), count(*) FILTER (WHERE is_primary)
    INTO v_count, v_primary FROM app.student_guardian_link
   WHERE tenant_id=v_tenant AND student_profile_id=v_student AND status <> 'BLOCKED';
  IF v_count > 0 AND v_primary <> 1 THEN
    RAISE EXCEPTION 'PRIMARY_GUARDIAN_REQUIRED: exactly one active primary guardian is required' USING ERRCODE='23514';
  END IF;
  RETURN NULL;
END $$;
DROP TRIGGER IF EXISTS trg_guardian_primary_exactly_one ON app.student_guardian_link;
CREATE CONSTRAINT TRIGGER trg_guardian_primary_exactly_one
  AFTER INSERT OR UPDATE OR DELETE ON app.student_guardian_link
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
  EXECUTE FUNCTION app.fn_guardian_primary_exactly_one();

COMMIT;