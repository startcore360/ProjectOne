-- EDU 360 V4.1 / T7 — assignment lifecycle and submissions.
-- Additive only. Tenant isolation is enforced with forced RLS and composite tenant keys.
BEGIN;

CREATE TABLE app.assignment (
    tenant_id uuid NOT NULL,
    assignment_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    section_id uuid NOT NULL,
    course_offering_id uuid NOT NULL,
    teacher_user_id uuid NOT NULL,
    title text NOT NULL,
    description text,
    max_marks numeric(8,2) NOT NULL,
    due_at timestamptz NOT NULL,
    allow_late_submission boolean NOT NULL DEFAULT false,
    late_penalty_percent_per_day numeric(5,2) NOT NULL DEFAULT 0,
    rubric_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    status text NOT NULL DEFAULT 'DRAFT',
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_assignment PRIMARY KEY (tenant_id, assignment_id),
    CONSTRAINT fk_assignment_tenant FOREIGN KEY (tenant_id) REFERENCES app.tenant_local(tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_institution FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution(tenant_id, institution_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_section FOREIGN KEY (tenant_id, institution_id, section_id) REFERENCES app.section(tenant_id, institution_id, section_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_offering FOREIGN KEY (tenant_id, institution_id, course_offering_id) REFERENCES app.course_offering(tenant_id, institution_id, course_offering_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_teacher FOREIGN KEY (tenant_id, teacher_user_id) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_creator FOREIGN KEY (tenant_id, created_by) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT ck_assignment_title CHECK (char_length(btrim(title)) BETWEEN 1 AND 200),
    CONSTRAINT ck_assignment_max_marks CHECK (max_marks > 0),
    CONSTRAINT ck_assignment_late_penalty CHECK (late_penalty_percent_per_day BETWEEN 0 AND 100),
    CONSTRAINT ck_assignment_rubric_object CHECK (jsonb_typeof(rubric_json) IN ('array','object')),
    CONSTRAINT ck_assignment_status CHECK (status IN ('DRAFT','PUBLISHED','CLOSED','ARCHIVED')),
    CONSTRAINT ck_assignment_row_version CHECK (row_version >= 1),
    CONSTRAINT ck_assignment_updated CHECK (updated_at >= created_at)
);
CREATE INDEX ix_assignment_section_due ON app.assignment(tenant_id, section_id, due_at DESC);
CREATE INDEX ix_assignment_teacher ON app.assignment(tenant_id, teacher_user_id, status);

CREATE TABLE app.assignment_submission (
    tenant_id uuid NOT NULL,
    submission_id uuid NOT NULL,
    assignment_id uuid NOT NULL,
    student_profile_id uuid NOT NULL,
    submitted_at timestamptz NOT NULL DEFAULT now(),
    file_id uuid NOT NULL,
    file_sha256 char(64) NOT NULL,
    is_late boolean NOT NULL,
    status text NOT NULL DEFAULT 'SUBMITTED',
    marks_awarded numeric(8,2),
    late_penalty_marks numeric(8,2) NOT NULL DEFAULT 0,
    final_marks numeric(8,2),
    feedback text,
    graded_by_user_id uuid,
    graded_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_assignment_submission PRIMARY KEY (tenant_id, submission_id),
    CONSTRAINT fk_assignment_submission_assignment FOREIGN KEY (tenant_id, assignment_id) REFERENCES app.assignment(tenant_id, assignment_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_submission_student FOREIGN KEY (tenant_id, student_profile_id) REFERENCES app.student_profile(tenant_id, student_profile_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_submission_file FOREIGN KEY (tenant_id, file_id) REFERENCES app.file_object(tenant_id, file_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_submission_creator FOREIGN KEY (tenant_id, created_by) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_submission_grader FOREIGN KEY (tenant_id, graded_by_user_id) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT uq_assignment_submission_student UNIQUE (tenant_id, assignment_id, student_profile_id),
    CONSTRAINT ck_assignment_submission_hash CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_assignment_submission_status CHECK (status IN ('SUBMITTED','GRADED','RETURNED')),
    CONSTRAINT ck_assignment_submission_marks CHECK (marks_awarded IS NULL OR marks_awarded >= 0),
    CONSTRAINT ck_assignment_submission_penalty CHECK (late_penalty_marks >= 0),
    CONSTRAINT ck_assignment_submission_final CHECK (final_marks IS NULL OR final_marks >= 0),
    CONSTRAINT ck_assignment_submission_grade_pair CHECK ((graded_by_user_id IS NULL) = (graded_at IS NULL)),
    CONSTRAINT ck_assignment_submission_row_version CHECK (row_version >= 1)
);
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
