-- B3.0: persist optional institution-specific staff ID formats alongside the
-- existing app.student_code_sequence used for student formats.
CREATE UNIQUE INDEX IF NOT EXISTS ux_student_code_sequence_institution
    ON app.student_code_sequence (tenant_id, institution_id);

CREATE TABLE IF NOT EXISTS app.staff_code_sequence (
    tenant_id uuid NOT NULL,
    staff_code_sequence_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    pattern text NOT NULL,
    last_value bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_staff_code_sequence PRIMARY KEY (tenant_id, staff_code_sequence_id),
    CONSTRAINT ck_staff_code_sequence_pattern CHECK (char_length(pattern) BETWEEN 1 AND 60),
    CONSTRAINT ck_staff_code_sequence_last_value CHECK (last_value >= 0),
    CONSTRAINT ck_staff_code_sequence_row_version CHECK (row_version >= 1),
    CONSTRAINT ck_staff_code_sequence_upd CHECK (updated_at >= created_at),
    CONSTRAINT uq_staff_code_sequence_tid UNIQUE (tenant_id, staff_code_sequence_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_staff_code_sequence_institution
    ON app.staff_code_sequence (tenant_id, institution_id);

CREATE TRIGGER trg_touch BEFORE UPDATE ON app.staff_code_sequence
    FOR EACH ROW EXECUTE FUNCTION app.fn_touch();

ALTER TABLE app.staff_code_sequence ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.staff_code_sequence FORCE ROW LEVEL SECURITY;

CREATE POLICY p_tenant ON app.staff_code_sequence
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

CREATE POLICY p_institution ON app.staff_code_sequence AS RESTRICTIVE
    USING (institution_id IS NULL OR current_setting('app.institution_scope', true) = 'ALL'
        OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))
    WITH CHECK (institution_id IS NULL OR current_setting('app.institution_scope', true) = 'ALL'
        OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]));

GRANT SELECT, INSERT, UPDATE, DELETE ON app.staff_code_sequence TO edu_app;
GRANT SELECT ON app.staff_code_sequence TO edu_report;
