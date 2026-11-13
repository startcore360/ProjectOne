-- DEC-23: represent exam days as typed calendar events, not title conventions.
ALTER TABLE app.academic_calendar_event
    DROP CONSTRAINT ck_academic_calendar_event_event_type,
    ADD CONSTRAINT ck_academic_calendar_event_event_type
        CHECK (event_type IN ('SEMESTER_DATES', 'UNIVERSITY_HOLIDAY', 'LOCAL_HOLIDAY', 'LOCAL_EVENT', 'EXAM_DAY'));

ALTER TABLE app.academic_calendar_event
    DROP CONSTRAINT ck_academic_calendar_event_r2,
    ADD CONSTRAINT ck_academic_calendar_event_r2
        CHECK (source_level <> 'UNIVERSITY'
            OR (institution_id IS NULL AND event_type IN ('SEMESTER_DATES', 'UNIVERSITY_HOLIDAY', 'EXAM_DAY')));

ALTER TABLE app.academic_calendar_event
    DROP CONSTRAINT ck_academic_calendar_event_r3,
    ADD CONSTRAINT ck_academic_calendar_event_r3
        CHECK (source_level <> 'INSTITUTION'
            OR (institution_id IS NOT NULL AND event_type IN ('LOCAL_HOLIDAY', 'LOCAL_EVENT', 'EXAM_DAY')));
