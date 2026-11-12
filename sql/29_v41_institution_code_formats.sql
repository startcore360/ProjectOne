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

-- PostgreSQL sequences are deliberately used for number allocation: nextval() is
-- non-transactional, so a rolled-back student/profile insert never reuses a code.
-- The sequence is created when the institution saves its format, before first use.
CREATE OR REPLACE FUNCTION app.ensure_institution_code_sequence(
    p_tenant_id uuid, p_institution_id uuid, p_kind text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, app
AS $$
DECLARE
    v_name text;
BEGIN
    IF p_kind = 'STUDENT' THEN
        IF NOT EXISTS (SELECT 1 FROM app.student_code_sequence
                       WHERE tenant_id=p_tenant_id AND institution_id=p_institution_id) THEN
            RAISE EXCEPTION 'STUDENT_ID_FORMAT_NOT_SET';
        END IF;
        v_name := 'edu_student_' || md5(p_tenant_id::text || ':' || p_institution_id::text);
    ELSIF p_kind = 'STAFF' THEN
        IF NOT EXISTS (SELECT 1 FROM app.staff_code_sequence
                       WHERE tenant_id=p_tenant_id AND institution_id=p_institution_id) THEN
            RAISE EXCEPTION 'STAFF_ID_FORMAT_NOT_SET';
        END IF;
        v_name := 'edu_staff_' || md5(p_tenant_id::text || ':' || p_institution_id::text);
    ELSE
        RAISE EXCEPTION 'INVALID_CODE_KIND';
    END IF;
    EXECUTE format('CREATE SEQUENCE IF NOT EXISTS app.%I START WITH 1 INCREMENT BY 1 NO CYCLE', v_name);
END;
$$;

CREATE OR REPLACE FUNCTION app.next_institution_code_value(
    p_tenant_id uuid, p_institution_id uuid, p_kind text)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, app
AS $$
DECLARE
    v_name text;
    v_value bigint;
BEGIN
    IF p_kind = 'STUDENT' THEN
        v_name := 'edu_student_' || md5(p_tenant_id::text || ':' || p_institution_id::text);
        IF NOT EXISTS (SELECT 1 FROM app.student_code_sequence
                       WHERE tenant_id=p_tenant_id AND institution_id=p_institution_id) THEN
            RAISE EXCEPTION 'STUDENT_ID_FORMAT_NOT_SET';
        END IF;
    ELSIF p_kind = 'STAFF' THEN
        v_name := 'edu_staff_' || md5(p_tenant_id::text || ':' || p_institution_id::text);
        IF NOT EXISTS (SELECT 1 FROM app.staff_code_sequence
                       WHERE tenant_id=p_tenant_id AND institution_id=p_institution_id) THEN
            RAISE EXCEPTION 'STAFF_ID_FORMAT_NOT_SET';
        END IF;
    ELSE
        RAISE EXCEPTION 'INVALID_CODE_KIND';
    END IF;
    EXECUTE format('SELECT nextval(%L::regclass)', 'app.' || v_name) INTO v_value;
    IF p_kind = 'STUDENT' THEN
        UPDATE app.student_code_sequence SET last_value=GREATEST(last_value,v_value)
         WHERE tenant_id=p_tenant_id AND institution_id=p_institution_id;
    ELSE
        UPDATE app.staff_code_sequence SET last_value=GREATEST(last_value,v_value)
         WHERE tenant_id=p_tenant_id AND institution_id=p_institution_id;
    END IF;
    RETURN v_value;
END;
$$;

CREATE OR REPLACE FUNCTION app.institution_code_format_locked(
    p_tenant_id uuid, p_institution_id uuid, p_kind text)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, app
AS $$
DECLARE
    v_name text;
    v_last bigint;
BEGIN
    IF p_kind = 'STUDENT' THEN
        v_name := 'edu_student_' || md5(p_tenant_id::text || ':' || p_institution_id::text);
        SELECT last_value INTO v_last FROM app.student_code_sequence
         WHERE tenant_id=p_tenant_id AND institution_id=p_institution_id;
    ELSIF p_kind = 'STAFF' THEN
        v_name := 'edu_staff_' || md5(p_tenant_id::text || ':' || p_institution_id::text);
        SELECT last_value INTO v_last FROM app.staff_code_sequence
         WHERE tenant_id=p_tenant_id AND institution_id=p_institution_id;
    ELSE
        RAISE EXCEPTION 'INVALID_CODE_KIND';
    END IF;
    IF COALESCE(v_last,0) > 0 THEN RETURN true; END IF;
    RETURN EXISTS (SELECT 1 FROM pg_sequences
                   WHERE schemaname='app' AND sequencename=v_name
                     AND last_value IS NOT NULL AND last_value > 0);
END;
$$;

GRANT EXECUTE ON FUNCTION app.ensure_institution_code_sequence(uuid,uuid,text) TO edu_app;
GRANT EXECUTE ON FUNCTION app.next_institution_code_value(uuid,uuid,text) TO edu_app;
GRANT EXECUTE ON FUNCTION app.institution_code_format_locked(uuid,uuid,text) TO edu_app;
