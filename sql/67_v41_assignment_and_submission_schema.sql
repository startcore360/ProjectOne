-- EDU 360 V4.1 / T7 — assignment lifecycle and submissions.
-- Additive only. Tenant isolation is enforced with forced RLS and composite tenant keys.
BEGIN;

-- The V3.3 baseline already owns app.assignment. Extend it in place so the T7
-- assignment lifecycle and the legacy homework/assignment workflows share one record.
ALTER TABLE app.assignment
    ADD COLUMN IF NOT EXISTS teacher_user_id uuid,
    ADD COLUMN IF NOT EXISTS description text,
    ADD COLUMN IF NOT EXISTS allow_late_submission boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS late_penalty_percent_per_day numeric(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rubric_json jsonb NOT NULL DEFAULT '[]'::jsonb;
UPDATE app.assignment SET teacher_user_id=created_by_user_id WHERE teacher_user_id IS NULL;
ALTER TABLE app.assignment ALTER COLUMN teacher_user_id SET NOT NULL;
ALTER TABLE app.assignment ALTER COLUMN max_marks TYPE numeric(8,2);
ALTER TABLE app.assignment DROP CONSTRAINT IF EXISTS ck_assignment_status;
ALTER TABLE app.assignment ADD CONSTRAINT ck_assignment_status
    CHECK (status IN ('DRAFT','PUBLISHED','CLOSED','ARCHIVED'));
ALTER TABLE app.assignment ADD CONSTRAINT ck_assignment_late_penalty
    CHECK (late_penalty_percent_per_day BETWEEN 0 AND 100);
ALTER TABLE app.assignment ADD CONSTRAINT ck_assignment_rubric_object
    CHECK (jsonb_typeof(rubric_json) IN ('array','object'));
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.assignment'::regclass AND conname='fk_assignment_teacher')
 THEN ALTER TABLE app.assignment ADD CONSTRAINT fk_assignment_teacher
      FOREIGN KEY (tenant_id,teacher_user_id) REFERENCES app.user_account(tenant_id,user_account_id) ON DELETE RESTRICT; END IF;
END $$;
CREATE OR REPLACE FUNCTION app.fn_assignment_legacy_compat()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_kind text;
BEGIN
    IF NEW.teacher_user_id IS NULL THEN NEW.teacher_user_id:=NEW.created_by_user_id; END IF;
    IF NEW.created_by_user_id IS NULL THEN NEW.created_by_user_id:=COALESCE(NEW.teacher_user_id,NEW.created_by); END IF;
    IF NEW.assignment_kind IS NULL THEN
        SELECT CASE WHEN institution_type='SCHOOL' THEN 'HOMEWORK' ELSE 'ASSIGNMENT' END
          INTO v_kind FROM app.institution
         WHERE tenant_id=NEW.tenant_id AND institution_id=NEW.institution_id;
        NEW.assignment_kind:=COALESCE(v_kind,'ASSIGNMENT');
    END IF;
    IF NEW.instructions IS NULL OR btrim(NEW.instructions)='' THEN
        NEW.instructions:=COALESCE(NULLIF(btrim(NEW.description),''),NEW.title);
    END IF;
    IF NEW.assignment_kind='ASSIGNMENT' AND NEW.allowed_file_types IS NULL THEN
        NEW.allowed_file_types:=ARRAY[]::text[];
    ELSIF NEW.assignment_kind='HOMEWORK' THEN
        NEW.allowed_file_types:=NULL;
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_assignment_legacy_compat ON app.assignment;
CREATE TRIGGER trg_assignment_legacy_compat
    BEFORE INSERT OR UPDATE ON app.assignment
    FOR EACH ROW EXECUTE FUNCTION app.fn_assignment_legacy_compat();
CREATE OR REPLACE FUNCTION app.fn_assignment_scope_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_section record; v_offering record;
BEGIN
    -- Legacy subject-group assignments do not have a section_id; preserve their existing scope.
    IF NEW.section_id IS NULL THEN RETURN NEW; END IF;
    SELECT institution_id, academic_session_id, semester_id, class_id
      INTO v_section FROM app.section
     WHERE tenant_id = NEW.tenant_id AND section_id = NEW.section_id;
    SELECT institution_id, academic_session_id, semester_id, class_id
      INTO v_offering FROM app.course_offering
     WHERE tenant_id = NEW.tenant_id AND course_offering_id = NEW.course_offering_id;
    IF v_section IS NULL OR v_offering IS NULL
       OR v_section.institution_id <> NEW.institution_id
       OR v_offering.institution_id <> NEW.institution_id
       OR v_section.academic_session_id <> v_offering.academic_session_id
       OR v_section.semester_id IS DISTINCT FROM v_offering.semester_id
       OR v_section.class_id IS DISTINCT FROM v_offering.class_id THEN
        RAISE EXCEPTION 'ASSIGNMENT_ACADEMIC_SCOPE_MISMATCH'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_assignment_scope_guard ON app.assignment;
CREATE TRIGGER trg_assignment_scope_guard
    BEFORE INSERT OR UPDATE ON app.assignment
    FOR EACH ROW EXECUTE FUNCTION app.fn_assignment_scope_guard();

CREATE INDEX ix_assignment_section_due ON app.assignment(tenant_id, section_id, due_at DESC);
CREATE INDEX ix_assignment_teacher ON app.assignment(tenant_id, teacher_user_id, status);

-- Add T7 submission metadata to the V3.3 submission record. A compatibility trigger
-- fills institution/enrollment keys required by the legacy submission and feedback tables.
ALTER TABLE app.assignment_submission
    ADD COLUMN IF NOT EXISTS student_profile_id uuid,
    ADD COLUMN IF NOT EXISTS file_id uuid,
    ADD COLUMN IF NOT EXISTS file_sha256 char(64),
    ADD COLUMN IF NOT EXISTS marks_awarded numeric(8,2),
    ADD COLUMN IF NOT EXISTS late_penalty_marks numeric(8,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS final_marks numeric(8,2),
    ADD COLUMN IF NOT EXISTS feedback text,
    ADD COLUMN IF NOT EXISTS graded_by_user_id uuid,
    ADD COLUMN IF NOT EXISTS graded_at timestamptz;
ALTER TABLE app.assignment_submission DROP CONSTRAINT IF EXISTS ck_assignment_submission_status;
ALTER TABLE app.assignment_submission ADD CONSTRAINT ck_assignment_submission_status
    CHECK (status IN ('SUBMITTED','EVALUATED','GRADED','RETURNED'));
ALTER TABLE app.assignment_submission ADD CONSTRAINT ck_assignment_submission_hash
    CHECK (file_sha256 IS NULL OR file_sha256 ~ '^[0-9a-f]{64}$');
ALTER TABLE app.assignment_submission ADD CONSTRAINT ck_assignment_submission_marks
    CHECK (marks_awarded IS NULL OR marks_awarded >= 0);
ALTER TABLE app.assignment_submission ADD CONSTRAINT ck_assignment_submission_penalty
    CHECK (late_penalty_marks >= 0);
ALTER TABLE app.assignment_submission ADD CONSTRAINT ck_assignment_submission_final
    CHECK (final_marks IS NULL OR final_marks >= 0);
ALTER TABLE app.assignment_submission ADD CONSTRAINT ck_assignment_submission_grade_pair
    CHECK ((graded_by_user_id IS NULL) = (graded_at IS NULL));
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.assignment_submission'::regclass AND conname='uq_assignment_submission_student')
 THEN ALTER TABLE app.assignment_submission ADD CONSTRAINT uq_assignment_submission_student
      UNIQUE (tenant_id,assignment_id,student_profile_id); END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.assignment_submission'::regclass AND conname='fk_assignment_submission_student')
 THEN ALTER TABLE app.assignment_submission ADD CONSTRAINT fk_assignment_submission_student
      FOREIGN KEY (tenant_id,student_profile_id) REFERENCES app.student_profile(tenant_id,student_profile_id) ON DELETE RESTRICT; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.assignment_submission'::regclass AND conname='fk_assignment_submission_file')
 THEN ALTER TABLE app.assignment_submission ADD CONSTRAINT fk_assignment_submission_file
      FOREIGN KEY (tenant_id,file_id) REFERENCES app.file_object(tenant_id,file_id) ON DELETE RESTRICT; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='app.assignment_submission'::regclass AND conname='fk_assignment_submission_grader')
 THEN ALTER TABLE app.assignment_submission ADD CONSTRAINT fk_assignment_submission_grader
      FOREIGN KEY (tenant_id,graded_by_user_id) REFERENCES app.user_account(tenant_id,user_account_id) ON DELETE RESTRICT; END IF;
END $$;
CREATE OR REPLACE FUNCTION app.fn_assignment_submission_legacy_compat()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.student_profile_id IS NOT NULL THEN
        SELECT a.institution_id,e.enrollment_id
          INTO NEW.institution_id,NEW.enrollment_id
          FROM app.assignment a
          JOIN app.student_enrollment e ON e.tenant_id=a.tenant_id
             AND e.section_id=a.section_id AND e.student_profile_id=NEW.student_profile_id
         WHERE a.tenant_id=NEW.tenant_id AND a.assignment_id=NEW.assignment_id
           AND e.outcome='IN_PROGRESS' AND e.ended_on IS NULL
         ORDER BY e.started_on DESC LIMIT 1;
        IF NEW.institution_id IS NULL OR NEW.enrollment_id IS NULL THEN
            RAISE EXCEPTION 'ASSIGNMENT_STUDENT_ENROLLMENT_NOT_FOUND' USING ERRCODE='23503';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_assignment_submission_legacy_compat ON app.assignment_submission;
CREATE TRIGGER trg_assignment_submission_legacy_compat
    BEFORE INSERT OR UPDATE ON app.assignment_submission
    FOR EACH ROW EXECUTE FUNCTION app.fn_assignment_submission_legacy_compat();
CREATE INDEX ix_assignment_submission_assignment ON app.assignment_submission(tenant_id, assignment_id, submitted_at DESC);
CREATE INDEX ix_assignment_submission_student ON app.assignment_submission(tenant_id, student_profile_id, submitted_at DESC);

-- Rubric grades and plagiarism observations are separate records so an assignment's
-- submission and grading history is auditable without rewriting the uploaded artifact.
CREATE TABLE app.submission_grade (
    tenant_id uuid NOT NULL,
    submission_grade_id uuid NOT NULL,
    submission_id uuid NOT NULL,
    rubric_item_key text NOT NULL,
    marks_awarded numeric(8,2) NOT NULL,
    comments text,
    graded_by_user_id uuid NOT NULL,
    graded_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_submission_grade PRIMARY KEY (tenant_id, submission_grade_id),
    CONSTRAINT fk_submission_grade_submission FOREIGN KEY (tenant_id, submission_id) REFERENCES app.assignment_submission(tenant_id, submission_id) ON DELETE RESTRICT,
    CONSTRAINT fk_submission_grade_grader FOREIGN KEY (tenant_id, graded_by_user_id) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT ck_submission_grade_marks CHECK (marks_awarded >= 0),
    CONSTRAINT ck_submission_grade_key CHECK (char_length(btrim(rubric_item_key)) BETWEEN 1 AND 100)
);
CREATE INDEX ix_submission_grade_submission ON app.submission_grade(tenant_id, submission_id);

CREATE TABLE app.plagiarism_log (
    tenant_id uuid NOT NULL,
    plagiarism_log_id uuid NOT NULL,
    submission_id uuid NOT NULL,
    provider text NOT NULL,
    similarity_percent numeric(5,2) NOT NULL,
    reference_id text,
    result_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    checked_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_plagiarism_log PRIMARY KEY (tenant_id, plagiarism_log_id),
    CONSTRAINT fk_plagiarism_log_submission FOREIGN KEY (tenant_id, submission_id) REFERENCES app.assignment_submission(tenant_id, submission_id) ON DELETE RESTRICT,
    CONSTRAINT ck_plagiarism_similarity CHECK (similarity_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_plagiarism_result CHECK (jsonb_typeof(result_json) = 'object')
);
CREATE INDEX ix_plagiarism_log_submission ON app.plagiarism_log(tenant_id, submission_id, checked_at DESC);

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['assignment','assignment_submission','submission_grade','plagiarism_log'] LOOP
    EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE app.%I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format('DROP POLICY IF EXISTS p_tenant ON app.%I', t);
    EXECUTE format('CREATE POLICY p_tenant ON app.%I USING (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid)', t);
  END LOOP;
END $;

-- Institution scope is enforced in PostgreSQL as well as by service authorization.
CREATE POLICY p_institution ON app.assignment AS RESTRICTIVE
USING (current_setting('app.institution_scope', true) = 'ALL'
       OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))
WITH CHECK (current_setting('app.institution_scope', true) = 'ALL'
       OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]));

CREATE POLICY p_institution ON app.assignment_submission AS RESTRICTIVE
USING (EXISTS (SELECT 1 FROM app.assignment a
                WHERE a.tenant_id = assignment_submission.tenant_id
                  AND a.assignment_id = assignment_submission.assignment_id
                  AND (current_setting('app.institution_scope', true) = 'ALL'
                       OR a.institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))))
WITH CHECK (EXISTS (SELECT 1 FROM app.assignment a
                     WHERE a.tenant_id = assignment_submission.tenant_id
                       AND a.assignment_id = assignment_submission.assignment_id
                       AND (current_setting('app.institution_scope', true) = 'ALL'
                            OR a.institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))));

CREATE POLICY p_institution ON app.submission_grade AS RESTRICTIVE
USING (EXISTS (SELECT 1 FROM app.assignment_submission s
                JOIN app.assignment a ON a.tenant_id = s.tenant_id AND a.assignment_id = s.assignment_id
                WHERE s.tenant_id = submission_grade.tenant_id
                  AND s.submission_id = submission_grade.submission_id
                  AND (current_setting('app.institution_scope', true) = 'ALL'
                       OR a.institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))))
WITH CHECK (EXISTS (SELECT 1 FROM app.assignment_submission s
                     JOIN app.assignment a ON a.tenant_id = s.tenant_id AND a.assignment_id = s.assignment_id
                     WHERE s.tenant_id = submission_grade.tenant_id
                       AND s.submission_id = submission_grade.submission_id
                       AND (current_setting('app.institution_scope', true) = 'ALL'
                            OR a.institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))));

CREATE POLICY p_institution ON app.plagiarism_log AS RESTRICTIVE
USING (EXISTS (SELECT 1 FROM app.assignment_submission s
                JOIN app.assignment a ON a.tenant_id = s.tenant_id AND a.assignment_id = s.assignment_id
                WHERE s.tenant_id = plagiarism_log.tenant_id
                  AND s.submission_id = plagiarism_log.submission_id
                  AND (current_setting('app.institution_scope', true) = 'ALL'
                       OR a.institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))))
WITH CHECK (EXISTS (SELECT 1 FROM app.assignment_submission s
                     JOIN app.assignment a ON a.tenant_id = s.tenant_id AND a.assignment_id = s.assignment_id
                     WHERE s.tenant_id = plagiarism_log.tenant_id
                       AND s.submission_id = plagiarism_log.submission_id
                       AND (current_setting('app.institution_scope', true) = 'ALL'
                            OR a.institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))));

GRANT SELECT, INSERT, UPDATE ON app.assignment TO edu_app;
GRANT SELECT, INSERT, UPDATE ON app.assignment_submission TO edu_app;
GRANT SELECT, INSERT ON app.submission_grade TO edu_app;
GRANT SELECT, INSERT ON app.plagiarism_log TO edu_app;
GRANT SELECT ON app.assignment, app.assignment_submission, app.submission_grade, app.plagiarism_log TO edu_report;

COMMENT ON TABLE app.assignment IS 'T7 ACT-055..ACT-060: published coursework assignments and weighted rubric.';
COMMENT ON TABLE app.assignment_submission IS 'T7 ACT-061..ACT-065: immutable file reference, checksum, deadline flag and grading result.';
COMMIT;
