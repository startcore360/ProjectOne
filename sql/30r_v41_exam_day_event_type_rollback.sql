-- Do not discard exam-day data during rollback.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM app.academic_calendar_event WHERE event_type = 'EXAM_DAY') THEN
        RAISE EXCEPTION 'EXAM_DAY_ROWS_EXIST: migrate or remove exam-day events before rollback';
    END IF;
END;
$$;

ALTER TABLE app.academic_calendar_event
    DROP CONSTRAINT ck_academic_calendar_event_r3,
    ADD CONSTRAINT ck_academic_calendar_event_r3
        CHECK (source_level <> 'INSTITUTION'
            OR (institution_id IS NOT NULL AND event_type IN ('LOCAL_HOLIDAY', 'LOCAL_EVENT')));

ALTER TABLE app.academic_calendar_event
    DROP CONSTRAINT ck_academic_calendar_event_r2,
    ADD CONSTRAINT ck_academic_calendar_event_r2
        CHECK (source_level <> 'UNIVERSITY'
            OR (institution_id IS NULL AND event_type IN ('SEMESTER_DATES', 'UNIVERSITY_HOLIDAY')));

ALTER TABLE app.academic_calendar_event
    DROP CONSTRAINT ck_academic_calendar_event_event_type,
    ADD CONSTRAINT ck_academic_calendar_event_event_type
        CHECK (event_type IN ('SEMESTER_DATES', 'UNIVERSITY_HOLIDAY', 'LOCAL_HOLIDAY', 'LOCAL_EVENT'));
