-- EDU 360 V4.1 / T7 — examinations, hall tickets, moderated marks and report cards.
-- This migration deliberately does not calculate fee balances. Until T8 supplies
-- app.fn_fee_balance(uuid, uuid), report-card publication is blocked (fail closed).
-- Hall tickets default to ineligible; the T7 service must verify T6 attendance before enabling.
BEGIN;

CREATE TABLE app.exam_cycle (
    tenant_id uuid NOT NULL,
    exam_cycle_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    academic_session_id uuid NOT NULL,
    semester_id uuid NOT NULL,
    cycle_name text NOT NULL,
    cycle_type text NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    status text NOT NULL DEFAULT 'DRAFT',
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_exam_cycle PRIMARY KEY (tenant_id, exam_cycle_id),
    CONSTRAINT fk_exam_cycle_tenant FOREIGN KEY (tenant_id) REFERENCES app.tenant_local(tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_cycle_institution FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution(tenant_id, institution_id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_cycle_session FOREIGN KEY (tenant_id, academic_session_id) REFERENCES app.academic_session(tenant_id, academic_session_id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_cycle_semester FOREIGN KEY (tenant_id, institution_id, semester_id) REFERENCES app.semester(tenant_id, institution_id, semester_id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_cycle_creator FOREIGN KEY (tenant_id, created_by) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT ck_exam_cycle_name CHECK (char_length(btrim(cycle_name)) BETWEEN 1 AND 120),
    CONSTRAINT ck_exam_cycle_type CHECK (cycle_type IN ('INTERNAL','MID_TERM','FINAL_SEMESTER')),
    CONSTRAINT ck_exam_cycle_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_exam_cycle_status CHECK (status IN ('DRAFT','SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT ck_exam_cycle_row_version CHECK (row_version >= 1)
);
CREATE INDEX ix_exam_cycle_semester ON app.exam_cycle(tenant_id, institution_id, semester_id, start_date DESC);

CREATE TABLE app.exam_schedule (
    tenant_id uuid NOT NULL,
    exam_schedule_id uuid NOT NULL,
    exam_cycle_id uuid NOT NULL,
    course_offering_id uuid NOT NULL,
    exam_date date NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL,
    max_marks numeric(8,2) NOT NULL,
    pass_marks numeric(8,2) NOT NULL,
    room_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_exam_schedule PRIMARY KEY (tenant_id, exam_schedule_id),
    CONSTRAINT fk_exam_schedule_cycle FOREIGN KEY (tenant_id, exam_cycle_id) REFERENCES app.exam_cycle(tenant_id, exam_cycle_id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_schedule_offering FOREIGN KEY (tenant_id, course_offering_id) REFERENCES app.course_offering(tenant_id, course_offering_id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_schedule_creator FOREIGN KEY (tenant_id, created_by) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT ck_exam_schedule_time CHECK (end_time > start_time),
    CONSTRAINT ck_exam_schedule_marks CHECK (max_marks > 0 AND pass_marks >= 0 AND pass_marks <= max_marks),
    CONSTRAINT ck_exam_schedule_row_version CHECK (row_version >= 1),
    CONSTRAINT uq_exam_schedule_course UNIQUE (tenant_id, exam_cycle_id, course_offering_id)
);
CREATE INDEX ix_exam_schedule_date ON app.exam_schedule(tenant_id, exam_cycle_id, exam_date, start_time);

CREATE OR REPLACE FUNCTION app.fn_exam_schedule_scope_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_institution_id uuid; v_semester_id uuid; v_start date; v_end date;
BEGIN
    SELECT institution_id, semester_id, start_date, end_date
      INTO v_institution_id, v_semester_id, v_start, v_end
      FROM app.exam_cycle
     WHERE tenant_id = NEW.tenant_id AND exam_cycle_id = NEW.exam_cycle_id;
    IF v_institution_id IS NULL THEN
        RAISE EXCEPTION 'EXAM_CYCLE_NOT_FOUND' USING ERRCODE = '23503';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM app.course_offering co
         WHERE co.tenant_id = NEW.tenant_id
           AND co.course_offering_id = NEW.course_offering_id
           AND co.institution_id = v_institution_id
           AND co.semester_id = v_semester_id
    ) THEN
        RAISE EXCEPTION 'EXAM_COURSE_SCOPE_MISMATCH' USING ERRCODE = '23514';
    END IF;
    IF NEW.exam_date < v_start OR NEW.exam_date > v_end THEN
        RAISE EXCEPTION 'EXAM_DATE_OUTSIDE_CYCLE' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_exam_schedule_scope_guard
    BEFORE INSERT OR UPDATE ON app.exam_schedule
    FOR EACH ROW EXECUTE FUNCTION app.fn_exam_schedule_scope_guard();

-- Room collisions are rejected transactionally, independently of UI validation.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.exam_schedule'::regclass
                    AND conname = 'ex_exam_schedule_room_no_overlap') THEN
        ALTER TABLE app.exam_schedule
          ADD CONSTRAINT ex_exam_schedule_room_no_overlap
          EXCLUDE USING gist (
              tenant_id WITH =,
              room_id WITH =,
              (tsrange(exam_date + start_time, exam_date + end_time, '[)')) WITH &&
          ) WHERE (room_id IS NOT NULL);
    END IF;
    IF to_regclass('app.room') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'app.exam_schedule'::regclass
                        AND conname = 'fk_exam_schedule_room') THEN
        ALTER TABLE app.exam_schedule ADD CONSTRAINT fk_exam_schedule_room
          FOREIGN KEY (tenant_id, room_id) REFERENCES app.room(tenant_id, room_id) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE TABLE app.exam_invigilator (
    tenant_id uuid NOT NULL,
    exam_invigilator_id uuid NOT NULL,
    exam_schedule_id uuid NOT NULL,
    staff_user_id uuid NOT NULL,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    assigned_by uuid NOT NULL,
    CONSTRAINT pk_exam_invigilator PRIMARY KEY (tenant_id, exam_invigilator_id),
    CONSTRAINT fk_exam_invigilator_schedule FOREIGN KEY (tenant_id, exam_schedule_id) REFERENCES app.exam_schedule(tenant_id, exam_schedule_id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_invigilator_staff FOREIGN KEY (tenant_id, staff_user_id) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_invigilator_assigner FOREIGN KEY (tenant_id, assigned_by) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT uq_exam_invigilator UNIQUE (tenant_id, exam_schedule_id, staff_user_id)
);

CREATE OR REPLACE FUNCTION app.fn_exam_invigilator_conflict_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM app.exam_invigilator other_i
          JOIN app.exam_schedule other_s
            ON other_s.tenant_id = other_i.tenant_id
           AND other_s.exam_schedule_id = other_i.exam_schedule_id
          JOIN app.exam_schedule new_s
            ON new_s.tenant_id = NEW.tenant_id
           AND new_s.exam_schedule_id = NEW.exam_schedule_id
         WHERE other_i.tenant_id = NEW.tenant_id
           AND other_i.staff_user_id = NEW.staff_user_id
           AND other_i.exam_invigilator_id <> COALESCE(NEW.exam_invigilator_id, '00000000-0000-0000-0000-000000000000'::uuid)
           AND other_s.exam_date = new_s.exam_date
           AND tsrange(other_s.exam_date + other_s.start_time, other_s.exam_date + other_s.end_time, '[)')
               && tsrange(new_s.exam_date + new_s.start_time, new_s.exam_date + new_s.end_time, '[)')
    ) THEN
        RAISE EXCEPTION 'INVIGILATOR_DOUBLE_BOOKED' USING ERRCODE = '23P01';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_exam_invigilator_conflict
    BEFORE INSERT OR UPDATE ON app.exam_invigilator
    FOR EACH ROW EXECUTE FUNCTION app.fn_exam_invigilator_conflict_guard();

CREATE TABLE app.hall_ticket (
    tenant_id uuid NOT NULL,
    hall_ticket_id uuid NOT NULL,
    exam_cycle_id uuid NOT NULL,
    student_profile_id uuid NOT NULL,
    ticket_number text NOT NULL,
    is_eligible boolean NOT NULL DEFAULT false,
    ineligible_reason text NOT NULL DEFAULT 'ATTENDANCE_DATA_UNAVAILABLE',
    qr_code_hash char(64) NOT NULL,
    issued_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    CONSTRAINT pk_hall_ticket PRIMARY KEY (tenant_id, hall_ticket_id),
    CONSTRAINT fk_hall_ticket_cycle FOREIGN KEY (tenant_id, exam_cycle_id) REFERENCES app.exam_cycle(tenant_id, exam_cycle_id) ON DELETE RESTRICT,
    CONSTRAINT fk_hall_ticket_student FOREIGN KEY (tenant_id, student_profile_id) REFERENCES app.student_profile(tenant_id, student_profile_id) ON DELETE RESTRICT,
    CONSTRAINT fk_hall_ticket_creator FOREIGN KEY (tenant_id, created_by) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT uq_hall_ticket_number UNIQUE (tenant_id, ticket_number),
    CONSTRAINT uq_hall_ticket_student UNIQUE (tenant_id, exam_cycle_id, student_profile_id),
    CONSTRAINT ck_hall_ticket_hash CHECK (qr_code_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_hall_ticket_eligibility CHECK (
        (is_eligible AND ineligible_reason = 'ELIGIBLE' AND issued_at IS NOT NULL)
        OR (NOT is_eligible AND ineligible_reason <> 'ELIGIBLE')
    )
);

CREATE TABLE app.exam_mark (
    tenant_id uuid NOT NULL,
    exam_mark_id uuid NOT NULL,
    exam_schedule_id uuid NOT NULL,
    student_profile_id uuid NOT NULL,
    marks_obtained numeric(8,2),
    is_absent boolean NOT NULL DEFAULT false,
    moderated_marks numeric(8,2),
    course_teacher_user_id uuid NOT NULL,
    external_examiner_user_id uuid,
    course_teacher_decision text NOT NULL DEFAULT 'PENDING',
    external_examiner_decision text NOT NULL DEFAULT 'PENDING',
    course_teacher_comment text,
    external_examiner_comment text,
    status text NOT NULL DEFAULT 'ENTERED',
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_exam_mark PRIMARY KEY (tenant_id, exam_mark_id),
    CONSTRAINT fk_exam_mark_schedule FOREIGN KEY (tenant_id, exam_schedule_id) REFERENCES app.exam_schedule(tenant_id, exam_schedule_id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_mark_student FOREIGN KEY (tenant_id, student_profile_id) REFERENCES app.student_profile(tenant_id, student_profile_id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_mark_teacher FOREIGN KEY (tenant_id, course_teacher_user_id) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_mark_external FOREIGN KEY (tenant_id, external_examiner_user_id) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_mark_creator FOREIGN KEY (tenant_id, created_by) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT uq_exam_mark_student UNIQUE (tenant_id, exam_schedule_id, student_profile_id),
    CONSTRAINT ck_exam_mark_absent CHECK (NOT is_absent OR marks_obtained IS NULL),
    CONSTRAINT ck_exam_mark_values CHECK ((marks_obtained IS NULL OR marks_obtained >= 0) AND (moderated_marks IS NULL OR moderated_marks >= 0)),
    CONSTRAINT ck_exam_mark_teacher_decision CHECK (course_teacher_decision IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT ck_exam_mark_external_decision CHECK (external_examiner_decision IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT ck_exam_mark_status CHECK (status IN ('ENTERED','COURSE_TEACHER_APPROVED','MODERATION_PENDING','MODERATED','APPROVED','REJECTED')),
    CONSTRAINT ck_exam_mark_external_independent CHECK (external_examiner_user_id IS NULL OR external_examiner_user_id <> course_teacher_user_id),
    CONSTRAINT ck_exam_mark_row_version CHECK (row_version >= 1)
);
CREATE INDEX ix_exam_mark_schedule_status ON app.exam_mark(tenant_id, exam_schedule_id, status);
CREATE INDEX ix_exam_mark_student ON app.exam_mark(tenant_id, student_profile_id, exam_schedule_id);

CREATE TABLE app.grade_scale (
    tenant_id uuid NOT NULL,
    grade_scale_id uuid NOT NULL,
    institution_id uuid,
    parent_grade_scale_id uuid,
    scale_name text NOT NULL,
    grade_ranges_json jsonb NOT NULL,
    is_active boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_grade_scale PRIMARY KEY (tenant_id, grade_scale_id),
    CONSTRAINT fk_grade_scale_tenant FOREIGN KEY (tenant_id) REFERENCES app.tenant_local(tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_grade_scale_institution FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution(tenant_id, institution_id) ON DELETE RESTRICT,
    CONSTRAINT fk_grade_scale_parent FOREIGN KEY (tenant_id, parent_grade_scale_id) REFERENCES app.grade_scale(tenant_id, grade_scale_id) ON DELETE RESTRICT,
    CONSTRAINT fk_grade_scale_creator FOREIGN KEY (tenant_id, created_by) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT ck_grade_scale_name CHECK (char_length(btrim(scale_name)) BETWEEN 1 AND 120),
    CONSTRAINT ck_grade_scale_ranges CHECK (jsonb_typeof(grade_ranges_json) = 'array' AND jsonb_array_length(grade_ranges_json) > 0),
    CONSTRAINT ck_grade_scale_row_version CHECK (row_version >= 1)
);

CREATE TABLE app.report_card (
    tenant_id uuid NOT NULL,
    report_card_id uuid NOT NULL,
    institution_id uuid NOT NULL,
    student_profile_id uuid NOT NULL,
    academic_session_id uuid NOT NULL,
    semester_id uuid NOT NULL,
    sgpa numeric(6,4),
    cgpa numeric(6,4),
    total_credits numeric(8,2) NOT NULL DEFAULT 0,
    grade_letter text,
    verification_hash char(64) NOT NULL,
    status text NOT NULL DEFAULT 'DRAFT',
    published_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL,
    row_version integer NOT NULL DEFAULT 1,
    CONSTRAINT pk_report_card PRIMARY KEY (tenant_id, report_card_id),
    CONSTRAINT fk_report_card_institution FOREIGN KEY (tenant_id, institution_id) REFERENCES app.institution(tenant_id, institution_id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_card_student FOREIGN KEY (tenant_id, institution_id, student_profile_id) REFERENCES app.student_profile(tenant_id, institution_id, student_profile_id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_card_session FOREIGN KEY (tenant_id, academic_session_id) REFERENCES app.academic_session(tenant_id, academic_session_id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_card_semester FOREIGN KEY (tenant_id, institution_id, semester_id) REFERENCES app.semester(tenant_id, institution_id, semester_id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_card_creator FOREIGN KEY (tenant_id, created_by) REFERENCES app.user_account(tenant_id, user_account_id) ON DELETE RESTRICT,
    CONSTRAINT uq_report_card_student_term UNIQUE (tenant_id, student_profile_id, semester_id),
    CONSTRAINT ck_report_card_status CHECK (status IN ('DRAFT','MODERATED','PUBLISHED')),
    CONSTRAINT ck_report_card_credits CHECK (total_credits >= 0),
    CONSTRAINT ck_report_card_hash CHECK (verification_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_report_card_published CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL)),
    CONSTRAINT ck_report_card_row_version CHECK (row_version >= 1)
);

-- Resolve a semester to its canonical term-lock record. Missing lock rows are locked.
CREATE OR REPLACE FUNCTION app.fn_exam_term_is_locked(p_tenant_id uuid, p_semester_id uuid)
RETURNS boolean LANGUAGE sql STABLE AS $$
    SELECT COALESCE((
        SELECT app.is_term_locked(tl.term_lock_id)
          FROM app.term_lock tl
         WHERE tl.tenant_id = p_tenant_id AND tl.semester_id = p_semester_id
    ), true)
$$;

CREATE OR REPLACE FUNCTION app.fn_guard_exam_mark_term_lock()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_semester_id uuid;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'EXAM_MARK_DELETE_FORBIDDEN: marks must be corrected through the moderation workflow'
            USING ERRCODE = 'P0001';
    END IF;
    SELECT ec.semester_id INTO v_semester_id
      FROM app.exam_schedule es
      JOIN app.exam_cycle ec ON ec.tenant_id = es.tenant_id AND ec.exam_cycle_id = es.exam_cycle_id
     WHERE es.tenant_id = NEW.tenant_id AND es.exam_schedule_id = NEW.exam_schedule_id;
    IF v_semester_id IS NULL OR app.fn_exam_term_is_locked(NEW.tenant_id, v_semester_id) THEN
        RAISE EXCEPTION 'TERM_LOCKED: academic term is locked or its lock record is unavailable'
            USING ERRCODE = 'P0001';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_exam_mark_term_lock
    BEFORE INSERT OR UPDATE OR DELETE ON app.exam_mark
    FOR EACH ROW EXECUTE FUNCTION app.fn_guard_exam_mark_term_lock();

-- Integration contract with T8: implement app.fn_fee_balance(tenant_id, student_profile_id)
-- returning numeric outstanding balance. Until it exists, publication is prohibited.
CREATE OR REPLACE FUNCTION app.fn_publish_gate(p_tenant_id uuid, p_report_card_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE
    v_student_id uuid;
    v_semester_id uuid;
    v_fee_balance numeric;
    v_fee_fn regprocedure;
BEGIN
    SELECT student_profile_id, semester_id
      INTO v_student_id, v_semester_id
      FROM app.report_card
     WHERE tenant_id = p_tenant_id AND report_card_id = p_report_card_id
     FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'REPORT_CARD_NOT_FOUND' USING ERRCODE = 'P0002';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM app.exam_schedule es
          JOIN app.exam_cycle ec ON ec.tenant_id = es.tenant_id AND ec.exam_cycle_id = es.exam_cycle_id
          JOIN app.exam_mark em ON em.tenant_id = es.tenant_id AND em.exam_schedule_id = es.exam_schedule_id
         WHERE ec.tenant_id = p_tenant_id AND ec.semester_id = v_semester_id
           AND em.status <> 'APPROVED'
    ) OR EXISTS (
        SELECT 1
          FROM app.exam_schedule es
          JOIN app.exam_cycle ec ON ec.tenant_id = es.tenant_id AND ec.exam_cycle_id = es.exam_cycle_id
         WHERE ec.tenant_id = p_tenant_id AND ec.semester_id = v_semester_id
           AND NOT EXISTS (SELECT 1 FROM app.exam_mark em
                            WHERE em.tenant_id = es.tenant_id AND em.exam_schedule_id = es.exam_schedule_id)
    ) THEN
        RAISE EXCEPTION 'PUBLISH_GATE_BLOCKED_MODERATION' USING ERRCODE = 'P0001';
    END IF;

    v_fee_fn := to_regprocedure('app.fn_fee_balance(uuid,uuid)');
    IF v_fee_fn IS NULL THEN
        RAISE EXCEPTION 'PUBLISH_GATE_BLOCKED_FEE_BALANCE_UNAVAILABLE'
            USING ERRCODE = 'P0001';
    END IF;
    EXECUTE 'SELECT app.fn_fee_balance($1, $2)' INTO v_fee_balance USING p_tenant_id, v_student_id;
    IF v_fee_balance IS NULL OR v_fee_balance < 0 THEN
        RAISE EXCEPTION 'PUBLISH_GATE_BLOCKED_FEE_BALANCE_INVALID'
            USING ERRCODE = 'P0001';
    END IF;
    IF v_fee_balance > 0 THEN
        RAISE EXCEPTION 'PUBLISH_GATE_BLOCKED_FEE_DUES' USING ERRCODE = 'P0001';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION app.fn_report_card_publish_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = 'PUBLISHED'
       AND (TG_OP = 'INSERT' OR OLD.status IS DISTINCT FROM 'PUBLISHED') THEN
        PERFORM app.fn_publish_gate(NEW.tenant_id, NEW.report_card_id);
        NEW.published_at := now();
    ELSIF NEW.status <> 'PUBLISHED' THEN
        NEW.published_at := NULL;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_report_card_publish_gate
    BEFORE INSERT OR UPDATE OF status ON app.report_card
    FOR EACH ROW EXECUTE FUNCTION app.fn_report_card_publish_guard();

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['exam_cycle','exam_schedule','exam_invigilator','hall_ticket','exam_mark','grade_scale','report_card'] LOOP
    EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE app.%I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format('DROP POLICY IF EXISTS p_tenant ON app.%I', t);
    EXECUTE format('CREATE POLICY p_tenant ON app.%I USING (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid)', t);
  END LOOP;
END $$;

-- Institution scope is independently enforced for all examination and results records.
CREATE POLICY p_institution ON app.exam_cycle AS RESTRICTIVE
USING (current_setting('app.institution_scope', true) = 'ALL'
       OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))
WITH CHECK (current_setting('app.institution_scope', true) = 'ALL'
       OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]));

CREATE POLICY p_institution ON app.exam_schedule AS RESTRICTIVE
USING (EXISTS (SELECT 1 FROM app.exam_cycle ec WHERE ec.tenant_id = exam_schedule.tenant_id
                AND ec.exam_cycle_id = exam_schedule.exam_cycle_id
                AND (current_setting('app.institution_scope', true) = 'ALL'
                     OR ec.institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))))
WITH CHECK (EXISTS (SELECT 1 FROM app.exam_cycle ec WHERE ec.tenant_id = exam_schedule.tenant_id
                     AND ec.exam_cycle_id = exam_schedule.exam_cycle_id
                     AND (current_setting('app.institution_scope', true) = 'ALL'
                          OR ec.institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))));

CREATE POLICY p_institution ON app.exam_invigilator AS RESTRICTIVE
USING (EXISTS (SELECT 1 FROM app.exam_schedule es JOIN app.exam_cycle ec
                ON ec.tenant_id = es.tenant_id AND ec.exam_cycle_id = es.exam_cycle_id
                WHERE es.tenant_id = exam_invigilator.tenant_id
                  AND es.exam_schedule_id = exam_invigilator.exam_schedule_id
                  AND (current_setting('app.institution_scope', true) = 'ALL'
                       OR ec.institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))))
WITH CHECK (EXISTS (SELECT 1 FROM app.exam_schedule es JOIN app.exam_cycle ec
                     ON ec.tenant_id = es.tenant_id AND ec.exam_cycle_id = es.exam_cycle_id
                     WHERE es.tenant_id = exam_invigilator.tenant_id
                       AND es.exam_schedule_id = exam_invigilator.exam_schedule_id
                       AND (current_setting('app.institution_scope', true) = 'ALL'
                            OR ec.institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))));

CREATE POLICY p_institution ON app.hall_ticket AS RESTRICTIVE
USING (EXISTS (SELECT 1 FROM app.exam_cycle ec WHERE ec.tenant_id = hall_ticket.tenant_id
                AND ec.exam_cycle_id = hall_ticket.exam_cycle_id
                AND (current_setting('app.institution_scope', true) = 'ALL'
                     OR ec.institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))))
WITH CHECK (EXISTS (SELECT 1 FROM app.exam_cycle ec WHERE ec.tenant_id = hall_ticket.tenant_id
                     AND ec.exam_cycle_id = hall_ticket.exam_cycle_id
                     AND (current_setting('app.institution_scope', true) = 'ALL'
                          OR ec.institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))));

CREATE POLICY p_institution ON app.exam_mark AS RESTRICTIVE
USING (EXISTS (SELECT 1 FROM app.exam_schedule es JOIN app.exam_cycle ec
                ON ec.tenant_id = es.tenant_id AND ec.exam_cycle_id = es.exam_cycle_id
                WHERE es.tenant_id = exam_mark.tenant_id
                  AND es.exam_schedule_id = exam_mark.exam_schedule_id
                  AND (current_setting('app.institution_scope', true) = 'ALL'
                       OR ec.institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))))
WITH CHECK (EXISTS (SELECT 1 FROM app.exam_schedule es JOIN app.exam_cycle ec
                     ON ec.tenant_id = es.tenant_id AND ec.exam_cycle_id = es.exam_cycle_id
                     WHERE es.tenant_id = exam_mark.tenant_id
                       AND es.exam_schedule_id = exam_mark.exam_schedule_id
                       AND (current_setting('app.institution_scope', true) = 'ALL'
                            OR ec.institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))));

CREATE POLICY p_institution ON app.grade_scale AS RESTRICTIVE
USING (institution_id IS NULL OR current_setting('app.institution_scope', true) = 'ALL'
       OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))
WITH CHECK (institution_id IS NULL OR current_setting('app.institution_scope', true) = 'ALL'
       OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]));

CREATE POLICY p_institution ON app.report_card AS RESTRICTIVE
USING (current_setting('app.institution_scope', true) = 'ALL'
       OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]))
WITH CHECK (current_setting('app.institution_scope', true) = 'ALL'
       OR institution_id = ANY (string_to_array(NULLIF(current_setting('app.institution_ids', true), ''), ',')::uuid[]));

GRANT SELECT, INSERT, UPDATE ON app.exam_cycle, app.exam_schedule, app.exam_invigilator, app.hall_ticket, app.exam_mark, app.grade_scale, app.report_card TO edu_app;
GRANT SELECT ON app.exam_cycle, app.exam_schedule, app.exam_invigilator, app.hall_ticket, app.exam_mark, app.grade_scale, app.report_card TO edu_report;
GRANT EXECUTE ON FUNCTION app.fn_exam_term_is_locked(uuid,uuid) TO edu_app, edu_report;
GRANT EXECUTE ON FUNCTION app.fn_publish_gate(uuid,uuid) TO edu_app, edu_report;

COMMENT ON TABLE app.exam_cycle IS 'T7 ACT-066..ACT-072: institutional exam cycle.';
COMMENT ON TABLE app.hall_ticket IS 'T7 ACT-073..ACT-075: fail-closed ticket eligibility until verified attendance data is supplied.';
COMMENT ON TABLE app.exam_mark IS 'T7 ACT-076..ACT-080: course-teacher and independent external-examiner moderation.';
COMMENT ON FUNCTION app.fn_publish_gate(uuid,uuid) IS 'T7 ACT-081..ACT-087: requires fully approved marks and the T8 fee-balance integration; missing integration blocks publication.';
COMMIT;
